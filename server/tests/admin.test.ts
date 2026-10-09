import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { pool } from "../src/db/pool.js";
import { cleanAppVersion } from "../src/middleware/auth.js";
import { describeAddonUrl } from "../src/services/adminService.js";
import { createTestSession, type TestSession } from "./helpers/auth.js";
import { resetDatabase } from "./helpers/db.js";

const app = createApp();
const auth = (s: TestSession, version?: string) => ({ Authorization: `Bearer ${s.token}`, ...(version ? { "X-ArcTV-App-Version": version } : {}) });

beforeEach(async () => {
  await resetDatabase();
});

async function waitFor<T>(read: () => Promise<T>, done: (value: T) => boolean): Promise<T> {
  let value = await read();
  for (let i = 0; i < 40 && !done(value); i++) {
    await new Promise((resolve) => setTimeout(resolve, 25));
    value = await read();
  }
  return value;
}
const versionOf = async (s: TestSession) => (await pool.query<{ app_version: string | null }>("SELECT d.app_version FROM devices d JOIN sessions x ON x.device_id = d.id WHERE x.id = $1", [s.sessionId])).rows[0]!.app_version;

describe("the developer panel is for admins only", () => {
  it("is invisible to everyone else", async () => {
    const plain = await createTestSession();
    expect((await request(app).get("/admin/users")).status).toBe(401);
    for (const path of ["/admin/summary", "/admin/users", `/admin/users/${plain.userId}`]) {
      expect((await request(app).get(path).set(auth(plain))).status).toBe(404);
    }
  });

  it("tells the app whether the account is an admin", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const plain = await createTestSession();
    expect((await request(app).get("/user/me").set(auth(admin))).body.isAdmin).toBe(true);
    expect((await request(app).get("/user/me").set(auth(plain))).body.isAdmin).toBe(false);
  });

  it("lists users with their plan, devices and counts, and can search", async () => {
    const admin = await createTestSession({ isAdmin: true, email: "dev@example.com" });
    const sam = await createTestSession({ email: "sam@example.com", displayName: "Sam" });
    await pool.query("INSERT INTO user_plus (user_id, plan, status, valid_until) VALUES ($1, 'monthly', 'active', now() + interval '10 days')", [sam.userId]);
    const all = await request(app).get("/admin/users").set(auth(admin));
    expect(all.status).toBe(200);
    expect(all.body.total).toBe(2);
    const row = all.body.users.find((u: { email: string }) => u.email === "sam@example.com");
    expect(row).toMatchObject({ displayName: "Sam", plan: "monthly", addons: 0, continueWatching: 0 });
    expect(row.devices).toHaveLength(1);
    const found = await request(app).get("/admin/users?q=SAM").set(auth(admin));
    expect(found.body.users.map((u: { email: string }) => u.email)).toEqual(["sam@example.com"]);
    expect((await request(app).get("/admin/users?q=%25").set(auth(admin))).body.total).toBe(0); // a literal %, not a wildcard
  });

  it("filters by plan, app version, addons, Continue Watching and when they were last seen", async () => {
    const admin = await createTestSession({ isAdmin: true, email: "dev@example.com" });
    const a = await createTestSession({ email: "a@example.com" });
    const b = await createTestSession({ email: "b@example.com" });
    const c = await createTestSession({ email: "c@example.com" });
    await pool.query("INSERT INTO user_plus (user_id, plan, status, valid_until) VALUES ($1, 'monthly', 'active', now() + interval '5 days')", [a.userId]);
    await pool.query("INSERT INTO user_plus (user_id, plan, status) VALUES ($1, 'lifetime', 'active')", [b.userId]);
    await pool.query("INSERT INTO user_plus (user_id, plan, status, valid_until) VALUES ($1, 'yearly', 'active', now() - interval '1 day')", [c.userId]); // lapsed: counts as free
    await pool.query("UPDATE devices SET app_version = '0.1.7', platform = 'fire_tv', last_seen_at = now() - interval '2 hours' WHERE user_id = $1", [a.userId]);
    await pool.query("UPDATE devices SET app_version = '0.1.6', platform = 'fire_tv', last_seen_at = now() - interval '40 days' WHERE user_id = $1", [b.userId]);
    await pool.query("UPDATE devices SET last_seen_at = now() - interval '3 days' WHERE user_id = $1", [c.userId]);
    await pool.query("INSERT INTO user_addons (user_id, manifest_url, addon_id, name, manifest_json) VALUES ($1, 'https://x.example/manifest.json', 'x', 'X', '{}')", [a.userId]);
    await pool.query("INSERT INTO continue_watching (user_id, provider_id, content_id, content_type, title, position_ms, duration_ms) VALUES ($1, 'p', 't', 'MOVIE', 'T', 1000, 9000)", [b.userId]);
    const emails = async (query: string) => ((await request(app).get(`/admin/users?${query}`).set(auth(admin))).body.users as Array<{ email: string }>).map((u) => u.email).sort();
    expect(await emails("plan=monthly")).toEqual(["a@example.com"]);
    expect(await emails("plan=lifetime")).toEqual(["b@example.com"]);
    expect(await emails("plan=free")).toEqual(["c@example.com", "dev@example.com"]);
    expect(await emails("device=fire_tv%7C0.1.7")).toEqual(["a@example.com"]);
    expect(await emails("device=fire_tv%7C0.1.6")).toEqual(["b@example.com"]);
    expect(await emails("device=fire_tv%7Cunknown")).toEqual(["c@example.com", "dev@example.com"]); // devices that never reported a version
    expect(await emails("addons=with")).toEqual(["a@example.com"]);
    expect(await emails("addons=none")).toEqual(["b@example.com", "c@example.com", "dev@example.com"]);
    expect(await emails("watching=with")).toEqual(["b@example.com"]);
    expect(await emails("seen=24h")).toEqual(["a@example.com", "dev@example.com"]);
    expect(await emails("seen=7d")).toEqual(["a@example.com", "c@example.com", "dev@example.com"]);
    expect(await emails("seen=older")).toEqual(["b@example.com"]);
    expect(await emails("plan=free&seen=7d&addons=none")).toEqual(["c@example.com", "dev@example.com"]);
    const counted = await request(app).get("/admin/users?plan=free&limit=1").set(auth(admin));
    expect(counted.body.total).toBe(2); // the total follows the filter, not the page
    expect(counted.body.users).toHaveLength(1);
    expect((await request(app).get("/admin/users?plan=gold").set(auth(admin))).status).toBe(400);
    expect((await request(app).get("/admin/users?device=x").set(auth(admin))).status).toBe(400);
  });

  it("shows one user's addons (without their keys), Continue Watching and history", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const sam = await createTestSession({ email: "sam@example.com" });
    await pool.query(
      `INSERT INTO user_addons (user_id, manifest_url, addon_id, name, manifest_json) VALUES
        ($1, 'https://torrentio.strem.fun/realdebrid=SECRET-KEY-123/manifest.json', 'torrentio', 'Torrentio', '{}'),
        ($1, 'https://v3-cinemeta.strem.io/manifest.json', 'cinemeta', 'Cinemeta', '{}')`,
      [sam.userId]
    );
    await pool.query(
      `INSERT INTO continue_watching (user_id, provider_id, content_id, content_type, title, position_ms, duration_ms) VALUES ($1, 'p', 'tt1', 'MOVIE', 'Dune', 130000, 9000000)`,
      [sam.userId]
    );
    await pool.query(`INSERT INTO watch_history (user_id, provider_id, content_id, content_type, title, position_ms, duration_ms, completed, watched_at, updated_at) VALUES ($1, 'p', 'tt1', 'MOVIE', 'Dune', 130000, 9000000, false, now(), now())`, [sam.userId]);
    const res = await request(app).get(`/admin/users/${sam.userId}`).set(auth(admin));
    expect(res.status).toBe(200);
    expect(res.body.continueWatching[0]).toMatchObject({ title: "Dune", positionMs: 130000 });
    expect(res.body.history[0]).toMatchObject({ title: "Dune", completed: false });
    const torrentio = res.body.addons.find((a: { name: string }) => a.name === "Torrentio");
    expect(torrentio).toMatchObject({ host: "torrentio.strem.fun", debrid: "realdebrid", configured: true });
    const text = JSON.stringify(res.body);
    expect(text).not.toContain("SECRET-KEY-123");
    expect(text).not.toContain("password");
    expect(text).not.toContain("hash");
    expect(res.body.addons.find((a: { name: string }) => a.name === "Cinemeta")).toMatchObject({ debrid: null, configured: false });
  });

  it("answers 404 for an unknown or malformed user id", async () => {
    const admin = await createTestSession({ isAdmin: true });
    expect((await request(app).get("/admin/users/00000000-0000-4000-8000-000000000000").set(auth(admin))).status).toBe(404);
    expect((await request(app).get("/admin/users/not-an-id").set(auth(admin))).status).toBe(400);
  });

  it("summarises users, activity, plans and which app versions are out there", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const a = await createTestSession();
    const b = await createTestSession();
    await request(app).get("/user/me").set(auth(a, "0.1.7"));
    await request(app).get("/user/me").set(auth(b, "0.1.6"));
    await waitFor(() => versionOf(b), (v) => v === "0.1.6");
    const res = await request(app).get("/admin/summary").set(auth(admin));
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ users: 3, plus: { monthly: 0, yearly: 0, lifetime: 0 } });
    const versions = Object.fromEntries(res.body.versions.map((v: { version: string; devices: number }) => [v.version, v.devices]));
    expect(versions).toMatchObject({ "0.1.7": 1, "0.1.6": 1, unknown: 1 });
  });
});

describe("who is online and who is watching right now", () => {
  const progress = (over: Record<string, unknown> = {}) => ({ providerId: "p", contentId: "tt1", contentType: "MOVIE", title: "A", positionMs: 5000, durationMs: 100000, completed: false, watchedAt: "2025-01-01T00:00:00.000Z", ...over });
  const live = async (admin: TestSession) => (await request(app).get("/admin/summary").set(auth(admin))).body.live;
  const setPlatform = (s: TestSession, platform: string) => pool.query("UPDATE devices d SET platform = $2 FROM sessions x WHERE x.device_id = d.id AND x.id = $1", [s.sessionId, platform]);

  it("counts people who used the app in the last 5 minutes, in total and per platform", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const tv = await createTestSession();
    const phone = await createTestSession();
    const gone = await createTestSession();
    await setPlatform(admin, "web");
    await setPlatform(tv, "fire_tv");
    await setPlatform(phone, "android_phone");
    await pool.query("UPDATE sessions SET last_used_at = now() - interval '6 minutes' WHERE id = $1", [gone.sessionId]);
    const l = await live(admin);
    expect(l.online).toEqual({ users: 3, byPlatform: { android_phone: 1, fire_tv: 1, web: 1 } }); // the admin looking at the panel is online too; "gone" is not
    expect(l.onlineWindowSeconds).toBe(300);
    expect(l.watchingWindowSeconds).toBe(45);
  });

  it("an account on two devices counts once in the total", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const a = await createTestSession();
    const second = await pool.query<{ id: string }>("INSERT INTO devices (user_id, device_identifier, platform) VALUES ($1, gen_random_uuid(), 'web') RETURNING id", [a.userId]);
    await pool.query("INSERT INTO sessions (user_id, device_id, access_token_hash, access_token_expires_at, refresh_token_hash, refresh_token_expires_at) VALUES ($1, $2, 'x1', now() + interval '1 hour', 'x2', now() + interval '1 day')", [a.userId, second.rows[0]!.id]);
    expect((await live(admin)).online.users).toBe(2); // the admin and that one account
  });

  it("someone saving playback progress is watching; finishing, stopping for a minute, or a revoked device is not", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const playing = await createTestSession();
    const finished = await createTestSession();
    const stopped = await createTestSession();
    await setPlatform(playing, "fire_tv");
    expect((await live(admin)).watching).toEqual({ users: 0, byPlatform: {} });
    expect((await request(app).post("/user/watch-progress").set(auth(playing)).send(progress())).status).toBe(200);
    expect((await request(app).post("/user/watch-progress").set(auth(finished)).send(progress({ completed: true }))).status).toBe(200);
    await request(app).post("/user/watch-progress").set(auth(stopped)).send(progress());
    await waitFor(async () => (await pool.query("SELECT 1 FROM devices WHERE last_progress_at IS NOT NULL")).rowCount, (n) => n === 2);
    await pool.query("UPDATE devices d SET last_progress_at = now() - interval '50 seconds' FROM sessions x WHERE x.device_id = d.id AND x.id = $1", [stopped.sessionId]);
    expect((await live(admin)).watching).toEqual({ users: 1, byPlatform: { fire_tv: 1 } });
    await pool.query("UPDATE devices d SET revoked_at = now() FROM sessions x WHERE x.device_id = d.id AND x.id = $1", [playing.sessionId]);
    expect((await live(admin)).watching.users).toBe(0);
  });

  it("uses the server's clock, not the time the device claims", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const s = await createTestSession();
    await request(app).post("/user/watch-progress").set(auth(s)).send(progress({ watchedAt: "2019-01-01T00:00:00.000Z" })); // a device with a wrong clock
    await waitFor(async () => (await live(admin)).watching.users, (n) => n === 1);
    expect((await live(admin)).watching.users).toBe(1);
  });
});

describe("app version tracking", () => {
  it("records the version the app reports, and follows an update straight away", async () => {
    const s = await createTestSession();
    expect(await versionOf(s)).toBeNull();
    await request(app).get("/user/me").set(auth(s, "0.1.6"));
    expect(await waitFor(() => versionOf(s), (v) => v === "0.1.6")).toBe("0.1.6");
    await request(app).get("/user/me").set(auth(s, "0.1.7")); // the person updated: no waiting for the next scheduled write
    expect(await waitFor(() => versionOf(s), (v) => v === "0.1.7")).toBe("0.1.7");
  });

  it("keeps the last known version when a request carries none, and ignores junk", async () => {
    const s = await createTestSession();
    await request(app).get("/user/me").set(auth(s, "0.1.7"));
    await waitFor(() => versionOf(s), (v) => v === "0.1.7");
    await request(app).get("/user/me").set(auth(s));
    await request(app).get("/user/me").set({ ...auth(s), "X-ArcTV-App-Version": "<script>" });
    await new Promise((resolve) => setTimeout(resolve, 150));
    expect(await versionOf(s)).toBe("0.1.7");
    expect(cleanAppVersion("0.1.7")).toBe("0.1.7");
    expect(cleanAppVersion("x".repeat(40))).toBeNull();
    expect(cleanAppVersion(undefined)).toBeNull();
  });
});

describe("describeAddonUrl", () => {
  it("keeps the host and the debrid service's name, never the key", () => {
    expect(describeAddonUrl("https://torrentio.strem.fun/torbox=ABC/manifest.json")).toEqual({ host: "torrentio.strem.fun", configured: true, debrid: "torbox" });
    expect(describeAddonUrl("https://host.example/manifest.json?apikey=SECRET")).toMatchObject({ host: "host.example", configured: true, debrid: null });
    expect(describeAddonUrl("not a url")).toMatchObject({ host: "(unreadable)" });
  });
});
