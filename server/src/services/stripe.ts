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
export type StripeFetch = (url: string, init: { method: string; headers: Record<string, string>; body?: string }) => Promise<{ ok: boolean; status: number; json(): Promise<unknown> }>;
let stripeFetch: StripeFetch = (url, init) => fetch(url, init);
export const setStripeFetch = (impl: StripeFetch | null): void => {
  stripeFetch = impl ?? ((url, init) => fetch(url, init));
};

/** What the Lifetime payment page says about the supporters board: it is the member's choice, made in the question above it or later in Settings. */
export const SUPPORTERS_BOARD_NOTICE = "As a Lifetime supporter you can choose to be on the supporters board on arctv.org, with your display name and picture or just as Anonymous. Answer the question above; you can change your mind any time in Settings → ArcTV Plus.";

/** The same note when the question could not be shown: the choice is made afterwards. */
export const SUPPORTERS_BOARD_NOTICE_LATER = "As a Lifetime supporter you can choose to be on the supporters board on arctv.org, with your display name and picture or just as Anonymous. It is off unless you turn it on in Settings → ArcTV Plus.";

/** The question on the Lifetime payment page ("custom field"), and what its two answers are. The webhook reads the answer back by this key. */
export const SUPPORTERS_FIELD_KEY = "supporters_board";
export const SUPPORTERS_FIELD_YES = "yes";
export const SUPPORTERS_FIELD_NO = "no";
export const SUPPORTERS_FIELD_ANONYMOUS = "anonymous";

/**
 * A required drop-down on the Lifetime payment page: "Put my name on the supporters board?". "No thanks" is listed first and nothing is pre-selected, so a
 * listing is only ever something the buyer chose; the buyer can be listed by name or as Anonymous (no name, no picture). Answers come back on the completed session's `custom_fields`.
 */
function addSupportersQuestion(form: URLSearchParams): void {
  form.set("custom_fields[0][key]", SUPPORTERS_FIELD_KEY);
  form.set("custom_fields[0][type]", "dropdown");
  form.set("custom_fields[0][optional]", "false");
  form.set("custom_fields[0][label][type]", "custom");
  form.set("custom_fields[0][label][custom]", "Put my name on the supporters board?");
  form.set("custom_fields[0][dropdown][options][0][label]", "No thanks, don't list me");
  form.set("custom_fields[0][dropdown][options][0][value]", SUPPORTERS_FIELD_NO);
  form.set("custom_fields[0][dropdown][options][1][label]", "Yes, show my display name and picture");
  form.set("custom_fields[0][dropdown][options][1][value]", SUPPORTERS_FIELD_YES);
  form.set("custom_fields[0][dropdown][options][2][label]", "Yes, but list me as Anonymous");
  form.set("custom_fields[0][dropdown][options][2][value]", SUPPORTERS_FIELD_ANONYMOUS);
}

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
  // Said on the payment page itself, before anyone pays: Lifetime members may choose to be on the supporters board (off unless they turn it on in Settings).
  if (input.plan === "lifetime") {
    form.set("custom_text[submit][message]", SUPPORTERS_BOARD_NOTICE);
    addSupportersQuestion(form);
  }
  if (input.stripeCustomerId) form.set("customer", input.stripeCustomerId);
  else form.set("customer_email", input.email);
  form.set("allow_promotion_codes", "true");

  const create = (fields: URLSearchParams) =>
    stripeFetch("https://api.stripe.com/v1/checkout/sessions", {
      method: "POST",
      headers: { Authorization: `Bearer ${config.stripeSecretKey}`, "Content-Type": "application/x-www-form-urlencoded" },
      body: fields.toString(),
    });
  let response = await create(form);
  if (response.status === 400 && form.has("custom_fields[0][key]")) {
    // Stripe refused the extra question: never let that stop a purchase. Try again without it (the board stays a choice made later in Settings).
    console.error("Stripe refused the supporters-board question; creating the Lifetime checkout without it");
    const plain = new URLSearchParams(form);
    for (const key of [...plain.keys()]) if (key.startsWith("custom_fields[")) plain.delete(key);
    plain.set("custom_text[submit][message]", SUPPORTERS_BOARD_NOTICE_LATER);
    response = await create(plain);
  }
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
    // The reason goes to the server log only: the thrown error (which a non-production response echoes as `detail`) stays generic.
    console.error(`Stripe subscription cancel refused (HTTP ${response.status})${detail ? `: ${detail}` : ""}`);
    throw new Error(`Stripe subscription cancel failed (HTTP ${response.status})`);
  }
}

/** What one plan costs on the list: the amount in the currency's smallest unit (pence, cents) and its lower-case ISO code. */
export interface PlanPrice {
  amount: number;
  currency: string;
}
export type PlanPrices = Record<PlusPlanId, PlanPrice>;

/** How long a read of the prices is reused: they change rarely, and /plus is read often. */
export const PRICES_TTL_MS = 6 * 60 * 60 * 1000;
let pricesCache: { value: PlanPrices; at: number } | null = null;
export const resetPricesCache = (): void => {
  pricesCache = null;
};

/**
 * The list price of each plan, read from Stripe (so it always matches what checkout charges) and kept for a few hours. Null when the
 * paywall is off, Stripe isn't configured, or any plan's price can't be read and nothing earlier is kept; a failed refresh serves the
 * last good answer. It never throws: a price display must not break reading someone's Plus status.
 */
export async function getPlanPrices(now: number = Date.now()): Promise<PlanPrices | null> {
  const config = getPlusConfig();
  if (!config.paywallOn || !config.stripeSecretKey) return null;
  if (pricesCache && now - pricesCache.at < PRICES_TTL_MS) return pricesCache.value;
  try {
    const plans: PlusPlanId[] = ["monthly", "yearly", "lifetime"];
    const entries = await Promise.all(
      plans.map(async (plan) => {
        const id = config.prices[plan];
        if (!id) throw new Error(`no price id for ${plan}`);
        const response = await stripeFetch(`https://api.stripe.com/v1/prices/${encodeURIComponent(id)}`, { method: "GET", headers: { Authorization: `Bearer ${config.stripeSecretKey}` } });
        const body = (await response.json().catch(() => null)) as { unit_amount?: unknown; currency?: unknown } | null;
        if (!response.ok || !body || typeof body.unit_amount !== "number" || typeof body.currency !== "string") throw new Error(`Stripe price ${plan} failed (HTTP ${response.status})`);
        return [plan, { amount: body.unit_amount, currency: body.currency.toLowerCase() }] as const;
      })
    );
    const value = Object.fromEntries(entries) as PlanPrices;
    pricesCache = { value, at: now };
    return value;
  } catch (error) {
    console.error(`Plus prices unavailable: ${error instanceof Error ? error.message : "unknown error"}`);
    return pricesCache?.value ?? null;
  }
}
