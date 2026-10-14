import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { pool } from "../src/db/pool.js";
import { createTestSession } from "./helpers/auth.js";
import { resetDatabase } from "./helpers/db.js";

const app = createApp();
beforeEach(async () => {
  await resetDatabase();
});

const event = (over: Record<string, unknown> = {}) => ({
  providerId: "com.linvo.cinemeta",
  contentId: "tt0111161",
  contentType: "MOVIE",
  title: "The Shawshank Redemption",
  releaseTitle: "Shawshank.2160p.DV.HEVC",
  resolution: "4K",
  codec: "HEVC",
  trigger: "error",
  outcome: "opened",
  errorMessage: "MediaCodecVideoRenderer error [ERROR_CODE_DECODING_FAILED]",
  ...over,
});

describe("POST /user/player-events/external", () => {
  it("requires sign-in", async () => {
    expect((await request(app).post("/user/player-events/external").send(event())).status).toBe(401);
  });

  it("stores the event and shows it to admins in the summary", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const res = await request(app).post("/user/player-events/external").set("Authorization", `Bearer ${admin.token}`).set("X-ArcTV-App-Version", "0.2.0").send(event());
    expect(res.status).toBe(204);
    await request(app).post("/user/player-events/external").set("Authorization", `Bearer ${admin.token}`).send(event({ trigger: "button", errorMessage: null }));
    const rows = await pool.query("SELECT launched_from AS trigger, app_version FROM external_player_events ORDER BY created_at");
    expect(rows.rows).toHaveLength(2);
    expect(rows.rows[0]).toMatchObject({ trigger: "error", app_version: "0.2.0" });
    const summary = await request(app).get("/admin/summary").set("Authorization", `Bearer ${admin.token}`);
    expect(summary.body.externalPlayer).toMatchObject({ opens7d: 2, users7d: 1, afterError7d: 1, fromButton7d: 1, noPlayer7d: 0 });
    expect(summary.body.externalPlayer.recent).toHaveLength(2);
  });

  it("records which player was chosen and counts VLC separately", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const send = (over: Record<string, unknown>) => request(app).post("/user/player-events/external").set("Authorization", `Bearer ${admin.token}`).send(event(over));
    expect((await send({ engine: "vlc" })).status).toBe(204);
    expect((await send({})).status).toBe(204);
    const rows = await pool.query("SELECT engine FROM external_player_events ORDER BY created_at");
    expect(rows.rows.map((r: { engine: string }) => r.engine)).toEqual(["vlc", "external"]);
    const summary = await request(app).get("/admin/summary").set("Authorization", `Bearer ${admin.token}`);
    expect(summary.body.externalPlayer).toMatchObject({ opens7d: 1, vlc7d: 1 });
    expect((await send({ engine: "mpv" })).status).toBe(400);
  });

  it("rejects an unknown trigger", async () => {
    const s = await createTestSession();
    expect((await request(app).post("/user/player-events/external").set("Authorization", `Bearer ${s.token}`).send(event({ trigger: "x" }))).status).toBe(400);
  });
});

describe("POST /user/player-events/playback", () => {
  const outcome = (over: Record<string, unknown> = {}) => ({ engine: "vlc", deviceModel: "AFTMM", resolution: "4K", codec: "HEVC", hdr: "Dolby Vision", startMs: 2400, watchedMs: 9000, outcome: "short", sourcesTried: 2, ...over });
  const post = (token: string, body: unknown) => request(app).post("/user/player-events/playback").set("Authorization", `Bearer ${token}`).set("X-ArcTV-App-Version", "0.5.0").send(body as object);

  it("requires sign-in and refuses nonsense", async () => {
    expect((await request(app).post("/user/player-events/playback").send(outcome())).status).toBe(401);
    const admin = await createTestSession({ isAdmin: true });
    expect((await post(admin.token, outcome({ outcome: "great" }))).status).toBe(400);
    expect((await post(admin.token, outcome({ engine: "other" }))).status).toBe(400);
    expect((await post(admin.token, outcome({ watchedMs: -1 }))).status).toBe(400);
  });

  it("stores how a viewing ended, with nothing about what was watched", async () => {
    const admin = await createTestSession({ isAdmin: true });
    expect((await post(admin.token, outcome())).status).toBe(204);
    const row = (await pool.query("SELECT * FROM playback_outcomes")).rows[0];
    expect(row).toMatchObject({ engine: "vlc", device_model: "AFTMM", resolution: "4K", outcome: "short", sources_tried: 2, app_version: "0.5.0", watched_ms: 9000, start_ms: 2400 });
    expect(Object.keys(row)).not.toContain("title");
    expect(Object.keys(row)).not.toContain("content_id");
  });

  it("is summarised for admins by device, player and quality, as early exits, errors and failed starts", async () => {
    const admin = await createTestSession({ isAdmin: true });
    for (const o of [
      outcome({ outcome: "ok", watchedMs: 600000, sourcesTried: 1 }),
      outcome({ outcome: "short" }),
      outcome({ outcome: "error", errorKind: "decoder", startMs: null, watchedMs: 0 }),
      outcome({ outcome: "never_started", startMs: null, watchedMs: 0, deviceModel: "Pixel 8", engine: "builtin", resolution: "1080p" }),
    ]) await post(admin.token, o);
    const res = await request(app).get("/admin/summary").set("Authorization", `Bearer ${admin.token}`);
    const p = res.body.playback;
    expect(p).toMatchObject({ plays7d: 4, users7d: 1, short7d: 1, errors7d: 1, neverStarted7d: 1, multiSource7d: 3 });
    expect(p.medianStartMs).toBe(2400);
    expect(p.byDevice[0]).toMatchObject({ label: "AFTMM", plays: 3, short: 1, errors: 1 });
    expect(p.byDevice[1]).toMatchObject({ label: "Pixel 8", plays: 1, neverStarted: 1 });
    expect(p.byEngine.map((g: { label: string }) => g.label).sort()).toEqual(["builtin", "vlc"]);
    expect(p.byResolution.map((g: { label: string }) => g.label)).toEqual(["4K", "1080p"]);
  });

  it("accepts a viewing that ended before any player opened", async () => {
    const admin = await createTestSession({ isAdmin: true });
    expect((await post(admin.token, outcome({ engine: "none", outcome: "never_started", startMs: null, watchedMs: 0, errorKind: "source no longer available" }))).status).toBe(204);
    const row = (await pool.query("SELECT engine, outcome, error_kind FROM playback_outcomes")).rows[0];
    expect(row).toMatchObject({ engine: "none", outcome: "never_started", error_kind: "source no longer available" });
  });

  it("is invisible to people who aren't admins", async () => {
    const plain = await createTestSession();
    expect((await post(plain.token, outcome())).status).toBe(204); // anyone signed in may report
    expect((await request(app).get("/admin/summary").set("Authorization", `Bearer ${plain.token}`)).status).toBe(404);
  });
});

describe("POST /user/player-events/download", () => {
  const send = (token: string, body: Record<string, unknown>) => request(app).post("/user/player-events/download").set("Authorization", `Bearer ${token}`).send(body);

  it("requires sign-in", async () => {
    expect((await request(app).post("/user/player-events/download").send({ kind: "opened", contentType: "MOVIE" })).status).toBe(401);
  });

  it("counts people who tried and people who pressed a file, and shows the split to admins", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const other = await createTestSession();
    expect((await send(admin.token, { kind: "opened", contentType: "MOVIE" })).status).toBe(204);
    expect((await send(admin.token, { kind: "file", contentType: "TV_SHOW", episode: true, resolution: "1080p", cached: true })).status).toBe(204);
    expect((await send(admin.token, { kind: "file", contentType: "MOVIE", resolution: "4K", cached: false })).status).toBe(204);
    expect((await send(other.token, { kind: "popup", contentType: "MOVIE" })).status).toBe(204);
    const summary = await request(app).get("/admin/summary").set("Authorization", `Bearer ${admin.token}`);
    expect(summary.body.downloads).toMatchObject({ triedPeople: 2, openedPeople: 1, startedPeople: 1, popupPeople: 1, popups: 1, opens: 1, files: 2, episodeFiles: 1, uncachedFiles: 1 });
    expect(summary.body.downloads.byQuality).toEqual(expect.arrayContaining([{ label: "1080p", files: 1 }, { label: "4K", files: 1 }]));
  });

  it("shows admins who tried and what they did, day by day, and nobody else", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const free = await createTestSession();
    await send(admin.token, { kind: "opened", contentType: "MOVIE" });
    await send(admin.token, { kind: "file", contentType: "TV_SHOW", episode: true, resolution: "1080p", cached: true });
    await send(admin.token, { kind: "file", contentType: "MOVIE", resolution: "1080p", cached: false });
    await send(free.token, { kind: "popup", contentType: "MOVIE" });
    await send(free.token, { kind: "popup", contentType: "TV_SHOW", episode: true });
    const res = await request(app).get("/admin/downloads").set("Authorization", `Bearer ${admin.token}`);
    expect(res.status).toBe(200);
    expect(res.body.days).toHaveLength(7);
    expect(res.body.days[6]).toMatchObject({ popups: 2, opens: 1, files: 2 });
    expect(res.body.byType).toEqual(expect.arrayContaining([{ type: "MOVIE", popups: 1, opens: 1, files: 1 }, { type: "TV_SHOW", popups: 1, opens: 0, files: 1 }]));
    expect(res.body.cached).toEqual({ cached: 1, uncached: 1, unknown: 0 });
    const byEmail = Object.fromEntries(res.body.people.map((p: { userId: string }) => [p.userId, p]));
    expect(byEmail[admin.userId]).toMatchObject({ popups: 0, opens: 1, files: 2, episodeFiles: 1, lastKind: "file", topQuality: "1080p" });
    expect(byEmail[free.userId]).toMatchObject({ popups: 2, opens: 0, files: 0, lastKind: "popup", topQuality: null, plan: null });
    expect(res.body.people[0].userId).toBe(free.userId); // most recent first
    expect(res.body.recent).toHaveLength(5);
    expect(res.body.recent[0]).toMatchObject({ kind: "popup", userId: free.userId });
    expect(JSON.stringify(res.body)).not.toMatch(/token|password/i);
    expect((await request(app).get("/admin/downloads").set("Authorization", `Bearer ${free.token}`)).status).toBe(404);
  });

  it("keeps the quality only on a pressed file and rejects an unknown kind", async () => {
    const s = await createTestSession();
    await send(s.token, { kind: "opened", contentType: "MOVIE", resolution: "4K", cached: true });
    const rows = await pool.query("SELECT resolution, cached FROM download_events");
    expect(rows.rows).toEqual([{ resolution: null, cached: null }]);
    expect((await send(s.token, { kind: "x", contentType: "MOVIE" })).status).toBe(400);
    expect((await send(s.token, { kind: "file", contentType: "SONG" })).status).toBe(400);
  });
});

describe("POST /user/player-events/app-promo", () => {
  const send = (token: string, body: Record<string, unknown>) => request(app).post("/user/player-events/app-promo").set("Authorization", `Bearer ${token}`).send(body);

  it("requires sign-in", async () => {
    expect((await request(app).post("/user/player-events/app-promo").send({ app: "mac", kind: "shown" })).status).toBe(401);
  });

  it("counts who was offered each desktop app and what they did, and shows admins the split", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const other = await createTestSession();
    expect((await send(admin.token, { app: "windows", kind: "shown" })).status).toBe(204);
    expect((await send(admin.token, { app: "windows", kind: "download" })).status).toBe(204);
    expect((await send(other.token, { app: "windows", kind: "shown" })).status).toBe(204);
    expect((await send(other.token, { app: "windows", kind: "closed" })).status).toBe(204);
    expect((await send(other.token, { app: "mac", kind: "shown" })).status).toBe(204);
    const summary = await request(app).get("/admin/summary").set("Authorization", `Bearer ${admin.token}`);
    const by = Object.fromEntries(summary.body.appPromos.map((p: { app: string }) => [p.app, p]));
    expect(by.windows).toMatchObject({ shownPeople: 2, downloadPeople: 1, closedPeople: 1, shown: 2, downloads: 1, closes: 1 });
    expect(by.mac).toMatchObject({ shownPeople: 1, downloadPeople: 0, closedPeople: 0, shown: 1, downloads: 0, closes: 0 });
  });

  it("shows admins, day by day and person by person, what each did, and nobody else", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const free = await createTestSession();
    await send(admin.token, { app: "windows", kind: "shown" });
    await send(admin.token, { app: "windows", kind: "download" });
    await send(free.token, { app: "mac", kind: "shown" });
    await send(free.token, { app: "mac", kind: "closed" });
    const res = await request(app).get("/admin/app-promos").set("Authorization", `Bearer ${admin.token}`);
    expect(res.status).toBe(200);
    expect(res.body.days).toHaveLength(7);
    expect(res.body.days[6]).toMatchObject({ windows: { shown: 1, downloads: 1 }, mac: { shown: 1, downloads: 0 } });
    const byUser = Object.fromEntries(res.body.people.map((p: { userId: string }) => [p.userId, p]));
    expect(byUser[admin.userId]).toMatchObject({ app: "windows", outcome: "download" });
    expect(byUser[free.userId]).toMatchObject({ app: "mac", outcome: "closed" });
    expect(res.body.recent).toHaveLength(4);
    expect(JSON.stringify(res.body)).not.toMatch(/token|password/i);
    expect((await request(app).get("/admin/app-promos").set("Authorization", `Bearer ${free.token}`)).status).toBe(404);
  });

  it("rejects an unknown app or kind", async () => {
    const s = await createTestSession();
    expect((await send(s.token, { app: "linux", kind: "shown" })).status).toBe(400);
    expect((await send(s.token, { app: "mac", kind: "x" })).status).toBe(400);
  });
});

