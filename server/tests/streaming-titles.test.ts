import type { Express } from "express";
import request from "supertest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp } from "../src/app.js";
import { resetDatabase } from "./helpers/db.js";
import { createTestSession } from "./helpers/auth.js";

let app: Express;
const fetchMock = vi.fn();

// the service's in-memory caches are module-level and survive between it() blocks, so each test uses its own service / page / TMDB ids
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

const tmdb = (body: unknown, ok = true) => ({ ok, json: async () => body });
const get = (token: string, query: Record<string, string>) => request(app).get("/user/streaming-titles").set("Authorization", `Bearer ${token}`).query(query);

describe("GET /user/streaming-titles", () => {
  it("rejects a request with no Authorization header", async () => {
    expect((await request(app).get("/user/streaming-titles").query({ service: "netflix", type: "MOVIE" })).status).toBe(401);
  });

  it("rejects a service, type, page or region that could not be right, without calling TMDB", async () => {
    const { token } = await createTestSession();
    for (const query of [
      { service: "net flix", type: "MOVIE" },
      { service: "netflix/../x", type: "MOVIE" },
      { service: "netflix", type: "MUSIC" },
      { service: "netflix", type: "MOVIE", page: "0" },
      { service: "netflix", type: "MOVIE", page: "501" },
      { service: "netflix", type: "MOVIE", region: "GBR" },
    ]) {
      expect((await get(token, query)).status).toBe(400);
    }
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("says it is not available, without calling TMDB, when no token is configured or the service has no TMDB provider", async () => {
    const { token } = await createTestSession();
    delete process.env.TMDB_READ_ACCESS_TOKEN;
    expect((await get(token, { service: "netflix", type: "MOVIE" })).body).toMatchObject({ available: false, items: [] });
    process.env.TMDB_READ_ACCESS_TOKEN = "test-token";
    expect((await get(token, { service: "zee5", type: "MOVIE" })).body).toMatchObject({ available: false, items: [] });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("returns a page of the service's titles with IMDb ids, leaving out those TMDB has none for", async () => {
    const { token } = await createTestSession();
    fetchMock.mockImplementation(async (url: string) => {
      if (url.includes("/discover/movie")) {
        return tmdb({
          total_pages: 40,
          results: [
            { id: 9001, title: "Alpha", release_date: "2020-05-01", vote_average: 7.46, overview: "A", poster_path: "/a.jpg", backdrop_path: "/ab.jpg" },
            { id: 9002, title: "Beta", release_date: "", vote_average: 0, overview: "", poster_path: null },
            { id: 9003, title: "NoImdb", release_date: "2019-01-01" },
          ],
        });
      }
      if (url.includes("/movie/9001/external_ids")) return tmdb({ imdb_id: "tt9000001" });
      if (url.includes("/movie/9002/external_ids")) return tmdb({ imdb_id: "tt9000002" });
      return tmdb({ imdb_id: null });
    });
    const response = await get(token, { service: "hulu", type: "MOVIE", page: "2", region: "us" });
    expect(response.status).toBe(200);
    expect(response.body.available).toBe(true);
    expect(response.body.page).toBe(2);
    expect(response.body.totalPages).toBe(40);
    expect(response.body.items).toEqual([
      { imdbId: "tt9000001", type: "MOVIE", title: "Alpha", year: 2020, rating: 7.5, overview: "A", poster: "https://image.tmdb.org/t/p/w342/a.jpg", backdrop: "https://image.tmdb.org/t/p/w780/ab.jpg" },
      { imdbId: "tt9000002", type: "MOVIE", title: "Beta", year: null, rating: null, overview: "", poster: null, backdrop: null },
    ]);
    const discoverUrl = String(fetchMock.mock.calls.find(([u]) => String(u).includes("/discover/movie"))![0]);
    expect(discoverUrl).toContain("with_watch_providers=15");
    expect(discoverUrl).toContain("watch_region=US");
    expect(discoverUrl).toContain("page=2");
    expect(discoverUrl).toContain("sort_by=popularity.desc");
  });

  it("reads series from the TV endpoint and caches the page", async () => {
    const { token } = await createTestSession();
    fetchMock.mockImplementation(async (url: string) =>
      url.includes("/discover/tv") ? tmdb({ total_pages: 1, results: [{ id: 9101, name: "Gamma", first_air_date: "2021-03-03", vote_average: 8 }] }) : tmdb({ imdb_id: "tt9100001" }),
    );
    const first = await get(token, { service: "shudder", type: "TV_SHOW" });
    expect(first.body.items).toMatchObject([{ imdbId: "tt9100001", type: "TV_SHOW", title: "Gamma", year: 2021 }]);
    const calls = fetchMock.mock.calls.length;
    await get(token, { service: "shudder", type: "TV_SHOW" });
    expect(fetchMock.mock.calls.length).toBe(calls);
  });

  it("says it is not available when TMDB does not answer", async () => {
    const { token } = await createTestSession();
    fetchMock.mockResolvedValue(tmdb({}, false));
    expect((await get(token, { service: "peacock", type: "MOVIE" })).body).toMatchObject({ available: false });
    fetchMock.mockRejectedValue(new Error("network"));
    expect((await get(token, { service: "peacock", type: "TV_SHOW" })).body).toMatchObject({ available: false });
  });
});
