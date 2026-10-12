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
