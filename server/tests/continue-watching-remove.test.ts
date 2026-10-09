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

const movie = (over: Record<string, unknown> = {}) => ({ providerId: "cinemeta", contentId: "tt1", contentType: "MOVIE", title: "A Movie", positionMs: 1_500_000, durationMs: 6_000_000, completed: false, watchedAt: "2025-01-01T00:00:00.000Z", ...over });
const episode = (over: Record<string, unknown> = {}) => ({ providerId: "cinemeta", contentId: "tt2", contentType: "TV_SHOW", seasonNumber: 1, episodeNumber: 2, episodeTitle: "Two", title: "A Show", positionMs: 300_000, durationMs: 1_500_000, completed: false, watchedAt: "2025-01-01T00:00:00.000Z", ...over });
const key = (id: string, updatedAt: string) => new URLSearchParams({ providerId: "cinemeta", contentId: id, contentType: id === "tt2" ? "TV_SHOW" : "MOVIE", updatedAt }).toString();

const post = (token: string, b: Record<string, unknown>) => request(app).post("/user/watch-progress").set("Authorization", `Bearer ${token}`).send(b);
const remove = (token: string, id: string, updatedAt: string, profile?: string) => {
  const r = request(app).delete(`/user/continue-watching?${key(id, updatedAt)}`).set("Authorization", `Bearer ${token}`);
  return profile ? r.set("X-ArcTV-Profile", profile) : r;
};
const list = (token: string) => request(app).get("/user/continue-watching").set("Authorization", `Bearer ${token}`);
const history = async (userId: string, contentId: string) => (await pool.query<{ position_ms: string; completed: boolean }>("SELECT position_ms, completed FROM watch_history WHERE user_id = $1 AND content_id = $2 ORDER BY episode_key", [userId, contentId])).rows;

describe("DELETE /user/continue-watching", () => {
  it("requires sign-in", async () => {
    expect((await request(app).delete(`/user/continue-watching?${key("tt1", "2025-01-02T00:00:00.000Z")}`)).status).toBe(401);
  });

  it("takes the title out of Continue Watching without marking it watched, and forgets the position", async () => {
    const s = await createTestSession();
    await post(s.token, movie());
    expect((await list(s.token)).body.items).toHaveLength(1);
    const res = await remove(s.token, "tt1", "2025-01-02T00:00:00.000Z");
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ contentId: "tt1", positionMs: 0 });
    expect(res.body.deletedAt).not.toBeNull();
    expect((await list(s.token)).body.items).toEqual([]);
    expect(await history(s.userId, "tt1")).toEqual([{ position_ms: "0", completed: false }]); // not "watched", and starts from 0
  });

  it("watching it again starts a fresh entry from where the new playback is", async () => {
    const s = await createTestSession();
    await post(s.token, movie());
    await remove(s.token, "tt1", "2025-01-02T00:00:00.000Z");
    await post(s.token, movie({ positionMs: 90_000, watchedAt: "2025-01-03T00:00:00.000Z" }));
    expect((await list(s.token)).body.items[0]).toMatchObject({ contentId: "tt1", positionMs: 90_000 });
  });

  it("leaves finished episodes alone and resets only the unfinished ones", async () => {
    const s = await createTestSession();
    await post(s.token, episode({ episodeNumber: 1, episodeTitle: "One", completed: true, positionMs: 1_500_000, watchedAt: "2025-01-01T00:00:00.000Z" }));
    await post(s.token, episode({ watchedAt: "2025-01-01T01:00:00.000Z" }));
    expect((await remove(s.token, "tt2", "2025-01-02T00:00:00.000Z")).status).toBe(200);
    expect(await history(s.userId, "tt2")).toEqual([
      { position_ms: "1500000", completed: true },
      { position_ms: "0", completed: false },
    ]);
  });

  it("an older removal loses to a newer playback and gets the current state back", async () => {
    const s = await createTestSession();
    await post(s.token, movie({ watchedAt: "2025-01-05T00:00:00.000Z" }));
    const res = await remove(s.token, "tt1", "2025-01-02T00:00:00.000Z");
    expect(res.status).toBe(200);
    expect(res.body.deletedAt).toBeNull();
    expect(res.body.positionMs).toBe(1_500_000);
    expect((await list(s.token)).body.items).toHaveLength(1);
  });

  it("answers 204 when the account never watched it, and is per profile", async () => {
    const s = await createTestSession();
    expect((await remove(s.token, "tt9", "2025-01-02T00:00:00.000Z")).status).toBe(204);
    await post(s.token, movie());
    expect((await remove(s.token, "tt1", "2025-01-02T00:00:00.000Z", "kid")).status).toBe(404); // not one of this account's profiles
    expect((await list(s.token)).body.items).toHaveLength(1);
  });

  it("rejects a malformed request", async () => {
    const s = await createTestSession();
    expect((await request(app).delete("/user/continue-watching?contentId=tt1").set("Authorization", `Bearer ${s.token}`)).status).toBe(400);
    expect((await remove(s.token, "tt1", "yesterday")).status).toBe(400);
  });
});
