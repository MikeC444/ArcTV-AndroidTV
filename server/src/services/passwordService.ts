import { pool } from "../db/pool.js";
import { HttpError } from "../lib/httpError.js";
import { hashPassword, verifyPassword } from "../security/password.js";
import { generateToken, hashToken } from "../security/tokens.js";
import { emailConfigured, sendEmail } from "./email.js";

/** How long an emailed reset link works. */
export const RESET_TTL_MS = 60 * 60 * 1000;
/** At most this many reset emails per account per hour, and never two within RESET_MIN_GAP_MS: nobody can flood an inbox or use up the daily email allowance. */
export const RESET_MAX_PER_HOUR = 3;
export const RESET_MIN_GAP_MS = 60 * 1000;

/**
 * The reset email as HTML: the logo on a dark bar (it is white), a heading, one button and a footer. Tables and inline styles only, which
 * is what email apps reliably show; the logo is the landing site's own, and an app that hides images shows "Arc TV" in its place.
 */
export function resetEmailHtml(link: string): string {
  return `<!doctype html>
<html><body style="margin:0;padding:0;background:#f2f3f5;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f2f3f5;padding:32px 12px;">
<tr><td align="center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:16px;overflow:hidden;font-family:Helvetica,Arial,sans-serif;">
<tr><td style="background:#0b0d10;padding:28px 32px;" align="left"><img src="https://arctv.org/arctv-logo.png" width="132" alt="Arc TV" style="display:block;border:0;width:132px;height:auto;color:#ffffff;font-size:20px;font-weight:bold;"></td></tr>
<tr><td style="padding:32px 32px 8px;color:#111418;">
<h1 style="margin:0 0 12px;font-size:22px;line-height:1.3;">Reset your password</h1>
<p style="margin:0 0 24px;font-size:15px;line-height:1.6;color:#3d4450;">Someone asked to reset the password for your Arc TV account. Press the button to choose a new one.</p>
<table role="presentation" cellpadding="0" cellspacing="0"><tr><td style="border-radius:10px;background:#0a9db0;"><a href="${link}" style="display:inline-block;padding:14px 28px;font-size:15px;font-weight:bold;color:#ffffff;text-decoration:none;border-radius:10px;">Choose a new password</a></td></tr></table>
<p style="margin:24px 0 0;font-size:13px;line-height:1.6;color:#6b7280;">The link works once and expires in 1 hour. If you didn&rsquo;t ask for this, you can ignore this email: your password hasn&rsquo;t changed.</p>
</td></tr>
<tr><td style="padding:24px 32px 28px;font-size:12px;line-height:1.6;color:#9aa1ab;border-top:1px solid #eceef1;">Arc TV &middot; <a href="https://arctv.org/privacy/" style="color:#9aa1ab;">Privacy Policy</a></td></tr>
</table>
</td></tr></table>
</body></html>`;
}

/** Where the emailed link points: the web app's reset page, with the token in the #fragment so it never reaches a server log. */
export function resetLink(token: string): string {
  const base = (process.env.PASSWORD_RESET_URL?.trim() || "https://web.arctv.org/reset-password").replace(/\/+$/, "");
  return `${base}#token=${encodeURIComponent(token)}`;
}

/**
 * Changes a signed-in account's password after checking the current one. Every OTHER session is signed out (the device making the change
 * stays signed in), and any reset link still waiting is cancelled. A wrong current password is 403, not 401: a 401 would read as "this
 * session is over" to the apps and the web server.
 */
export async function changePassword(userId: string, sessionId: string, currentPassword: string, newPassword: string): Promise<void> {
  const found = await pool.query<{ password_hash: string }>("SELECT password_hash FROM users WHERE id = $1 AND deleted_at IS NULL", [userId]);
  const row = found.rows[0];
  if (!row || !(await verifyPassword(row.password_hash, currentPassword))) throw new HttpError(403, "Your current password isn't right");
  if (currentPassword === newPassword) throw new HttpError(400, "Choose a password different from your current one");
  const passwordHash = await hashPassword(newPassword);

  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query("UPDATE users SET password_hash = $2, updated_at = now() WHERE id = $1", [userId, passwordHash]);
    await client.query("UPDATE sessions SET revoked_at = now() WHERE user_id = $1 AND id <> $2 AND revoked_at IS NULL", [userId, sessionId]);
    await client.query("UPDATE password_reset_tokens SET used_at = now() WHERE user_id = $1 AND used_at IS NULL", [userId]);
    await client.query("COMMIT");
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}

/**
 * "Forgot password": emails a one-time link to the account with this address. Answers the same way whether or not there is one (the
 * route always says "if that email has an account..."), and the email is sent in the background so the answer takes as long either way.
 * 503 while email isn't set up on this server. Returns the sending (wrapped, so awaiting this never waits for it), for tests; null when no email goes out.
 */
export async function requestPasswordReset(email: string): Promise<{ sending: Promise<void> } | null> {
  if (!emailConfigured()) throw new HttpError(503, "Password reset isn't available yet");
  const found = await pool.query<{ id: string }>("SELECT id FROM users WHERE email = $1 AND deleted_at IS NULL", [email]);
  const user = found.rows[0];
  if (!user) return null;

  const recent = await pool.query<{ n: number; newest: Date | null }>(
    "SELECT count(*)::int AS n, max(created_at) AS newest FROM password_reset_tokens WHERE user_id = $1 AND created_at > now() - interval '1 hour'",
    [user.id]
  );
  const { n, newest } = recent.rows[0]!;
  if (n >= RESET_MAX_PER_HOUR || (newest && Date.now() - newest.getTime() < RESET_MIN_GAP_MS)) return null;

  const token = generateToken();
  // A new link replaces any earlier one still waiting.
  await pool.query("UPDATE password_reset_tokens SET used_at = now() WHERE user_id = $1 AND used_at IS NULL", [user.id]);
  await pool.query("INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES ($1, $2, $3)", [user.id, hashToken(token), new Date(Date.now() + RESET_TTL_MS)]);

  const link = resetLink(token);
  const sending = sendEmail({
    to: email,
    subject: "Reset your Arc TV password",
    text: `Someone asked to reset the password for your Arc TV account.\n\nChoose a new password here (the link works once and expires in 1 hour):\n${link}\n\nIf you didn't ask for this, you can ignore this email: your password hasn't changed.\n\nArc TV · Privacy Policy: https://arctv.org/privacy/`,
    html: resetEmailHtml(link),
  }).catch((error: unknown) => console.error(`password reset email failed: ${error instanceof Error ? error.message : "unknown error"}`));
  return { sending };
}

/** Sets a new password from an emailed link. The link works once; afterwards every session on the account is signed out. */
export async function resetPassword(token: string, newPassword: string): Promise<void> {
  const passwordHash = await hashPassword(newPassword);
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const claimed = await client.query<{ user_id: string }>(
      "UPDATE password_reset_tokens SET used_at = now() WHERE token_hash = $1 AND used_at IS NULL AND expires_at > now() RETURNING user_id",
      [hashToken(token)]
    );
    const userId = claimed.rows[0]?.user_id;
    const updated = userId
      ? await client.query("UPDATE users SET password_hash = $2, updated_at = now() WHERE id = $1 AND deleted_at IS NULL", [userId, passwordHash])
      : null;
    if (!userId || updated?.rowCount !== 1) throw new HttpError(400, "This reset link is invalid or has expired. Ask for a new one");
    await client.query("UPDATE sessions SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL", [userId]);
    await client.query("UPDATE password_reset_tokens SET used_at = now() WHERE user_id = $1 AND used_at IS NULL", [userId]);
    await client.query("COMMIT");
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}
