import { pool } from "../db/pool.js";
import type { DownloadEventInput } from "../schemas/downloadEvents.js";

/** Rows older than this are of no use to the developer panel (it looks back a week) and are dropped. */
const KEEP_DAYS = 90;

export async function recordDownloadEvent(userId: string, input: DownloadEventInput): Promise<void> {
  await pool.query(
    `INSERT INTO download_events (user_id, kind, content_type, episode, resolution, cached) VALUES ($1, $2, $3, $4, $5, $6)`,
    [userId, input.kind, input.contentType, input.episode, input.kind === "file" ? input.resolution ?? null : null, input.kind === "file" ? input.cached ?? null : null]
  );
  // Housekeeping now and then instead of a scheduled job: one report in a hundred also clears out the old rows.
  if (Math.random() < 0.01) await pool.query(`DELETE FROM download_events WHERE created_at < now() - interval '${KEEP_DAYS} days'`);
}

/**
 * The developer panel's "Downloads": the last 7 days of web Download use. `triedPeople` opened the panel or were shown the Plus popup,
 * `startedPeople` pressed a file (the most the web app can know: the browser does the download), `popupPeople` had no Plus.
 */
export async function downloadsSummary() {
  const [totals, byQuality] = await Promise.all([
    pool.query<{ popupPeople: number; openedPeople: number; startedPeople: number; triedPeople: number; popups: number; opens: number; files: number; episodeFiles: number; uncachedFiles: number }>(
      `SELECT count(DISTINCT user_id) FILTER (WHERE kind = 'popup')::int AS "popupPeople",
              count(DISTINCT user_id) FILTER (WHERE kind = 'opened')::int AS "openedPeople",
              count(DISTINCT user_id) FILTER (WHERE kind = 'file')::int AS "startedPeople",
              count(DISTINCT user_id)::int AS "triedPeople",
              count(*) FILTER (WHERE kind = 'popup')::int AS popups,
              count(*) FILTER (WHERE kind = 'opened')::int AS opens,
              count(*) FILTER (WHERE kind = 'file')::int AS files,
              count(*) FILTER (WHERE kind = 'file' AND episode)::int AS "episodeFiles",
              count(*) FILTER (WHERE kind = 'file' AND cached = false)::int AS "uncachedFiles"
       FROM download_events WHERE created_at > now() - interval '7 days'`
    ),
    pool.query<{ label: string; files: number }>(
      `SELECT COALESCE(NULLIF(resolution, ''), 'unknown') AS label, count(*)::int AS files
       FROM download_events WHERE kind = 'file' AND created_at > now() - interval '7 days'
       GROUP BY 1 ORDER BY files DESC, label LIMIT 8`
    ),
  ]);
  return { ...totals.rows[0]!, byQuality: byQuality.rows };
}

export interface DownloadDay {
  date: string;
  popups: number;
  opens: number;
  files: number;
}
export interface DownloadPerson {
  userId: string;
  email: string;
  displayName: string | null;
  /** Their Plus plan right now, or null. */
  plan: string | null;
  popups: number;
  opens: number;
  files: number;
  episodeFiles: number;
  lastAt: string;
  lastKind: "popup" | "opened" | "file";
  /** The quality they pressed most, or null when they never pressed a file. */
  topQuality: string | null;
}
export interface DownloadRecent {
  kind: "popup" | "opened" | "file";
  contentType: "MOVIE" | "TV_SHOW";
  episode: boolean;
  resolution: string | null;
  cached: boolean | null;
  at: string;
  userId: string;
  email: string;
  displayName: string | null;
}

const ACTIVE_PLAN = `CASE WHEN p.status = 'active' AND (p.plan = 'lifetime' OR p.valid_until > now()) THEN p.plan ELSE NULL END`;

/**
 * The developer panel's Downloads view, in depth, for the last 7 days: each UTC day (so a trend can be drawn), movies against shows,
 * cached against not, who tried and what they did (one row per person, most recent first), and the latest events. Still no title,
 * content id, release name or file address: only what the web app reports.
 */
export async function downloadsDetail() {
  const [days, byType, cached, people, recent] = await Promise.all([
    pool.query<DownloadDay>(
      `SELECT to_char(d::date, 'YYYY-MM-DD') AS date,
              count(e.id) FILTER (WHERE e.kind = 'popup')::int AS popups,
              count(e.id) FILTER (WHERE e.kind = 'opened')::int AS opens,
              count(e.id) FILTER (WHERE e.kind = 'file')::int AS files
       FROM generate_series((now() AT TIME ZONE 'utc')::date - 6, (now() AT TIME ZONE 'utc')::date, interval '1 day') d
       LEFT JOIN download_events e ON (e.created_at AT TIME ZONE 'utc')::date = d::date
       GROUP BY d ORDER BY d`
    ),
    pool.query<{ type: string; popups: number; opens: number; files: number }>(
      `SELECT content_type AS type,
              count(*) FILTER (WHERE kind = 'popup')::int AS popups,
              count(*) FILTER (WHERE kind = 'opened')::int AS opens,
              count(*) FILTER (WHERE kind = 'file')::int AS files
       FROM download_events WHERE created_at > now() - interval '7 days' GROUP BY content_type ORDER BY content_type`
    ),
    pool.query<{ cached: number; uncached: number; unknown: number }>(
      `SELECT count(*) FILTER (WHERE cached = true)::int AS cached,
              count(*) FILTER (WHERE cached = false)::int AS uncached,
              count(*) FILTER (WHERE cached IS NULL)::int AS unknown
       FROM download_events WHERE kind = 'file' AND created_at > now() - interval '7 days'`
    ),
    pool.query<DownloadPerson>(
      `SELECT u.id AS "userId", u.email, u.display_name AS "displayName", ${ACTIVE_PLAN} AS plan,
              count(*) FILTER (WHERE e.kind = 'popup')::int AS popups,
              count(*) FILTER (WHERE e.kind = 'opened')::int AS opens,
              count(*) FILTER (WHERE e.kind = 'file')::int AS files,
              count(*) FILTER (WHERE e.kind = 'file' AND e.episode)::int AS "episodeFiles",
              max(e.created_at) AS "lastAt",
              (array_agg(e.kind ORDER BY e.created_at DESC))[1] AS "lastKind",
              mode() WITHIN GROUP (ORDER BY e.resolution) FILTER (WHERE e.kind = 'file' AND e.resolution IS NOT NULL AND e.resolution <> '') AS "topQuality"
       FROM download_events e JOIN users u ON u.id = e.user_id LEFT JOIN user_plus p ON p.user_id = u.id
       WHERE e.created_at > now() - interval '7 days' AND u.deleted_at IS NULL
       GROUP BY u.id, u.email, u.display_name, p.status, p.plan, p.valid_until
       ORDER BY max(e.created_at) DESC LIMIT 100`
    ),
    pool.query<DownloadRecent>(
      `SELECT e.kind, e.content_type AS "contentType", e.episode, e.resolution, e.cached, e.created_at AS at,
              u.id AS "userId", u.email, u.display_name AS "displayName"
       FROM download_events e JOIN users u ON u.id = e.user_id
       WHERE e.created_at > now() - interval '7 days' AND u.deleted_at IS NULL
       ORDER BY e.created_at DESC LIMIT 60`
    ),
  ]);
  return { days: days.rows, byType: byType.rows, cached: cached.rows[0]!, people: people.rows, recent: recent.rows };
}
