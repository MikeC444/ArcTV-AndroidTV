import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { pool } from "../src/db/pool.js";
import { STREAMING_CATALOGS_ADDON_ID, STREAMING_CATALOGS_MANIFEST_URL } from "../src/data/streamingCatalogsManifest.js";
import { createTestSession, type TestSession } from "./helpers/auth.js";
import { resetDatabase } from "./helpers/db.js";

let app: Express;
beforeEach(async () => {
  app = createApp();
  await resetDatabase();
});

const CINEMETA_URL = "https://v3-cinemeta.strem.io/manifest.json";
const OPENSUBTITLES_URL = "https://opensubtitles-v3.strem.io/manifest.json";
const auth = (s: TestSession, profile?: string) => ({ Authorization: `Bearer ${s.token}`, ...(profile ? { "X-ArcTV-Profile": profile } : {}) });
type Row = { manifestUrl: string; name: string; addonId: string; enabled: boolean; sortOrder: number; manifestJson: { resources: unknown[]; catalogs: Array<{ id: string; name: string }> } };
const list = async (s: TestSession, profile?: string) => (await request(app).get("/user/addons").set(auth(s, profile))).body.items as Row[];
const streaming = (rows: Row[]) => rows.filter((a) => a.manifestUrl === STREAMING_CATALOGS_MANIFEST_URL);
const install = (s: TestSession, manifestUrl: string, addonId: string, name: string, sortOrder = 0) =>
  request(app).post("/user/addons").set(auth(s)).send({ manifestUrl, addonId, name, manifestJson: { id: addonId, name, catalogs: [{ type: "movie", id: "top" }] }, enabled: true, sortOrder, updatedAt: "2025-01-01T00:00:00.000Z" });

describe("the server gives every adult profile the Streaming Catalogs addon once", () => {
  it("adds it after Cinemeta and OpenSubtitles for a brand-new account, catalogue-only, with the services in it, and not twice", async () => {
    const s = await createTestSession({ platform: "web" });
    const first = await list(s);
    expect(first.map((a) => a.manifestUrl)).toEqual([CINEMETA_URL, OPENSUBTITLES_URL, STREAMING_CATALOGS_MANIFEST_URL]);
    const row = streaming(first)[0]!;
    expect(row).toMatchObject({ addonId: STREAMING_CATALOGS_ADDON_ID, name: "Streaming Catalogs", enabled: true, sortOrder: 2 });
    expect(row.manifestJson.resources).toEqual(["catalog"]);
    expect(row.manifestJson.catalogs.map((c) => c.name)).toEqual(expect.arrayContaining(["Netflix", "Disney+", "Prime Video", "Apple TV+", "HBO Max", "Shudder", "Discovery+"]));
    expect(streaming(await list(s))).toHaveLength(1);
    const rows = await pool.query("SELECT count(*)::int AS n FROM user_addons WHERE user_id = $1 AND manifest_url = $2", [s.userId, STREAMING_CATALOGS_MANIFEST_URL]);
    expect(rows.rows[0].n).toBe(1);
  });

  it("adds it for an existing account, after the addons it already has, which are kept", async () => {
    const s = await createTestSession({ platform: "web" });
    await pool.query("INSERT INTO addon_seed_marks (user_id) VALUES ($1)", [s.userId]);
    await pool.query("INSERT INTO subtitles_addon_marks (user_id) VALUES ($1)", [s.userId]);
    await install(s, "https://tmdb.example/manifest.json", "id.tmdb", "TMDB", 4);
    const rows = await list(s);
    expect(rows.map((a) => a.name)).toEqual(["TMDB", "Streaming Catalogs"]);
    expect(rows[1]!.sortOrder).toBe(5);
  });

  it("does not give it back to someone who removed it, on any device", async () => {
    const s = await createTestSession({ platform: "web" });
    await list(s);
    await request(app).delete("/user/addons").set(auth(s)).query({ manifestUrl: STREAMING_CATALOGS_MANIFEST_URL, updatedAt: "2030-01-01T00:00:00.000Z" });
    expect(streaming(await list(s))).toHaveLength(0);
    expect(streaming(await list(s))).toHaveLength(0);
  });

  it("leaves an account alone that installed the addon itself (with its own link), or had installed and removed it before this existed", async () => {
    const own = await createTestSession({ platform: "web" });
    await install(own, "https://other-host.example/own-config/manifest.json", STREAMING_CATALOGS_ADDON_ID, "Streaming Catalogs");
    const rows = await list(own);
    expect(rows.filter((a) => a.addonId === STREAMING_CATALOGS_ADDON_ID)).toHaveLength(1);
    expect(streaming(rows)).toHaveLength(0);

    const removedBefore = await createTestSession({ platform: "web" });
    await install(removedBefore, STREAMING_CATALOGS_MANIFEST_URL, STREAMING_CATALOGS_ADDON_ID, "Streaming Catalogs");
    await request(app).delete("/user/addons").set(auth(removedBefore)).query({ manifestUrl: STREAMING_CATALOGS_MANIFEST_URL, updatedAt: "2025-02-01T00:00:00.000Z" });
    expect(streaming(await list(removedBefore))).toHaveLength(0);
  });

  it("looks at each profile on its own, and leaves a kids profile out", async () => {
    const s = await createTestSession({ platform: "web" });
    const adult = await request(app).post("/user/profiles").set(auth(s)).send({ name: "Sam", avatar: "owl", kind: "adult" });
    const kids = await request(app).post("/user/profiles").set(auth(s)).send({ name: "Kit", avatar: "owl", kind: "kids" });
    expect(adult.status).toBeLessThan(300);
    expect(kids.status).toBeLessThan(300);
    expect(streaming(await list(s))).toHaveLength(1);
    expect(streaming(await list(s, adult.body.id ?? adult.body.profile?.id))).toHaveLength(1);
    expect(streaming(await list(s, kids.body.id ?? kids.body.profile?.id))).toHaveLength(0);
  });
});
