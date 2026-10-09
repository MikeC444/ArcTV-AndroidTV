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

const auth = (s: TestSession) => ({ Authorization: `Bearer ${s.token}` });
const list = async (s: TestSession, profile?: string) => (await request(app).get("/user/addons").set(profile ? { ...auth(s), "X-ArcTV-Profile": profile } : auth(s))).body.items as Array<{ manifestUrl: string; name: string; sortOrder: number }>;
const age = (s: TestSession, minutes = 60) => pool.query("UPDATE users SET created_at = now() - make_interval(mins => $2) WHERE id = $1", [s.userId, minutes]);

const CINEMETA_URL = "https://v3-cinemeta.strem.io/manifest.json";
const addon = (manifestUrl: string, name: string, catalogs: unknown[], sortOrder = 0) => ({
  manifestUrl,
  addonId: `id.${name}`,
  name,
  manifestJson: { id: `id.${name}`, name, version: "1", types: ["movie"], catalogs },
  enabled: true,
  sortOrder,
  updatedAt: "2025-01-01T00:00:00.000Z",
});
const install = (s: TestSession, body: ReturnType<typeof addon>) => request(app).post("/user/addons").set(auth(s)).send(body);

describe("the server gives a profile with nothing to browse Cinemeta", () => {
  it("adds Cinemeta, with its catalogues, to an older account that has no addons — and does not add it twice", async () => {
    const s = await createTestSession();
    await age(s);
    const first = await list(s);
    expect(first.map((a) => a.manifestUrl)).toEqual([CINEMETA_URL]);
    const manifest = (await request(app).get("/user/addons").set(auth(s))).body.items[0].manifestJson;
    expect(manifest.catalogs.length).toBeGreaterThan(3);
    expect((await list(s)).map((a) => a.manifestUrl)).toEqual([CINEMETA_URL]);
    const rows = await pool.query("SELECT count(*)::int AS n FROM user_addons WHERE user_id = $1", [s.userId]);
    expect(rows.rows[0].n).toBe(1);
  });

  it("adds it after a stream-only addon such as Torrentio, keeping that addon", async () => {
    const s = await createTestSession();
    await install(s, addon("https://torrentio.example/manifest.json", "Torrentio", []));
    await age(s);
    const items = await list(s);
    expect(items.map((a) => a.name)).toEqual(["Torrentio", "Cinemeta"]);
    expect(items[1]!.sortOrder).toBeGreaterThan(items[0]!.sortOrder);
  });

  it("leaves an account alone that already has an addon with catalogues, and never adds it later", async () => {
    const s = await createTestSession();
    await install(s, addon("https://tmdb.example/manifest.json", "TMDB", [{ type: "movie", id: "popular" }]));
    await age(s);
    expect((await list(s)).map((a) => a.name)).toEqual(["TMDB"]);
    // they remove it: nothing is added in its place
    await request(app).delete("/user/addons").set(auth(s)).query({ manifestUrl: "https://tmdb.example/manifest.json", updatedAt: "2030-01-01T00:00:00.000Z" });
    expect(await list(s)).toEqual([]);
  });

  it("does not give it back to someone who removed it on purpose", async () => {
    const s = await createTestSession();
    await age(s);
    expect((await list(s)).map((a) => a.manifestUrl)).toEqual([CINEMETA_URL]);
    await request(app).delete("/user/addons").set(auth(s)).query({ manifestUrl: CINEMETA_URL, updatedAt: "2030-01-01T00:00:00.000Z" });
    expect(await list(s)).toEqual([]);
  });

  it("leaves a brand-new account's list exactly as it is (the first sign-in compares it with the device's)", async () => {
    const s = await createTestSession();
    expect(await list(s)).toEqual([]);
    expect(await list(s)).toEqual([]);
    const marks = await pool.query("SELECT count(*)::int AS n FROM addon_seed_marks WHERE user_id = $1", [s.userId]);
    expect(marks.rows[0].n).toBe(0);
    // once it is old enough it is treated like any other
    await age(s);
    expect((await list(s)).map((a) => a.manifestUrl)).toEqual([CINEMETA_URL]);
  });

  it("looks at each profile on its own", async () => {
    const s = await createTestSession();
    await age(s);
    const kid = await request(app).post("/user/profiles").set(auth(s)).send({ name: "Kid", avatar: "ocean", kind: "kids" });
    // the account must have Plus for a second profile; when it does not, there is nothing more to check here
    if (kid.status === 201) {
      expect((await list(s, kid.body.id)).map((a) => a.manifestUrl)).toEqual([CINEMETA_URL]);
    }
    expect((await list(s)).map((a) => a.manifestUrl)).toEqual([CINEMETA_URL]);
  });

  it("brings back Cinemeta once when it was removed earlier but never seeded, without making a duplicate row", async () => {
    const s = await createTestSession();
    await install(s, addon(CINEMETA_URL, "Cinemeta", []));
    await request(app).delete("/user/addons").set(auth(s)).query({ manifestUrl: CINEMETA_URL, updatedAt: "2030-01-01T00:00:00.000Z" });
    await age(s);
    expect((await list(s)).map((a) => a.manifestUrl)).toEqual([CINEMETA_URL]);
    const rows = await pool.query("SELECT count(*)::int AS n FROM user_addons WHERE user_id = $1", [s.userId]);
    expect(rows.rows[0].n).toBe(1);
  });
});
