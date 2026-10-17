import { getTmdbReadAccessToken } from "../config/env.js";

const TMDB_BASE_URL = "https://api.themoviedb.org/3";
const POSTER_BASE_URL = "https://image.tmdb.org/t/p/w342";
const BACKDROP_BASE_URL = "https://image.tmdb.org/t/p/w780";

// A catalogue page changes slowly (a ranking by popularity); an IMDb id never changes. One TMDB key backs every account, so keep the traffic low.
const PAGE_TTL_MS = 6 * 60 * 60 * 1000;
const ID_TTL_MS = 30 * 24 * 60 * 60 * 1000;

/**
 * TMDB's "watch provider" id for each streaming service the apps know (the ids are the same in every country; which titles a provider has is
 * per region). A service not listed here has no TMDB source and the apps just keep the addon's own titles.
 */
export const TMDB_PROVIDER_IDS: Record<string, number> = {
  netflix: 8,
  prime: 9,
  disney: 337,
  max: 1899,
  apple: 350,
  paramount: 531,
  hulu: 15,
  peacock: 386,
  crunchyroll: 283,
  skyshowtime: 1773,
  britbox: 151,
  itvx: 41,
  iplayer: 38,
  channel4: 103,
  shudder: 99,
  discovery: 520,
  mubi: 11,
  criterion: 258,
  starz: 43,
  acorn: 87,
};

export type StreamingTitleType = "MOVIE" | "TV_SHOW";

export interface StreamingTitle {
  imdbId: string;
  type: StreamingTitleType;
  title: string;
  year: number | null;
  rating: number | null;
  overview: string;
  poster: string | null;
  backdrop: string | null;
}

export interface StreamingTitlesPage {
  items: StreamingTitle[];
  page: number;
  totalPages: number;
}

interface TmdbDiscoverResult {
  id?: number;
  title?: string;
  name?: string;
  release_date?: string;
  first_air_date?: string;
  vote_average?: number;
  vote_count?: number;
  overview?: string;
  poster_path?: string | null;
  backdrop_path?: string | null;
}
interface TmdbDiscoverResponse {
  results?: TmdbDiscoverResult[];
  total_pages?: number;
}
interface TmdbExternalIds {
  imdb_id?: string | null;
}

const pageCache = new Map<string, { result: StreamingTitlesPage; expiresAt: number }>();
const idCache = new Map<string, { imdbId: string | null; expiresAt: number }>();

async function tmdbGet<T>(path: string, token: string): Promise<T | null> {
  const response = await fetch(`${TMDB_BASE_URL}${path}`, { headers: { Authorization: `Bearer ${token}`, Accept: "application/json" } });
  if (!response.ok) return null;
  return (await response.json()) as T;
}

async function imdbIdOf(tmdbId: number, type: StreamingTitleType, token: string): Promise<string | null> {
  const key = `${type}:${tmdbId}`;
  const cached = idCache.get(key);
  if (cached && cached.expiresAt > Date.now()) return cached.imdbId;
  try {
    const ids = await tmdbGet<TmdbExternalIds>(`/${type === "TV_SHOW" ? "tv" : "movie"}/${tmdbId}/external_ids`, token);
    const imdbId = ids?.imdb_id && /^tt\d{1,10}$/.test(ids.imdb_id) ? ids.imdb_id : null;
    idCache.set(key, { imdbId, expiresAt: Date.now() + ID_TTL_MS });
    return imdbId;
  } catch {
    return null; // not remembered: the next page asks again
  }
}

const yearOf = (date: string | undefined): number | null => {
  const year = date ? Number.parseInt(date.slice(0, 4), 10) : NaN;
  return Number.isFinite(year) ? year : null;
};

/**
 * One page (about 20 titles) of what a streaming service carries in a country, most popular first, from TMDB's discover. The apps open titles by
 * IMDb id (that is what the addons use), so each result is given its IMDb id and the ones TMDB has none for are left out.
 * Returns null when the service has no TMDB provider id or TMDB isn't configured / doesn't answer; an empty page when there is nothing more.
 */
export async function findStreamingTitles(service: string, type: StreamingTitleType, page: number, region: string): Promise<StreamingTitlesPage | null> {
  const providerId = TMDB_PROVIDER_IDS[service];
  const token = getTmdbReadAccessToken();
  if (providerId === undefined || !token) return null;

  const key = `${service}|${type}|${page}|${region}`;
  const cached = pageCache.get(key);
  if (cached && cached.expiresAt > Date.now()) return cached.result;

  try {
    const kind = type === "TV_SHOW" ? "tv" : "movie";
    const query = new URLSearchParams({
      with_watch_providers: String(providerId),
      watch_region: region,
      with_watch_monetization_types: "flatrate",
      sort_by: "popularity.desc",
      "vote_count.gte": "20", // keeps out the entries nobody has rated
      include_adult: "false",
      page: String(page),
    });
    const discovered = await tmdbGet<TmdbDiscoverResponse>(`/discover/${kind}?${query}`, token);
    if (!discovered) return null;
    const results = (discovered.results ?? []).filter((r): r is TmdbDiscoverResult & { id: number } => typeof r.id === "number");
    const withIds = await Promise.all(results.map(async (r) => ({ r, imdbId: await imdbIdOf(r.id, type, token) })));
    const items: StreamingTitle[] = [];
    for (const { r, imdbId } of withIds) {
      const title = (type === "TV_SHOW" ? r.name : r.title)?.trim();
      if (!imdbId || !title) continue;
      items.push({
        imdbId,
        type,
        title,
        year: yearOf(type === "TV_SHOW" ? r.first_air_date : r.release_date),
        rating: typeof r.vote_average === "number" && r.vote_average > 0 ? Math.round(r.vote_average * 10) / 10 : null,
        overview: r.overview?.trim() ?? "",
        poster: r.poster_path ? `${POSTER_BASE_URL}${r.poster_path}` : null,
        backdrop: r.backdrop_path ? `${BACKDROP_BASE_URL}${r.backdrop_path}` : null,
      });
    }
    const result: StreamingTitlesPage = { items, page, totalPages: Math.min(discovered.total_pages ?? page, 500) };
    pageCache.set(key, { result, expiresAt: Date.now() + PAGE_TTL_MS });
    return result;
  } catch {
    return null; // a service page keeps the addon's titles and never breaks over this
  }
}
