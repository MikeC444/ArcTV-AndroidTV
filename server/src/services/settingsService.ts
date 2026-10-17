import { pool } from "../db/pool.js";
import type { SettingsInput } from "../schemas/settings.js";

export interface UserSettings {
  homeRowOrder: string[];
  hiddenRowIds: string[];
  autoplayNextEpisode: boolean;
  skipIntroEnabled: boolean;
  subtitlesEnabled: boolean;
  /** An ISO 639-1 code (e.g. "en"), or null for "no preference". */
  defaultSubtitleLanguage: string | null;
  /** An ISO 639-1 code (e.g. "en"), or null for "no preference". */
  defaultAudioLanguage: string | null;
  /** Genre names hidden from every browse surface; empty means nothing is blocked. */
  blockedGenres: string[];
  /** When Your stats was last reset (it then counts only what was watched after this), or null if never. It only ever moves forward. */
  statsResetAt: Date | null;
  /** null only for an account that has never pushed settings from any device. */
  updatedAt: Date | null;
}

const DEFAULT_SETTINGS: UserSettings = {
  homeRowOrder: [],
  hiddenRowIds: [],
  autoplayNextEpisode: true,
  skipIntroEnabled: true,
  subtitlesEnabled: true,
  defaultSubtitleLanguage: null,
  defaultAudioLanguage: null,
  blockedGenres: [],
  statsResetAt: null,
  updatedAt: null,
};

interface SettingsRow {
  home_row_order: string[];
  hidden_row_ids: string[];
  autoplay_next_episode: boolean;
  skip_intro_enabled: boolean;
  subtitles_enabled: boolean;
  default_subtitle_language: string | null;
  default_audio_language: string | null;
  blocked_genres: string[];
  stats_reset_at: Date | null;
  updated_at: Date;
}

function mapRow(row: SettingsRow): UserSettings {
  return {
    homeRowOrder: row.home_row_order,
    hiddenRowIds: row.hidden_row_ids,
    autoplayNextEpisode: row.autoplay_next_episode,
    skipIntroEnabled: row.skip_intro_enabled,
    subtitlesEnabled: row.subtitles_enabled,
    defaultSubtitleLanguage: row.default_subtitle_language,
    defaultAudioLanguage: row.default_audio_language,
    blockedGenres: row.blocked_genres,
    statsResetAt: row.stats_reset_at,
    updatedAt: row.updated_at,
  };
}

/** No row yet (brand new account, never synced from any device) reads as the same defaults the column definitions themselves use, not an error. */
export async function getUserSettings(userId: string, profileId: string): Promise<UserSettings> {
  const result = await pool.query<SettingsRow>(
    `SELECT home_row_order, hidden_row_ids, autoplay_next_episode, skip_intro_enabled,
            subtitles_enabled, default_subtitle_language, default_audio_language, blocked_genres, stats_reset_at, updated_at
     FROM user_settings WHERE user_id = $1 AND profile_id = $2`,
    [userId, profileId]
  );
  const row = result.rows[0];
  return row ? mapRow(row) : DEFAULT_SETTINGS;
}

/**
 * Upserts settings using last-write-wins keyed on the client-supplied
 * updatedAt (when that device made the change locally, not when this
 * request happened to reach the server) -- comparing by receive order
 * instead would let a device that was offline for a while overwrite a
 * genuinely newer change from elsewhere just by reconnecting later.
 *
 * The comparison and the write happen in one atomic statement (the same
 * "UPDATE ... WHERE ... RETURNING" shape used throughout this codebase's
 * auth/QR flows) rather than a separate read-compare-then-write, so two
 * concurrent pushes from different devices can't race each other into an
 * inconsistent result.
 *
 * Always returns the row's current authoritative state afterward -- this
 * write's own values if it won the race, or whatever was already there if
 * it lost -- so a caller can simply persist the response as its new local
 * cache either way, without needing to know which one happened.
 */
/** The streaming-service tiles' order is kept in the same list as the rows' order, as `streaming:<id>` entries. */
const isServiceOrderEntry = (id: string): boolean => id.startsWith("streaming:");

/**
 * The order to store when [incoming] arrives: an app that predates the streaming tiles (the phone, Mac and Windows apps, the Fire TV before 0.7.0) pushes the
 * row order back without the tiles' `streaming:` entries, which would silently reset the tile order the person chose on the web or the TV. So when the incoming
 * list has none, the stored ones are kept (after the rows). A list that has any is the person's own choice and replaces them.
 */
export function keepServiceOrder(incoming: string[], stored: string[]): string[] {
  if (incoming.some(isServiceOrderEntry)) return incoming;
  return [...incoming, ...stored.filter(isServiceOrderEntry)];
}

export async function upsertUserSettings(userId: string, profileId: string, input: SettingsInput): Promise<UserSettings> {
  const existing = await pool.query<{ home_row_order: string[] }>("SELECT home_row_order FROM user_settings WHERE user_id = $1 AND profile_id = $2", [userId, profileId]);
  const homeRowOrder = keepServiceOrder(input.homeRowOrder, existing.rows[0]?.home_row_order ?? []);
  const result = await pool.query<SettingsRow>(
    `INSERT INTO user_settings (user_id, home_row_order, hidden_row_ids, autoplay_next_episode, skip_intro_enabled, subtitles_enabled, default_subtitle_language, default_audio_language, blocked_genres, updated_at, profile_id, stats_reset_at)
     VALUES ($1, $2::jsonb, $3::jsonb, $4, $5, $6, $7, $12::text, COALESCE($9::jsonb, '[]'::jsonb), $8, $10, $14::timestamptz)
     ON CONFLICT (user_id, profile_id) DO UPDATE SET
       home_row_order = EXCLUDED.home_row_order,
       hidden_row_ids = EXCLUDED.hidden_row_ids,
       autoplay_next_episode = EXCLUDED.autoplay_next_episode,
       skip_intro_enabled = EXCLUDED.skip_intro_enabled,
       subtitles_enabled = EXCLUDED.subtitles_enabled,
       default_subtitle_language = EXCLUDED.default_subtitle_language,
       -- A client that didn't send it (an older build) keeps what is stored; null clears it.
       default_audio_language = CASE WHEN $11::boolean THEN EXCLUDED.default_audio_language ELSE user_settings.default_audio_language END,
       -- A client that didn't send the list (an older build) keeps what is stored.
       blocked_genres = COALESCE($9::jsonb, user_settings.blocked_genres),
       -- A client that didn't send it (an older build) keeps what is stored; null clears the reset.
       -- A reset can only move forward: a later time replaces it, but an earlier one, a null or an older client that didn't send it leaves
       -- it alone, so the old numbers can't be brought back by another device or a stale copy of the settings.
       stats_reset_at = CASE WHEN $13::boolean AND EXCLUDED.stats_reset_at IS NOT NULL
                             THEN GREATEST(COALESCE(user_settings.stats_reset_at, EXCLUDED.stats_reset_at), EXCLUDED.stats_reset_at)
                             ELSE user_settings.stats_reset_at END,
       updated_at = EXCLUDED.updated_at
     WHERE EXCLUDED.updated_at > user_settings.updated_at
     RETURNING home_row_order, hidden_row_ids, autoplay_next_episode, skip_intro_enabled, subtitles_enabled, default_subtitle_language, default_audio_language, blocked_genres, stats_reset_at, updated_at`,
    [
      userId,
      JSON.stringify(homeRowOrder),
      JSON.stringify(input.hiddenRowIds),
      input.autoplayNextEpisode,
      input.skipIntroEnabled,
      input.subtitlesEnabled,
      input.defaultSubtitleLanguage,
      new Date(input.updatedAt),
      input.blockedGenres === undefined ? null : JSON.stringify(input.blockedGenres),
      profileId,
      input.defaultAudioLanguage !== undefined,
      input.defaultAudioLanguage ?? null,
      input.statsResetAt !== undefined,
      input.statsResetAt ? new Date(input.statsResetAt) : null,
    ]
  );

  if (result.rows.length > 0) return mapRow(result.rows[0]!);
  // The incoming write lost the race (not newer than what's already
  // stored) -- the INSERT/DO UPDATE above is a no-op in that case, so
  // there's nothing to map; fetch and return the still-current row.
  return getUserSettings(userId, profileId);
}
