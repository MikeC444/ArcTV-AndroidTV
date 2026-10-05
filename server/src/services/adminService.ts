import { pool } from "../db/pool.js";

/**
 * Read-only views for the developer panel. Deliberately returns only what a developer needs: never password hashes, tokens, PIN hashes
 * or the full addon address (addon addresses often carry a person's debrid key, so only the host and the debrid service's name come out).
 */

const DEBRID = /(realdebrid|real-debrid|alldebrid|premiumize|debridlink|debrid-link|torbox|offcloud|easydebrid|pikpak|putio)/i;

/** "https://torrentio.strem.fun/realdebrid=KEY/manifest.json" → host and a debrid name; the path and query (the secret part) are dropped. */
export function describeAddonUrl(url: string): { host: string; configured: boolean; debrid: string | null } {
  let host = "(unreadable)";
  let rest = "";
  try {
    const u = new URL(url);
    host = u.host;
    rest = decodeURIComponent(`${u.pathname}${u.search}`);
  } catch {
    rest = url;
  }
  const segments = rest.split("/").filter(Boolean);
  const match = DEBRID.exec(rest);
  return { host, configured: segments.length > 1 || rest.includes("?"), debrid: match ? match[1]!.toLowerCase().replace("real-debrid", "realdebrid").replace("debrid-link", "debridlink") : null };
}

const ACTIVE_PLAN = `CASE WHEN p.status = 'active' AND (p.plan = 'lifetime' OR p.valid_until > now()) THEN p.plan ELSE NULL END`;

export async function summary() {
  const [users, active, plans, versions, external] = await Promise.all([
    pool.query<{ total: number; new7d: number }>(`SELECT count(*)::int AS total, count(*) FILTER (WHERE created_at > now() - interval '7 days')::int AS new7d FROM users WHERE deleted_at IS NULL`),
    pool.query<{ n: number }>(`SELECT count(DISTINCT d.user_id)::int AS n FROM devices d JOIN users u ON u.id = d.user_id WHERE d.revoked_at IS NULL AND u.deleted_at IS NULL AND d.last_seen_at > now() - interval '7 days'`),
    pool.query<{ plan: string; n: number }>(`SELECT ${ACTIVE_PLAN} AS plan, count(*)::int AS n FROM user_plus p JOIN users u ON u.id = p.user_id WHERE u.deleted_at IS NULL GROUP BY 1`),
    pool.query<{ version: string; platform: string; devices: number }>(
      `SELECT COALESCE(d.app_version, 'unknown') AS version, d.platform, count(*)::int AS devices
       FROM devices d JOIN users u ON u.id = d.user_id WHERE d.revoked_at IS NULL AND u.deleted_at IS NULL
       GROUP BY 1, 2 ORDER BY devices DESC, version DESC`
    ),
    externalPlayerSummary(),
  ]);
  const plus: Record<string, number> = { monthly: 0, yearly: 0, lifetime: 0 };
  for (const row of plans.rows) if (row.plan) plus[row.plan] = row.n;
  return { users: users.rows[0]!.total, newLast7Days: users.rows[0]!.new7d, activeLast7Days: active.rows[0]!.n, plus, versions: versions.rows, externalPlayer: external };
}

/** How often people hand a title to another player: a lot of "after an error" points at the built-in player, not at taste. */
export async function externalPlayerSummary() {
  const [totals, recent] = await Promise.all([
    pool.query<{ opens7d: number; users7d: number; afterError7d: number; fromButton7d: number; noPlayer7d: number; opensTotal: number }>(
      `SELECT count(*) FILTER (WHERE created_at > now() - interval '7 days' AND outcome = 'opened')::int AS "opens7d",
              count(DISTINCT user_id) FILTER (WHERE created_at > now() - interval '7 days')::int AS "users7d",
              count(*) FILTER (WHERE created_at > now() - interval '7 days' AND launched_from = 'error')::int AS "afterError7d",
              count(*) FILTER (WHERE created_at > now() - interval '7 days' AND launched_from = 'button')::int AS "fromButton7d",
              count(*) FILTER (WHERE created_at > now() - interval '7 days' AND outcome = 'no_player')::int AS "noPlayer7d",
              count(*) FILTER (WHERE outcome = 'opened')::int AS "opensTotal"
       FROM external_player_events`
    ),
    pool.query(
      `SELECT e.title, e.release_title AS "releaseTitle", e.resolution, e.codec, e.launched_from AS trigger, e.outcome, e.error_message AS "errorMessage",
              e.app_version AS "appVersion", e.created_at AS "createdAt", u.email
       FROM external_player_events e JOIN users u ON u.id = e.user_id
       ORDER BY e.created_at DESC LIMIT 50`
    ),
  ]);
  return { ...totals.rows[0]!, recent: recent.rows };
}

export interface UserListQuery {
  q?: string;
  /** "free", or an active plan. */
  plan?: "free" | "monthly" | "yearly" | "lifetime";
  /** "<platform>|<app version>" of one of the person's devices; the version "unknown" means one that never reported. */
  device?: string;
  addons?: "with" | "none";
  watching?: "with" | "none";
  seen?: "1h" | "24h" | "7d" | "30d" | "older" | "never";
  limit: number;
  offset: number;
}

const LAST_SEEN = `(SELECT max(d.last_seen_at) FROM devices d WHERE d.user_id = u.id)`;
const SEEN_WITHIN: Record<string, string> = { "1h": "1 hour", "24h": "24 hours", "7d": "7 days", "30d": "30 days" };

export async function listUsers({ q, plan, device, addons, watching, seen, limit, offset }: UserListQuery) {
  const like = q && q.trim() ? `%${q.trim().replace(/[\\%_]/g, "\\$&")}%` : null;
  const params: unknown[] = [like];
  const clauses = [`u.deleted_at IS NULL`, `($1::text IS NULL OR u.email ILIKE $1 OR u.display_name ILIKE $1)`];
  const param = (value: unknown) => `$${params.push(value)}`;
  if (plan === "free") clauses.push(`NOT EXISTS (SELECT 1 FROM user_plus p WHERE p.user_id = u.id AND p.status = 'active' AND (p.plan = 'lifetime' OR p.valid_until > now()))`);
  else if (plan) clauses.push(`EXISTS (SELECT 1 FROM user_plus p WHERE p.user_id = u.id AND p.plan = ${param(plan)} AND p.status = 'active' AND (p.plan = 'lifetime' OR p.valid_until > now()))`);
  if (device) {
    const [platform, version] = device.split("|", 2);
    clauses.push(`EXISTS (SELECT 1 FROM devices d WHERE d.user_id = u.id AND d.revoked_at IS NULL AND d.platform = ${param(platform ?? "")} AND ${version === "unknown" ? "d.app_version IS NULL" : `d.app_version = ${param(version ?? "")}`})`);
  }
  if (addons) clauses.push(`${addons === "none" ? "NOT " : ""}EXISTS (SELECT 1 FROM user_addons a WHERE a.user_id = u.id AND a.deleted_at IS NULL)`);
  if (watching) clauses.push(`${watching === "none" ? "NOT " : ""}EXISTS (SELECT 1 FROM continue_watching c WHERE c.user_id = u.id AND c.deleted_at IS NULL)`);
  if (seen === "never") clauses.push(`${LAST_SEEN} IS NULL`);
  else if (seen === "older") clauses.push(`${LAST_SEEN} < now() - interval '30 days'`);
  else if (seen && SEEN_WITHIN[seen]) clauses.push(`${LAST_SEEN} > now() - interval '${SEEN_WITHIN[seen]}'`);
  const where = clauses.join(" AND ");
  const limitParam = param(limit);
  const offsetParam = param(offset);
  const [total, rows] = await Promise.all([
    pool.query<{ n: number }>(`SELECT count(*)::int AS n FROM users u WHERE ${where}`, params.slice(0, params.length - 2)),
    pool.query(
      `SELECT u.id, u.email, u.display_name AS "displayName", u.created_at AS "createdAt", u.is_admin AS "isAdmin",
              ${ACTIVE_PLAN} AS plan, p.valid_until AS "plusUntil", COALESCE(p.cancel_at_period_end, false) AS "cancelling",
              (SELECT count(*)::int FROM profiles pr WHERE pr.user_id = u.id) AS profiles,
              (SELECT count(*)::int FROM user_addons a WHERE a.user_id = u.id AND a.deleted_at IS NULL) AS addons,
              (SELECT count(*)::int FROM continue_watching c WHERE c.user_id = u.id AND c.deleted_at IS NULL) AS "continueWatching",
              (SELECT max(d.last_seen_at) FROM devices d WHERE d.user_id = u.id) AS "lastSeenAt",
              COALESCE((SELECT json_agg(json_build_object('name', d.device_name, 'platform', d.platform, 'appVersion', d.app_version, 'lastSeenAt', d.last_seen_at) ORDER BY d.last_seen_at DESC)
                        FROM devices d WHERE d.user_id = u.id AND d.revoked_at IS NULL), '[]'::json) AS devices
       FROM users u LEFT JOIN user_plus p ON p.user_id = u.id
       WHERE ${where}
       ORDER BY COALESCE((SELECT max(d.last_seen_at) FROM devices d WHERE d.user_id = u.id), u.created_at) DESC
       LIMIT ${limitParam} OFFSET ${offsetParam}`,
      params
    ),
  ]);
  return { total: total.rows[0]!.n, users: rows.rows };
}

export async function userDetail(userId: string) {
  const user = await pool.query(
    `SELECT u.id, u.email, u.display_name AS "displayName", u.created_at AS "createdAt", u.is_admin AS "isAdmin",
            ${ACTIVE_PLAN} AS plan, p.status AS "plusStatus", p.valid_until AS "plusUntil", COALESCE(p.cancel_at_period_end, false) AS "cancelling",
            (p.stripe_subscription_id IS NOT NULL) AS "hasStripeSubscription"
     FROM users u LEFT JOIN user_plus p ON p.user_id = u.id WHERE u.id = $1 AND u.deleted_at IS NULL`,
    [userId]
  );
  if (!user.rows[0]) return null;
  const [profiles, devices, addons, continueWatching, history, watchlist] = await Promise.all([
    pool.query(`SELECT id, name, kind, avatar, is_default AS "isDefault", (pin_hash IS NOT NULL) AS "hasPin", created_at AS "createdAt" FROM profiles WHERE user_id = $1 ORDER BY is_default DESC, created_at`, [userId]),
    pool.query(
      `SELECT device_name AS name, platform, app_version AS "appVersion", last_seen_at AS "lastSeenAt", created_at AS "createdAt", revoked_at AS "revokedAt" FROM devices WHERE user_id = $1 ORDER BY revoked_at IS NOT NULL, last_seen_at DESC`,
      [userId]
    ),
    pool.query<{ manifest_url: string }>(
      `SELECT profile_id AS "profileId", name, addon_id AS "addonId", manifest_url, enabled, installed_at AS "installedAt", updated_at AS "updatedAt" FROM user_addons WHERE user_id = $1 AND deleted_at IS NULL ORDER BY profile_id, sort_order`,
      [userId]
    ),
    pool.query(
      `SELECT profile_id AS "profileId", title, content_type AS "contentType", season_number AS "seasonNumber", episode_number AS "episodeNumber", episode_title AS "episodeTitle", position_ms::float8 AS "positionMs", duration_ms::float8 AS "durationMs", last_watched_at AS "lastWatchedAt"
       FROM continue_watching WHERE user_id = $1 AND deleted_at IS NULL ORDER BY last_watched_at DESC LIMIT 100`,
      [userId]
    ),
    pool.query(
      `SELECT profile_id AS "profileId", title, content_type AS "contentType", season_number AS "seasonNumber", episode_number AS "episodeNumber", position_ms::float8 AS "positionMs", duration_ms::float8 AS "durationMs", completed, watched_at AS "watchedAt"
       FROM watch_history WHERE user_id = $1 ORDER BY watched_at DESC LIMIT 50`,
      [userId]
    ),
    pool.query<{ n: number }>(`SELECT count(*)::int AS n FROM watchlist_items WHERE user_id = $1 AND deleted_at IS NULL`, [userId]),
  ]);
  return {
    user: user.rows[0],
    profiles: profiles.rows,
    devices: devices.rows,
    addons: addons.rows.map(({ manifest_url, ...rest }) => ({ ...rest, ...describeAddonUrl(manifest_url) })),
    continueWatching: continueWatching.rows,
    history: history.rows,
    myListCount: watchlist.rows[0]!.n,
  };
}
