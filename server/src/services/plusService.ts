import { getPlusConfig, type PlusPlanId } from "../config/env.js";
import { pool } from "../db/pool.js";

export interface PlusRow {
  plan: PlusPlanId;
  status: "active" | "canceled";
  validUntil: Date | null;
  stripeCustomerId: string | null;
  stripeSubscriptionId: string | null;
  cancelAtPeriodEnd: boolean;
  /** A Lifetime member who has chosen to have their display name on the supporters board (off until they say so). */
  supporterBoard: boolean;
  lastEventAt: Date;
}

export interface Entitlement {
  /** Whether this account has Plus right now. */
  active: boolean;
  /** "early_access" while the paywall is off and everyone has Plus; null when there is no Plus. */
  plan: PlusPlanId | "early_access" | null;
  /** When a subscription's paid period ends (null for Lifetime, early access and no Plus). */
  validUntil: string | null;
  /** A subscription that has been cancelled and will not renew: Plus runs to validUntil and then ends. */
  cancelAtPeriodEnd: boolean;
  /** Whether Plus is paid at all yet. Clients show the subscribe screen only when this is true. */
  paywall: boolean;
  /** For a Lifetime member: whether their display name is on the supporters board. null for everyone else. */
  supporterBoard: boolean | null;
}

/**
 * What an account has, from what is stored: the paywall off means everyone does (early access); on, a Lifetime plan that
 * is active, or a subscription that is active and not past its paid period. Pure, so every case is tested directly.
 */
export function computeEntitlement(row: PlusRow | null, paywallOn: boolean, now: Date = new Date()): Entitlement {
  if (!paywallOn) return { active: true, plan: "early_access", validUntil: null, cancelAtPeriodEnd: false, paywall: false, supporterBoard: null };
  if (!row || row.status !== "active") return { active: false, plan: null, validUntil: null, cancelAtPeriodEnd: false, paywall: true, supporterBoard: null };
  if (row.plan === "lifetime") return { active: true, plan: "lifetime", validUntil: null, cancelAtPeriodEnd: false, paywall: true, supporterBoard: row.supporterBoard };
  const stillPaid = row.validUntil !== null && row.validUntil.getTime() > now.getTime();
  return { active: stillPaid, plan: stillPaid ? row.plan : null, validUntil: stillPaid ? row.validUntil!.toISOString() : null, cancelAtPeriodEnd: stillPaid && row.cancelAtPeriodEnd, paywall: true, supporterBoard: null };
}

interface DbRow {
  plan: PlusPlanId;
  status: "active" | "canceled";
  valid_until: Date | null;
  stripe_customer_id: string | null;
  stripe_subscription_id: string | null;
  cancel_at_period_end: boolean;
  supporter_board: boolean;
  last_event_at: Date;
}

const mapRow = (row: DbRow): PlusRow => ({
  plan: row.plan,
  status: row.status,
  validUntil: row.valid_until,
  stripeCustomerId: row.stripe_customer_id,
  stripeSubscriptionId: row.stripe_subscription_id,
  cancelAtPeriodEnd: row.cancel_at_period_end,
  supporterBoard: row.supporter_board,
  lastEventAt: row.last_event_at,
});

export async function getPlusRow(userId: string): Promise<PlusRow | null> {
  const result = await pool.query<DbRow>("SELECT plan, status, valid_until, stripe_customer_id, stripe_subscription_id, cancel_at_period_end, supporter_board, last_event_at FROM user_plus WHERE user_id = $1", [userId]);
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
  /** Defaults to false. */
  cancelAtPeriodEnd?: boolean;
  /** The Stripe event's own time: a write older than what is stored is ignored, so out-of-order delivery can't undo a newer event. */
  eventAt: Date;
}

/** Writes an account's Plus state, unless a newer Stripe event has already been applied. Returns whether it was applied. */
export async function writePlus(write: PlusWrite): Promise<boolean> {
  const result = await pool.query(
    `INSERT INTO user_plus (user_id, plan, status, valid_until, stripe_customer_id, stripe_subscription_id, cancel_at_period_end, last_event_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
     ON CONFLICT (user_id) DO UPDATE SET
       plan = EXCLUDED.plan,
       status = EXCLUDED.status,
       valid_until = EXCLUDED.valid_until,
       stripe_customer_id = COALESCE(EXCLUDED.stripe_customer_id, user_plus.stripe_customer_id),
       stripe_subscription_id = COALESCE(EXCLUDED.stripe_subscription_id, user_plus.stripe_subscription_id),
       cancel_at_period_end = EXCLUDED.cancel_at_period_end,
       last_event_at = EXCLUDED.last_event_at,
       updated_at = now()
     WHERE EXCLUDED.last_event_at >= user_plus.last_event_at
     RETURNING user_id`,
    [write.userId, write.plan, write.status, write.validUntil, write.stripeCustomerId, write.stripeSubscriptionId, write.cancelAtPeriodEnd ?? false, write.eventAt]
  );
  return (result.rowCount ?? 0) > 0;
}

/** The account a Stripe subscription or customer belongs to, from what the checkout stored. */
export async function findUserForStripe(subscriptionId: string | null, customerId: string | null): Promise<{ userId: string; row: PlusRow } | null> {
  const result = await pool.query<DbRow & { user_id: string }>(
    `SELECT user_id, plan, status, valid_until, stripe_customer_id, stripe_subscription_id, cancel_at_period_end, supporter_board, last_event_at
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

/** A Lifetime member chooses to have their display name on the supporters board, or takes it off again. False when the account is not an active Lifetime member. */
export async function setSupporterBoard(userId: string, show: boolean): Promise<boolean> {
  const result = await pool.query(
    `UPDATE user_plus
     SET supporter_board = $2, supporter_since = CASE WHEN $2::boolean THEN COALESCE(supporter_since, now()) ELSE supporter_since END, updated_at = now()
     WHERE user_id = $1 AND plan = 'lifetime' AND status = 'active'`,
    [userId, show]
  );
  return (result.rowCount ?? 0) > 0;
}

export interface Supporter {
  /** The display name, at most 24 characters. */
  name: string;
  /** The picture id of their own (default) profile, or null when it has none yet. */
  avatar: string | null;
  /** The month they joined the board, "2026-10". */
  since: string | null;
}

export interface Supporters {
  /** The earliest to join first. */
  supporters: Supporter[];
  /** How many are on the board (the list is cut at a limit). */
  total: number;
}

/** The nickname shown for a supporter whose account has no display name. */
export const ANONYMOUS_SUPPORTER = "Anonymous supporter";

/**
 * Who is on the supporters board: active Lifetime members who chose to be listed, by display name (at most 24 characters), the picture of their
 * own profile and the month they joined, the earliest first. Nothing else about them (no email, no id) ever leaves here.
 */
export async function listSupporters(limit = 500): Promise<Supporters> {
  const result = await pool.query<{ name: string | null; avatar: string | null; since: string | null; total: string }>(
    `SELECT left(btrim(u.display_name), 24) AS name, pr.avatar AS avatar,
            to_char(p.supporter_since AT TIME ZONE 'UTC', 'YYYY-MM') AS since, count(*) OVER () AS total
     FROM user_plus p
     JOIN users u ON u.id = p.user_id
     LEFT JOIN profiles pr ON pr.user_id = p.user_id AND pr.is_default
     WHERE p.plan = 'lifetime' AND p.status = 'active' AND p.supporter_board AND u.deleted_at IS NULL
     ORDER BY p.supporter_since ASC NULLS LAST, p.created_at ASC
     LIMIT $1`,
    [limit]
  );
  return {
    supporters: result.rows.map((row) => ({ name: row.name && row.name !== "" ? row.name : ANONYMOUS_SUPPORTER, avatar: row.avatar, since: row.since })),
    total: result.rows[0] ? Number(result.rows[0].total) : 0,
  };
}
