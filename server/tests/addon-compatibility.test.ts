import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { STREAMING_CATALOGS_MANIFEST_URL } from "../src/data/streamingCatalogsManifest.js";
import { addonSupportedBy, compareVersions } from "../src/services/addonCompatibility.js";
import { createTestSession } from "./helpers/auth.js";
import { resetDatabase } from "./helpers/db.js";

let app: Express;
beforeEach(async () => {
  app = createApp();
  await resetDatabase();
});

const STREAMING = "pw.ers.netflix-catalog";

describe("compareVersions", () => {
  it("compares dotted versions by number", () => {
    expect(compareVersions("0.6.1", "0.6.1")).toBe(0);
    expect(compareVersions("0.6.0", "0.6.1")).toBe(-1);
    expect(compareVersions("0.10.0", "0.6.1")).toBe(1);
    expect(compareVersions("1", "0.9.9")).toBe(1);
    expect(compareVersions("0.6", "0.6.0")).toBe(0);
  });
});

describe("addonSupportedBy", () => {
  it("lists an addon the table does not mention to everyone", () => {
    expect(addonSupportedBy("com.linvo.cinemeta", null, null)).toBe(true);
    expect(addonSupportedBy("com.linvo.cinemeta", "fire_tv", "0.1.0")).toBe(true);
  });

  it("lists a catalogue-only addon to the web app, and to the Fire TV from 0.7.0 (the first version with its Home tiles)", () => {
    expect(addonSupportedBy(STREAMING, "web", null)).toBe(true);
    expect(addonSupportedBy(STREAMING, "fire_tv", "0.7.0")).toBe(true);
    expect(addonSupportedBy(STREAMING, "fire_tv", "0.7.1")).toBe(true);
    expect(addonSupportedBy(STREAMING, "fire_tv", "0.6.1")).toBe(false);
    expect(addonSupportedBy(STREAMING, "fire_tv", "0.6.0")).toBe(false);
    expect(addonSupportedBy(STREAMING, "fire_tv", null)).toBe(false);
  });

  it("does not list it to an app that is not named (until it ships support)", () => {
    expect(addonSupportedBy(STREAMING, "android", "9.9.9")).toBe(false);
    expect(addonSupportedBy(STREAMING, "windows", "1.0.0")).toBe(false);
    expect(addonSupportedBy(STREAMING, null, null)).toBe(false);
  });
});

describe("GET /user/addons keeps the Streaming Catalogs addon from apps that are behind", () => {
  const urls = async (token: string, headers: Record<string, string> = {}) =>
    ((await request(app).get("/user/addons").set({ Authorization: `Bearer ${token}`, ...headers })).body.items as Array<{ manifestUrl: string }>).map((a) => a.manifestUrl);

  it("sends it to the web app", async () => {
    const s = await createTestSession({ platform: "web" });
    expect(await urls(s.token)).toContain(STREAMING_CATALOGS_MANIFEST_URL);
  });

  it("does not send it to a Fire TV on 0.6.0, but sends the other addons", async () => {
    const s = await createTestSession({ platform: "fire_tv", appVersion: "0.6.0" });
    const list = await urls(s.token);
    expect(list).not.toContain(STREAMING_CATALOGS_MANIFEST_URL);
    expect(list).toContain("https://v3-cinemeta.strem.io/manifest.json");
  });

  it("sends it to a Fire TV that says it is on 0.7.0 (the version it sends now wins over the one on record), and not to one that is still on 0.6.1", async () => {
    const s = await createTestSession({ platform: "fire_tv", appVersion: "0.6.0" });
    expect(await urls(s.token, { "X-ArcTV-App-Version": "0.7.0" })).toContain(STREAMING_CATALOGS_MANIFEST_URL);
    expect(await urls(s.token, { "X-ArcTV-App-Version": "0.6.1" })).not.toContain(STREAMING_CATALOGS_MANIFEST_URL);
  });

  it("does not send it to a Fire TV (the default platform) that never said which version it is", async () => {
    const s = await createTestSession();
    expect(await urls(s.token)).not.toContain(STREAMING_CATALOGS_MANIFEST_URL);
  });
});
