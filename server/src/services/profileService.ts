import { randomBytes } from "node:crypto";
import { pool } from "../db/pool.js";
import { HttpError } from "../lib/httpError.js";
import { hashPassword, verifyPassword } from "../security/password.js";
import { PROFILE_LIMIT, type ProfileCreateInput, type ProfileUpdateInput } from "../schemas/profiles.js";
import { getEntitlement } from "./plusService.js";

export const DEFAULT_PROFILE_ID = "main";

export interface Profile {
  id: string;
  name: string;
  avatar: string;
  kind: "adult" | "kids";
  /** The PIN itself is never returned: this only says one is needed to open, change or remove the profile. */
  hasPin: boolean;
  isDefault: boolean;
}

interface ProfileRow {
  id: string;
  name: string;
  avatar: string;
  kind: "adult" | "kids";
  has_pin: boolean;
  is_default: boolean;
}

const COLUMNS = `id, name, avatar, kind, (pin_hash IS NOT NULL) AS has_pin, is_default`;

const mapRow = (row: ProfileRow): Profile => ({ id: row.id, name: row.name, avatar: row.avatar, kind: row.kind, hasPin: row.has_pin, isDefault: row.is_default });

/** Five wrong PINs in a row lock guessing for this long (4 digits is only 10,000 possibilities). */
export const PIN_MAX_FAILURES = 5;
export const PIN_LOCK_SECONDS = 5 * 60;

/** Every library table that carries a profile_id: a removed profile takes its library with it. */
const LIBRARY_TABLES = ["user_settings", "user_addons", "watchlist_items", "watch_history", "continue_watching", "movie_feedback"] as const;

/** Profiles beyond the account's own are part of ArcTV Plus. */
export async function requirePlus(userId: string): Promise<void> {
  if (!(await getEntitlement(userId)).active) throw new HttpError(403, "Profiles are part of ArcTV Plus");
}

/** The account's own profile, created on first use (named after the account, or "Me"). */
async function ensureDefaultProfile(userId: string, displayName: string | null, client: Pick<typeof pool, "query"> = pool): Promise<void> {
  const name = (displayName ?? "").trim().slice(0, 24) || "Me";
  await client.query(
    `INSERT INTO profiles (user_id, id, name, avatar, kind, is_default) VALUES ($1, $2, $3, 'sunrise', 'adult', true)
     ON CONFLICT (user_id, id) DO NOTHING`,
    [userId, DEFAULT_PROFILE_ID, name]
  );
}

/** The account's profiles, its own first, then in the order they were made. */
export async function listProfiles(userId: string, displayName: string | null): Promise<Profile[]> {
  await ensureDefaultProfile(userId, displayName);
  const result = await pool.query<ProfileRow>(`SELECT ${COLUMNS} FROM profiles WHERE user_id = $1 ORDER BY is_default DESC, created_at ASC, id ASC`, [userId]);
  return result.rows.map(mapRow);
}

async function getProfile(userId: string, id: string): Promise<Profile | null> {
  const result = await pool.query<ProfileRow>(`SELECT ${COLUMNS} FROM profiles WHERE user_id = $1 AND id = $2`, [userId, id]);
  return result.rows[0] ? mapRow(result.rows[0]) : null;
}

/** Does this id name one of the account's profiles? 'main' always does, without a lookup (and without the profile having a row yet). */
export async function profileExists(userId: string, id: string): Promise<boolean> {
  if (id === DEFAULT_PROFILE_ID) return true;
  const result = await pool.query("SELECT 1 FROM profiles WHERE user_id = $1 AND id = $2", [userId, id]);
  return result.rowCount === 1;
}

export async function createProfile(userId: string, displayName: string | null, input: ProfileCreateInput): Promise<Profile> {
  await requirePlus(userId);
  const pinHash = input.pin ? await hashPassword(input.pin) : null;
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    // Serialises concurrent creates for one account, so two at once can't both slip under the limit.
    await client.query("SELECT id FROM users WHERE id = $1 FOR UPDATE", [userId]);
    await ensureDefaultProfile(userId, displayName, client);
    const count = await client.query<{ n: string }>("SELECT count(*)::text AS n FROM profiles WHERE user_id = $1", [userId]);
    if (Number(count.rows[0]!.n) >= PROFILE_LIMIT) throw new HttpError(400, `An account can have up to ${PROFILE_LIMIT} profiles`);
    const result = await client.query<ProfileRow>(
      `INSERT INTO profiles (user_id, id, name, avatar, kind, pin_hash) VALUES ($1, $2, $3, $4, $5, $6) RETURNING ${COLUMNS}`,
      [userId, `p_${randomBytes(5).toString("hex")}`, input.name, input.avatar, input.kind, pinHash]
    );
    await client.query("COMMIT");
    return mapRow(result.rows[0]!);
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}

export async function updateProfile(userId: string, displayName: string | null, id: string, input: ProfileUpdateInput): Promise<Profile> {
  await requirePlus(userId);
  if (id === DEFAULT_PROFILE_ID) await ensureDefaultProfile(userId, displayName);
  if (id === DEFAULT_PROFILE_ID && input.kind === "kids") throw new HttpError(400, "The account's own profile can't be a kids profile");
  // pin: undefined leaves it, null removes it, a string sets it. Changing it clears any lockout.
  const setPin = input.pin !== undefined;
  const pinHash = typeof input.pin === "string" ? await hashPassword(input.pin) : null;
  const result = await pool.query<ProfileRow>(
    `UPDATE profiles SET
       name = COALESCE($3, name),
       avatar = COALESCE($4, avatar),
       kind = COALESCE($5, kind),
       pin_hash = CASE WHEN $6 THEN $7 ELSE pin_hash END,
       pin_failed_attempts = CASE WHEN $6 THEN 0 ELSE pin_failed_attempts END,
       pin_locked_until = CASE WHEN $6 THEN NULL ELSE pin_locked_until END,
       updated_at = now()
     WHERE user_id = $1 AND id = $2
     RETURNING ${COLUMNS}`,
    [userId, id, input.name ?? null, input.avatar ?? null, input.kind ?? null, setPin, pinHash]
  );
  if (!result.rows[0]) throw new HttpError(404, "Profile not found");
  return mapRow(result.rows[0]);
}

/** Removes a profile and everything in its library. The account's own profile can't be removed. */
export async function deleteProfile(userId: string, id: string): Promise<void> {
  await requirePlus(userId);
  if (id === DEFAULT_PROFILE_ID) throw new HttpError(400, "The account's own profile can't be removed");
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const removed = await client.query("DELETE FROM profiles WHERE user_id = $1 AND id = $2", [userId, id]);
    if (removed.rowCount !== 1) throw new HttpError(404, "Profile not found");
    for (const table of LIBRARY_TABLES) await client.query(`DELETE FROM ${table} WHERE user_id = $1 AND profile_id = $2`, [userId, id]);
    await client.query("COMMIT");
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}

/**
 * Checks a profile's PIN. A profile without one has nothing to check (passes). Wrong PINs are counted: after
 * PIN_MAX_FAILURES in a row guessing is refused (429 + Retry-After) until the lock runs out, however many addresses the
 * guesses come from. The counter and the check happen under one row lock, so parallel guesses can't each get a free try.
 */
export async function verifyPin(userId: string, id: string, pin: string): Promise<void> {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const found = await client.query<{ pin_hash: string | null; pin_failed_attempts: number; locked_for: string | null }>(
      `SELECT pin_hash, pin_failed_attempts, GREATEST(0, CEIL(EXTRACT(EPOCH FROM (pin_locked_until - now()))))::text AS locked_for
       FROM profiles WHERE user_id = $1 AND id = $2 FOR UPDATE`,
      [userId, id]
    );
    const row = found.rows[0];
    if (!row) throw new HttpError(404, "Profile not found");
    if (!row.pin_hash) {
      await client.query("COMMIT");
      return;
    }
    const lockedFor = Number(row.locked_for ?? 0);
    if (lockedFor > 0) throw new HttpError(429, "Too many wrong PINs. Try again later", lockedFor);

    if (await verifyPassword(row.pin_hash, pin)) {
      await client.query("UPDATE profiles SET pin_failed_attempts = 0, pin_locked_until = NULL WHERE user_id = $1 AND id = $2", [userId, id]);
      await client.query("COMMIT");
      return;
    }
    const failures = row.pin_failed_attempts + 1;
    const lock = failures >= PIN_MAX_FAILURES;
    await client.query(
      `UPDATE profiles SET pin_failed_attempts = $3, pin_locked_until = CASE WHEN $4 THEN now() + make_interval(secs => $5) ELSE pin_locked_until END WHERE user_id = $1 AND id = $2`,
      [userId, id, lock ? 0 : failures, lock, PIN_LOCK_SECONDS]
    );
    await client.query("COMMIT"); // the failed try must be kept, so this is a commit and then the refusal
    throw new HttpError(403, "Wrong PIN");
  } catch (error) {
    await client.query("ROLLBACK").catch(() => undefined);
    throw error;
  } finally {
    client.release();
  }
}

export { getProfile };
