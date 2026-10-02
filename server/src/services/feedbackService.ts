import { pool } from "../db/pool.js";
import type { FeedbackDeleteInput, FeedbackInput } from "../schemas/feedback.js";

export interface MovieFeedback {
  profileId: string;
  providerId: string;
  contentId: string;
  contentType: string;
  title: string;
  feedback: "like" | "dislike";
  updatedAt: Date;
  /** Non-null means the feedback is currently cleared -- lets a client tell "still set" from "cleared since I last knew" on the shape POST and DELETE both return. */
  deletedAt: Date | null;
}

interface FeedbackRow {
  profile_id: string;
  provider_id: string;
  content_id: string;
  content_type: string;
  title: string;
  feedback: "like" | "dislike";
  updated_at: Date;
  deleted_at: Date | null;
}

const COLUMNS = `profile_id, provider_id, content_id, content_type, title, feedback, updated_at, deleted_at`;

function mapRow(row: FeedbackRow): MovieFeedback {
  return {
    profileId: row.profile_id,
    providerId: row.provider_id,
    contentId: row.content_id,
    contentType: row.content_type,
    title: row.title,
    feedback: row.feedback,
    updatedAt: row.updated_at,
    deletedAt: row.deleted_at,
  };
}

/** This profile's feedback that is currently set -- what a sign-in or app launch pulls down. Never includes cleared rows. */
export async function listActiveFeedback(userId: string, profileId: string): Promise<MovieFeedback[]> {
  const result = await pool.query<FeedbackRow>(
    `SELECT ${COLUMNS} FROM movie_feedback
     WHERE user_id = $1 AND profile_id = $2 AND deleted_at IS NULL
     ORDER BY updated_at ASC`,
    [userId, profileId]
  );
  return result.rows.map(mapRow);
}

/**
 * Sets (or re-sets after a clear) one movie's feedback. Last-write-wins on
 * the client's own updatedAt in one atomic statement, like the watchlist:
 * a device that was offline cannot overwrite a newer decision made
 * elsewhere just by reconnecting later. Always returns the slot's current
 * authoritative state -- this write if it won, whatever was already there
 * if it lost -- so the caller can reconcile either way.
 */
export async function upsertFeedback(userId: string, input: FeedbackInput): Promise<MovieFeedback> {
  const result = await pool.query<FeedbackRow>(
    `INSERT INTO movie_feedback (user_id, profile_id, provider_id, content_id, content_type, title, feedback, updated_at, deleted_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NULL)
     ON CONFLICT (user_id, profile_id, provider_id, content_id, content_type) DO UPDATE SET
       title = EXCLUDED.title,
       feedback = EXCLUDED.feedback,
       updated_at = EXCLUDED.updated_at,
       deleted_at = NULL
     WHERE EXCLUDED.updated_at > movie_feedback.updated_at
     RETURNING ${COLUMNS}`,
    [userId, input.profileId, input.providerId, input.contentId, input.contentType, input.title, input.feedback, new Date(input.updatedAt)]
  );
  if (result.rows.length > 0) return mapRow(result.rows[0]!);
  return (await getFeedback(userId, input.profileId, input.providerId, input.contentId, input.contentType)) as MovieFeedback;
}

/**
 * Clears one movie's feedback (soft delete) under the same last-write-wins
 * rule. Returns null only when the slot never existed for this user, so
 * there is nothing server-side to reconcile; otherwise the slot's current
 * state, whether this call cleared it or lost to a newer write.
 */
export async function clearFeedback(userId: string, input: FeedbackDeleteInput): Promise<MovieFeedback | null> {
  const result = await pool.query<FeedbackRow>(
    `UPDATE movie_feedback
     SET deleted_at = $6, updated_at = $6
     WHERE user_id = $1 AND profile_id = $2 AND provider_id = $3 AND content_id = $4 AND content_type = $5
       AND updated_at < $6
     RETURNING ${COLUMNS}`,
    [userId, input.profileId, input.providerId, input.contentId, input.contentType, new Date(input.updatedAt)]
  );
  if (result.rows.length > 0) return mapRow(result.rows[0]!);
  return getFeedback(userId, input.profileId, input.providerId, input.contentId, input.contentType);
}

async function getFeedback(userId: string, profileId: string, providerId: string, contentId: string, contentType: string): Promise<MovieFeedback | null> {
  const result = await pool.query<FeedbackRow>(
    `SELECT ${COLUMNS} FROM movie_feedback
     WHERE user_id = $1 AND profile_id = $2 AND provider_id = $3 AND content_id = $4 AND content_type = $5`,
    [userId, profileId, providerId, contentId, contentType]
  );
  return result.rows[0] ? mapRow(result.rows[0]) : null;
}
