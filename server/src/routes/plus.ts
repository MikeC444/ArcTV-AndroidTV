import { Router, raw } from "express";
import { z } from "zod";
import { getPlusConfig } from "../config/env.js";
import { HttpError } from "../lib/httpError.js";
import { requireAuth } from "../middleware/auth.js";
import { validate } from "../middleware/validate.js";
import { getEntitlement, getPlusRow } from "../services/plusService.js";
import { createCheckoutSession, verifyStripeSignature } from "../services/stripe.js";
import { handleStripeEvent, type StripeEvent } from "../services/stripeWebhook.js";

export const plusRouter = Router();

/** What this account has: { active, plan, validUntil, paywall }. While the paywall is off (early access) everyone is active. */
plusRouter.get("/plus", requireAuth, async (req, res, next) => {
  try {
    res.json(await getEntitlement(req.user!.id));
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
    const session = await createCheckoutSession({ userId: req.user!.id, email: req.user!.email, plan, stripeCustomerId: row?.stripeCustomerId ?? null });
    // The page to open, plus what it will charge so a TV can show the price next to the QR code.
    res.json({ url: session.url, amountTotal: session.amountTotal, currency: session.currency });
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
