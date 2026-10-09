import { pool } from "../db/pool.js";
import type { PickedDismissalInput } from "../schemas/pickedDismissals.js";

export interface PickedDismissal {
  profileId: string;
  contentId: string;
  dismissedAt: Date;
}

interface Row {
  profile_id: string;
  content_id: string;
  dismissed_at: Date;
}

/** Removals older than this are of no use to any client (the longest hide is days) and are dropped. */
export const KEEP_DAYS = 30;

const mapRow = (row: Row): PickedDismissal => ({ profileId: row.profile_id, contentId: row.content_id, dismissedAt: row.dismissed_at });

/** This profile's recent removals -- what a sign-in or app launch pulls down. The client decides which are still within its hide window. */
export async function listRecent(userId: string, profileId: string): Promise<PickedDismissal[]> {
  const result = await pool.query<Row>(
    `SELECT profile_id, content_id, dismissed_at FROM picked_dismissals
     WHERE user_id = $1 AND profile_id = $2 AND dismissed_at > now() - make_interval(days => $3)
     ORDER BY dismissed_at ASC`,
    [userId, profileId, KEEP_DAYS]
  );
  return result.rows.map(mapRow);
}

/**
 * Saves one removal. Last-write-wins on the client's own dismissedAt in one atomic statement, so a device that was offline cannot move a
 * title's removal backwards. Always returns the slot's current state (this write if it won, otherwise what was already there).
 */
export async function upsert(userId: string, input: PickedDismissalInput): Promise<PickedDismissal> {
  await pool.query(`DELETE FROM picked_dismissals WHERE user_id = $1 AND dismissed_at < now() - make_interval(days => $2)`, [userId, KEEP_DAYS]);
  const result = await pool.query<Row>(
    `INSERT INTO picked_dismissals (user_id, profile_id, content_id, dismissed_at)
     VALUES ($1, $2, $3, $4)
     ON CONFLICT (user_id, profile_id, content_id) DO UPDATE SET dismissed_at = EXCLUDED.dismissed_at
     WHERE EXCLUDED.dismissed_at > picked_dismissals.dismissed_at
     RETURNING profile_id, content_id, dismissed_at`,
    [userId, input.profileId, input.contentId, new Date(input.dismissedAt)]
  );
  if (result.rows.length > 0) return mapRow(result.rows[0]!);
  const current = await pool.query<Row>(
    `SELECT profile_id, content_id, dismissed_at FROM picked_dismissals WHERE user_id = $1 AND profile_id = $2 AND content_id = $3`,
    [userId, input.profileId, input.contentId]
  );
  return mapRow(current.rows[0]!);
}
