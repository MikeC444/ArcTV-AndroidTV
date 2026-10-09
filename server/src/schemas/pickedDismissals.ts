import { z } from "zod";

// Mirrors picked_dismissals' columns (see migrations/0026_picked_dismissals.sql). dismissedAt is the client's own timestamp.
const profileIdSchema = z.string().min(1).max(100);

export const pickedDismissalBodySchema = z.object({
  profileId: profileIdSchema.default("main"),
  contentId: z.string().min(1).max(500),
  dismissedAt: z.iso.datetime("dismissedAt must be an ISO-8601 UTC timestamp"),
});
export type PickedDismissalInput = z.infer<typeof pickedDismissalBodySchema>;

export const pickedDismissalListQuerySchema = z.object({
  profileId: profileIdSchema.default("main"),
});
