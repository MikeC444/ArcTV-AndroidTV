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

/** Someone counts as online when their session made any request within this long (the apps only call the server when they sync, browse or play). */
export const ONLINE_WINDOW_SECONDS = 5 * 60;
/** Someone counts as watching when a device saved playback progress within this long (the apps save about every 15 seconds while playing). */
export const WATCHING_WINDOW_SECONDS = 45;

export interface LiveCount {
  users: number;
  byPlatform: Record<string, number>;
}

const countByPlatform = (rows: Array<{ platform: string; n: number }>, total: number): LiveCount => ({ users: total, byPlatform: Object.fromEntries(rows.map((r) => [r.platform, r.n])) });

/** How many people are online and how many are watching right now: distinct accounts, in total and per platform (a person on two platforms counts once in the total). */
export async function liveSummary() {
  const online = `FROM sessions s JOIN devices d ON d.id = s.device_id JOIN users u ON u.id = s.user_id
                  WHERE s.revoked_at IS NULL AND d.revoked_at IS NULL AND u.deleted_at IS NULL AND s.last_used_at > now() - make_interval(secs => $1)`;
  const watching = `FROM devices d JOIN users u ON u.id = d.user_id
                    WHERE d.revoked_at IS NULL AND u.deleted_at IS NULL AND d.last_progress_at > now() - make_interval(secs => $1)`;
  const [onlineTotal, onlineBy, watchingTotal, watchingBy] = await Promise.all([
    pool.query<{ n: number }>(`SELECT count(DISTINCT s.user_id)::int AS n ${online}`, [ONLINE_WINDOW_SECONDS]),
    pool.query<{ platform: string; n: number }>(`SELECT d.platform, count(DISTINCT s.user_id)::int AS n ${online} GROUP BY d.platform ORDER BY n DESC, d.platform`, [ONLINE_WINDOW_SECONDS]),
    pool.query<{ n: number }>(`SELECT count(DISTINCT d.user_id)::int AS n ${watching}`, [WATCHING_WINDOW_SECONDS]),
    pool.query<{ platform: string; n: number }>(`SELECT d.platform, count(DISTINCT d.user_id)::int AS n ${watching} GROUP BY d.platform ORDER BY n DESC, d.platform`, [WATCHING_WINDOW_SECONDS]),
  ]);
  return {
    online: countByPlatform(onlineBy.rows, onlineTotal.rows[0]!.n),
    watching: countByPlatform(watchingBy.rows, watchingTotal.rows[0]!.n),
    onlineWindowSeconds: ONLINE_WINDOW_SECONDS,
    watchingWindowSeconds: WATCHING_WINDOW_SECONDS,
  };
}

/** How many days of growth the panel's chart gets (its range buttons pick 7, 30 or 90 of them, or all of these). */
export const GROWTH_DAYS = 180;

export interface GrowthPoint {
  /** The day, UTC, as YYYY-MM-DD. */
  date: string;
  /** Accounts created that day. */
  newUsers: number;
  /** Accounts that existed by the end of that day (deleted accounts are not counted on any day). */
  total: number;
}

/**
 * A continuous daily series ending today: [baseline] accounts existed before the first day, and [perDay] (YYYY-MM-DD to count) says how many were
 * created on each day after; days nobody signed up are still listed, so the chart has no gaps. Pure, so it is tested without a database.
 */
export function buildGrowth(baseline: number, perDay: Record<string, number>, today: Date, days: number): GrowthPoint[] {
  const start = Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate()) - (days - 1) * 86_400_000;
  const points: GrowthPoint[] = [];
  let total = baseline;
  for (let i = 0; i < days; i++) {
    const date = new Date(start + i * 86_400_000).toISOString().slice(0, 10);
    const newUsers = perDay[date] ?? 0;
    total += newUsers;
    points.push({ date, newUsers, total });
  }
  return points;
}

/** Sign-ups per day for the last [days] days with the running total, for the developer panel's growth chart. */
export async function userGrowth(days = GROWTH_DAYS): Promise<GrowthPoint[]> {
  const since = `date_trunc('day', now() AT TIME ZONE 'UTC') - make_interval(days => $1 - 1)`;
  const [before, daily] = await Promise.all([
    pool.query<{ n: number }>(`SELECT count(*)::int AS n FROM users WHERE deleted_at IS NULL AND (created_at AT TIME ZONE 'UTC') < ${since}`, [days]),
    pool.query<{ day: string; n: number }>(
      `SELECT to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS day, count(*)::int AS n FROM users
       WHERE deleted_at IS NULL AND (created_at AT TIME ZONE 'UTC') >= ${since} GROUP BY 1`,
      [days]
    ),
  ]);
  return buildGrowth(before.rows[0]!.n, Object.fromEntries(daily.rows.map((r) => [r.day, r.n])), new Date(), days);
}

export async function summary() {
  const [users, active, plans, versions, external, live, growth] = await Promise.all([
    pool.query<{ total: number; new7d: number }>(`SELECT count(*)::int AS total, count(*) FILTER (WHERE created_at > now() - interval '7 days')::int AS new7d FROM users WHERE deleted_at IS NULL`),
    pool.query<{ n: number }>(`SELECT count(DISTINCT d.user_id)::int AS n FROM devices d JOIN users u ON u.id = d.user_id WHERE d.revoked_at IS NULL AND u.deleted_at IS NULL AND d.last_seen_at > now() - interval '7 days'`),
    pool.query<{ plan: string; n: number }>(`SELECT ${ACTIVE_PLAN} AS plan, count(*)::int AS n FROM user_plus p JOIN users u ON u.id = p.user_id WHERE u.deleted_at IS NULL GROUP BY 1`),
    pool.query<{ version: string; platform: string; devices: number }>(
      `SELECT COALESCE(d.app_version, 'unknown') AS version, d.platform, count(*)::int AS devices
       FROM devices d JOIN users u ON u.id = d.user_id WHERE d.revoked_at IS NULL AND u.deleted_at IS NULL
       GROUP BY 1, 2 ORDER BY devices DESC, version DESC`
    ),
    externalPlayerSummary(),
    liveSummary(),
    userGrowth(),
  ]);
  const plus: Record<string, number> = { monthly: 0, yearly: 0, lifetime: 0 };
  for (const row of plans.rows) if (row.plan) plus[row.plan] = row.n;
  return { users: users.rows[0]!.total, newLast7Days: users.rows[0]!.new7d, activeLast7Days: active.rows[0]!.n, plus, versions: versions.rows, externalPlayer: external, live, userGrowth: growth };
}

/** How often people hand a title to another player (another app, or VLC's engine inside the app; `opens7d` counts the apps, `vlc7d` the engine): a lot of "after an error" points at the built-in player, not at taste. */
export async function externalPlayerSummary() {
  const [totals, recent] = await Promise.all([
    pool.query<{ opens7d: number; vlc7d: number; users7d: number; afterError7d: number; fromButton7d: number; noPlayer7d: number; opensTotal: number }>(
      `SELECT count(*) FILTER (WHERE created_at > now() - interval '7 days' AND outcome = 'opened' AND engine = 'external')::int AS "opens7d",
              count(*) FILTER (WHERE created_at > now() - interval '7 days' AND engine = 'vlc')::int AS "vlc7d",
              count(DISTINCT user_id) FILTER (WHERE created_at > now() - interval '7 days')::int AS "users7d",
              count(*) FILTER (WHERE created_at > now() - interval '7 days' AND launched_from = 'error')::int AS "afterError7d",
              count(*) FILTER (WHERE created_at > now() - interval '7 days' AND launched_from = 'button')::int AS "fromButton7d",
              count(*) FILTER (WHERE created_at > now() - interval '7 days' AND outcome = 'no_player')::int AS "noPlayer7d",
              count(*) FILTER (WHERE outcome = 'opened' AND engine = 'external')::int AS "opensTotal"
       FROM external_player_events`
    ),
    pool.query(
      `SELECT e.title, e.release_title AS "releaseTitle", e.resolution, e.codec, e.launched_from AS trigger, e.outcome, e.engine, e.error_message AS "errorMessage",
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

/** Who has clicked a one-off pop-up away (newest first), with when and on which app version; `total` is the whole count, `limit`/`offset` page the list. */
export async function featureIntroAcks(feature: string, limit: number, offset: number) {
  const [total, rows] = await Promise.all([
    pool.query<{ n: number }>(`SELECT count(*)::int AS n FROM feature_intro_acks a JOIN users u ON u.id = a.user_id WHERE a.feature = $1 AND u.deleted_at IS NULL`, [feature]),
    pool.query(
      `SELECT u.id, u.email, u.display_name AS "displayName", a.acknowledged_at AS "acknowledgedAt", a.app_version AS "appVersion", d.platform
       FROM feature_intro_acks a
       JOIN users u ON u.id = a.user_id AND u.deleted_at IS NULL
       LEFT JOIN devices d ON d.id = a.device_id
       WHERE a.feature = $1
       ORDER BY a.acknowledged_at DESC
       LIMIT $2 OFFSET $3`,
      [feature, limit, offset]
    ),
  ]);
  return { feature, total: total.rows[0]!.n, users: rows.rows };
}
