import { z } from "zod";

// What the web app reports when someone uses Download (see migrations/0031_download_events.sql). No title, no content id, no file address.
export const downloadEventSchema = z.object({
  kind: z.enum(["popup", "opened", "file"]),
  contentType: z.enum(["MOVIE", "TV_SHOW"]),
  episode: z.boolean().default(false),
  resolution: z.string().trim().max(40).nullable().optional(),
  cached: z.boolean().nullable().optional(),
});
export type DownloadEventInput = z.infer<typeof downloadEventSchema>;
