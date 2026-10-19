import { createHash } from "node:crypto";
import { pool } from "../db/pool.js";
import type { CrashReportInput } from "../schemas/crashReports.js";

const KEEP_DAYS = 90;

/**
 * Which crash this is, so repeats group together: its kind, the error's class (not its message, which often holds ids or numbers) and the
 * first line of the trace inside the app's own code (or else the first line of the trace). Short, for grouping only.
 */
export function crashSignature(report: Pick<CrashReportInput, "kind" | "exception" | "stackTrace">): string {
  const errorClass = (report.exception ?? "").split(":")[0]!.trim();
  const lines = report.stackTrace.split("\n").map((l) => l.trim());
  const frame = lines.find((l) => l.startsWith("at com.mangotv")) ?? lines.find((l) => l.startsWith("at ")) ?? lines[0] ?? "";
  return createHash("sha256").update(`${report.kind}|${errorClass}|${frame}`).digest("hex").slice(0, 16);
}

/** Saves one report; the developer panel lists them (nothing is emailed). */
export async function saveCrashReport(userId: string | null, report: CrashReportInput): Promise<void> {
  if (Math.random() < 0.01) await pool.query(`DELETE FROM crash_reports WHERE created_at < now() - interval '${KEEP_DAYS} days'`);
  const signature = crashSignature(report);
  await pool.query(
    `INSERT INTO crash_reports (user_id, platform, app_version, device_model, os_version, kind, exception, stack_trace, signature, happened_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
    [userId, report.platform, report.appVersion, report.deviceModel ?? null, report.osVersion ?? null, report.kind, report.exception ?? null, report.stackTrace, signature, new Date(report.happenedAt)]
  );
}

export interface CrashGroup {
  signature: string;
  kind: string;
  exception: string | null;
  count: number;
  people: number;
  firstAt: Date;
  lastAt: Date;
  versions: string[];
  devices: string[];
}

/** The developer panel's Crashes view: the last 30 days grouped by crash (most recent first), and the latest reports with their traces. */
export async function crashesDetail(limit: number) {
  const [groups, recent] = await Promise.all([
    pool.query<CrashGroup>(
      `SELECT signature, max(kind) AS kind, (array_agg(exception ORDER BY created_at DESC))[1] AS exception, count(*)::int AS count,
              count(DISTINCT user_id)::int AS people, min(created_at) AS "firstAt", max(created_at) AS "lastAt",
              (array_agg(DISTINCT platform || ' ' || app_version))[1:6] AS versions,
              (array_agg(DISTINCT device_model) FILTER (WHERE device_model IS NOT NULL))[1:6] AS devices
       FROM crash_reports WHERE created_at > now() - interval '30 days'
       GROUP BY signature ORDER BY max(created_at) DESC LIMIT 100`
    ),
    pool.query(
      `SELECT c.id, c.signature, c.kind, c.exception, c.stack_trace AS "stackTrace", c.platform, c.app_version AS "appVersion", c.device_model AS "deviceModel",
              c.os_version AS "osVersion", c.happened_at AS "happenedAt", c.created_at AS "createdAt", u.id AS "userId", u.email
       FROM crash_reports c LEFT JOIN users u ON u.id = c.user_id
       ORDER BY c.created_at DESC LIMIT $1`,
      [limit]
    ),
  ]);
  return { groups: groups.rows, recent: recent.rows };
}
