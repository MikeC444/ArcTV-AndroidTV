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

const ack = (token: string, body: unknown = { feature: "torrent_intro" }) =>
  request(app).post("/user/feature-intros/ack").set("Authorization", `Bearer ${token}`).set("X-ArcTV-App-Version", "0.3.0").send(body as object);

describe("POST /user/feature-intros/ack", () => {
  it("requires sign-in", async () => {
    expect((await request(app).post("/user/feature-intros/ack").send({ feature: "torrent_intro" })).status).toBe(401);
  });

  it("rejects an unknown feature", async () => {
    const s = await createTestSession();
    expect((await ack(s.token, { feature: "nope" })).status).toBe(400);
    expect((await ack(s.token, {})).status).toBe(400);
  });

  it("records the first click only", async () => {
    const s = await createTestSession();
    expect((await ack(s.token)).status).toBe(204);
    const first = await pool.query("SELECT acknowledged_at, app_version FROM feature_intro_acks");
    expect(first.rows).toHaveLength(1);
    expect(first.rows[0].app_version).toBe("0.3.0");
    expect((await ack(s.token)).status).toBe(204);
    const again = await pool.query("SELECT acknowledged_at FROM feature_intro_acks");
    expect(again.rows).toHaveLength(1);
    expect(again.rows[0].acknowledged_at).toEqual(first.rows[0].acknowledged_at);
  });
});

describe("GET /admin/feature-intros/:feature", () => {
  it("is hidden from non-admins like any admin path", async () => {
    const s = await createTestSession();
    expect((await request(app).get("/admin/feature-intros/torrent_intro").set("Authorization", `Bearer ${s.token}`)).status).toBe(404);
  });

  it("lists who clicked it, newest first, with a total", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const a = await createTestSession();
    const b = await createTestSession();
    await createTestSession(); // never clicked
    await ack(a.token);
    await new Promise((r) => setTimeout(r, 20));
    await ack(b.token);
    const res = await request(app).get("/admin/feature-intros/torrent_intro").set("Authorization", `Bearer ${admin.token}`);
    expect(res.status).toBe(200);
    expect(res.body.total).toBe(2);
    expect(res.body.users.map((u: { email: string }) => u.email)).toEqual([b.email, a.email]);
    expect(res.body.users[0]).toMatchObject({ appVersion: "0.3.0" });
    expect(res.body.users[0].acknowledgedAt).toBeTruthy();
    expect(JSON.stringify(res.body)).not.toMatch(/password|token|hash/i);
    const page = await request(app).get("/admin/feature-intros/torrent_intro?limit=1&offset=1").set("Authorization", `Bearer ${admin.token}`);
    expect(page.body.total).toBe(2);
    expect(page.body.users).toHaveLength(1);
    expect(page.body.users[0].email).toBe(a.email);
  });

  it("rejects an unknown feature", async () => {
    const admin = await createTestSession({ isAdmin: true });
    expect((await request(app).get("/admin/feature-intros/other").set("Authorization", `Bearer ${admin.token}`)).status).toBe(400);
  });
});
