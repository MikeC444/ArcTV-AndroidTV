import type { NextFunction, Request, Response } from "express";
import { pool } from "../db/pool.js";
import { HttpError } from "../lib/httpError.js";
import { hashToken } from "../security/tokens.js";

const BEARER_PREFIX = "Bearer ";

interface SessionRow {
  session_id: string;
  device_id: string;
  user_id: string;
  email: string;
  display_name: string | null;
  is_admin: boolean;
}

/**
 * Verifies the Authorization: Bearer <token> header against the sessions
 * table and attaches req.user/req.session on success. Every failure path
 * — missing header, malformed header, unknown token, expired token,
 * revoked session, a soft-deleted owning user, or a remotely-revoked
 * device — returns the same generic 401 rather than a reason-specific
 * message, so a request can't be used to probe which of those states a
 * given token is in.
 *
 * This is the only source of "who is making this request" anywhere in
 * the API: route handlers must read req.user, never a client-supplied id
 * from a param/query/body, which is what makes "user A can't access user
 * B's data" true by construction rather than by remembering to check it
 * in every handler.
 */
export async function requireAuth(req: Request, _res: Response, next: NextFunction): Promise<void> {
  try {
    const header = req.header("authorization");
    const token = header?.startsWith(BEARER_PREFIX) ? header.slice(BEARER_PREFIX.length).trim() : undefined;
    if (!token) {
      next(new HttpError(401, "Unauthorized"));
      return;
    }

    const tokenHash = hashToken(token);
    const result = await pool.query<SessionRow>(
      `SELECT s.id AS session_id, s.device_id, u.id AS user_id, u.email, u.display_name, u.is_admin
       FROM sessions s
       JOIN users u ON u.id = s.user_id
       JOIN devices d ON d.id = s.device_id
       WHERE s.access_token_hash = $1
         AND s.revoked_at IS NULL
         AND s.access_token_expires_at > now()
         AND u.deleted_at IS NULL
         AND d.revoked_at IS NULL`,
      [tokenHash]
    );

    const row = result.rows[0];
    if (!row) {
      next(new HttpError(401, "Unauthorized"));
      return;
    }

    req.user = { id: row.user_id, email: row.email, displayName: row.display_name, isAdmin: row.is_admin };
    req.session = { id: row.session_id, deviceId: row.device_id };

    // Best-effort activity timestamp — never let it slow down or fail the
    // actual request it's piggybacking on.
    void pool
      .query("UPDATE sessions SET last_used_at = now() WHERE id = $1", [row.session_id])
      .catch((error: unknown) => console.error("failed to update session last_used_at", error));

    // Which app version this device is on, and when it was last seen: written only when the version changed or the last write is
    // a few minutes old, so an update shows up on the very next request without a write on every one.
    const appVersion = cleanAppVersion(req.header("x-arctv-app-version"));
    void pool
      .query(
        `UPDATE devices SET app_version = COALESCE($2, app_version), last_seen_at = now(), updated_at = now()
         WHERE id = $1 AND (app_version IS DISTINCT FROM COALESCE($2, app_version) OR last_seen_at < now() - interval '5 minutes')`,
        [row.device_id, appVersion]
      )
      .catch((error: unknown) => console.error("failed to update device app version", error));

    next();
  } catch (error) {
    next(error);
  }
}

/** The app's own version string ("0.1.7"), or null when absent or odd-looking. */
export function cleanAppVersion(value: string | undefined): string | null {
  return value && /^[0-9A-Za-z][0-9A-Za-z.+_-]{0,31}$/.test(value.trim()) ? value.trim() : null;
}
