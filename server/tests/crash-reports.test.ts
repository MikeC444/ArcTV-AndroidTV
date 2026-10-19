import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { pool } from "../src/db/pool.js";
import { crashSignature } from "../src/services/crashReportService.js";
import { createTestSession } from "./helpers/auth.js";
import { resetDatabase } from "./helpers/db.js";

let app: Express;
beforeEach(async () => {
  app = createApp();
  await resetDatabase();
  await pool.query("TRUNCATE crash_reports");
});

const report = (overrides: Record<string, unknown> = {}) => ({
  platform: "fire_tv",
  appVersion: "0.7.0",
  deviceModel: "NVIDIA SHIELD Android TV",
  osVersion: "Android 11 (API 30)",
  kind: "crash",
  exception: "java.lang.IllegalArgumentException: Key tt0111161 was already used",
  stackTrace: "java.lang.IllegalArgumentException: Key tt0111161 was already used\n\tat androidx.compose.foundation.lazy.layout.X.a(X.kt:1)\n\tat com.mangotv.app.ui.components.ContentRowKt.ContentRow(ContentRow.kt:148)",
  happenedAt: new Date().toISOString(),
  ...overrides,
});

describe("POST /crash-reports", () => {
  it("keeps a report from a signed-in app with its account", async () => {
    const session = await createTestSession();
    const res = await request(app).post("/crash-reports").set("Authorization", `Bearer ${session.token}`).send(report());
    expect(res.status).toBe(204);
    const rows = await pool.query("SELECT user_id, kind, device_model, signature FROM crash_reports");
    expect(rows.rows).toEqual([expect.objectContaining({ user_id: session.userId, kind: "crash", device_model: "NVIDIA SHIELD Android TV" })]);
  });

  it("keeps a guest's report (or one with a dead token) without an account", async () => {
    expect((await request(app).post("/crash-reports").send(report())).status).toBe(204);
    expect((await request(app).post("/crash-reports").set("Authorization", "Bearer not-a-real-token").send(report())).status).toBe(204);
    const rows = await pool.query("SELECT user_id FROM crash_reports");
    expect(rows.rows).toEqual([{ user_id: null }, { user_id: null }]);
  });

  it("refuses a malformed report and cuts an overlong trace", async () => {
    expect((await request(app).post("/crash-reports").send(report({ kind: "explosion" }))).status).toBe(400);
    expect((await request(app).post("/crash-reports").send(report({ happenedAt: "yesterday" }))).status).toBe(400);
    expect((await request(app).post("/crash-reports").send(report({ stackTrace: "x".repeat(19_000) }))).status).toBe(204);
    const rows = await pool.query<{ n: number }>("SELECT length(stack_trace) AS n FROM crash_reports");
    expect(rows.rows[0]!.n).toBe(16_000);
  });
});

describe("crash signature", () => {
  it("groups the same crash whatever its message, and tells different places apart", () => {
    const a = crashSignature(report() as never);
    const b = crashSignature(report({ exception: "java.lang.IllegalArgumentException: Key tt999 was already used" }) as never);
    const c = crashSignature(report({ stackTrace: "java.lang.IllegalArgumentException\n\tat com.mangotv.app.ui.home.HeroSection.a(HeroSection.kt:9)" }) as never);
    expect(a).toBe(b);
    expect(a).not.toBe(c);
  });
});

describe("GET /admin/crashes", () => {
  it("lists crashes grouped and recent for an admin, and is a 404 for anyone else", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const user = await createTestSession();
    await request(app).post("/crash-reports").set("Authorization", `Bearer ${user.token}`).send(report());
    await request(app).post("/crash-reports").send(report({ exception: "java.lang.IllegalArgumentException: Key tt2 was already used" }));
    await request(app).post("/crash-reports").send(report({ kind: "low_memory", exception: null, stackTrace: "Killed by the system: low memory" }));

    const res = await request(app).get("/admin/crashes").set("Authorization", `Bearer ${admin.token}`);
    expect(res.status).toBe(200);
    expect(res.body.groups).toHaveLength(2);
    const keyCrash = res.body.groups.find((g: { kind: string }) => g.kind === "crash");
    expect(keyCrash).toMatchObject({ count: 2, people: 1, devices: ["NVIDIA SHIELD Android TV"], versions: ["fire_tv 0.7.0"] });
    expect(res.body.recent).toHaveLength(3);
    expect(res.body.recent.some((r: { email: string | null }) => r.email === user.email)).toBe(true);

    expect((await request(app).get("/admin/crashes").set("Authorization", `Bearer ${user.token}`)).status).toBe(404);
  });
});
