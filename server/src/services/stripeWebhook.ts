import type { PlusPlanId } from "../config/env.js";
import { findUserForStripe, userExists, writePlus } from "./plusService.js";

export interface StripeEvent {
  id?: string;
  type: string;
  created: number;
  data: { object: Record<string, unknown> };
}

export type WebhookOutcome = "applied" | "ignored";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const PLANS: ReadonlySet<string> = new Set(["monthly", "yearly", "lifetime"]);
const DAY_MS = 24 * 3600_000;

/** Until Stripe's own subscription event brings the real period end, a new subscription is paid for a little over one period. */
const PROVISIONAL_DAYS: Record<"monthly" | "yearly", number> = { monthly: 33, yearly: 368 };

const str = (value: unknown): string | null => (typeof value === "string" && value !== "" ? value : null);
const obj = (value: unknown): Record<string, unknown> => (value && typeof value === "object" ? (value as Record<string, unknown>) : {});
const planOf = (metadata: unknown): PlusPlanId | null => {
  const plan = str(obj(metadata).plan);
  return plan && PLANS.has(plan) ? (plan as PlusPlanId) : null;
};

/** The end of the paid period. Older API versions put it on the subscription, newer ones on its items. */
function periodEnd(subscription: Record<string, unknown>): Date | null {
  const direct = subscription.current_period_end;
  const fromItem = obj(Array.isArray(obj(subscription.items).data) ? (obj(subscription.items).data as unknown[])[0] : null).current_period_end;
  const seconds = typeof direct === "number" ? direct : typeof fromItem === "number" ? fromItem : null;
  return seconds === null ? null : new Date(seconds * 1000);
}

/**
 * Applies one verified Stripe event to the account it belongs to. Only the events that change what someone has paid for
 * are handled; everything else is acknowledged and ignored. Safe to receive twice or out of order: writes are keyed on
 * the event's own time.
 */
export async function handleStripeEvent(event: StripeEvent): Promise<WebhookOutcome> {
  const eventAt = new Date(event.created * 1000);
  const object = obj(event.data?.object);

  if (event.type === "checkout.session.completed") {
    const userId = str(object.client_reference_id) ?? str(obj(object.metadata).user_id);
    const plan = planOf(object.metadata);
    if (!userId || !UUID.test(userId) || !plan || !(await userExists(userId))) return "ignored";
    const mode = str(object.mode);
    if (plan === "lifetime") {
      // The one-time payment: only once Stripe says it was actually paid.
      if (mode !== "payment" || object.payment_status !== "paid") return "ignored";
      const applied = await writePlus({ userId, plan, status: "active", validUntil: null, stripeCustomerId: str(object.customer), stripeSubscriptionId: null, eventAt });
      return applied ? "applied" : "ignored";
    }
    if (mode !== "subscription") return "ignored";
    // A free trial: access for the trial and a day over, until Stripe's own subscription event brings the real end.
    const trialDays = Number(obj(object.metadata).trial_days);
    const provisionalDays = Number.isInteger(trialDays) && trialDays > 0 && trialDays <= 31 ? trialDays + 1 : PROVISIONAL_DAYS[plan];
    const applied = await writePlus({
      userId,
      plan,
      status: "active",
      validUntil: new Date(eventAt.getTime() + provisionalDays * DAY_MS),
      stripeCustomerId: str(object.customer),
      stripeSubscriptionId: str(object.subscription),
      eventAt,
    });
    return applied ? "applied" : "ignored";
  }

  if (event.type === "customer.subscription.updated" || event.type === "customer.subscription.deleted") {
    const found = await findUserForStripe(str(object.id), str(object.customer));
    // A Lifetime owner is never changed by a subscription event (they may have had a subscription before upgrading).
    if (!found || found.row.plan === "lifetime") return "ignored";
    const plan = planOf(object.metadata) ?? found.row.plan;
    const status = str(object.status);
    const ended = event.type === "customer.subscription.deleted" || status === "canceled" || status === "unpaid" || status === "incomplete_expired";
    const end = periodEnd(object);
    const applied = await writePlus({
      userId: found.userId,
      plan,
      status: ended ? "canceled" : "active",
      // Cancelled: access ends when the paid period does (or now, if Stripe says it already ended).
      validUntil: ended ? (end && end.getTime() < eventAt.getTime() ? end : eventAt) : end ?? found.row.validUntil,
      stripeCustomerId: str(object.customer),
      stripeSubscriptionId: str(object.id),
      // Scheduled to stop at the end of the paid period (the person cancelled, here or in Stripe): still active until then.
      cancelAtPeriodEnd: !ended && (object.cancel_at_period_end === true || typeof object.cancel_at === "number"),
      eventAt,
    });
    return applied ? "applied" : "ignored";
  }

  return "ignored";
}
