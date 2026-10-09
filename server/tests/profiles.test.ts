import type { Express } from "express";
import request from "supertest";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { pool } from "../src/db/pool.js";
import { PIN_LOCK_SECONDS, PIN_MAX_FAILURES } from "../src/services/profileService.js";
import { createTestSession, type TestSession } from "./helpers/auth.js";
import { resetDatabase } from "./helpers/db.js";

let app: Express;
const saved = process.env.PLUS_PAYWALL;

beforeEach(async () => {
  app = createApp();
  await resetDatabase();
  process.env.PLUS_PAYWALL = "off"; // early access: everyone has Plus
});
afterEach(() => {
  if (saved === undefined) delete process.env.PLUS_PAYWALL;
  else process.env.PLUS_PAYWALL = saved;
});

const auth = (s: TestSession) => ({ Authorization: `Bearer ${s.token}` });
const list = (s: TestSession) => request(app).get("/user/profiles").set(auth(s));
const create = (s: TestSession, body: Record<string, unknown> = {}) => request(app).post("/user/profiles").set(auth(s)).send({ name: "Sam", avatar: "ocean", kind: "adult", ...body });
const verify = (s: TestSession, id: string, pin: string) => request(app).post(`/user/profiles/${id}/verify-pin`).set(auth(s)).send({ pin });
const as = (s: TestSession, profileId?: string) => (profileId ? { ...auth(s), "X-ArcTV-Profile": profileId } : auth(s));
const item = (over: Record<string, unknown> = {}) => ({ providerId: "p", contentId: "tt1", contentType: "MOVIE", title: "A", updatedAt: "2025-01-01T00:00:00.000Z", ...over });

describe("/user/profiles", () => {
  it("requires sign-in", async () => {
    expect((await request(app).get("/user/profiles")).status).toBe(401);
    expect((await request(app).post("/user/profiles").send({})).status).toBe(401);
    expect((await request(app).delete("/user/profiles/p_x")).status).toBe(401);
    expect((await request(app).post("/user/profiles/main/verify-pin").send({ pin: "1234" })).status).toBe(401);
  });

  it("gives every account its own profile, named after the account, created on first use", async () => {
    const s = await createTestSession({ displayName: "Alex" });
    expect((await pool.query("SELECT 1 FROM profiles WHERE user_id = $1", [s.userId])).rowCount).toBe(0);
    const res = await list(s);
    expect(res.status).toBe(200);
    expect(res.body.profiles).toEqual([{ id: "main", name: "Alex", avatar: "sunrise", kind: "adult", hasPin: false, isDefault: true }]);
    expect((await list(s)).body.profiles).toHaveLength(1); // listing again doesn't make another
    expect((await list(await createTestSession())).body.profiles[0].name).toBe("Me");
  });

  it("creates profiles and lists them with the account's own first", async () => {
    const s = await createTestSession({ displayName: "Alex" });
    const made = await create(s, { name: "Kids", avatar: "monster", kind: "kids" });
    expect(made.status).toBe(201);
    expect(made.body).toMatchObject({ name: "Kids", avatar: "monster", kind: "kids", hasPin: false, isDefault: false });
    expect(made.body.id).toMatch(/^p_[0-9a-f]{10}$/);
    expect((await list(s)).body.profiles.map((p: { name: string }) => p.name)).toEqual(["Alex", "Kids"]);
  });

  it("allows up to 5 in any adult / kids mix, then refuses, even when created at the same moment", async () => {
    const s = await createTestSession();
    expect((await Promise.all([1, 2, 3, 4, 5, 6, 7].map((i) => create(s, { name: `P${i}`, kind: i % 2 ? "kids" : "adult" })))).filter((r) => r.status === 201)).toHaveLength(4);
    const over = await create(s, { name: "Sixth" });
    expect(over.status).toBe(400);
    expect(over.body.error).toMatch(/up to 5/);
    expect((await list(s)).body.profiles).toHaveLength(5);
  });

  it("allows an all-kids (or all-adult) mix", async () => {
    const s = await createTestSession();
    for (let i = 0; i < 4; i++) expect((await create(s, { name: `K${i}`, kind: "kids" })).status).toBe(201);
    expect((await list(s)).body.profiles.filter((p: { kind: string }) => p.kind === "kids")).toHaveLength(4);
  });

  it("accepts the illustrated avatar ids as well as the older colour-tile ones", async () => {
    const s = await createTestSession();
    for (const avatar of ["cat", "retro-tv", "astronaut", "sunrise"]) expect((await create(s, { name: avatar, avatar })).status).toBe(201);
  });

  it("validates names, avatars, kinds and PINs, and refuses unknown fields", async () => {
    const s = await createTestSession();
    for (const bad of [{ name: "  " }, { name: "x".repeat(25) }, { avatar: "nope" }, { kind: "teen" }, { pin: "12" }, { pin: "12345" }, { pin: "abcd" }, { id: "main" }, { isDefault: true }]) {
      expect((await create(s, bad)).status, JSON.stringify(bad)).toBe(400);
    }
    expect((await create(s, { name: "  Padded  " })).body.name).toBe("Padded");
  });

  it("never returns the PIN or its hash", async () => {
    const s = await createTestSession();
    const made = await create(s, { pin: "4321" });
    expect(made.body.hasPin).toBe(true);
    const text = JSON.stringify([made.body, (await list(s)).body]);
    expect(text).not.toMatch(/4321|pin_hash|argon2/i);
    const row = await pool.query("SELECT pin_hash FROM profiles WHERE id = $1", [made.body.id]);
    expect(row.rows[0].pin_hash).toMatch(/^\$argon2id\$/);
  });

  it("changes a profile: name, picture, kind, and sets / changes / removes the PIN", async () => {
    const s = await createTestSession();
    const { id } = (await create(s)).body;
    const put = (body: Record<string, unknown>) => request(app).put(`/user/profiles/${id}`).set(auth(s)).send(body);
    expect((await put({ name: "Samuel", avatar: "fox", kind: "kids" })).body).toMatchObject({ name: "Samuel", avatar: "fox", kind: "kids", hasPin: false });
    expect((await put({ pin: "1111" })).body.hasPin).toBe(true);
    expect((await verify(s, id, "1111")).status).toBe(204);
    expect((await put({ pin: "2222" })).body.hasPin).toBe(true);
    expect((await verify(s, id, "1111")).status).toBe(403);
    expect((await verify(s, id, "2222")).status).toBe(204);
    expect((await put({ name: "Still locked" })).body).toMatchObject({ name: "Still locked", hasPin: true }); // an omitted pin leaves it
    expect((await put({ pin: null })).body.hasPin).toBe(false);
    expect((await put({ pin: "12" })).status).toBe(400);
    expect((await request(app).put("/user/profiles/p_nope").set(auth(s)).send({ name: "x" })).status).toBe(404);
  });

  it("the account's own profile can be renamed and locked, but never removed or made a kids profile", async () => {
    const s = await createTestSession({ displayName: "Alex" });
    expect((await request(app).put("/user/profiles/main").set(auth(s)).send({ name: "Owner", pin: "9999" })).body).toMatchObject({ id: "main", name: "Owner", hasPin: true, isDefault: true });
    expect((await request(app).put("/user/profiles/main").set(auth(s)).send({ kind: "kids" })).status).toBe(400);
    expect((await request(app).delete("/user/profiles/main").set(auth(s))).status).toBe(400);
    expect((await list(s)).body.profiles).toHaveLength(1);
  });

  it("removing a profile deletes its library and leaves the others alone", async () => {
    const s = await createTestSession();
    const { id } = (await create(s)).body;
    const lib = async (profile?: string) => ({
      list: (await request(app).get("/user/watchlist").set(as(s, profile))).body.items.length,
      cw: (await request(app).get("/user/continue-watching").set(as(s, profile))).body.items.length,
    });
    for (const p of [undefined, id]) {
      await request(app).post("/user/watchlist").set(as(s, p)).send(item());
      await request(app).post("/user/watch-progress").set(as(s, p)).send({ providerId: "p", contentId: "tt1", contentType: "MOVIE", title: "A", positionMs: 5000, durationMs: 100000, completed: false, watchedAt: "2025-01-01T00:00:00.000Z" });
      await request(app).put("/user/settings").set(as(s, p)).send({ homeRowOrder: [], hiddenRowIds: [], autoplayNextEpisode: false, skipIntroEnabled: true, subtitlesEnabled: true, defaultSubtitleLanguage: null, updatedAt: "2025-01-01T00:00:00.000Z" });
      await request(app).post("/user/feedback").set(as(s, p)).send({ providerId: "p", contentId: "tt1", contentType: "MOVIE", title: "A", feedback: "like", updatedAt: "2025-01-01T00:00:00.000Z" });
      await request(app).post("/user/picked-dismissals").set(as(s, p)).send({ contentId: "tt1", dismissedAt: new Date().toISOString() });
    }
    expect(await lib(id)).toEqual({ list: 1, cw: 1 });
    expect((await request(app).delete(`/user/profiles/${id}`).set(auth(s))).status).toBe(204);
    for (const table of ["watchlist_items", "watch_history", "continue_watching", "user_settings", "movie_feedback", "picked_dismissals"]) {
      expect((await pool.query(`SELECT 1 FROM ${table} WHERE user_id = $1 AND profile_id = $2`, [s.userId, id])).rowCount, table).toBe(0);
      expect((await pool.query(`SELECT 1 FROM ${table} WHERE user_id = $1 AND profile_id = 'main'`, [s.userId])).rowCount, `${table} main`).toBe(1);
    }
    expect(await lib()).toEqual({ list: 1, cw: 1 });
    expect((await request(app).delete(`/user/profiles/${id}`).set(auth(s))).status).toBe(404);
  });

  it("one account can't see, change, remove or use another's profiles", async () => {
    const a = await createTestSession();
    const b = await createTestSession();
    const { id } = (await create(a)).body;
    expect((await list(b)).body.profiles).toHaveLength(1);
    expect((await request(app).put(`/user/profiles/${id}`).set(auth(b)).send({ name: "Mine now" })).status).toBe(404);
    expect((await request(app).delete(`/user/profiles/${id}`).set(auth(b))).status).toBe(404);
    expect((await verify(b, id, "1234")).status).toBe(404);
    expect((await request(app).get("/user/watchlist").set(as(b, id))).status).toBe(404);
    expect((await list(a)).body.profiles).toHaveLength(2);
  });
});

describe("ArcTV Plus is needed for more than the account's own profile", () => {
  it("refuses create, change and remove without Plus, but still lists", async () => {
    const s = await createTestSession();
    const { id } = (await create(s)).body;
    process.env.PLUS_PAYWALL = "on"; // paywall on and this account hasn't paid
    expect((await create(s, { name: "More" })).status).toBe(403);
    expect((await request(app).put(`/user/profiles/${id}`).set(auth(s)).send({ name: "x" })).status).toBe(403);
    expect((await request(app).delete(`/user/profiles/${id}`).set(auth(s))).status).toBe(403);
    expect((await list(s)).status).toBe(200);
    expect((await list(s)).body.profiles).toHaveLength(2); // nothing is deleted when Plus lapses
  });

  it("refuses to use another profile's library without Plus, and keeps the account's own working", async () => {
    const s = await createTestSession();
    const { id } = (await create(s)).body;
    await request(app).post("/user/watchlist").set(as(s, id)).send(item());
    process.env.PLUS_PAYWALL = "on";
    expect((await request(app).get("/user/watchlist").set(as(s, id))).status).toBe(403);
    expect((await request(app).get("/user/watchlist").set(as(s, "main"))).status).toBe(200);
    expect((await request(app).get("/user/watchlist").set(as(s))).status).toBe(200);
    process.env.PLUS_PAYWALL = "off"; // Plus is back: the library is still there
    expect((await request(app).get("/user/watchlist").set(as(s, id))).body.items).toHaveLength(1);
  });
});

describe("PINs", () => {
  it("204 for the right PIN, 403 for a wrong one (never 401), and a profile with no PIN passes", async () => {
    const s = await createTestSession();
    const locked = (await create(s, { pin: "1234" })).body.id;
    const open = (await create(s, { name: "Open" })).body.id;
    expect((await verify(s, locked, "1234")).status).toBe(204);
    expect((await verify(s, locked, "0000")).status).toBe(403);
    expect((await verify(s, open, "0000")).status).toBe(204);
    expect((await verify(s, locked, "12")).status).toBe(400);
  });

  it(`locks guessing for ${PIN_LOCK_SECONDS / 60} minutes after ${PIN_MAX_FAILURES} wrong PINs in a row, with Retry-After, even for the right PIN`, async () => {
    const s = await createTestSession();
    const id = (await create(s, { pin: "1234" })).body.id;
    for (let i = 0; i < PIN_MAX_FAILURES; i++) expect((await verify(s, id, "0000")).status).toBe(403);
    const locked = await verify(s, id, "1234");
    expect(locked.status).toBe(429);
    expect(Number(locked.headers["retry-after"])).toBeGreaterThan(PIN_LOCK_SECONDS - 5);
    expect((await verify(s, id, "0000")).status).toBe(429);
  });

  it("counts wrong PINs that arrive at once, so parallel guesses don't each get a free try", async () => {
    const s = await createTestSession();
    const id = (await create(s, { pin: "1234" })).body.id;
    const results = await Promise.all(Array.from({ length: 12 }, (_, i) => verify(s, id, String(i).padStart(4, "0")).then((r) => r.status)));
    expect(results.filter((status) => status === 403)).toHaveLength(PIN_MAX_FAILURES);
    expect(results.filter((status) => status === 429)).toHaveLength(12 - PIN_MAX_FAILURES);
  });

  it("a right PIN resets the count, and the lock ends by itself", async () => {
    const s = await createTestSession();
    const id = (await create(s, { pin: "1234" })).body.id;
    for (let i = 0; i < PIN_MAX_FAILURES - 1; i++) await verify(s, id, "0000");
    expect((await verify(s, id, "1234")).status).toBe(204);
    for (let i = 0; i < PIN_MAX_FAILURES - 1; i++) expect((await verify(s, id, "0000")).status).toBe(403); // a fresh count, not 4 + 4
    for (let i = 0; i < 1; i++) await verify(s, id, "0000");
    expect((await verify(s, id, "1234")).status).toBe(429);
    await pool.query("UPDATE profiles SET pin_locked_until = now() - interval '1 second' WHERE id = $1", [id]);
    expect((await verify(s, id, "1234")).status).toBe(204);
    // after the lock ran out a single wrong PIN is one failure, not an instant second lock
    await pool.query("UPDATE profiles SET pin_locked_until = now() - interval '1 second', pin_failed_attempts = 0 WHERE id = $1", [id]);
    expect((await verify(s, id, "0000")).status).toBe(403);
    expect((await verify(s, id, "1234")).status).toBe(204);
  });

  it("changing the PIN clears a lockout", async () => {
    const s = await createTestSession();
    const id = (await create(s, { pin: "1234" })).body.id;
    for (let i = 0; i < PIN_MAX_FAILURES; i++) await verify(s, id, "0000");
    expect((await verify(s, id, "1234")).status).toBe(429);
    await request(app).put(`/user/profiles/${id}`).set(auth(s)).send({ pin: "5678" });
    expect((await verify(s, id, "5678")).status).toBe(204);
  });
});

describe("X-ArcTV-Profile: every library is per profile", () => {
  async function twoProfiles() {
    const s = await createTestSession();
    return { s, kid: (await create(s, { name: "Kid", kind: "kids" })).body.id as string };
  }

  it("My List", async () => {
    const { s, kid } = await twoProfiles();
    await request(app).post("/user/watchlist").set(as(s)).send(item({ contentId: "tt-main" }));
    await request(app).post("/user/watchlist").set(as(s, kid)).send(item({ contentId: "tt-kid" }));
    const ids = async (p?: string) => (await request(app).get("/user/watchlist").set(as(s, p))).body.items.map((i: { contentId: string }) => i.contentId);
    expect(await ids()).toEqual(["tt-main"]);
    expect(await ids("main")).toEqual(["tt-main"]);
    expect(await ids(kid)).toEqual(["tt-kid"]);
    // the same title in two profiles is two separate rows, and removing it in one leaves the other
    await request(app).post("/user/watchlist").set(as(s, kid)).send(item({ contentId: "tt-main" }));
    const q = new URLSearchParams({ providerId: "p", contentId: "tt-main", contentType: "MOVIE", updatedAt: "2025-06-01T00:00:00.000Z" });
    await request(app).delete(`/user/watchlist?${q}`).set(as(s, kid));
    expect(await ids(kid)).toEqual(["tt-kid"]);
    expect(await ids()).toEqual(["tt-main"]);
  });

  it("Continue Watching, watch history and 'watched' progress", async () => {
    const { s, kid } = await twoProfiles();
    const progress = (p: string | undefined, over: Record<string, unknown>) =>
      request(app).post("/user/watch-progress").set(as(s, p)).send({ providerId: "p", contentId: "tt1", contentType: "MOVIE", title: "A", positionMs: 5000, durationMs: 100000, completed: false, watchedAt: "2025-01-01T00:00:00.000Z", ...over });
    await progress(undefined, { positionMs: 5000 });
    await progress(kid, { positionMs: 70000, watchedAt: "2025-01-02T00:00:00.000Z" });
    const cw = async (p?: string) => (await request(app).get("/user/continue-watching").set(as(s, p))).body.items.map((i: { positionMs: number }) => i.positionMs);
    expect(await cw()).toEqual([5000]);
    expect(await cw(kid)).toEqual([70000]);
    await progress(kid, { completed: true, watchedAt: "2025-01-03T00:00:00.000Z" }); // finishing in one profile
    expect(await cw(kid)).toEqual([]);
    expect(await cw()).toEqual([5000]); // doesn't touch the other's resume point
    const hist = async (p?: string) => (await request(app).get("/user/history").set(as(s, p))).body.items;
    expect(await hist()).toHaveLength(1);
    expect((await hist())[0].completed).toBe(false);
    expect((await hist(kid))[0].completed).toBe(true);
  });

  it("settings (and a new profile starts from the defaults)", async () => {
    const { s, kid } = await twoProfiles();
    const body = (over: Record<string, unknown>) => ({ homeRowOrder: [], hiddenRowIds: [], autoplayNextEpisode: true, skipIntroEnabled: true, subtitlesEnabled: true, defaultSubtitleLanguage: null, updatedAt: "2025-01-01T00:00:00.000Z", ...over });
    await request(app).put("/user/settings").set(as(s)).send(body({ blockedGenres: ["Horror"], autoplayNextEpisode: false }));
    const kids = await request(app).get("/user/settings").set(as(s, kid));
    expect(kids.body).toMatchObject({ blockedGenres: [], autoplayNextEpisode: true, updatedAt: null });
    await request(app).put("/user/settings").set(as(s, kid)).send(body({ blockedGenres: ["Romance"], updatedAt: "2025-01-02T00:00:00.000Z" }));
    expect((await request(app).get("/user/settings").set(as(s))).body).toMatchObject({ blockedGenres: ["Horror"], autoplayNextEpisode: false });
    expect((await request(app).get("/user/settings").set(as(s, kid))).body.blockedGenres).toEqual(["Romance"]);
  });

  it("addons", async () => {
    const { s, kid } = await twoProfiles();
    const addon = (url: string) => ({ manifestUrl: url, addonId: "a", name: "A", manifestJson: { id: "a", catalogs: [{ type: "movie", id: "top" }] }, enabled: true, sortOrder: 0, updatedAt: "2025-01-01T00:00:00.000Z" });
    await request(app).post("/user/addons").set(as(s)).send(addon("https://a.test/manifest.json"));
    await request(app).post("/user/addons").set(as(s, kid)).send(addon("https://b.test/manifest.json"));
    const urls = async (p?: string) => (await request(app).get("/user/addons").set(as(s, p))).body.items.map((i: { manifestUrl: string }) => i.manifestUrl);
    expect(await urls()).toEqual(["https://a.test/manifest.json"]);
    expect(await urls(kid)).toEqual(["https://b.test/manifest.json"]);
    await request(app).post("/user/addons").set(as(s, kid)).send(addon("https://a.test/manifest.json")); // the same addon in both
    expect(await urls()).toHaveLength(1);
    expect(await urls(kid)).toHaveLength(2);
  });

  it("Like / Not for me: the header decides, and the profileId in the request still works without one", async () => {
    const { s, kid } = await twoProfiles();
    const fb = { providerId: "p", contentId: "tt1", contentType: "MOVIE", title: "A", feedback: "like", updatedAt: "2025-01-01T00:00:00.000Z" };
    await request(app).post("/user/feedback").set(as(s, kid)).send({ ...fb, profileId: "main" }); // the header wins over a stale body
    const saved = await pool.query("SELECT profile_id FROM movie_feedback WHERE user_id = $1", [s.userId]);
    expect(saved.rows.map((r) => r.profile_id)).toEqual([kid]);
    expect((await request(app).get("/user/feedback").set(as(s, kid))).body.items).toHaveLength(1);
    expect((await request(app).get("/user/feedback").set(as(s))).body.items).toHaveLength(0); // no header: profileId defaults to main
    expect((await request(app).get(`/user/feedback?profileId=${kid}`).set(as(s))).body.items).toHaveLength(1); // clients that send profileId themselves
    const q = new URLSearchParams({ providerId: "p", contentId: "tt1", contentType: "MOVIE", updatedAt: "2025-02-01T00:00:00.000Z" });
    await request(app).delete(`/user/feedback?${q}`).set(as(s, kid));
    expect((await request(app).get("/user/feedback").set(as(s, kid))).body.items).toHaveLength(0);
  });

  it("rejects a malformed or unknown profile header", async () => {
    const { s } = await twoProfiles();
    expect((await request(app).get("/user/watchlist").set({ ...auth(s), "X-ArcTV-Profile": "bad id!" })).status).toBe(400);
    expect((await request(app).get("/user/watchlist").set(as(s, "p_0000000000"))).status).toBe(404);
  });

  it("account-level calls ignore the header", async () => {
    const { s, kid } = await twoProfiles();
    expect((await request(app).get("/user/me").set(as(s, kid))).status).toBe(200);
    expect((await request(app).get("/user/plus").set(as(s, kid))).status).toBe(200);
    expect((await request(app).get("/user/profiles").set(as(s, kid))).body.profiles).toHaveLength(2);
  });
});

describe("existing data", () => {
  it("everything saved before profiles belongs to the account's own profile ('main'), with no header needed", async () => {
    const s = await createTestSession();
    // a row written the way the pre-profiles code wrote it: no profile_id at all
    await pool.query("INSERT INTO watchlist_items (user_id, provider_id, content_id, content_type, title) VALUES ($1, 'p', 'tt-old', 'MOVIE', 'Old')", [s.userId]);
    await pool.query("INSERT INTO user_addons (user_id, manifest_url, addon_id, name, manifest_json) VALUES ($1, 'https://old.test/m.json', 'o', 'Old', '{\"catalogs\":[{\"type\":\"movie\",\"id\":\"top\"}]}'::jsonb)", [s.userId]);
    await pool.query("INSERT INTO user_settings (user_id, autoplay_next_episode) VALUES ($1, false)", [s.userId]);
    expect((await request(app).get("/user/watchlist").set(as(s))).body.items.map((i: { contentId: string }) => i.contentId)).toEqual(["tt-old"]);
    expect((await request(app).get("/user/watchlist").set(as(s, "main"))).body.items).toHaveLength(1);
    expect((await request(app).get("/user/addons").set(as(s))).body.items).toHaveLength(1);
    expect((await request(app).get("/user/settings").set(as(s))).body.autoplayNextEpisode).toBe(false);
  });

  it("the database refuses a second 'main', a default that isn't main, and a kids default", async () => {
    const s = await createTestSession();
    await list(s);
    const insert = (id: string, isDefault: boolean, kind: string) => pool.query("INSERT INTO profiles (user_id, id, name, avatar, kind, is_default) VALUES ($1, $2, 'x', 'fox', $3, $4)", [s.userId, id, kind, isDefault]);
    await expect(insert("main", true, "adult")).rejects.toThrow(); // already there
    await expect(insert("p_other", true, "adult")).rejects.toThrow();
    await expect(insert("main", false, "adult")).rejects.toThrow();
    await expect(pool.query("UPDATE profiles SET kind = 'kids' WHERE user_id = $1 AND id = 'main'", [s.userId])).rejects.toThrow();
  });
});
