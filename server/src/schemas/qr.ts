import { z } from "zod";
import { displayName } from "./auth.js";

// Same device-identity fields register/login accept (see schemas/auth.ts)
// — the TV supplies these once, at QR-session creation time, rather than
// the phone supplying them at completion time, since the phone is never
// the device that ends up signed in.
export const createQrSchema = z.object({
  deviceId: z.uuid("deviceId must be a UUID"),
  deviceName: z.string().trim().min(1).max(100).optional(),
  platform: z.string().trim().min(1).max(50).optional(),
});
export type CreateQrInput = z.infer<typeof createQrSchema>;

export const qrTokenQuerySchema = z.object({
  token: z.string().min(1, "token is required"),
});
export type QrTokenQuery = z.infer<typeof qrTokenQuerySchema>;

export const completeQrSchema = z.object({
  token: z.string().min(1, "token is required"),
  mode: z.enum(["login", "register"]),
  email: z.string().trim().toLowerCase().email("Must be a valid email address"),
  password: z.string().min(8, "Password must be at least 8 characters").max(256),
  displayName: z.string().trim().max(100).optional(),
}).superRefine((body, ctx) => {
  // Creating an account needs a name (it becomes the default profile); signing in to an existing one does not.
  if (body.mode !== "register") return;
  const parsed = displayName.safeParse(body.displayName);
  if (!parsed.success) ctx.addIssue({ code: "custom", path: ["displayName"], message: parsed.error.issues[0]?.message ?? "Enter a display name" });
});
export type CompleteQrInput = z.infer<typeof completeQrSchema>;
