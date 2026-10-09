import { Router, raw } from "express";
import { z } from "zod";
import { getPlusConfig } from "../config/env.js";
import { HttpError } from "../lib/httpError.js";
import { requireAuth } from "../middleware/auth.js";
import { validate } from "../middleware/validate.js";
import { getEntitlement, getPlusRow, writePlus } from "../services/plusService.js";
import { cancelSubscriptionAtPeriodEnd, createCheckoutSession, PLUS_TRIAL_DAYS, verifyStripeSignature } from "../services/stripe.js";
import { handleStripeEvent, type StripeEvent } from "../services/stripeWebhook.js";

export const plusRouter = Router();

/** What this account has: { active, plan, validUntil, paywall }. While the paywall is off (early access) everyone is active. */
plusRouter.get("/plus", requireAuth, async (req, res, next) => {
  try {
    const entitlement = await getEntitlement(req.user!.id);
    // The free trial on offer for this account (days; it applies to the yearly plan only), so apps can say so before checkout: only with the paywall on and no Plus ever.
    const trialDays = entitlement.paywall && !(await getPlusRow(req.user!.id)) ? PLUS_TRIAL_DAYS : 0;
    res.json({ ...entitlement, trialDays });
  } catch (error) {
    next(error);
  }
});

const checkoutBody = z.object({ plan: z.enum(["monthly", "yearly", "lifetime"]) });

/** Starts a Stripe Checkout for one plan and answers with the hosted page's URL (to open, or to show as a QR code on the TV). */
plusRouter.post("/plus/checkout", requireAuth, validate({ body: checkoutBody }), async (req, res, next) => {
  try {
    const { plan } = req.validated!.body as z.infer<typeof checkoutBody>;
    if (!getPlusConfig().paywallOn) throw new HttpError(503, "Plus checkout isn't available yet");
    const entitlement = await getEntitlement(req.user!.id);
    if (entitlement.active && entitlement.plan === "lifetime") throw new HttpError(409, "You already have Plus for life");
    const row = await getPlusRow(req.user!.id);
    // A free trial is only for someone who has never had Plus (any past row, even a cancelled trial, means they have), and only on the yearly plan.
    const trialDays = !row && plan === "yearly" ? PLUS_TRIAL_DAYS : 0;
    const session = await createCheckoutSession({ userId: req.user!.id, email: req.user!.email, plan, stripeCustomerId: row?.stripeCustomerId ?? null, trialDays });
    // The page to open, plus what it will charge (after any trial) so a TV can show the price next to the QR code, and the free days.
    res.json({ url: session.url, amountTotal: session.amountTotal, currency: session.currency, trialDays });
  } catch (error) {
    next(error);
  }
});

/**
 * Cancels a monthly or yearly subscription at the end of the period already paid for: nothing is refunded and Plus stays on until then.
 * Answers with the account's new entitlement (cancelAtPeriodEnd true). Lifetime can't be cancelled (409); neither can an account with no
 * subscription (404). Cancelling twice is fine.
 */
plusRouter.post("/plus/cancel", requireAuth, async (req, res, next) => {
  try {
    if (!getPlusConfig().paywallOn) throw new HttpError(503, "Plus billing isn't available yet");
    const row = await getPlusRow(req.user!.id);
    if (row?.plan === "lifetime" && row.status === "active") throw new HttpError(409, "Lifetime Plus has no subscription to cancel");
    const entitlement = await getEntitlement(req.user!.id);
    if (!row || !entitlement.active || !row.stripeSubscriptionId) throw new HttpError(404, "There is no subscription to cancel");
    if (!row.cancelAtPeriodEnd) {
      await cancelSubscriptionAtPeriodEnd(row.stripeSubscriptionId);
      // Reflect it straight away; Stripe's own subscription.updated event then confirms the same thing.
      await writePlus({ userId: req.user!.id, plan: row.plan, status: "active", validUntil: row.validUntil, stripeCustomerId: row.stripeCustomerId, stripeSubscriptionId: row.stripeSubscriptionId, cancelAtPeriodEnd: true, eventAt: new Date() });
    }
    res.json(await getEntitlement(req.user!.id));
  } catch (error) {
    next(error);
  }
});

/**
 * Stripe's webhook. Mounted by app.ts BEFORE the JSON body parser, because the signature is over the exact raw bytes.
 * Anything not signed with our endpoint secret is refused with 400 and changes nothing.
 */
export const stripeWebhookRouter = Router();

stripeWebhookRouter.post("/stripe/webhook", raw({ type: "application/json", limit: "1mb" }), async (req, res, next) => {
  try {
    const secret = getPlusConfig().stripeWebhookSecret;
    if (!secret) throw new HttpError(503, "Webhook not configured");
    const body = req.body as Buffer;
    if (!Buffer.isBuffer(body) || !verifyStripeSignature(body, req.header("stripe-signature"), secret)) {
      throw new HttpError(400, "Invalid signature");
    }
    let event: StripeEvent;
    try {
      event = JSON.parse(body.toString("utf8")) as StripeEvent;
    } catch {
      throw new HttpError(400, "Invalid payload");
    }
    if (typeof event?.type !== "string" || typeof event.created !== "number") throw new HttpError(400, "Invalid payload");
    const outcome = await handleStripeEvent(event);
    res.json({ received: true, outcome });
  } catch (error) {
    next(error);
  }
});
