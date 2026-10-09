import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { pool } from "../src/db/pool.js";
import { resetDatabase } from "./helpers/db.js";
import { createTestSession } from "./helpers/auth.js";

let app: Express;

beforeEach(async () => {
  app = createApp();
  await resetDatabase();
});

const ago = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString();
const post = (token: string, b: Record<string, unknown>) => request(app).post("/user/picked-dismissals").set("Authorization", `Bearer ${token}`).send(b);
const list = (token: string, profileId = "main") => request(app).get(`/user/picked-dismissals?profileId=${profileId}`).set("Authorization", `Bearer ${token}`);

describe("/user/picked-dismissals", () => {
  it("requires sign-in", async () => {
    expect((await request(app).get("/user/picked-dismissals")).status).toBe(401);
    expect((await request(app).post("/user/picked-dismissals").send({ contentId: "tt1", dismissedAt: ago(0) })).status).toBe(401);
  });

  it("starts empty, stores a removal and returns it on another device", async () => {
    const s = await createTestSession();
    expect((await list(s.token)).body.items).toEqual([]);
    const at = ago(1);
    const saved = await post(s.token, { contentId: "tt0111161", dismissedAt: at });
    expect(saved.status).toBe(200);
    expect(saved.body).toEqual({ profileId: "main", contentId: "tt0111161", dismissedAt: at });
    expect((await list(s.token)).body.items).toEqual([{ profileId: "main", contentId: "tt0111161", dismissedAt: at }]);
  });

  it("a later removal of the same title moves it forward; an older one loses and gets the current time back", async () => {
    const s = await createTestSession();
    await post(s.token, { contentId: "tt1", dismissedAt: ago(4) });
    const newer = await post(s.token, { contentId: "tt1", dismissedAt: ago(1) });
    const stale = await post(s.token, { contentId: "tt1", dismissedAt: ago(3) });
    expect(stale.body.dismissedAt).toBe(newer.body.dismissedAt);
    expect((await list(s.token)).body.items).toHaveLength(1);
  });

  it("is per profile and per account", async () => {
    const a = await createTestSession();
    const b = await createTestSession();
    await post(a.token, { contentId: "tt1", dismissedAt: ago(1) });
    expect((await list(b.token)).body.items).toEqual([]);
    expect((await list(a.token, "kid")).body.items).toEqual([]);
  });

  it("forgets removals older than 30 days", async () => {
    const s = await createTestSession();
    await pool.query("INSERT INTO picked_dismissals (user_id, profile_id, content_id, dismissed_at) VALUES ($1, 'main', 'old', now() - interval '31 days'), ($1, 'main', 'new', now() - interval '2 days')", [s.userId]);
    expect((await list(s.token)).body.items.map((i: { contentId: string }) => i.contentId)).toEqual(["new"]);
    await post(s.token, { contentId: "tt9", dismissedAt: ago(0) });
    expect((await pool.query("SELECT 1 FROM picked_dismissals WHERE content_id = 'old'")).rowCount).toBe(0);
  });

  it("rejects a missing or malformed body", async () => {
    const s = await createTestSession();
    expect((await post(s.token, { dismissedAt: ago(0) })).status).toBe(400);
    expect((await post(s.token, { contentId: "tt1", dismissedAt: "yesterday" })).status).toBe(400);
  });
});
