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

const event = (over: Record<string, unknown> = {}) => ({
  providerId: "com.linvo.cinemeta",
  contentId: "tt0111161",
  contentType: "MOVIE",
  title: "The Shawshank Redemption",
  releaseTitle: "Shawshank.2160p.DV.HEVC",
  resolution: "4K",
  codec: "HEVC",
  trigger: "error",
  outcome: "opened",
  errorMessage: "MediaCodecVideoRenderer error [ERROR_CODE_DECODING_FAILED]",
  ...over,
});

describe("POST /user/player-events/external", () => {
  it("requires sign-in", async () => {
    expect((await request(app).post("/user/player-events/external").send(event())).status).toBe(401);
  });

  it("stores the event and shows it to admins in the summary", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const res = await request(app).post("/user/player-events/external").set("Authorization", `Bearer ${admin.token}`).set("X-ArcTV-App-Version", "0.2.0").send(event());
    expect(res.status).toBe(204);
    await request(app).post("/user/player-events/external").set("Authorization", `Bearer ${admin.token}`).send(event({ trigger: "button", errorMessage: null }));
    const rows = await pool.query("SELECT launched_from AS trigger, app_version FROM external_player_events ORDER BY created_at");
    expect(rows.rows).toHaveLength(2);
    expect(rows.rows[0]).toMatchObject({ trigger: "error", app_version: "0.2.0" });
    const summary = await request(app).get("/admin/summary").set("Authorization", `Bearer ${admin.token}`);
    expect(summary.body.externalPlayer).toMatchObject({ opens7d: 2, users7d: 1, afterError7d: 1, fromButton7d: 1, noPlayer7d: 0 });
    expect(summary.body.externalPlayer.recent).toHaveLength(2);
  });

  it("records which player was chosen and counts VLC separately", async () => {
    const admin = await createTestSession({ isAdmin: true });
    const send = (over: Record<string, unknown>) => request(app).post("/user/player-events/external").set("Authorization", `Bearer ${admin.token}`).send(event(over));
    expect((await send({ engine: "vlc" })).status).toBe(204);
    expect((await send({})).status).toBe(204);
    const rows = await pool.query("SELECT engine FROM external_player_events ORDER BY created_at");
    expect(rows.rows.map((r: { engine: string }) => r.engine)).toEqual(["vlc", "external"]);
    const summary = await request(app).get("/admin/summary").set("Authorization", `Bearer ${admin.token}`);
    expect(summary.body.externalPlayer).toMatchObject({ opens7d: 1, vlc7d: 1 });
    expect((await send({ engine: "mpv" })).status).toBe(400);
  });

  it("rejects an unknown trigger", async () => {
    const s = await createTestSession();
    expect((await request(app).post("/user/player-events/external").set("Authorization", `Bearer ${s.token}`).send(event({ trigger: "x" }))).status).toBe(400);
  });
});
