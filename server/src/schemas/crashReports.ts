import { z } from "zod";

/** What closed the app: an uncaught error, a native (C/C++) crash, a freeze the system ended, the system closing it for memory, or another exit. */
export const CRASH_KINDS = ["crash", "native_crash", "anr", "low_memory", "other_exit"] as const;

export const crashReportSchema = z.object({
  platform: z.string().trim().min(1).max(30),
  appVersion: z.string().trim().min(1).max(40),
  deviceModel: z.string().trim().max(120).nullable().optional(),
  osVersion: z.string().trim().max(60).nullable().optional(),
  kind: z.enum(CRASH_KINDS),
  // The error's class and message, e.g. "java.lang.IllegalStateException: Key tt1 was already used"
  exception: z.string().max(500).nullable().optional(),
  // Longer ones are cut (the app already sends at most this much)
  stackTrace: z.string().max(20_000).transform((s) => s.slice(0, 16_000)),
  happenedAt: z.iso.datetime("happenedAt must be an ISO-8601 UTC timestamp"),
});
export type CrashReportInput = z.infer<typeof crashReportSchema>;
