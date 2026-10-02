import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { resetDatabase } from "./helpers/db.js";
import { createTestSession } from "./helpers/auth.js";

let app: Express;

beforeEach(async () => {
  app = createApp();
  await resetDatabase();
});

const body = (over: Record<string, unknown> = {}) => ({
  profileId: "main",
  providerId: "com.linvo.cinemeta",
  contentId: "tt0111161",
  contentType: "MOVIE",
  title: "The Shawshank Redemption",
  feedback: "like",
  updatedAt: "2025-01-01T00:00:00.000Z",
  ...over,
});
const clearQuery = (updatedAt: string, profileId = "main") =>
  new URLSearchParams({ profileId, providerId: "com.linvo.cinemeta", contentId: "tt0111161", contentType: "MOVIE", updatedAt }).toString();

const post = (token: string, b: Record<string, unknown>) => request(app).post("/user/feedback").set("Authorization", `Bearer ${token}`).send(b);
const list = (token: string, profileId = "main") => request(app).get(`/user/feedback?profileId=${profileId}`).set("Authorization", `Bearer ${token}`);
const clear = (token: string, updatedAt: string, profileId = "main") =>
  request(app).delete(`/user/feedback?${clearQuery(updatedAt, profileId)}`).set("Authorization", `Bearer ${token}`);

describe("/user/feedback", () => {
  it("requires sign-in", async () => {
    expect((await request(app).get("/user/feedback")).status).toBe(401);
    expect((await request(app).post("/user/feedback").send(body())).status).toBe(401);
    expect((await request(app).delete(`/user/feedback?${clearQuery("2025-01-01T00:00:00.000Z")}`)).status).toBe(401);
  });

  it("starts empty, stores a Like and returns it from a list on another device", async () => {
    const s = await createTestSession();
    expect((await list(s.token)).body.items).toEqual([]);
    const created = await post(s.token, body());
    expect(created.status).toBe(200);
    expect(created.body).toMatchObject({ contentId: "tt0111161", feedback: "like", profileId: "main", deletedAt: null });
    const seen = await list(s.token);
    expect(seen.body.items).toHaveLength(1);
    expect(seen.body.items[0]).toMatchObject({ contentId: "tt0111161", feedback: "like" });
  });

  it("a newer write replaces the feedback; an older one loses and gets the current state back", async () => {
    const s = await createTestSession();
    await post(s.token, body({ feedback: "like", updatedAt: "2025-01-02T00:00:00.000Z" }));
    const newer = await post(s.token, body({ feedback: "dislike", updatedAt: "2025-01-03T00:00:00.000Z" }));
    expect(newer.body.feedback).toBe("dislike");
    const stale = await post(s.token, body({ feedback: "like", updatedAt: "2025-01-01T00:00:00.000Z" }));
    expect(stale.body.feedback).toBe("dislike");
    expect((await list(s.token)).body.items[0].feedback).toBe("dislike");
  });

  it("clearing removes it from the list, an older clear loses, and re-giving feedback after a clear works", async () => {
    const s = await createTestSession();
    await post(s.token, body({ updatedAt: "2025-01-02T00:00:00.000Z" }));
    const lost = await clear(s.token, "2025-01-01T00:00:00.000Z");
    expect(lost.body.deletedAt).toBeNull();
    expect((await list(s.token)).body.items).toHaveLength(1);
    const cleared = await clear(s.token, "2025-01-03T00:00:00.000Z");
    expect(cleared.status).toBe(200);
    expect(cleared.body.deletedAt).not.toBeNull();
    expect((await list(s.token)).body.items).toEqual([]);
    const again = await post(s.token, body({ feedback: "dislike", updatedAt: "2025-01-04T00:00:00.000Z" }));
    expect(again.body).toMatchObject({ feedback: "dislike", deletedAt: null });
    expect((await list(s.token)).body.items).toHaveLength(1);
  });

  it("clearing something that never existed is a 204", async () => {
    const s = await createTestSession();
    expect((await clear(s.token, "2025-01-03T00:00:00.000Z")).status).toBe(204);
  });

  it("keeps profiles apart and never shows one account's feedback to another", async () => {
    const a = await createTestSession();
    const b = await createTestSession();
    await post(a.token, body({ profileId: "main", feedback: "like" }));
    await post(a.token, body({ profileId: "kids", feedback: "dislike" }));
    expect((await list(a.token, "main")).body.items.map((i: { feedback: string }) => i.feedback)).toEqual(["like"]);
    expect((await list(a.token, "kids")).body.items.map((i: { feedback: string }) => i.feedback)).toEqual(["dislike"]);
    expect((await list(b.token, "main")).body.items).toEqual([]);
    await clear(b.token, "2025-02-01T00:00:00.000Z");
    expect((await list(a.token, "main")).body.items).toHaveLength(1);
  });

  it("defaults the profile to main and rejects bad input", async () => {
    const s = await createTestSession();
    const { profileId: _p, ...noProfile } = body();
    expect((await post(s.token, noProfile)).body.profileId).toBe("main");
    expect((await post(s.token, body({ feedback: "love" }))).status).toBe(400);
    expect((await post(s.token, body({ updatedAt: "yesterday" }))).status).toBe(400);
    expect((await post(s.token, body({ contentId: "" }))).status).toBe(400);
  });
});
