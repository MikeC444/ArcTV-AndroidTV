import { pool } from "../db/pool.js";
import type { PlaybackOutcomeInput } from "../schemas/playbackOutcomes.js";

/** Rows older than this are of no use to the developer panel (it looks back a week or a month) and are dropped. */
const KEEP_DAYS = 90;

export async function recordPlaybackOutcome(userId: string, deviceId: string, appVersion: string | null, input: PlaybackOutcomeInput): Promise<void> {
  await pool.query(
    `INSERT INTO playback_outcomes
       (user_id, device_id, app_version, device_model, engine, resolution, codec, hdr, start_ms, watched_ms, outcome, error_kind, sources_tried)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)`,
    [
      userId, deviceId, appVersion, input.deviceModel ?? null, input.engine, input.resolution ?? null, input.codec ?? null, input.hdr ?? null,
      input.startMs ?? null, input.watchedMs, input.outcome, input.errorKind ?? null, input.sourcesTried,
    ]
  );
  // Housekeeping now and then instead of a scheduled job: one report in a hundred also clears out the old rows.
  if (Math.random() < 0.01) await pool.query(`DELETE FROM playback_outcomes WHERE created_at < now() - interval '${KEEP_DAYS} days'`);
}

interface Group {
  label: string;
  plays: number;
  short: number;
  errors: number;
  neverStarted: number;
}

const GROUP_SQL = (column: string, limit: number) => `
  SELECT COALESCE(NULLIF(${column}, ''), 'unknown') AS label, count(*)::int AS plays,
         count(*) FILTER (WHERE outcome = 'short')::int AS short,
         count(*) FILTER (WHERE outcome = 'error')::int AS errors,
         count(*) FILTER (WHERE outcome = 'never_started')::int AS "neverStarted"
  FROM playback_outcomes WHERE created_at > now() - interval '7 days'
  GROUP BY 1 ORDER BY plays DESC, label LIMIT ${limit}`;

/**
 * The developer panel's "Playback health": how viewings of the last 7 days ended, overall and split by device model, player and picture
 * quality, so a device or format that people keep abandoning stands out. "Left early" counts short watches, errors and plays that never started.
 */
export async function playbackSummary() {
  const [totals, byDevice, byEngine, byResolution] = await Promise.all([
    pool.query<{ plays7d: number; users7d: number; short7d: number; errors7d: number; neverStarted7d: number; medianStartMs: number | null; multiSource7d: number }>(
      `SELECT count(*)::int AS "plays7d", count(DISTINCT user_id)::int AS "users7d",
              count(*) FILTER (WHERE outcome = 'short')::int AS "short7d",
              count(*) FILTER (WHERE outcome = 'error')::int AS "errors7d",
              count(*) FILTER (WHERE outcome = 'never_started')::int AS "neverStarted7d",
              (percentile_cont(0.5) WITHIN GROUP (ORDER BY start_ms))::int AS "medianStartMs",
              count(*) FILTER (WHERE sources_tried > 1)::int AS "multiSource7d"
       FROM playback_outcomes WHERE created_at > now() - interval '7 days'`
    ),
    pool.query<Group>(GROUP_SQL("device_model", 12)),
    pool.query<Group>(GROUP_SQL("engine", 4)),
    pool.query<Group>(GROUP_SQL("resolution", 8)),
  ]);
  return { ...totals.rows[0]!, byDevice: byDevice.rows, byEngine: byEngine.rows, byResolution: byResolution.rows };
}
