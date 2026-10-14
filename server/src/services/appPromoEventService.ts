import { pool } from "../db/pool.js";
import type { AppPromoEventInput } from "../schemas/appPromoEvents.js";

/** Rows older than this are of no use to the developer panel (it looks back a week) and are dropped. */
const KEEP_DAYS = 90;

export async function recordAppPromoEvent(userId: string, input: AppPromoEventInput): Promise<void> {
  await pool.query(`INSERT INTO app_promo_events (user_id, app, kind) VALUES ($1, $2, $3)`, [userId, input.app, input.kind]);
  // Housekeeping now and then instead of a scheduled job: one report in a hundred also clears out the old rows.
  if (Math.random() < 0.01) await pool.query(`DELETE FROM app_promo_events WHERE created_at < now() - interval '${KEEP_DAYS} days'`);
}

export interface AppPromoTotals {
  app: "mac" | "windows";
  /** People the popup appeared for in the last 7 days. */
  shownPeople: number;
  /** People who pressed Get the app. */
  downloadPeople: number;
  /** People who closed it without pressing it. */
  closedPeople: number;
  shown: number;
  downloads: number;
  closes: number;
}

/**
 * The developer panel's "Desktop app popups": the last 7 days, per app. A person who both pressed the button and (in another visit) closed it
 * counts in both "pressed" and "closed" people; `shownPeople` is everyone the popup reached.
 */
export async function appPromosSummary(): Promise<AppPromoTotals[]> {
  const rows = await pool.query<AppPromoTotals>(
    `SELECT a.app,
            count(DISTINCT e.user_id) FILTER (WHERE e.kind = 'shown')::int AS "shownPeople",
            count(DISTINCT e.user_id) FILTER (WHERE e.kind = 'download')::int AS "downloadPeople",
            count(DISTINCT e.user_id) FILTER (WHERE e.kind = 'closed')::int AS "closedPeople",
            count(*) FILTER (WHERE e.kind = 'shown')::int AS shown,
            count(*) FILTER (WHERE e.kind = 'download')::int AS downloads,
            count(*) FILTER (WHERE e.kind = 'closed')::int AS closes
     FROM (VALUES ('mac'), ('windows')) AS a(app)
     LEFT JOIN app_promo_events e ON e.app = a.app AND e.created_at > now() - interval '7 days'
     GROUP BY a.app ORDER BY a.app DESC`
  );
  return rows.rows;
}

export interface AppPromoDay {
  date: string;
  mac: { shown: number; downloads: number };
  windows: { shown: number; downloads: number };
}
export interface AppPromoPerson {
  userId: string;
  email: string;
  displayName: string | null;
  app: "mac" | "windows";
  /** What they did with it, latest first: "download" wins over "closed" wins over "shown". */
  outcome: "download" | "closed" | "shown";
  at: string;
}
export interface AppPromoRecent {
  app: "mac" | "windows";
  kind: "shown" | "download" | "closed";
  at: string;
  userId: string;
  email: string;
  displayName: string | null;
}

/** The panel's view in depth, for the last 7 days: each UTC day, who was offered which app and what they did, and the latest events. */
export async function appPromosDetail() {
  const [days, people, recent] = await Promise.all([
    pool.query<{ date: string; app: "mac" | "windows"; shown: number; downloads: number }>(
      `SELECT to_char(d::date, 'YYYY-MM-DD') AS date, a.app,
              count(e.id) FILTER (WHERE e.kind = 'shown')::int AS shown,
              count(e.id) FILTER (WHERE e.kind = 'download')::int AS downloads
       FROM generate_series((now() AT TIME ZONE 'utc')::date - 6, (now() AT TIME ZONE 'utc')::date, interval '1 day') d
       CROSS JOIN (VALUES ('mac'), ('windows')) AS a(app)
       LEFT JOIN app_promo_events e ON e.app = a.app AND (e.created_at AT TIME ZONE 'utc')::date = d::date
       GROUP BY d, a.app ORDER BY d, a.app`
    ),
    pool.query<AppPromoPerson>(
      `SELECT u.id AS "userId", u.email, u.display_name AS "displayName", e.app,
              CASE WHEN bool_or(e.kind = 'download') THEN 'download' WHEN bool_or(e.kind = 'closed') THEN 'closed' ELSE 'shown' END AS outcome,
              max(e.created_at) AS at
       FROM app_promo_events e JOIN users u ON u.id = e.user_id
       WHERE e.created_at > now() - interval '7 days' AND u.deleted_at IS NULL
       GROUP BY u.id, u.email, u.display_name, e.app
       ORDER BY max(e.created_at) DESC LIMIT 100`
    ),
    pool.query<AppPromoRecent>(
      `SELECT e.app, e.kind, e.created_at AS at, u.id AS "userId", u.email, u.display_name AS "displayName"
       FROM app_promo_events e JOIN users u ON u.id = e.user_id
       WHERE e.created_at > now() - interval '7 days' AND u.deleted_at IS NULL
       ORDER BY e.created_at DESC LIMIT 60`
    ),
  ]);
  const byDate = new Map<string, AppPromoDay>();
  for (const r of days.rows) {
    const day = byDate.get(r.date) ?? { date: r.date, mac: { shown: 0, downloads: 0 }, windows: { shown: 0, downloads: 0 } };
    day[r.app] = { shown: r.shown, downloads: r.downloads };
    byDate.set(r.date, day);
  }
  return { days: [...byDate.values()], people: people.rows, recent: recent.rows };
}
