import { Router } from "express";
import type { z } from "zod";
import { optionalUserId } from "../middleware/auth.js";
import { createCrashReportRateLimiter } from "../middleware/rateLimit.js";
import { validate } from "../middleware/validate.js";
import { crashReportSchema } from "../schemas/crashReports.js";
import { saveCrashReport } from "../services/crashReportService.js";

/**
 * POST /crash-reports: an app that closed unexpectedly sends what it saved the next time it opens. Signed in or not (a crash can happen
 * before sign-in); with a live session the report is kept with the account. Read only by the developer panel (GET /admin/crashes).
 */
export function createCrashReportsRouter(): Router {
  const router = Router();
  router.post("/", createCrashReportRateLimiter(), validate({ body: crashReportSchema }), async (req, res, next) => {
    try {
      await saveCrashReport(await optionalUserId(req), req.validated!.body as z.infer<typeof crashReportSchema>);
      res.status(204).end();
    } catch (error) {
      next(error);
    }
  });
  return router;
}
