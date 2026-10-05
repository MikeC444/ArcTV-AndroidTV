import { z } from "zod";

// What the app reports when someone confirms "Play in external player" (see migrations/0021_external_player_events.sql).
export const externalPlayerEventSchema = z.object({
  providerId: z.string().min(1).max(200),
  contentId: z.string().min(1).max(500),
  contentType: z.enum(["MOVIE", "TV_SHOW"]),
  title: z.string().min(1).max(500),
  releaseTitle: z.string().max(500).nullable().optional(),
  resolution: z.string().max(40).nullable().optional(),
  codec: z.string().max(80).nullable().optional(),
  trigger: z.enum(["button", "error"]),
  outcome: z.enum(["opened", "no_player"]),
  errorMessage: z.string().max(1000).nullable().optional(),
});
export type ExternalPlayerEventInput = z.infer<typeof externalPlayerEventSchema>;
