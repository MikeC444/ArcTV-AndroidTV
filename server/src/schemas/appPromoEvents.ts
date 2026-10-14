import { z } from "zod";

// What the web app reports about its "get the desktop app" popups (see migrations/0032_app_promo_events.sql). Nothing else is sent.
export const appPromoEventSchema = z.object({
  app: z.enum(["mac", "windows"]),
  kind: z.enum(["shown", "download", "closed"]),
});
export type AppPromoEventInput = z.infer<typeof appPromoEventSchema>;
