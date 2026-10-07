import { createHmac } from "node:crypto";
import type { Express } from "express";
import request from "supertest";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { pool } from "../src/db/pool.js";
import { computeEntitlement, type PlusRow } from "../src/services/plusService.js";
import { setStripeFetch, verifyStripeSignature } from "../src/services/stripe.js";
import { createTestSession } from "./helpers/auth.js";
import { resetDatabase } from "./helpers/db.js";

const SECRET = "whsec_test_secret";
let app: Express;

const envKeys = ["PLUS_PAYWALL", "STRIPE_SECRET_KEY", "STRIPE_WEBHOOK_SECRET", "STRIPE_PRICE_MONTHLY", "STRIPE_PRICE_YEARLY", "STRIPE_PRICE_LIFETIME", "API_BASE_URL"] as const;
const saved: Record<string, string | undefined> = {};

function paywall(on: boolean) {
  process.env.PLUS_PAYWALL = on ? "on" : "off";
  process.env.STRIPE_SECRET_KEY = "sk_test_x";
  process.env.STRIPE_WEBHOOK_SECRET = SECRET;
  process.env.STRIPE_PRICE_MONTHLY = "price_monthly";
  process.env.STRIPE_PRICE_YEARLY = "price_yearly";
  process.env.STRIPE_PRICE_LIFETIME = "price_lifetime";
  process.env.API_BASE_URL = "https://api.example.test";
}

beforeEach(async () => {
  for (const key of envKeys) saved[key] = process.env[key];
  app = createApp();
  await resetDatabase();
  paywall(true);
});
afterEach(() => {
  for (const key of envKeys) {
    if (saved[key] === undefined) delete process.env[key];
    else process.env[key] = saved[key];
  }
  setStripeFetch(null);
});

const sign = (body: string, at = Math.floor(Date.now() / 1000), secret = SECRET) => `t=${at},v1=${createHmac("sha256", secret).update(`${at}.${body}`).digest("hex")}`;
const nowSec = () => Math.floor(Date.now() / 1000);

async function webhook(event: Record<string, unknown>, header?: string) {
  const body = JSON.stringify(event);
  return request(app).post("/stripe/webhook").set("Content-Type", "application/json").set("Stripe-Signature", header ?? sign(body)).send(body);
}
const entitlement = (token: string) => request(app).get("/user/plus").set("Authorization", `Bearer ${token}`);

const completed = (userId: string, over: Record<string, unknown> = {}, created = nowSec()) => ({
  id: "evt_1",
  type: "checkout.session.completed",
  created,
  data: { object: { client_reference_id: userId, mode: "subscription", customer: "cus_1", subscription: "sub_1", payment_status: "paid", metadata: { plan: "monthly", user_id: userId }, ...over } },
});
const subscriptionEvent = (type: string, over: Record<string, unknown>, created = nowSec()) => ({ id: "evt_s", type, created, data: { object: { id: "sub_1", customer: "cus_1", status: "active", current_period_end: nowSec() + 30 * 86400, metadata: { plan: "monthly" }, ...over } } });

describe("computeEntitlement", () => {
  const row = (over: Partial<PlusRow> = {}): PlusRow => ({ plan: "monthly", status: "active", validUntil: new Date(Date.now() + 86_400_000), stripeCustomerId: null, stripeSubscriptionId: null, cancelAtPeriodEnd: false, lastEventAt: new Date(), ...over });
  it("everyone has Plus while the paywall is off (early access)", () => {
    expect(computeEntitlement(null, false)).toEqual({ active: true, plan: "early_access", validUntil: null, cancelAtPeriodEnd: false, paywall: false });
  });
  it("with the paywall on, only a paid account has it", () => {
    expect(computeEntitlement(null, true)).toMatchObject({ active: false, plan: null, paywall: true });
    expect(computeEntitlement(row(), true)).toMatchObject({ active: true, plan: "monthly" });
    expect(computeEntitlement(row({ validUntil: new Date(Date.now() - 1000) }), true)).toMatchObject({ active: false, plan: null });
    expect(computeEntitlement(row({ status: "canceled" }), true)).toMatchObject({ active: false });
    expect(computeEntitlement(row({ plan: "lifetime", validUntil: null }), true)).toMatchObject({ active: true, plan: "lifetime", validUntil: null });
  });
});

describe("verifyStripeSignature", () => {
  const body = '{"a":1}';
  it("accepts a correct, fresh signature and refuses everything else", () => {
    const t = nowSec();
    expect(verifyStripeSignature(body, sign(body, t), SECRET, t)).toBe(true);
    expect(verifyStripeSignature(body + " ", sign(body, t), SECRET, t)).toBe(false); // tampered body
    expect(verifyStripeSignature(body, sign(body, t, "other"), SECRET, t)).toBe(false); // wrong secret
    expect(verifyStripeSignature(body, sign(body, t - 3600), SECRET, t)).toBe(false); // replayed an hour later
    expect(verifyStripeSignature(body, undefined, SECRET, t)).toBe(false);
    expect(verifyStripeSignature(body, "t=abc,v1=zz", SECRET, t)).toBe(false);
    expect(verifyStripeSignature(body, `t=${t},v1=abcd`, SECRET, t)).toBe(false);
  });
});

describe("GET /user/plus", () => {
  it("needs sign-in", async () => {
    expect((await request(app).get("/user/plus")).status).toBe(401);
  });
  it("is active for everyone while the paywall is off, and inactive without a payment once it is on", async () => {
    const s = await createTestSession();
    paywall(false);
    expect((await entitlement(s.token)).body).toEqual({ active: true, plan: "early_access", validUntil: null, cancelAtPeriodEnd: false, paywall: false, trialDays: 0 });
    paywall(true);
    expect((await entitlement(s.token)).body).toEqual({ active: false, plan: null, validUntil: null, cancelAtPeriodEnd: false, paywall: true, trialDays: 5 });
  });
});

describe("POST /stripe/webhook", () => {
  it("refuses a missing or wrong signature and changes nothing", async () => {
    const s = await createTestSession();
    const bad = await webhook(completed(s.userId), "t=1,v1=00");
    expect(bad.status).toBe(400);
    const none = await request(app).post("/stripe/webhook").set("Content-Type", "application/json").send(JSON.stringify(completed(s.userId)));
    expect(none.status).toBe(400);
    expect((await entitlement(s.token)).body.active).toBe(false);
  });

  it("a paid subscription checkout turns Plus on for that account only", async () => {
    const a = await createTestSession();
    const b = await createTestSession();
    expect((await webhook(completed(a.userId))).body).toMatchObject({ received: true, outcome: "applied" });
    expect((await entitlement(a.token)).body).toMatchObject({ active: true, plan: "monthly", paywall: true });
    expect((await entitlement(b.token)).body.active).toBe(false);
  });

  it("a Lifetime payment is active with no end date, and only once actually paid", async () => {
    const s = await createTestSession();
    const unpaid = completed(s.userId, { mode: "payment", subscription: null, payment_status: "unpaid", metadata: { plan: "lifetime" } });
    expect((await webhook(unpaid)).body.outcome).toBe("ignored");
    expect((await entitlement(s.token)).body.active).toBe(false);
    const paid = completed(s.userId, { mode: "payment", subscription: null, payment_status: "paid", metadata: { plan: "lifetime" } });
    expect((await webhook(paid)).body.outcome).toBe("applied");
    expect((await entitlement(s.token)).body).toMatchObject({ active: true, plan: "lifetime", validUntil: null });
  });

  it("ignores an unknown account, a bad id, a missing plan and an unrelated event", async () => {
    expect((await webhook(completed("00000000-0000-4000-8000-000000000000"))).body.outcome).toBe("ignored");
    expect((await webhook(completed("not-a-uuid"))).body.outcome).toBe("ignored");
    const s = await createTestSession();
    expect((await webhook(completed(s.userId, { metadata: {} }))).body.outcome).toBe("ignored");
    expect((await webhook({ id: "e", type: "charge.refunded", created: nowSec(), data: { object: {} } })).body.outcome).toBe("ignored");
    expect((await entitlement(s.token)).body.active).toBe(false);
  });

  it("a subscription renewal moves the paid period; cancelling ends Plus; a late old event can't undo a newer one", async () => {
    const s = await createTestSession();
    const t0 = nowSec();
    await webhook(completed(s.userId, {}, t0));
    const newEnd = nowSec() + 60 * 86400;
    await webhook(subscriptionEvent("customer.subscription.updated", { current_period_end: newEnd }, t0 + 10));
    const body = (await entitlement(s.token)).body;
    expect(body.active).toBe(true);
    expect(Date.parse(body.validUntil)).toBe(newEnd * 1000);

    await webhook(subscriptionEvent("customer.subscription.deleted", { status: "canceled" }, t0 + 20));
    expect((await entitlement(s.token)).body.active).toBe(false);

    // an older "updated" event arriving after the cancellation must not bring Plus back
    expect((await webhook(subscriptionEvent("customer.subscription.updated", { current_period_end: newEnd }, t0 + 5))).body.outcome).toBe("ignored");
    expect((await entitlement(s.token)).body.active).toBe(false);
  });

  it("a subscription event never touches a Lifetime owner", async () => {
    const s = await createTestSession();
    await webhook(completed(s.userId, { mode: "payment", subscription: null, metadata: { plan: "lifetime" } }, nowSec() - 100));
    expect((await webhook(subscriptionEvent("customer.subscription.deleted", { status: "canceled" }))).body.outcome).toBe("ignored");
    expect((await entitlement(s.token)).body).toMatchObject({ active: true, plan: "lifetime" });
  });

  it("is safe to receive the same event twice", async () => {
    const s = await createTestSession();
    const event = completed(s.userId);
    await webhook(event);
    await webhook(event);
    expect((await pool.query("SELECT count(*)::int AS n FROM user_plus WHERE user_id = $1", [s.userId])).rows[0].n).toBe(1);
    expect((await entitlement(s.token)).body.active).toBe(true);
  });
});

describe("POST /user/plus/checkout", () => {
  const post = (token: string, body: unknown) => request(app).post("/user/plus/checkout").set("Authorization", `Bearer ${token}`).send(body as object);

  it("needs sign-in and a valid plan", async () => {
    expect((await request(app).post("/user/plus/checkout").send({ plan: "monthly" })).status).toBe(401);
    const s = await createTestSession();
    expect((await post(s.token, { plan: "weekly" })).status).toBe(400);
  });

  it("says it isn't available while the paywall is off or Stripe isn't configured", async () => {
    const s = await createTestSession();
    paywall(false);
    expect((await post(s.token, { plan: "monthly" })).status).toBe(503);
    paywall(true);
    delete process.env.STRIPE_PRICE_YEARLY;
    expect((await post(s.token, { plan: "yearly" })).status).toBe(503);
  });

  it("creates a Stripe session tied to the account and returns its URL", async () => {
    const s = await createTestSession();
    let sent: { url: string; headers: Record<string, string>; form: URLSearchParams } | null = null;
    setStripeFetch(async (url, init) => {
      sent = { url, headers: init.headers, form: new URLSearchParams(init.body) };
      return { ok: true, status: 200, json: async () => ({ url: "https://checkout.stripe.test/c/pay_123", amount_total: 2999, currency: "GBP" }) };
    });
    const response = await post(s.token, { plan: "yearly" });
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ url: "https://checkout.stripe.test/c/pay_123", amountTotal: 2999, currency: "gbp", trialDays: 5 });
    expect(sent!.form.get("subscription_data[trial_period_days]")).toBe("5");
    expect(sent!.url).toBe("https://api.stripe.com/v1/checkout/sessions");
    expect(sent!.headers.Authorization).toBe("Bearer sk_test_x");
    expect(sent!.form.get("mode")).toBe("subscription");
    expect(sent!.form.get("line_items[0][price]")).toBe("price_yearly");
    expect(sent!.form.get("client_reference_id")).toBe(s.userId);
    expect(sent!.form.get("metadata[plan]")).toBe("yearly");
    expect(sent!.form.get("customer_email")).toBe(s.email);
    expect(sent!.form.get("success_url")).toBe("https://api.example.test/plus/thanks");

    const lifetime = await post(s.token, { plan: "lifetime" });
    expect(lifetime.status).toBe(200);
    expect(sent!.form.get("mode")).toBe("payment");
    expect(sent!.form.get("line_items[0][price]")).toBe("price_lifetime");
    expect(sent!.form.get("subscription_data[trial_period_days]")).toBeNull();
  });

  it("gives the free trial only to someone who has never had Plus, and a trial gives a few days of access first", async () => {
    const s = await createTestSession();
    let form: URLSearchParams | null = null;
    setStripeFetch(async (_url, init) => {
      form = new URLSearchParams(init.body);
      return { ok: true, status: 200, json: async () => ({ url: "https://checkout.stripe.test/c/x", amount_total: 999, currency: "gbp" }) };
    });
    expect((await post(s.token, { plan: "monthly" })).body.trialDays).toBe(5);
    expect(form!.get("metadata[trial_days]")).toBe("5");

    await webhook(completed(s.userId, { metadata: { plan: "monthly", user_id: s.userId, trial_days: "5" } }));
    const until = new Date((await entitlement(s.token)).body.validUntil).getTime();
    expect(until).toBeGreaterThan(Date.now() + 5 * 86400_000);
    expect(until).toBeLessThan(Date.now() + 7 * 86400_000);

    // Having had Plus once (even only a trial) ends the offer.
    const again = await post(s.token, { plan: "monthly" });
    expect(again.body.trialDays).toBe(0);
    expect((await entitlement(s.token)).body.trialDays).toBe(0);
    expect((await entitlement((await createTestSession()).token)).body.trialDays).toBe(5);
    expect(form!.get("subscription_data[trial_period_days]")).toBeNull();
  });

  it("a Stripe failure is a server error, not a leaked message; a Lifetime owner is told they already have it", async () => {
    const s = await createTestSession();
    setStripeFetch(async () => ({ ok: false, status: 500, json: async () => ({ error: { message: "secret detail" } }) }));
    const failed = await post(s.token, { plan: "monthly" });
    expect(failed.status).toBe(500);
    expect(JSON.stringify(failed.body)).not.toContain("secret detail");
    await webhook(completed(s.userId, { mode: "payment", subscription: null, metadata: { plan: "lifetime" } }));
    expect((await post(s.token, { plan: "yearly" })).status).toBe(409);
  });
});

describe("the page Stripe sends people back to", () => {
  it("is served with its logo and script (no login needed)", async () => {
    const page = await request(app).get("/plus/thanks");
    expect(page.status).toBe(200);
    expect(page.text).toContain("/arctv-logo.png");
    expect(page.text).toContain("Checkout cancelled");
    expect((await request(app).get("/arctv-logo.png")).status).toBe(200);
    expect((await request(app).get("/plus-thanks.js")).status).toBe(200);
  });
});

describe("POST /user/plus/cancel", () => {
  const cancel = (token: string) => request(app).post("/user/plus/cancel").set("Authorization", `Bearer ${token}`).send({});
  const subscribed = async () => {
    const s = await createTestSession();
    await webhook(completed(s.userId));
    return s;
  };

  it("needs a sign-in, and a subscription to cancel", async () => {
    expect((await request(app).post("/user/plus/cancel").send({})).status).toBe(401);
    const s = await createTestSession();
    expect((await cancel(s.token)).status).toBe(404);
  });

  it("is refused while the paywall is off", async () => {
    const s = await createTestSession();
    paywall(false);
    expect((await cancel(s.token)).status).toBe(503);
  });

  it("stops renewal at the end of the paid period and keeps Plus until then", async () => {
    const s = await subscribed();
    let sent: { url: string; headers: Record<string, string>; form: URLSearchParams } | null = null;
    setStripeFetch(async (url, init) => {
      sent = { url, headers: init.headers, form: new URLSearchParams(init.body) };
      return { ok: true, status: 200, json: async () => ({}) };
    });
    const response = await cancel(s.token);
    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({ active: true, plan: "monthly", cancelAtPeriodEnd: true, paywall: true });
    expect(response.body.validUntil).toBeTruthy();
    expect(sent!.url).toBe("https://api.stripe.com/v1/subscriptions/sub_1");
    expect(sent!.headers.Authorization).toBe("Bearer sk_test_x");
    expect(sent!.form.get("cancel_at_period_end")).toBe("true");
    expect((await entitlement(s.token)).body).toMatchObject({ active: true, cancelAtPeriodEnd: true });

    // Cancelling again changes nothing and doesn't call Stripe a second time.
    sent = null;
    expect((await cancel(s.token)).body).toMatchObject({ cancelAtPeriodEnd: true });
    expect(sent).toBeNull();
  });

  it("follows Stripe: a subscription scheduled to end shows as cancelling, and renewing clears it", async () => {
    const s = await subscribed();
    await webhook(subscriptionEvent("customer.subscription.updated", { cancel_at_period_end: true }, nowSec() + 1));
    expect((await entitlement(s.token)).body).toMatchObject({ active: true, cancelAtPeriodEnd: true });
    await webhook(subscriptionEvent("customer.subscription.updated", { cancel_at_period_end: false }, nowSec() + 2));
    expect((await entitlement(s.token)).body).toMatchObject({ active: true, cancelAtPeriodEnd: false });
  });

  it("can't cancel Lifetime, and a Stripe failure is a server error that leaves the account as it was", async () => {
    const life = await createTestSession();
    await webhook(completed(life.userId, { mode: "payment", subscription: null, metadata: { plan: "lifetime" } }));
    expect((await cancel(life.token)).status).toBe(409);

    const s = await subscribed();
    setStripeFetch(async () => ({ ok: false, status: 500, json: async () => ({ error: { message: "secret detail" } }) }));
    const failed = await cancel(s.token);
    expect(failed.status).toBe(500);
    expect(JSON.stringify(failed.body)).not.toContain("secret detail");
    expect((await entitlement(s.token)).body).toMatchObject({ active: true, cancelAtPeriodEnd: false });
  });
});
