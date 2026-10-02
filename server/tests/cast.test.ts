import type { Express } from "express";
import request from "supertest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp } from "../src/app.js";
import { resetDatabase } from "./helpers/db.js";
import { createTestSession } from "./helpers/auth.js";

let app: Express;
const fetchMock = vi.fn();

// castService's in-memory cache is a module-level Map that persists across it() blocks, so every test uses its own IMDb id.
beforeEach(async () => {
  app = createApp();
  await resetDatabase();
  vi.stubGlobal("fetch", fetchMock);
  process.env.TMDB_READ_ACCESS_TOKEN = "test-token";
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
  delete process.env.TMDB_READ_ACCESS_TOKEN;
});

function tmdbResponse(body: unknown, ok = true) {
  return { ok, json: async () => body };
}

describe("GET /user/cast", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).get("/user/cast").query({ imdbId: "tt1000001", type: "MOVIE" });
    expect(response.status).toBe(401);
  });

  it("rejects an id that is not an IMDb id, so nothing else can reach TMDB's path", async () => {
    const session = await createTestSession();
    for (const imdbId of ["1000001", "tt1000001/../x", "tt", "ttabc"]) {
      const response = await request(app).get("/user/cast").set("Authorization", `Bearer ${session.token}`).query({ imdbId, type: "MOVIE" });
      expect(response.status).toBe(400);
    }
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("returns an empty list without calling TMDB when no token is configured", async () => {
    delete process.env.TMDB_READ_ACCESS_TOKEN;
    const session = await createTestSession();
    const response = await request(app).get("/user/cast").set("Authorization", `Bearer ${session.token}`).query({ imdbId: "tt1000002", type: "MOVIE" });
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ cast: [] });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("returns the movie's cast with photos and characters", async () => {
    fetchMock
      .mockResolvedValueOnce(tmdbResponse({ movie_results: [{ id: 42 }] }))
      .mockResolvedValueOnce(
        tmdbResponse({
          cast: [
            { name: "Ana", character: "Neo", profile_path: "/ana.jpg" },
            { name: "Bo", character: "", profile_path: null },
            { name: "  " },
          ],
        }),
      );
    const session = await createTestSession();
    const response = await request(app).get("/user/cast").set("Authorization", `Bearer ${session.token}`).query({ imdbId: "tt1000003", type: "MOVIE" });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      cast: [
        { name: "Ana", character: "Neo", photo: "https://image.tmdb.org/t/p/w185/ana.jpg" },
        { name: "Bo", character: null, photo: null },
      ],
    });
    expect(String(fetchMock.mock.calls[0]![0])).toContain("/find/tt1000003");
    expect(String(fetchMock.mock.calls[1]![0])).toContain("/movie/42/credits");
  });

  it("uses the TV endpoints for a show", async () => {
    fetchMock
      .mockResolvedValueOnce(tmdbResponse({ tv_results: [{ id: 7 }], movie_results: [{ id: 99 }] }))
      .mockResolvedValueOnce(tmdbResponse({ cast: [{ name: "Cy", character: "Bob", profile_path: "/cy.jpg" }] }));
    const session = await createTestSession();
    const response = await request(app).get("/user/cast").set("Authorization", `Bearer ${session.token}`).query({ imdbId: "tt1000004", type: "TV_SHOW" });

    expect(response.body.cast).toHaveLength(1);
    expect(String(fetchMock.mock.calls[1]![0])).toContain("/tv/7/credits");
  });

  it("returns an empty list when TMDB has no match", async () => {
    fetchMock.mockResolvedValueOnce(tmdbResponse({ movie_results: [], tv_results: [] }));
    const session = await createTestSession();
    const response = await request(app).get("/user/cast").set("Authorization", `Bearer ${session.token}`).query({ imdbId: "tt1000005", type: "MOVIE" });
    expect(response.body).toEqual({ cast: [] });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("caches a result so a second lookup doesn't call TMDB again", async () => {
    fetchMock
      .mockResolvedValueOnce(tmdbResponse({ movie_results: [{ id: 5 }] }))
      .mockResolvedValueOnce(tmdbResponse({ cast: [{ name: "Di", character: "X", profile_path: null }] }));
    const session = await createTestSession();
    const query = { imdbId: "tt1000006", type: "MOVIE" };

    const first = await request(app).get("/user/cast").set("Authorization", `Bearer ${session.token}`).query(query);
    const second = await request(app).get("/user/cast").set("Authorization", `Bearer ${session.token}`).query(query);
    expect(second.body).toEqual(first.body);
    expect(fetchMock).toHaveBeenCalledTimes(2); // find + credits once, not twice
  });

  it("degrades to an empty list instead of throwing when TMDB itself errors", async () => {
    fetchMock.mockResolvedValueOnce({ ok: false, json: async () => ({}) });
    const session = await createTestSession();
    const response = await request(app).get("/user/cast").set("Authorization", `Bearer ${session.token}`).query({ imdbId: "tt1000007", type: "MOVIE" });
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ cast: [] });
  });
});
