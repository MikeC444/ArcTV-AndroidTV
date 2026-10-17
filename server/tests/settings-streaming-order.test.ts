import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { keepServiceOrder } from "../src/services/settingsService.js";
import { createTestSession } from "./helpers/auth.js";
import { resetDatabase } from "./helpers/db.js";

describe("keepServiceOrder", () => {
  it("keeps the stored streaming entries when the incoming order has none", () => {
    expect(keepServiceOrder(["Popular_base", "Action"], ["Action", "streaming:hulu", "streaming:netflix"])).toEqual(["Popular_base", "Action", "streaming:hulu", "streaming:netflix"]);
  });
  it("leaves an order that has streaming entries as it is, even if they are fewer", () => {
    expect(keepServiceOrder(["Popular_base", "streaming:netflix"], ["streaming:hulu", "streaming:netflix"])).toEqual(["Popular_base", "streaming:netflix"]);
  });
  it("adds nothing when none is stored", () => {
    expect(keepServiceOrder(["a", "b"], ["a"])).toEqual(["a", "b"]);
  });
});

describe("PUT /user/settings and the streaming tiles' order", () => {
  let app: Express;
  beforeEach(async () => {
    app = createApp();
    await resetDatabase();
  });

  const body = (order: string[], updatedAt: string) => ({
    homeRowOrder: order,
    hiddenRowIds: [],
    autoplayNextEpisode: true,
    skipIntroEnabled: true,
    subtitlesEnabled: false,
    defaultSubtitleLanguage: null,
    updatedAt,
  });

  it("an older app that pushes the row order without the tiles' entries does not reset the tiles' order", async () => {
    const s = await createTestSession({ platform: "web" });
    const put = (order: string[], at: string) => request(app).put("/user/settings").set("Authorization", `Bearer ${s.token}`).send(body(order, at));
    expect((await put(["Popular_base", "streaming:hulu", "streaming:netflix"], "2026-10-17T10:00:00.000Z")).status).toBe(200);
    const older = await put(["Action", "Popular_base"], "2026-10-17T11:00:00.000Z");
    expect(older.status).toBe(200);
    expect(older.body.homeRowOrder).toEqual(["Action", "Popular_base", "streaming:hulu", "streaming:netflix"]);
    const got = await request(app).get("/user/settings").set("Authorization", `Bearer ${s.token}`);
    expect(got.body.homeRowOrder).toEqual(["Action", "Popular_base", "streaming:hulu", "streaming:netflix"]);
  });

  it("an app that sends tiles' entries replaces them", async () => {
    const s = await createTestSession({ platform: "web" });
    const put = (order: string[], at: string) => request(app).put("/user/settings").set("Authorization", `Bearer ${s.token}`).send(body(order, at));
    await put(["streaming:hulu", "streaming:netflix"], "2026-10-17T10:00:00.000Z");
    const next = await put(["streaming:netflix", "streaming:hulu"], "2026-10-17T11:00:00.000Z");
    expect(next.body.homeRowOrder).toEqual(["streaming:netflix", "streaming:hulu"]);
  });
});
