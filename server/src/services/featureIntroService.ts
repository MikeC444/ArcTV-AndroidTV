import { pool } from "../db/pool.js";
import type { FeatureIntroAckInput } from "../schemas/featureIntros.js";

/** Records that a user clicked a one-off pop-up away. The first click is kept; reporting again changes nothing. */
export async function acknowledge(userId: string, deviceId: string, appVersion: string | null, input: FeatureIntroAckInput): Promise<void> {
  await pool.query(
    `INSERT INTO feature_intro_acks (user_id, feature, device_id, app_version) VALUES ($1, $2, $3, $4)
     ON CONFLICT (user_id, feature) DO NOTHING`,
    [userId, input.feature, deviceId, appVersion]
  );
}
