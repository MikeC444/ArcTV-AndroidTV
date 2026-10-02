import { z } from "zod";

// Mirrors movie_feedback's columns (see migrations/0016_movie_feedback.sql).
// updatedAt is the client's own local mutation timestamp, not
// server-assigned -- the same last-write-wins contract as the watchlist,
// applied per (profile, addon, title) slot.
const contentTypeSchema = z.enum(["MOVIE", "TV_SHOW"]);
const profileIdSchema = z.string().min(1).max(100);

export const feedbackBodySchema = z.object({
  profileId: profileIdSchema.default("main"),
  providerId: z.string().min(1).max(200),
  contentId: z.string().min(1).max(500),
  contentType: contentTypeSchema,
  title: z.string().min(1).max(500),
  feedback: z.enum(["like", "dislike"]),
  updatedAt: z.iso.datetime("updatedAt must be an ISO-8601 UTC timestamp"),
});
export type FeedbackInput = z.infer<typeof feedbackBodySchema>;

export const feedbackListQuerySchema = z.object({
  profileId: profileIdSchema.default("main"),
});

// The natural key plus updatedAt, carried as query params since a DELETE
// request has no conventional body across every HTTP client.
export const feedbackDeleteQuerySchema = z.object({
  profileId: profileIdSchema.default("main"),
  providerId: z.string().min(1).max(200),
  contentId: z.string().min(1).max(500),
  contentType: contentTypeSchema,
  updatedAt: z.iso.datetime("updatedAt must be an ISO-8601 UTC timestamp"),
});
export type FeedbackDeleteInput = z.infer<typeof feedbackDeleteQuerySchema>;
