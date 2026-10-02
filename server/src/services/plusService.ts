import { getPlusConfig, type PlusPlanId } from "../config/env.js";
import { pool } from "../db/pool.js";

export interface PlusRow {
  plan: PlusPlanId;
  status: "active" | "canceled";
  validUntil: Date | null;
  stripeCustomerId: string | null;
  stripeSubscriptionId: string | null;
  lastEventAt: Date;
}

export interface Entitlement {
  /** Whether this account has Plus right now. */
  active: boolean;
  /** "early_access" while the paywall is off and everyone has Plus; null when there is no Plus. */
  plan: PlusPlanId | "early_access" | null;
  /** When a subscription's paid period ends (null for Lifetime, early access and no Plus). */
  validUntil: string | null;
  /** Whether Plus is paid at all yet. Clients show the subscribe screen only when this is true. */
  paywall: boolean;
}

/**
 * What an account has, from what is stored: the paywall off means everyone does (early access); on, a Lifetime plan that
 * is active, or a subscription that is active and not past its paid period. Pure, so every case is tested directly.
 */
export function computeEntitlement(row: PlusRow | null, paywallOn: boolean, now: Date = new Date()): Entitlement {
  if (!paywallOn) return { active: true, plan: "early_access", validUntil: null, paywall: false };
  if (!row || row.status !== "active") return { active: false, plan: null, validUntil: null, paywall: true };
  if (row.plan === "lifetime") return { active: true, plan: "lifetime", validUntil: null, paywall: true };
  const stillPaid = row.validUntil !== null && row.validUntil.getTime() > now.getTime();
  return { active: stillPaid, plan: stillPaid ? row.plan : null, validUntil: stillPaid ? row.validUntil!.toISOString() : null, paywall: true };
}

interface DbRow {
  plan: PlusPlanId;
  status: "active" | "canceled";
  valid_until: Date | null;
  stripe_customer_id: string | null;
  stripe_subscription_id: string | null;
  last_event_at: Date;
}

const mapRow = (row: DbRow): PlusRow => ({
  plan: row.plan,
  status: row.status,
  validUntil: row.valid_until,
  stripeCustomerId: row.stripe_customer_id,
  stripeSubscriptionId: row.stripe_subscription_id,
  lastEventAt: row.last_event_at,
});

export async function getPlusRow(userId: string): Promise<PlusRow | null> {
  const result = await pool.query<DbRow>("SELECT plan, status, valid_until, stripe_customer_id, stripe_subscription_id, last_event_at FROM user_plus WHERE user_id = $1", [userId]);
  return result.rows[0] ? mapRow(result.rows[0]) : null;
}

export async function getEntitlement(userId: string): Promise<Entitlement> {
  const { paywallOn } = getPlusConfig();
  if (!paywallOn) return computeEntitlement(null, false);
  return computeEntitlement(await getPlusRow(userId), true);
}

export interface PlusWrite {
  userId: string;
  plan: PlusPlanId;
  status: "active" | "canceled";
  validUntil: Date | null;
  stripeCustomerId: string | null;
  stripeSubscriptionId: string | null;
  /** The Stripe event's own time: a write older than what is stored is ignored, so out-of-order delivery can't undo a newer event. */
  eventAt: Date;
}

/** Writes an account's Plus state, unless a newer Stripe event has already been applied. Returns whether it was applied. */
export async function writePlus(write: PlusWrite): Promise<boolean> {
  const result = await pool.query(
    `INSERT INTO user_plus (user_id, plan, status, valid_until, stripe_customer_id, stripe_subscription_id, last_event_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7)
     ON CONFLICT (user_id) DO UPDATE SET
       plan = EXCLUDED.plan,
       status = EXCLUDED.status,
       valid_until = EXCLUDED.valid_until,
       stripe_customer_id = COALESCE(EXCLUDED.stripe_customer_id, user_plus.stripe_customer_id),
       stripe_subscription_id = COALESCE(EXCLUDED.stripe_subscription_id, user_plus.stripe_subscription_id),
       last_event_at = EXCLUDED.last_event_at,
       updated_at = now()
     WHERE EXCLUDED.last_event_at >= user_plus.last_event_at
     RETURNING user_id`,
    [write.userId, write.plan, write.status, write.validUntil, write.stripeCustomerId, write.stripeSubscriptionId, write.eventAt]
  );
  return (result.rowCount ?? 0) > 0;
}

/** The account a Stripe subscription or customer belongs to, from what the checkout stored. */
export async function findUserForStripe(subscriptionId: string | null, customerId: string | null): Promise<{ userId: string; row: PlusRow } | null> {
  const result = await pool.query<DbRow & { user_id: string }>(
    `SELECT user_id, plan, status, valid_until, stripe_customer_id, stripe_subscription_id, last_event_at
     FROM user_plus
     WHERE ($1::text IS NOT NULL AND stripe_subscription_id = $1) OR ($2::text IS NOT NULL AND stripe_customer_id = $2)
     LIMIT 1`,
    [subscriptionId, customerId]
  );
  const row = result.rows[0];
  return row ? { userId: row.user_id, row: mapRow(row) } : null;
}

export async function userExists(userId: string): Promise<boolean> {
  const result = await pool.query("SELECT 1 FROM users WHERE id = $1 AND deleted_at IS NULL", [userId]);
  return (result.rowCount ?? 0) > 0;
}
