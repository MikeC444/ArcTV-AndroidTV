import { z } from "zod";

/** The one-off pop-ups the app can report as clicked away (see migrations/0024_feature_intro_acks.sql). */
export const FEATURE_INTROS = ["torrent_intro"] as const;

export const featureIntroAckSchema = z.object({
  feature: z.enum(FEATURE_INTROS),
});
export type FeatureIntroAckInput = z.infer<typeof featureIntroAckSchema>;
