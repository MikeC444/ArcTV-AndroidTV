import { getTmdbReadAccessToken } from "../config/env.js";

const TMDB_BASE_URL = "https://api.themoviedb.org/3";
const IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w185";

// Same rationale as trailerService / releaseDateService: one TMDB key backs every account, so keeping TMDB traffic low
// matters, and a day-old cast list is just as good as a fresh one.
const CACHE_TTL_MS = 24 * 60 * 60 * 1000;
const MAX_CAST = 20;

export interface CastEntry {
  name: string;
  character: string | null;
  photo: string | null;
}

export type CastTitleType = "MOVIE" | "TV_SHOW";

interface TmdbFindResponse {
  movie_results?: Array<{ id?: number }>;
  tv_results?: Array<{ id?: number }>;
}

interface TmdbCreditsResponse {
  cast?: Array<{ name?: string; character?: string; profile_path?: string | null }>;
}

// In-memory only, same tradeoff as the other TMDB lookups: a restart just means the next request re-asks TMDB once.
const cache = new Map<string, { result: CastEntry[]; expiresAt: number }>();

async function tmdbGet<T>(path: string, token: string): Promise<T | null> {
  const response = await fetch(`${TMDB_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}`, Accept: "application/json" },
  });
  if (!response.ok) return null;
  return (await response.json()) as T;
}

async function lookUp(imdbId: string, type: CastTitleType): Promise<CastEntry[]> {
  const token = getTmdbReadAccessToken();
  if (!token) return [];

  try {
    // imdbId was validated against /^tt\d{1,10}$/ by castQuerySchema, so it is safe to place in the path.
    const found = await tmdbGet<TmdbFindResponse>(`/find/${imdbId}?external_source=imdb_id`, token);
    const tmdbId = (type === "TV_SHOW" ? found?.tv_results : found?.movie_results)?.[0]?.id;
    if (tmdbId === undefined) return [];

    const credits = await tmdbGet<TmdbCreditsResponse>(`/${type === "TV_SHOW" ? "tv" : "movie"}/${tmdbId}/credits`, token);
    return (credits?.cast ?? [])
      .filter((member): member is { name: string; character?: string; profile_path?: string | null } => typeof member.name === "string" && member.name.trim() !== "")
      .slice(0, MAX_CAST)
      .map((member) => ({
        name: member.name,
        character: member.character?.trim() ? member.character : null,
        photo: member.profile_path ? `${IMAGE_BASE_URL}${member.profile_path}` : null,
      }));
  } catch {
    // Cast photos are a nice-to-have on top of the addon's own cast list, never allowed to break loading a Detail page.
    return [];
  }
}

/**
 * Looks up a title's cast (with the character played and a photo address) on TMDB, for addons that send names only.
 * Caches hits and misses in memory for CACHE_TTL_MS. Returns an empty list when TMDB isn't configured or has no match.
 */
export async function findCast(imdbId: string, type: CastTitleType): Promise<CastEntry[]> {
  const key = `${type}:${imdbId}`;
  const cached = cache.get(key);
  if (cached && cached.expiresAt > Date.now()) return cached.result;

  const result = await lookUp(imdbId, type);
  cache.set(key, { result, expiresAt: Date.now() + CACHE_TTL_MS });
  return result;
}
