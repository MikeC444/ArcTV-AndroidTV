import { z } from "zod";

// GET query params, not a body -- a pure read/lookup, same shape as the trailer and release-date schemas. The IMDb id is
// matched strictly (tt + digits) because it is placed into a TMDB request path; nothing else a caller sends reaches TMDB.
export const castQuerySchema = z.object({
  imdbId: z.string().regex(/^tt\d{1,10}$/),
  type: z.enum(["MOVIE", "TV_SHOW"]),
});
export type CastQueryInput = z.infer<typeof castQuerySchema>;
