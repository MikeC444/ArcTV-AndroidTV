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
  /** With supporterBoard on: listed as "Anonymous" (no name, no picture) rather than by display name. */
  supporterAnonymous: boolean;
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
  /** For a Lifetime member on the board: whether they are listed as Anonymous. null for everyone else. */
  supporterAnonymous: boolean | null;
}

/**
 * What an account has, from what is stored: the paywall off means everyone does (early access); on, a Lifetime plan that
 * is active, or a subscription that is active and not past its paid period. Pure, so every case is tested directly.
 */
export function computeEntitlement(row: PlusRow | null, paywallOn: boolean, now: Date = new Date()): Entitlement {
  if (!paywallOn) return { active: true, plan: "early_access", validUntil: null, cancelAtPeriodEnd: false, paywall: false, supporterBoard: null, supporterAnonymous: null };
  if (!row || row.status !== "active") return { active: false, plan: null, validUntil: null, cancelAtPeriodEnd: false, paywall: true, supporterBoard: null, supporterAnonymous: null };
  if (row.plan === "lifetime") return { active: true, plan: "lifetime", validUntil: null, cancelAtPeriodEnd: false, paywall: true, supporterBoard: row.supporterBoard, supporterAnonymous: row.supporterBoard ? row.supporterAnonymous : null };
  const stillPaid = row.validUntil !== null && row.validUntil.getTime() > now.getTime();
  return { active: stillPaid, plan: stillPaid ? row.plan : null, validUntil: stillPaid ? row.validUntil!.toISOString() : null, cancelAtPeriodEnd: stillPaid && row.cancelAtPeriodEnd, paywall: true, supporterBoard: null, supporterAnonymous: null };
}

interface DbRow {
  plan: PlusPlanId;
  status: "active" | "canceled";
  valid_until: Date | null;
  stripe_customer_id: string | null;
  stripe_subscription_id: string | null;
  cancel_at_period_end: boolean;
  supporter_board: boolean;
  supporter_anonymous: boolean;
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
  supporterAnonymous: row.supporter_anonymous,
  lastEventAt: row.last_event_at,
});

export async function getPlusRow(userId: string): Promise<PlusRow | null> {
  const result = await pool.query<DbRow>("SELECT plan, status, valid_until, stripe_customer_id, stripe_subscription_id, cancel_at_period_end, supporter_board, supporter_anonymous, last_event_at FROM user_plus WHERE user_id = $1", [userId]);
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
  /** A new Lifetime purchase whose buyer answered Yes to the supporters-board question at checkout. Ignored for an account that already was Lifetime, so a retry never undoes a later choice. */
  joinBoard?: "named" | "anonymous";
  /** The Stripe event's own time: a write older than what is stored is ignored, so out-of-order delivery can't undo a newer event. */
  eventAt: Date;
}

/** Writes an account's Plus state, unless a newer Stripe event has already been applied. Returns whether it was applied. */
export async function writePlus(write: PlusWrite): Promise<boolean> {
  const result = await pool.query(
    `INSERT INTO user_plus (user_id, plan, status, valid_until, stripe_customer_id, stripe_subscription_id, cancel_at_period_end, last_event_at, supporter_board, supporter_anonymous, supporter_since)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9::boolean, $10::boolean, CASE WHEN $9::boolean THEN $8::timestamptz END)
     ON CONFLICT (user_id) DO UPDATE SET
       plan = EXCLUDED.plan,
       status = EXCLUDED.status,
       valid_until = EXCLUDED.valid_until,
       stripe_customer_id = COALESCE(EXCLUDED.stripe_customer_id, user_plus.stripe_customer_id),
       stripe_subscription_id = COALESCE(EXCLUDED.stripe_subscription_id, user_plus.stripe_subscription_id),
       cancel_at_period_end = EXCLUDED.cancel_at_period_end,
       -- Joins the board only when an account becomes Lifetime and said Yes; an account that already was Lifetime keeps whatever it chose since.
       supporter_board = user_plus.supporter_board OR (EXCLUDED.supporter_board AND user_plus.plan <> 'lifetime'),
       supporter_anonymous = CASE WHEN EXCLUDED.supporter_board AND user_plus.plan <> 'lifetime' THEN EXCLUDED.supporter_anonymous ELSE user_plus.supporter_anonymous END,
       supporter_since = COALESCE(user_plus.supporter_since, CASE WHEN EXCLUDED.supporter_board AND user_plus.plan <> 'lifetime' THEN EXCLUDED.supporter_since END),
       last_event_at = EXCLUDED.last_event_at,
       updated_at = now()
     WHERE EXCLUDED.last_event_at >= user_plus.last_event_at
     RETURNING user_id`,
    [write.userId, write.plan, write.status, write.validUntil, write.stripeCustomerId, write.stripeSubscriptionId, write.cancelAtPeriodEnd ?? false, write.eventAt, write.joinBoard !== undefined, write.joinBoard === "anonymous"]
  );
  return (result.rowCount ?? 0) > 0;
}

/** The account a Stripe subscription or customer belongs to, from what the checkout stored. */
export async function findUserForStripe(subscriptionId: string | null, customerId: string | null): Promise<{ userId: string; row: PlusRow } | null> {
  const result = await pool.query<DbRow & { user_id: string }>(
    `SELECT user_id, plan, status, valid_until, stripe_customer_id, stripe_subscription_id, cancel_at_period_end, supporter_board, supporter_anonymous, last_event_at
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
export async function setSupporterBoard(userId: string, show: boolean, anonymous = false): Promise<boolean> {
  const result = await pool.query(
    `UPDATE user_plus
     SET supporter_board = $2, supporter_anonymous = ($2::boolean AND $3::boolean), supporter_since = CASE WHEN $2::boolean THEN COALESCE(supporter_since, now()) ELSE supporter_since END, updated_at = now()
     WHERE user_id = $1 AND plan = 'lifetime' AND status = 'active'`,
    [userId, show, anonymous]
  );
  return (result.rowCount ?? 0) > 0;
}

export interface Supporter {
  /** The display name, at most 24 characters; "Anonymous" for someone who chose to be listed without a name. */
  name: string;
  /** The picture id of their own (default) profile, or null when it has none yet (and always null for an anonymous supporter). */
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

/** The name shown for a supporter who chose to be listed anonymously. */
export const ANONYMOUS_LISTED = "Anonymous";

/**
 * Who is on the supporters board: active Lifetime members who chose to be listed, by display name (at most 24 characters) and the picture of their
 * own profile, or as "Anonymous" with no picture if they chose that, and the month they joined, the earliest first. Nothing else about them (no email, no id) ever leaves here.
 */
export async function listSupporters(limit = 500): Promise<Supporters> {
  const result = await pool.query<{ name: string | null; avatar: string | null; anonymous: boolean; since: string | null; total: string }>(
    `SELECT CASE WHEN p.supporter_anonymous THEN NULL ELSE left(btrim(u.display_name), 24) END AS name, CASE WHEN p.supporter_anonymous THEN NULL ELSE pr.avatar END AS avatar, p.supporter_anonymous AS anonymous,
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
    supporters: result.rows.map((row) => ({ name: row.anonymous ? ANONYMOUS_LISTED : row.name && row.name !== "" ? row.name : ANONYMOUS_SUPPORTER, avatar: row.avatar, since: row.since })),
    total: result.rows[0] ? Number(result.rows[0].total) : 0,
  };
}
