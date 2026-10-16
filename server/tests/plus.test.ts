import { createHmac } from "node:crypto";
import type { Express } from "express";
import request from "supertest";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { pool } from "../src/db/pool.js";
import { computeEntitlement, writePlus, type PlusRow } from "../src/services/plusService.js";
import { getPlanPrices, PRICES_TTL_MS, resetPricesCache, setStripeFetch, SUPPORTERS_BOARD_NOTICE, SUPPORTERS_BOARD_NOTICE_LATER, SUPPORTERS_FIELD_KEY, verifyStripeSignature, type StripeFetch } from "../src/services/stripe.js";
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

/** The list prices the default Stripe stub answers with, by price id. */
const LIST_PRICES: Record<string, { unit_amount: number; currency: string }> = {
  price_monthly: { unit_amount: 499, currency: "GBP" },
  price_yearly: { unit_amount: 3999, currency: "GBP" },
  price_lifetime: { unit_amount: 9999, currency: "GBP" },
};
let priceReads = 0;
const priceStub: StripeFetch = async (url) => {
  const id = decodeURIComponent(url.split("/v1/prices/")[1] ?? "");
  priceReads += 1;
  const price = LIST_PRICES[id];
  return price ? { ok: true, status: 200, json: async () => price } : { ok: false, status: 404, json: async () => ({}) };
};

beforeEach(async () => {
  for (const key of envKeys) saved[key] = process.env[key];
  resetPricesCache();
  priceReads = 0;
  setStripeFetch(priceStub);
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
  const row = (over: Partial<PlusRow> = {}): PlusRow => ({ plan: "monthly", status: "active", validUntil: new Date(Date.now() + 86_400_000), stripeCustomerId: null, stripeSubscriptionId: null, cancelAtPeriodEnd: false, supporterBoard: false, lastEventAt: new Date(), ...over });
  it("everyone has Plus while the paywall is off (early access)", () => {
    expect(computeEntitlement(null, false)).toEqual({ active: true, plan: "early_access", validUntil: null, cancelAtPeriodEnd: false, paywall: false, supporterBoard: null });
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
    expect((await entitlement(s.token)).body).toEqual({ active: true, plan: "early_access", validUntil: null, cancelAtPeriodEnd: false, paywall: false, supporterBoard: null, trialDays: 0, prices: null });
    paywall(true);
    expect((await entitlement(s.token)).body).toEqual({
      active: false,
      plan: null,
      validUntil: null,
      cancelAtPeriodEnd: false,
      paywall: true,
      supporterBoard: null,
      trialDays: 5,
      prices: { monthly: { amount: 499, currency: "gbp" }, yearly: { amount: 3999, currency: "gbp" }, lifetime: { amount: 9999, currency: "gbp" } },
    });
  });

  it("reads each list price from Stripe once and reuses it, and never fails the request when Stripe does", async () => {
    const s = await createTestSession();
    await entitlement(s.token);
    await entitlement(s.token);
    expect(priceReads).toBe(3); // one read per plan, not per request

    resetPricesCache();
    setStripeFetch(async () => ({ ok: false, status: 500, json: async () => ({}) }));
    const down = await entitlement(s.token);
    expect(down.status).toBe(200);
    expect(down.body.prices).toBeNull();
    expect(down.body.active).toBe(false);
  });

  it("keeps showing the last good prices when a refresh fails, and shows none when a plan has no price configured", async () => {
    const t0 = 1_000_000;
    expect((await getPlanPrices(t0))?.yearly.amount).toBe(3999);
    setStripeFetch(async () => ({ ok: false, status: 503, json: async () => ({}) }));
    expect((await getPlanPrices(t0 + PRICES_TTL_MS + 1))?.yearly.amount).toBe(3999);

    resetPricesCache();
    delete process.env.STRIPE_PRICE_LIFETIME;
    expect(await getPlanPrices(t0)).toBeNull();
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
    // Monthly never gets the trial, even for someone who has never had Plus.
    const monthly = await post(s.token, { plan: "monthly" });
    expect(monthly.body.trialDays).toBe(0);
    expect(form!.get("subscription_data[trial_period_days]")).toBeNull();
    expect(form!.get("metadata[trial_days]")).toBeNull();

    expect((await post(s.token, { plan: "yearly" })).body.trialDays).toBe(5);
    expect(form!.get("metadata[trial_days]")).toBe("5");

    await webhook(completed(s.userId, { metadata: { plan: "yearly", user_id: s.userId, trial_days: "5" } }));
    const until = new Date((await entitlement(s.token)).body.validUntil).getTime();
    expect(until).toBeGreaterThan(Date.now() + 5 * 86400_000);
    expect(until).toBeLessThan(Date.now() + 7 * 86400_000);

    // Having had Plus once (even only a trial) ends the offer.
    const again = await post(s.token, { plan: "yearly" });
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

describe("the supporters board (opt-in)", () => {
  const lifetime = (userId: string, created = nowSec()) => webhook(completed(userId, { mode: "payment", subscription: null, metadata: { plan: "lifetime", user_id: userId } }, created));
  const board = () => request(app).get("/supporters");
  const toggle = (token: string, show: unknown) => request(app).put("/user/plus/supporter").set("Authorization", `Bearer ${token}`).send({ show });

  it("is public, shows display names only, and starts empty", async () => {
    const response = await board();
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ supporters: [], total: 0 });
    expect(response.headers["cache-control"]).toContain("max-age=300");
  });

  it("buying Lifetime does not put anyone on it: they are off until they say so", async () => {
    const s = await createTestSession({ displayName: "Ada Lovelace" });
    expect((await entitlement(s.token)).body.supporterBoard).toBeNull(); // not Lifetime yet
    await lifetime(s.userId);
    expect((await entitlement(s.token)).body).toMatchObject({ plan: "lifetime", supporterBoard: false });
    expect((await board()).body).toEqual({ supporters: [], total: 0 });
  });

  it("a Lifetime member who opts in is listed by display name, with nothing else", async () => {
    const s = await createTestSession({ displayName: "  Ada Lovelace  " });
    await lifetime(s.userId);
    const joined = await toggle(s.token, true);
    expect(joined.status).toBe(200);
    expect(joined.body).toMatchObject({ plan: "lifetime", supporterBoard: true });
    const response = await board();
    expect(response.body).toEqual({ supporters: [{ name: "Ada Lovelace", avatar: null, since: new Date().toISOString().slice(0, 7) }], total: 1 });
    expect(JSON.stringify(response.body)).not.toContain(s.email);
    expect(JSON.stringify(response.body)).not.toContain(s.userId);
  });

  it("an account with no display name shows as an anonymous supporter, and a long name is cut at 24 characters", async () => {
    const plain = await createTestSession();
    const long = await createTestSession({ displayName: "x".repeat(60) });
    for (const s of [plain, long]) {
      await lifetime(s.userId);
      await toggle(s.token, true);
      await new Promise((resolve) => setTimeout(resolve, 15));
    }
    expect((await board()).body.supporters.map((x: { name: string }) => x.name)).toEqual(["Anonymous supporter", "x".repeat(24)]);
  });

  it("shows the picture of the member's own profile, and nothing else about them", async () => {
    const s = await createTestSession({ displayName: "Pic" });
    await lifetime(s.userId);
    await toggle(s.token, true);
    await pool.query("INSERT INTO profiles (user_id, id, name, avatar, kind, is_default) VALUES ($1, 'main', 'Pic', 'fox', 'adult', true) ON CONFLICT (user_id, id) DO UPDATE SET avatar = 'fox'", [s.userId]);
    await pool.query("INSERT INTO profiles (user_id, id, name, avatar, kind, is_default) VALUES ($1, 'p_other', 'Kid', 'dragon', 'kids', false)", [s.userId]);
    const entry = (await board()).body.supporters[0];
    expect(Object.keys(entry).sort()).toEqual(["avatar", "name", "since"]);
    expect(entry).toMatchObject({ name: "Pic", avatar: "fox" }); // the account's own profile, not another one
  });

  it("lists the earliest to join first", async () => {
    const first = await createTestSession({ displayName: "First" });
    const second = await createTestSession({ displayName: "Second" });
    await lifetime(first.userId);
    await lifetime(second.userId);
    await toggle(second.token, true);
    await new Promise((resolve) => setTimeout(resolve, 15));
    await toggle(first.token, true);
    expect((await board()).body.supporters.map((x: { name: string }) => x.name)).toEqual(["Second", "First"]);
  });

  it("a member can take their name off and put it back; coming back keeps their place", async () => {
    const a = await createTestSession({ displayName: "Grace" });
    const b = await createTestSession({ displayName: "Linus" });
    await lifetime(a.userId);
    await lifetime(b.userId);
    await toggle(a.token, true);
    await new Promise((resolve) => setTimeout(resolve, 15));
    await toggle(b.token, true);
    expect((await toggle(a.token, false)).body.supporterBoard).toBe(false);
    expect((await board()).body.supporters.map((x: { name: string }) => x.name)).toEqual(["Linus"]);
    await toggle(a.token, true);
    expect((await board()).body.supporters.map((x: { name: string }) => x.name)).toEqual(["Grace", "Linus"]); // Grace was first
  });

  it("subscribers can't be on it, only an active Lifetime member can switch it, and a deleted account is not shown", async () => {
    expect((await request(app).put("/user/plus/supporter").send({ show: true })).status).toBe(401);
    const monthly = await createTestSession({ displayName: "Monthly" });
    await webhook(completed(monthly.userId));
    expect((await toggle(monthly.token, true)).status).toBe(409);
    const free = await createTestSession();
    expect((await toggle(free.token, true)).status).toBe(409);
    const s = await createTestSession({ displayName: "Gone" });
    expect((await toggle(s.token, "yes")).status).toBe(400);
    await lifetime(s.userId);
    await toggle(s.token, true);
    await pool.query("UPDATE users SET deleted_at = now() WHERE id = $1", [s.userId]);
    expect((await board()).body.total).toBe(0);
  });

  it("a Stripe retry of the purchase does not change what the member chose", async () => {
    const s = await createTestSession({ displayName: "Ada" });
    await lifetime(s.userId);
    await toggle(s.token, true);
    await lifetime(s.userId); // the same purchase delivered again
    expect((await entitlement(s.token)).body.supporterBoard).toBe(true);
    await toggle(s.token, false);
    await lifetime(s.userId);
    expect((await entitlement(s.token)).body.supporterBoard).toBe(false);
  });

  it("the Lifetime payment page says the board is a choice made in Settings; the other plans do not mention it", async () => {
    const s = await createTestSession();
    let form: URLSearchParams | null = null;
    setStripeFetch(async (_url, init) => {
      form = new URLSearchParams(init.body);
      return { ok: true, status: 200, json: async () => ({ url: "https://checkout.stripe.test/c/x", amount_total: 1, currency: "gbp" }) };
    });
    const post = (plan: string) => request(app).post("/user/plus/checkout").set("Authorization", `Bearer ${s.token}`).send({ plan });
    await post("lifetime");
    expect(form!.get("custom_text[submit][message]")).toBe(SUPPORTERS_BOARD_NOTICE);
    expect(SUPPORTERS_BOARD_NOTICE).toMatch(/choose/);
    await post("monthly");
    expect(form!.get("custom_text[submit][message]")).toBeNull();
  });
});

describe("the supporters-board question at checkout", () => {
  const answered = (userId: string, answer: "yes" | "no" | null, created = nowSec()) =>
    webhook(
      completed(
        userId,
        {
          mode: "payment",
          subscription: null,
          metadata: { plan: "lifetime", user_id: userId },
          custom_fields: answer === null ? [] : [{ key: SUPPORTERS_FIELD_KEY, type: "dropdown", dropdown: { value: answer } }],
        },
        created,
      ),
    );
  const board = () => request(app).get("/supporters");
  const toggle = (token: string, show: boolean) => request(app).put("/user/plus/supporter").set("Authorization", `Bearer ${token}`).send({ show });

  it("the Lifetime payment page asks it as a required drop-down, No first and nothing pre-selected; the other plans are not asked", async () => {
    const s = await createTestSession();
    const forms: URLSearchParams[] = [];
    setStripeFetch(async (_url, init) => {
      forms.push(new URLSearchParams(init.body));
      return { ok: true, status: 200, json: async () => ({ url: "https://checkout.stripe.test/c/x", amount_total: 1, currency: "gbp" }) };
    });
    const post = (plan: string) => request(app).post("/user/plus/checkout").set("Authorization", `Bearer ${s.token}`).send({ plan });
    expect((await post("lifetime")).status).toBe(200);
    const f = forms[0]!;
    expect(f.get("custom_fields[0][key]")).toBe(SUPPORTERS_FIELD_KEY);
    expect(f.get("custom_fields[0][type]")).toBe("dropdown");
    expect(f.get("custom_fields[0][optional]")).toBe("false");
    expect(f.get("custom_fields[0][dropdown][options][0][value]")).toBe("no"); // listed first
    expect(f.get("custom_fields[0][dropdown][options][1][value]")).toBe("yes");
    expect(f.get("custom_fields[0][dropdown][default_value]")).toBeNull(); // nothing is pre-selected
    expect(f.get("custom_fields[0][label][custom]")!.length).toBeLessThanOrEqual(50); // Stripe's limit
    expect(f.get("custom_text[submit][message]")).toBe(SUPPORTERS_BOARD_NOTICE);
    expect((await post("monthly")).status).toBe(200);
    expect([...forms[1]!.keys()].some((k) => k.startsWith("custom_fields"))).toBe(false);
  });

  it("answering Yes puts the buyer on the board; No, no answer or anything else does not", async () => {
    const yes = await createTestSession({ displayName: "Yes Person" });
    const no = await createTestSession({ displayName: "No Person" });
    const none = await createTestSession({ displayName: "Silent Person" });
    const odd = await createTestSession({ displayName: "Odd Person" });
    await answered(yes.userId, "yes");
    await answered(no.userId, "no");
    await answered(none.userId, null);
    await webhook(completed(odd.userId, { mode: "payment", subscription: null, metadata: { plan: "lifetime", user_id: odd.userId }, custom_fields: [{ key: SUPPORTERS_FIELD_KEY, dropdown: { value: "YES please" } }, "junk", null] }));
    expect((await entitlement(yes.token)).body.supporterBoard).toBe(true);
    for (const s of [no, none, odd]) expect((await entitlement(s.token)).body.supporterBoard).toBe(false);
    expect((await board()).body.supporters.map((x: { name: string }) => x.name)).toEqual(["Yes Person"]);
  });

  it("a Stripe retry of the purchase never undoes a later choice", async () => {
    const s = await createTestSession({ displayName: "Changed Mind" });
    await answered(s.userId, "yes");
    await toggle(s.token, false);
    await answered(s.userId, "yes"); // the same purchase delivered again
    expect((await entitlement(s.token)).body.supporterBoard).toBe(false);
    expect((await board()).body.total).toBe(0);
    const t = await createTestSession({ displayName: "Later Yes" });
    await answered(t.userId, "no");
    await toggle(t.token, true);
    await answered(t.userId, "no"); // and a retry of a No does not take them off either
    expect((await entitlement(t.token)).body.supporterBoard).toBe(true);
  });

  it("an upgrade from a subscription to Lifetime with Yes joins the board; a subscriber is not on it before", async () => {
    const s = await createTestSession({ displayName: "Upgrader" });
    await webhook(completed(s.userId)); // monthly
    expect((await entitlement(s.token)).body.supporterBoard).toBeNull();
    await answered(s.userId, "yes", nowSec() + 5);
    expect((await entitlement(s.token)).body).toMatchObject({ plan: "lifetime", supporterBoard: true });
  });

  it("if Stripe refuses the question, the Lifetime checkout is created without it, so the purchase is never blocked", async () => {
    const s = await createTestSession();
    const forms: URLSearchParams[] = [];
    setStripeFetch(async (_url, init) => {
      const f = new URLSearchParams(init.body);
      forms.push(f);
      if (f.has("custom_fields[0][key]")) return { ok: false, status: 400, json: async () => ({ error: { message: "Invalid custom_fields" } }) };
      return { ok: true, status: 200, json: async () => ({ url: "https://checkout.stripe.test/c/ok", amount_total: 1, currency: "gbp" }) };
    });
    const response = await request(app).post("/user/plus/checkout").set("Authorization", `Bearer ${s.token}`).send({ plan: "lifetime" });
    expect(response.status).toBe(200);
    expect(response.body.url).toBe("https://checkout.stripe.test/c/ok");
    expect(forms).toHaveLength(2);
    expect([...forms[1]!.keys()].some((k) => k.startsWith("custom_fields"))).toBe(false);
    expect(forms[1]!.get("custom_text[submit][message]")).toBe(SUPPORTERS_BOARD_NOTICE_LATER); // and the note says it is chosen later in Settings

    // a 400 on a plan that was never asked the question is still an error
    setStripeFetch(async () => ({ ok: false, status: 400, json: async () => ({}) }));
    expect((await request(app).post("/user/plus/checkout").set("Authorization", `Bearer ${s.token}`).send({ plan: "monthly" })).status).toBe(500);
  });
});

