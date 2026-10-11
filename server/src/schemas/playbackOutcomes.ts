import { z } from "zod";

// What the app reports when a viewing ends (see migrations/0028_playback_outcomes.sql). No title, no content id, no stream address.
export const playbackOutcomeSchema = z.object({
  engine: z.enum(["vlc", "builtin"]),
  deviceModel: z.string().trim().max(80).nullable().optional(),
  resolution: z.string().trim().max(40).nullable().optional(),
  codec: z.string().trim().max(80).nullable().optional(),
  hdr: z.string().trim().max(40).nullable().optional(),
  startMs: z.number().int().min(0).max(3_600_000).nullable().optional(),
  watchedMs: z.number().int().min(0).max(86_400_000),
  outcome: z.enum(["ok", "short", "error", "never_started"]),
  errorKind: z.string().trim().max(200).nullable().optional(),
  sourcesTried: z.number().int().min(1).max(50).default(1),
});
export type PlaybackOutcomeInput = z.infer<typeof playbackOutcomeSchema>;
