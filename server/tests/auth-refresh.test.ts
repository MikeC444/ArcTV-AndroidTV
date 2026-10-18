import { randomUUID } from "node:crypto";
import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { resetDatabase } from "./helpers/db.js";
import { pool } from "../src/db/pool.js";
import { createTestSession } from "./helpers/auth.js";

// Fresh app per test — see tests/me.test.ts's comment on why (the auth
// rate limiter's counters must not leak between tests).
let app: Express;

beforeEach(async () => {
  app = createApp();
  await resetDatabase();
});

describe("POST /auth/refresh", () => {
  it("rotates both tokens and the new access token works", async () => {
    const registered = await request(app)
      .post("/auth/register")
      .send({ displayName: "Tester", email: "gina@example.com", password: "correct-horse-battery", deviceId: randomUUID() });

    const refreshed = await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });

    expect(refreshed.status).toBe(200);
    expect(refreshed.body.accessToken).not.toBe(registered.body.accessToken);
    expect(refreshed.body.refreshToken).not.toBe(registered.body.refreshToken);

    const me = await request(app).get("/user/me").set("Authorization", `Bearer ${refreshed.body.accessToken}`);
    expect(me.status).toBe(200);
  });

  it("invalidates the previous access token immediately, not just at its own natural expiry", async () => {
    const registered = await request(app)
      .post("/auth/register")
      .send({ displayName: "Tester", email: "irene@example.com", password: "correct-horse-battery", deviceId: randomUUID() });

    await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });

    const meWithOldToken = await request(app)
      .get("/user/me")
      .set("Authorization", `Bearer ${registered.body.accessToken}`);
    expect(meWithOldToken.status).toBe(401);
  });

  it("rejects reuse of a refresh token that was replaced two rotations ago — replay prevention", async () => {
    const registered = await request(app)
      .post("/auth/register")
      .send({ displayName: "Tester", email: "harry@example.com", password: "correct-horse-battery", deviceId: randomUUID() });

    const first = await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });
    await request(app).post("/auth/refresh").send({ refreshToken: first.body.refreshToken });
    const replay = await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });

    expect(replay.status).toBe(401);
  });

  it("answers one retry with the token just replaced (the reply may have been lost), and keeps the token it issued before working", async () => {
    const registered = await request(app)
      .post("/auth/register")
      .send({ displayName: "Tester", email: "olga@example.com", password: "correct-horse-battery", deviceId: randomUUID() });

    const first = await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });
    const retry = await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });
    expect(retry.status).toBe(200);

    // The app may have received the first reply after all (another tab, a late response): that token still works once.
    const late = await request(app).post("/auth/refresh").send({ refreshToken: first.body.refreshToken });
    expect(late.status).toBe(200);

    const me = await request(app).get("/user/me").set("Authorization", `Bearer ${late.body.accessToken}`);
    expect(me.status).toBe(200);
  });

  it("rejects the token just replaced once the grace period is over", async () => {
    const registered = await request(app)
      .post("/auth/register")
      .send({ displayName: "Tester", email: "paul@example.com", password: "correct-horse-battery", deviceId: randomUUID() });

    await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });
    await pool.query("UPDATE sessions SET prev_refresh_expires_at = now() - interval '1 second'");
    const late = await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });

    expect(late.status).toBe(401);
  });

  it("does not let the token just replaced revive a revoked session", async () => {
    const registered = await request(app)
      .post("/auth/register")
      .send({ displayName: "Tester", email: "quinn@example.com", password: "correct-horse-battery", deviceId: randomUUID() });

    const first = await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });
    await request(app).post("/auth/logout").set("Authorization", `Bearer ${first.body.accessToken}`);
    const retry = await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });

    expect(retry.status).toBe(401);
  });

  it("rejects an unknown/garbage refresh token", async () => {
    const response = await request(app).post("/auth/refresh").send({ refreshToken: "not-a-real-token" });
    expect(response.status).toBe(401);
  });

  it("rejects an expired refresh token", async () => {
    const session = await createTestSession({ refreshExpiresInMs: -1000 });
    const response = await request(app).post("/auth/refresh").send({ refreshToken: session.refreshToken });
    expect(response.status).toBe(401);
  });

  it("rejects a revoked session's refresh token", async () => {
    const session = await createTestSession({ revoked: true });
    const response = await request(app).post("/auth/refresh").send({ refreshToken: session.refreshToken });
    expect(response.status).toBe(401);
  });

  it("rejects a refresh token whose device has been remotely revoked", async () => {
    const session = await createTestSession({ deviceRevoked: true });
    const response = await request(app).post("/auth/refresh").send({ refreshToken: session.refreshToken });
    expect(response.status).toBe(401);
  });
});
