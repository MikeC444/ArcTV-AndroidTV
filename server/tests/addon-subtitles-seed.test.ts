import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { pool } from "../src/db/pool.js";
import { createTestSession, type TestSession } from "./helpers/auth.js";
import { resetDatabase } from "./helpers/db.js";

let app: Express;
beforeEach(async () => {
  app = createApp();
  await resetDatabase();
});

const OPENSUBTITLES_URL = "https://opensubtitles-v3.strem.io/manifest.json";
const auth = (s: TestSession, profile?: string) => ({ Authorization: `Bearer ${s.token}`, ...(profile ? { "X-ArcTV-Profile": profile } : {}) });
const list = async (s: TestSession, profile?: string) =>
  (await request(app).get("/user/addons").set(auth(s, profile))).body.items as Array<{ manifestUrl: string; name: string; addonId: string; enabled: boolean; manifestJson: { resources: unknown[] } }>;

describe("the server gives every profile the OpenSubtitles addon once", () => {
  it("adds it next to Cinemeta for a brand-new account, with the subtitles resource, and not twice", async () => {
    const s = await createTestSession();
    const first = await list(s);
    expect(first.map((a) => a.manifestUrl)).toEqual(["https://v3-cinemeta.strem.io/manifest.json", OPENSUBTITLES_URL]);
    expect(first[1]).toMatchObject({ addonId: "org.stremio.opensubtitlesv3", enabled: true });
    expect(first[1]!.manifestJson.resources).toContain("subtitles");
    expect((await list(s)).map((a) => a.manifestUrl)).toEqual(first.map((a) => a.manifestUrl));
    const rows = await pool.query("SELECT count(*)::int AS n FROM user_addons WHERE user_id = $1 AND manifest_url = $2", [s.userId, OPENSUBTITLES_URL]);
    expect(rows.rows[0].n).toBe(1);
  });

  it("adds it for an existing account that has its own addons, keeping them", async () => {
    const s = await createTestSession();
    await pool.query("INSERT INTO addon_seed_marks (user_id) VALUES ($1)", [s.userId]);
    await request(app).post("/user/addons").set(auth(s)).send({
      manifestUrl: "https://tmdb.example/manifest.json", addonId: "id.tmdb", name: "TMDB", manifestJson: { id: "id.tmdb", name: "TMDB", catalogs: [{ type: "movie", id: "popular" }] },
      enabled: true, sortOrder: 0, updatedAt: "2025-01-01T00:00:00.000Z",
    });
    expect((await list(s)).map((a) => a.name)).toEqual(["TMDB", "OpenSubtitles v3"]);
  });

  it("does not give it back to someone who removed it, on any device", async () => {
    const s = await createTestSession();
    await list(s);
    await request(app).delete("/user/addons").set(auth(s)).query({ manifestUrl: OPENSUBTITLES_URL, updatedAt: "2030-01-01T00:00:00.000Z" });
    expect((await list(s)).map((a) => a.manifestUrl)).toEqual(["https://v3-cinemeta.strem.io/manifest.json"]);
    expect((await list(s)).map((a) => a.manifestUrl)).toEqual(["https://v3-cinemeta.strem.io/manifest.json"]);
  });

  it("leaves it alone for an account that already installed it, or had installed and removed it before this existed", async () => {
    const installed = await createTestSession();
    await request(app).post("/user/addons").set(auth(installed)).send({
      manifestUrl: OPENSUBTITLES_URL, addonId: "org.stremio.opensubtitlesv3", name: "OpenSubtitles", manifestJson: { id: "org.stremio.opensubtitlesv3", name: "OpenSubtitles" },
      enabled: true, sortOrder: 0, updatedAt: "2025-01-01T00:00:00.000Z",
    });
    expect((await list(installed)).filter((a) => a.manifestUrl === OPENSUBTITLES_URL)).toHaveLength(1);

    const removedBefore = await createTestSession();
    await request(app).post("/user/addons").set(auth(removedBefore)).send({
      manifestUrl: OPENSUBTITLES_URL, addonId: "org.stremio.opensubtitlesv3", name: "OpenSubtitles", manifestJson: { id: "org.stremio.opensubtitlesv3", name: "OpenSubtitles" },
      enabled: true, sortOrder: 0, updatedAt: "2025-01-01T00:00:00.000Z",
    });
    await request(app).delete("/user/addons").set(auth(removedBefore)).query({ manifestUrl: OPENSUBTITLES_URL, updatedAt: "2025-02-01T00:00:00.000Z" });
    expect((await list(removedBefore)).some((a) => a.manifestUrl === OPENSUBTITLES_URL)).toBe(false);
  });
});
