import { z } from "zod";

// GET query params, a pure read. `service` is one of the web/apps' streaming-service ids (checked against the table in
// streamingTitlesService.ts, so nothing a caller sends is placed into a TMDB path or query unchecked); `region` is a two-letter country code.
export const streamingTitlesQuerySchema = z.object({
  service: z.string().regex(/^[a-z0-9]{2,20}$/),
  type: z.enum(["MOVIE", "TV_SHOW"]),
  page: z.coerce.number().int().min(1).max(500).default(1),
  region: z
    .string()
    .regex(/^[A-Za-z]{2}$/)
    .transform((value) => value.toUpperCase())
    .default("GB"),
});
export type StreamingTitlesQueryInput = z.infer<typeof streamingTitlesQuerySchema>;
