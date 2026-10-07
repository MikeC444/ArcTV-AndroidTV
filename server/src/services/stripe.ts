import { createHmac, timingSafeEqual } from "node:crypto";
import { getPlusConfig, type PlusPlanId } from "../config/env.js";
import { HttpError } from "../lib/httpError.js";

/** How far a webhook's timestamp may be from now before it is refused (Stripe's own default is five minutes). */
export const WEBHOOK_TOLERANCE_SECONDS = 300;

/**
 * Checks a Stripe webhook's `Stripe-Signature` header against the raw request body: `t=<unix>,v1=<hex hmac>[,v1=...]`,
 * where the HMAC-SHA256 is over `${t}.${body}` keyed with the endpoint's signing secret. Constant-time compare, and a
 * timestamp outside the tolerance is refused so a captured request can't be replayed later.
 */
export function verifyStripeSignature(rawBody: Buffer | string, header: string | undefined, secret: string, nowSeconds: number = Math.floor(Date.now() / 1000)): boolean {
  if (!header) return false;
  let timestamp: number | null = null;
  const signatures: string[] = [];
  for (const part of header.split(",")) {
    const [key, value] = part.split("=", 2);
    if (key === "t" && value) timestamp = Number(value);
    else if (key === "v1" && value) signatures.push(value);
  }
  if (timestamp === null || !Number.isFinite(timestamp) || signatures.length === 0) return false;
  if (Math.abs(nowSeconds - timestamp) > WEBHOOK_TOLERANCE_SECONDS) return false;
  const body = typeof rawBody === "string" ? rawBody : rawBody.toString("utf8");
  const expected = createHmac("sha256", secret).update(`${timestamp}.${body}`).digest();
  return signatures.some((candidate) => {
    if (!/^[0-9a-f]+$/i.test(candidate) || candidate.length !== expected.length * 2) return false;
    return timingSafeEqual(Buffer.from(candidate, "hex"), expected);
  });
}

/** Swappable for tests, which must never reach the real Stripe API. */
export type StripeFetch = (url: string, init: { method: string; headers: Record<string, string>; body: string }) => Promise<{ ok: boolean; status: number; json(): Promise<unknown> }>;
let stripeFetch: StripeFetch = (url, init) => fetch(url, init);
export const setStripeFetch = (impl: StripeFetch | null): void => {
  stripeFetch = impl ?? ((url, init) => fetch(url, init));
};

/** Free days on a first monthly or yearly subscription: Stripe takes the card now and bills when they end. */
export const PLUS_TRIAL_DAYS = 5;

export interface CheckoutInput {
  userId: string;
  email: string;
  plan: PlusPlanId;
  /** A customer id we already know for this account, so a returning payer isn't created twice. */
  stripeCustomerId: string | null;
  /** Free days before the first charge (subscriptions only; ignored for Lifetime). 0 or missing: none. */
  trialDays?: number;
}

/**
 * Creates a Stripe Checkout Session for one plan and returns the hosted page's URL. The account id travels as
 * `client_reference_id` (and the plan as metadata), so the webhook can tell who paid for what without trusting
 * anything the client sends back. Subscriptions use subscription mode; Lifetime is a one-time payment.
 */
export interface CheckoutSession {
  /** The hosted payment page. */
  url: string;
  /** What the person will be charged, in the currency's smallest unit (pence, cents), as Stripe worked it out; null if Stripe didn't say. */
  amountTotal: number | null;
  /** Lower-case ISO currency code ("gbp"); null if Stripe didn't say. */
  currency: string | null;
}

export async function createCheckoutSession(input: CheckoutInput): Promise<CheckoutSession> {
  const config = getPlusConfig();
  const priceId = config.prices[input.plan];
  if (!config.paywallOn || !config.stripeSecretKey || !priceId) {
    throw new HttpError(503, "Plus checkout isn't available yet");
  }
  const form = new URLSearchParams();
  form.set("mode", input.plan === "lifetime" ? "payment" : "subscription");
  form.set("line_items[0][price]", priceId);
  form.set("line_items[0][quantity]", "1");
  form.set("success_url", config.successUrl);
  form.set("cancel_url", config.cancelUrl);
  form.set("client_reference_id", input.userId);
  form.set("metadata[plan]", input.plan);
  form.set("metadata[user_id]", input.userId);
  if (input.plan !== "lifetime") {
    form.set("subscription_data[metadata][plan]", input.plan);
    form.set("subscription_data[metadata][user_id]", input.userId);
    if (input.trialDays && input.trialDays > 0) {
      form.set("subscription_data[trial_period_days]", String(input.trialDays));
      // Read back by the webhook, so a trial's provisional access is the trial, not a whole month.
      form.set("metadata[trial_days]", String(input.trialDays));
    }
  }
  if (input.stripeCustomerId) form.set("customer", input.stripeCustomerId);
  else form.set("customer_email", input.email);
  form.set("allow_promotion_codes", "true");

  const response = await stripeFetch("https://api.stripe.com/v1/checkout/sessions", {
    method: "POST",
    headers: { Authorization: `Bearer ${config.stripeSecretKey}`, "Content-Type": "application/x-www-form-urlencoded" },
    body: form.toString(),
  });
  const body = (await response.json().catch(() => null)) as { url?: unknown; amount_total?: unknown; currency?: unknown } | null;
  if (!response.ok || !body || typeof body.url !== "string") {
    throw new Error(`Stripe checkout session failed (HTTP ${response.status})`);
  }
  return {
    url: body.url,
    amountTotal: typeof body.amount_total === "number" ? body.amount_total : null,
    currency: typeof body.currency === "string" ? body.currency.toLowerCase() : null,
  };
}

/** Tells Stripe to stop renewing a subscription at the end of the period already paid for (the person keeps Plus until then). */
export async function cancelSubscriptionAtPeriodEnd(subscriptionId: string): Promise<void> {
  const { stripeSecretKey } = getPlusConfig();
  if (!stripeSecretKey) throw new HttpError(503, "Plus billing isn't available yet");
  const response = await stripeFetch(`https://api.stripe.com/v1/subscriptions/${encodeURIComponent(subscriptionId)}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${stripeSecretKey}`, "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ cancel_at_period_end: "true" }).toString(),
  });
  if (!response.ok) {
    // Say why in the server log (Stripe's own message and code, never the key): a key without permission to change subscriptions, a
    // subscription id from the other mode (test vs live) and an already-ended subscription all look alike from the HTTP status alone.
    const body = (await response.json().catch(() => null)) as { error?: { message?: unknown; code?: unknown; type?: unknown } } | null;
    const detail = [body?.error?.type, body?.error?.code, body?.error?.message].filter((part) => typeof part === "string").join(" / ");
    throw new Error(`Stripe subscription cancel failed (HTTP ${response.status})${detail ? `: ${detail}` : ""}`);
  }
}
