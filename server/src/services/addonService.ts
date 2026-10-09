import { pool } from "../db/pool.js";
import { CINEMETA_ADDON_ID, CINEMETA_MANIFEST, CINEMETA_MANIFEST_URL } from "../data/cinemetaManifest.js";
import type { AddonDeleteInput, AddonInput } from "../schemas/addons.js";

export interface UserAddon {
  manifestUrl: string;
  addonId: string;
  name: string;
  manifestJson: Record<string, unknown>;
  enabled: boolean;
  sortOrder: number;
  updatedAt: Date;
  /** Non-null means this addon is currently removed -- only meaningful on POST/DELETE responses, same contract as watchlist's deletedAt. listActiveAddons never returns a row with this set. */
  deletedAt: Date | null;
}

interface AddonRow {
  manifest_url: string;
  addon_id: string;
  name: string;
  manifest_json: Record<string, unknown>;
  enabled: boolean;
  sort_order: number;
  updated_at: Date;
  deleted_at: Date | null;
}

const ROW_COLUMNS = `manifest_url, addon_id, name, manifest_json, enabled, sort_order, updated_at, deleted_at`;

function mapRow(row: AddonRow): UserAddon {
  return {
    manifestUrl: row.manifest_url,
    addonId: row.addon_id,
    name: row.name,
    manifestJson: row.manifest_json,
    enabled: row.enabled,
    sortOrder: row.sort_order,
    updatedAt: row.updated_at,
    deletedAt: row.deleted_at,
  };
}

/** Does this addon give Home something to show? Its manifest lists at least one catalogue (Cinemeta, TMDB and the like do; Torrentio does not). */
function offersCatalogues(addon: UserAddon): boolean {
  const catalogs = addon.manifestJson.catalogs;
  return addon.enabled && Array.isArray(catalogs) && catalogs.length > 0;
}

/**
 * Makes sure a profile has something to browse, whatever app version asks. The apps used to install Cinemeta only once per device, locally, so an
 * account that first signed in without it (a guest's untouched default is never uploaded; a device that had already run the app) had an empty Home
 * and old versions can never be fixed from the client. When a profile's active addons offer no catalogue (none at all, or only stream addons such as
 * Torrentio) and it has not been looked at before, Cinemeta is added to it. This happens once per profile: the mark is written either way, so
 * removing Cinemeta later (or having another catalogue addon from the start) is respected. It applies to a brand-new account at its very first sync too,
 * so nobody starts with an empty Home. Returns the list to send (re-read when Cinemeta was added).
 */
export async function seedDefaultAddon(userId: string, profileId: string, addons: UserAddon[]): Promise<UserAddon[]> {
  const marked = await pool.query(`SELECT 1 FROM addon_seed_marks WHERE user_id = $1 AND profile_id = $2`, [userId, profileId]);
  if (marked.rows.length > 0) return addons;

  if (addons.some(offersCatalogues)) {
    await pool.query(`INSERT INTO addon_seed_marks (user_id, profile_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`, [userId, profileId]);
    return addons;
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    // The first request to write the mark is the one that adds Cinemeta; a concurrent one finds the mark and does nothing.
    const claimed = await client.query(`INSERT INTO addon_seed_marks (user_id, profile_id) VALUES ($1, $2) ON CONFLICT DO NOTHING RETURNING 1`, [userId, profileId]);
    if (claimed.rows.length > 0) {
      const next = addons.reduce((max, addon) => Math.max(max, addon.sortOrder), -1) + 1;
      await client.query(
        `INSERT INTO user_addons (user_id, manifest_url, addon_id, name, manifest_json, enabled, sort_order, updated_at, deleted_at, profile_id)
         VALUES ($1, $2, $3, 'Cinemeta', $4::jsonb, true, $5, now(), NULL, $6)
         ON CONFLICT (user_id, profile_id, manifest_url) DO UPDATE SET
           enabled = true, deleted_at = NULL, sort_order = EXCLUDED.sort_order, updated_at = now()`,
        [userId, CINEMETA_MANIFEST_URL, CINEMETA_ADDON_ID, JSON.stringify(CINEMETA_MANIFEST), next, profileId]
      );
    }
    await client.query("COMMIT");
    if (claimed.rows.length === 0) return addons;
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
  return readActiveAddons(userId, profileId);
}

async function readActiveAddons(userId: string, profileId: string): Promise<UserAddon[]> {
  const result = await pool.query<AddonRow>(
    `SELECT ${ROW_COLUMNS} FROM user_addons
     WHERE user_id = $1 AND profile_id = $2 AND deleted_at IS NULL
     ORDER BY sort_order ASC`,
    [userId, profileId]
  );
  return result.rows.map(mapRow);
}

/** This account's currently-installed addons, in display order -- what a fresh sign-in or app launch pulls down to seed/replace ProviderRegistry. Never includes soft-deleted rows. A profile with nothing to browse is given Cinemeta first (see [seedDefaultAddon]). */
export async function listActiveAddons(userId: string, profileId: string): Promise<UserAddon[]> {
  return seedDefaultAddon(userId, profileId, await readActiveAddons(userId, profileId));
}

/**
 * Installs (or un-removes/updates) one addon, keyed on the same
 * (user_id, manifest_url) natural key the table's unique constraint
 * enforces. Last-write-wins on the client's own updatedAt, in one atomic
 * statement -- the same shape as watchlistService.upsertWatchlistItem,
 * applied to a single-column natural key instead of a three-column one.
 * installed_at is deliberately left untouched by the DO UPDATE (only set
 * by the initial INSERT's DEFAULT now()) -- removing and re-adding the
 * same addon is a metadata update to one durable slot, not a fresh
 * install.
 */
export async function upsertAddon(userId: string, profileId: string, input: AddonInput): Promise<UserAddon> {
  const result = await pool.query<AddonRow>(
    `INSERT INTO user_addons (user_id, manifest_url, addon_id, name, manifest_json, enabled, sort_order, updated_at, deleted_at, profile_id)
     VALUES ($1, $2, $3, $4, $5::jsonb, $6, $7, $8, NULL, $9)
     ON CONFLICT (user_id, profile_id, manifest_url) DO UPDATE SET
       addon_id = EXCLUDED.addon_id,
       name = EXCLUDED.name,
       manifest_json = EXCLUDED.manifest_json,
       enabled = EXCLUDED.enabled,
       sort_order = EXCLUDED.sort_order,
       updated_at = EXCLUDED.updated_at,
       deleted_at = NULL
     WHERE EXCLUDED.updated_at > user_addons.updated_at
     RETURNING ${ROW_COLUMNS}`,
    [
      userId,
      input.manifestUrl,
      input.addonId,
      input.name,
      JSON.stringify(input.manifestJson),
      input.enabled,
      input.sortOrder,
      new Date(input.updatedAt),
      profileId,
    ]
  );

  if (result.rows.length > 0) return mapRow(result.rows[0]!);
  // Lost the race (not newer than what's already stored) -- fetch and
  // return the still-current row instead.
  return getAddon(userId, profileId, input.manifestUrl) as Promise<UserAddon>;
}

/**
 * Soft-deletes one addon via the same atomic, LWW-gated shape as
 * upsertAddon. Returns null only when the addon never existed for this
 * user at all (e.g. a pre-Milestone-9 local-only install this device
 * never pushed) -- nothing server-side to reconcile in that case. When a
 * row does exist, always returns its current state, whether this call
 * actually removed it or lost the race to a newer write elsewhere.
 */
export async function removeAddon(userId: string, profileId: string, input: AddonDeleteInput): Promise<UserAddon | null> {
  const result = await pool.query<AddonRow>(
    `UPDATE user_addons
     SET deleted_at = $3, updated_at = $3
     WHERE user_id = $1 AND manifest_url = $2
       AND updated_at < $3 AND profile_id = $4
     RETURNING ${ROW_COLUMNS}`,
    [userId, input.manifestUrl, new Date(input.updatedAt), profileId]
  );

  if (result.rows.length > 0) return mapRow(result.rows[0]!);
  return getAddon(userId, profileId, input.manifestUrl);
}

async function getAddon(userId: string, profileId: string, manifestUrl: string): Promise<UserAddon | null> {
  const result = await pool.query<AddonRow>(
    `SELECT ${ROW_COLUMNS} FROM user_addons WHERE user_id = $1 AND manifest_url = $2 AND profile_id = $3`,
    [userId, manifestUrl, profileId]
  );
  return result.rows[0] ? mapRow(result.rows[0]) : null;
}
