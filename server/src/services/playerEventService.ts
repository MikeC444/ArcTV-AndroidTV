import { pool } from "../db/pool.js";
import type { ExternalPlayerEventInput } from "../schemas/playerEvents.js";

export async function recordExternalPlayerEvent(userId: string, deviceId: string, appVersion: string | null, input: ExternalPlayerEventInput): Promise<void> {
  await pool.query(
    `INSERT INTO external_player_events
       (user_id, device_id, app_version, provider_id, content_id, content_type, title, release_title, resolution, codec, launched_from, outcome, error_message)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)`,
    [
      userId, deviceId, appVersion, input.providerId, input.contentId, input.contentType, input.title,
      input.releaseTitle ?? null, input.resolution ?? null, input.codec ?? null, input.trigger, input.outcome, input.errorMessage ?? null,
    ]
  );
}
