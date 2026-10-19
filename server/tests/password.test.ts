import { randomUUID } from "node:crypto";
import type { Express } from "express";
import request from "supertest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp } from "../src/app.js";
import { pool } from "../src/db/pool.js";
import { setMailFetch } from "../src/services/email.js";
import { resetDatabase } from "./helpers/db.js";

let app: Express;
let sent: Array<{ from: string; to: string[]; subject: string; text: string; html: string }>;

beforeEach(async () => {
  app = createApp();
  await resetDatabase();
  sent = [];
  process.env.RESEND_API_KEY = "re_test_key";
  setMailFetch(async (_url, init) => {
    sent.push(JSON.parse(init.body) as { from: string; to: string[]; subject: string; text: string; html: string });
    return { ok: true, status: 200, text: async () => "{}" };
  });
});

afterEach(() => {
  delete process.env.RESEND_API_KEY;
  setMailFetch(null);
});

async function register(email: string, password: string) {
  const res = await request(app).post("/auth/register").send({ displayName: "Tester", email, password, deviceId: randomUUID() });
  expect(res.status).toBe(201);
  return res.body as { accessToken: string; refreshToken: string };
}

const login = (email: string, password: string) => request(app).post("/auth/login").send({ email, password, deviceId: randomUUID() });
const me = (token: string) => request(app).get("/user/me").set("Authorization", `Bearer ${token}`);
const tokenFrom = (text: string) => decodeURIComponent(/#token=([^\s]+)/.exec(text)![1]!);

describe("POST /auth/password/change", () => {
  it("changes the password, keeps this device signed in and signs every other one out", async () => {
    const here = await register("pat@example.com", "old-password-1");
    const other = (await login("pat@example.com", "old-password-1")).body as { accessToken: string };
    const res = await request(app).post("/auth/password/change").set("Authorization", `Bearer ${here.accessToken}`).send({ currentPassword: "old-password-1", newPassword: "new-password-2" });
    expect(res.status).toBe(204);
    expect((await me(here.accessToken)).status).toBe(200);
    expect((await me(other.accessToken)).status).toBe(401);
    expect((await login("pat@example.com", "old-password-1")).status).toBe(401);
    expect((await login("pat@example.com", "new-password-2")).status).toBe(200);
  });

  it("refuses a wrong current password with 403 (not 401, which would read as signed out) and changes nothing", async () => {
    const here = await register("quinn@example.com", "old-password-1");
    const res = await request(app).post("/auth/password/change").set("Authorization", `Bearer ${here.accessToken}`).send({ currentPassword: "not-it-at-all", newPassword: "new-password-2" });
    expect(res.status).toBe(403);
    expect((await login("quinn@example.com", "old-password-1")).status).toBe(200);
  });

  it("refuses the same password, a short one, and a request with no session", async () => {
    const here = await register("rae@example.com", "old-password-1");
    const auth = { Authorization: `Bearer ${here.accessToken}` };
    expect((await request(app).post("/auth/password/change").set(auth).send({ currentPassword: "old-password-1", newPassword: "old-password-1" })).status).toBe(400);
    expect((await request(app).post("/auth/password/change").set(auth).send({ currentPassword: "old-password-1", newPassword: "short" })).status).toBe(400);
    expect((await request(app).post("/auth/password/change").send({ currentPassword: "old-password-1", newPassword: "new-password-2" })).status).toBe(401);
  });
});

describe("forgot password", () => {
  it("is 503 until email is set up", async () => {
    delete process.env.RESEND_API_KEY;
    expect((await request(app).post("/auth/password/forgot").send({ email: "x@example.com" })).status).toBe(503);
  });

  it("answers the same for an address with no account, and sends nothing", async () => {
    const res = await request(app).post("/auth/password/forgot").send({ email: "nobody@example.com" });
    expect(res.status).toBe(204);
    expect(sent).toHaveLength(0);
  });

  it("emails a one-time link that sets a new password and signs every device out", async () => {
    const session = await register("sam@example.com", "old-password-1");
    expect((await request(app).post("/auth/password/forgot").send({ email: "SAM@example.com" })).status).toBe(204);
    await vi.waitFor(() => expect(sent).toHaveLength(1));
    expect(sent[0]!.to).toEqual(["sam@example.com"]);
    expect(sent[0]!.from).toBe("Arc TV <no-reply@arctv.org>");
    expect(sent[0]!.subject).toBe("Reset your Arc TV password");
    expect(sent[0]!.text).toContain("https://web.arctv.org/reset-password#token=");
    const token = tokenFrom(sent[0]!.text);
    // the branded HTML carries the same link on its button, and the logo
    expect(sent[0]!.html).toContain(`href="https://web.arctv.org/reset-password#token=${encodeURIComponent(token)}"`);
    expect(sent[0]!.html).toContain('src="https://arctv.org/arctv-logo.png"');

    expect((await request(app).post("/auth/password/reset").send({ token, newPassword: "new-password-2" })).status).toBe(204);
    expect((await me(session.accessToken)).status).toBe(401);
    expect((await request(app).post("/auth/refresh").send({ refreshToken: session.refreshToken })).status).toBe(401);
    expect((await login("sam@example.com", "old-password-1")).status).toBe(401);
    expect((await login("sam@example.com", "new-password-2")).status).toBe(200);
    // the link works once
    expect((await request(app).post("/auth/password/reset").send({ token, newPassword: "third-password-3" })).status).toBe(400);
  });

  it("refuses an expired link and a made-up one", async () => {
    await register("tia@example.com", "old-password-1");
    await request(app).post("/auth/password/forgot").send({ email: "tia@example.com" });
    await vi.waitFor(() => expect(sent).toHaveLength(1));
    await pool.query("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute'");
    expect((await request(app).post("/auth/password/reset").send({ token: tokenFrom(sent[0]!.text), newPassword: "new-password-2" })).status).toBe(400);
    expect((await request(app).post("/auth/password/reset").send({ token: "x".repeat(43), newPassword: "new-password-2" })).status).toBe(400);
    expect((await login("tia@example.com", "old-password-1")).status).toBe(200);
  });

  it("sends at most one email a minute per account, and a newer link replaces the older one", async () => {
    await register("uma@example.com", "old-password-1");
    await request(app).post("/auth/password/forgot").send({ email: "uma@example.com" });
    await request(app).post("/auth/password/forgot").send({ email: "uma@example.com" });
    await vi.waitFor(() => expect(sent).toHaveLength(1));
    const first = tokenFrom(sent[0]!.text);

    await pool.query("UPDATE password_reset_tokens SET created_at = now() - interval '2 minutes'");
    await request(app).post("/auth/password/forgot").send({ email: "uma@example.com" });
    await vi.waitFor(() => expect(sent).toHaveLength(2));
    expect((await request(app).post("/auth/password/reset").send({ token: first, newPassword: "new-password-2" })).status).toBe(400);
    expect((await request(app).post("/auth/password/reset").send({ token: tokenFrom(sent[1]!.text), newPassword: "new-password-2" })).status).toBe(204);
  });

  it("sends nothing to a deleted account", async () => {
    await register("val@example.com", "old-password-1");
    await pool.query("UPDATE users SET deleted_at = now() WHERE email = 'val@example.com'");
    expect((await request(app).post("/auth/password/forgot").send({ email: "val@example.com" })).status).toBe(204);
    expect(sent).toHaveLength(0);
  });
});

describe("POST /auth/password/change-link", () => {
  it("needs a signed-in session", async () => {
    expect((await request(app).post("/auth/password/change-link")).status).toBe(401);
  });

  it("gives a one-time link that sets a new password for exactly the signed-in account and signs every device out", async () => {
    const other = await register("zed@example.com", "other-password-1");
    const session = await register("yan@example.com", "old-password-1");
    const res = await request(app).post("/auth/password/change-link").set("Authorization", `Bearer ${session.accessToken}`);
    expect(res.status).toBe(200);
    expect(res.body.url).toContain("https://web.arctv.org/reset-password#token=");
    expect(new Date(res.body.expiresAt).getTime()).toBeGreaterThan(Date.now() + 14 * 60 * 1000);
    expect(new Date(res.body.expiresAt).getTime()).toBeLessThanOrEqual(Date.now() + 15 * 60 * 1000);
    const token = tokenFrom(res.body.url);

    expect((await request(app).post("/auth/password/reset").send({ token, newPassword: "new-password-2" })).status).toBe(204);
    expect((await login("yan@example.com", "new-password-2")).status).toBe(200);
    expect((await login("yan@example.com", "old-password-1")).status).toBe(401);
    expect((await me(session.accessToken)).status).toBe(401);
    // another account is untouched
    expect((await me(other.accessToken)).status).toBe(200);
    // it works once
    expect((await request(app).post("/auth/password/reset").send({ token, newPassword: "third-password-3" })).status).toBe(400);
  });

  it("expires after 15 minutes, and a newer link replaces the older one", async () => {
    const session = await register("xia@example.com", "old-password-1");
    const ask = () => request(app).post("/auth/password/change-link").set("Authorization", `Bearer ${session.accessToken}`);
    const first = tokenFrom((await ask()).body.url);
    const second = tokenFrom((await ask()).body.url);
    expect((await request(app).post("/auth/password/reset").send({ token: first, newPassword: "new-password-2" })).status).toBe(400);
    await pool.query("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute'");
    expect((await request(app).post("/auth/password/reset").send({ token: second, newPassword: "new-password-2" })).status).toBe(400);
  });

  it("does not use up the account's reset emails", async () => {
    const session = await register("wes@example.com", "old-password-1");
    for (let i = 0; i < 5; i++) await request(app).post("/auth/password/change-link").set("Authorization", `Bearer ${session.accessToken}`);
    await pool.query("UPDATE password_reset_tokens SET created_at = now() - interval '2 minutes'");
    expect((await request(app).post("/auth/password/forgot").send({ email: "wes@example.com" })).status).toBe(204);
    await vi.waitFor(() => expect(sent).toHaveLength(1));
  });
});
