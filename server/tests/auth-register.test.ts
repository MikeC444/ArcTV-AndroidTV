import { randomUUID } from "node:crypto";
import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { pool } from "../src/db/pool.js";
import { resetDatabase } from "./helpers/db.js";

// Fresh app per test — see tests/me.test.ts's comment on why (the auth
// rate limiter's counters must not leak between tests).
let app: Express;

beforeEach(async () => {
  app = createApp();
  await resetDatabase();
});

describe("POST /auth/register", () => {
  it("creates an account and returns usable tokens", async () => {
    const deviceId = randomUUID();
    const response = await request(app)
      .post("/auth/register")
      .send({ displayName: "Tester", email: "Alice@Example.com", password: "correct-horse-battery", deviceId, deviceName: "Living Room" });

    expect(response.status).toBe(201);
    expect(response.body.user.email).toBe("alice@example.com"); // normalized to lowercase
    expect(response.body.accessToken).toBeTruthy();
    expect(response.body.refreshToken).toBeTruthy();
    // Different secrets, not a mistaken copy of the same value.
    expect(response.body.accessToken).not.toBe(response.body.refreshToken);

    const me = await request(app).get("/user/me").set("Authorization", `Bearer ${response.body.accessToken}`);
    expect(me.status).toBe(200);
    expect(me.body.id).toBe(response.body.user.id);
  });

  it("never stores the plaintext password", async () => {
    const response = await request(app)
      .post("/auth/register")
      .send({ displayName: "Tester", email: "bob@example.com", password: "correct-horse-battery", deviceId: randomUUID() });
    expect(response.status).toBe(201);

    const row = await pool.query("SELECT password_hash FROM users WHERE id = $1", [response.body.user.id]);
    const hash: string = row.rows[0].password_hash;
    expect(hash).not.toBe("correct-horse-battery");
    expect(hash.startsWith("$argon2id$")).toBe(true);
  });

  it("rejects a duplicate email with 409, without ever touching the first account", async () => {
    const deviceId = randomUUID();
    await request(app).post("/auth/register").send({ displayName: "Tester", email: "dup@example.com", password: "correct-horse-battery", deviceId });

    const second = await request(app)
      .post("/auth/register")
      .send({ displayName: "Tester", email: "dup@example.com", password: "a-different-password", deviceId: randomUUID() });

    expect(second.status).toBe(409);

    const count = await pool.query("SELECT count(*)::int AS n FROM users WHERE email = $1", ["dup@example.com"]);
    expect(count.rows[0].n).toBe(1);
  });

  it("rejects a password shorter than 8 characters", async () => {
    const response = await request(app)
      .post("/auth/register")
      .send({ displayName: "Tester", email: "short@example.com", password: "short", deviceId: randomUUID() });
    expect(response.status).toBe(400);
  });

  it("rejects an invalid email", async () => {
    const response = await request(app)
      .post("/auth/register")
      .send({ displayName: "Tester", email: "not-an-email", password: "correct-horse-battery", deviceId: randomUUID() });
    expect(response.status).toBe(400);
  });

  it("rejects a non-UUID deviceId", async () => {
    const response = await request(app)
      .post("/auth/register")
      .send({ displayName: "Tester", email: "device@example.com", password: "correct-horse-battery", deviceId: "not-a-uuid" });
    expect(response.status).toBe(400);
  });
});

describe("the display name on sign-up", () => {
  const register = (over: Record<string, unknown>) =>
    request(app).post("/auth/register").send({ email: "name@example.com", password: "correct-horse-battery", deviceId: randomUUID(), ...over });

  it("when sent must be a real name: empty and blank are refused with a plain message", async () => {
    for (const over of [{ displayName: "" }, { displayName: "   " }]) {
      const response = await register(over);
      expect(response.status).toBe(400);
      expect(JSON.stringify(response.body)).toContain("display name");
    }
    expect((await pool.query("SELECT 1 FROM users WHERE email = 'name@example.com'")).rowCount).toBe(0);
  });

  it("may be left out by an older app (the phone, Mac and Windows apps once had it optional): the name is then taken from the email", async () => {
    const response = await register({ email: "alex.smith@example.com" });
    expect(response.status).toBe(201);
    expect(response.body.user.displayName).toBe("Alex");
    const profiles = await request(app).get("/user/profiles").set("Authorization", `Bearer ${response.body.accessToken}`);
    expect(profiles.body.profiles[0]).toMatchObject({ name: "Alex", isDefault: true });
  });

  it("is at most 24 characters, the length of a profile name, so the two are always the same", async () => {
    expect((await register({ displayName: "x".repeat(25) })).status).toBe(400);
    expect((await register({ displayName: "x".repeat(24) })).status).toBe(201);
  });

  it("is kept (trimmed) on the account and becomes its default profile", async () => {
    const response = await register({ displayName: "  Alex  " });
    expect(response.status).toBe(201);
    expect(response.body.user.displayName).toBe("Alex");
    const profiles = await request(app).get("/user/profiles").set("Authorization", `Bearer ${response.body.accessToken}`);
    expect(profiles.status).toBe(200);
    expect(profiles.body.profiles).toHaveLength(1);
    expect(profiles.body.profiles[0]).toMatchObject({ name: "Alex", isDefault: true });
  });
});
