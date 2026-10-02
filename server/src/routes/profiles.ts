import { Router } from "express";
import type { z } from "zod";
import { requireAuth } from "../middleware/auth.js";
import { createPinRateLimiter } from "../middleware/rateLimit.js";
import { validate } from "../middleware/validate.js";
import { profileCreateBodySchema, profileIdParamsSchema, profileUpdateBodySchema, verifyPinBodySchema } from "../schemas/profiles.js";
import * as profileService from "../services/profileService.js";

type IdParams = z.infer<typeof profileIdParamsSchema>;

/** A function, not a shared router: each app instance gets its own rate-limit counters (see middleware/rateLimit.ts). */
export function createProfilesRouter(): Router {
  const profilesRouter = Router();
  const pinLimiter = createPinRateLimiter();

  profilesRouter.get("/profiles", requireAuth, async (req, res, next) => {
    try {
      res.json({ profiles: await profileService.listProfiles(req.user!.id, req.user!.displayName) });
    } catch (error) {
      next(error);
    }
  });

  profilesRouter.post("/profiles", requireAuth, validate({ body: profileCreateBodySchema }), async (req, res, next) => {
    try {
      const input = req.validated!.body as z.infer<typeof profileCreateBodySchema>;
      res.status(201).json(await profileService.createProfile(req.user!.id, req.user!.displayName, input));
    } catch (error) {
      next(error);
    }
  });

  profilesRouter.put("/profiles/:id", requireAuth, validate({ params: profileIdParamsSchema, body: profileUpdateBodySchema }), async (req, res, next) => {
    try {
      const { id } = req.validated!.params as IdParams;
      const input = req.validated!.body as z.infer<typeof profileUpdateBodySchema>;
      res.json(await profileService.updateProfile(req.user!.id, req.user!.displayName, id, input));
    } catch (error) {
      next(error);
    }
  });

  profilesRouter.delete("/profiles/:id", requireAuth, validate({ params: profileIdParamsSchema }), async (req, res, next) => {
    try {
      const { id } = req.validated!.params as IdParams;
      await profileService.deleteProfile(req.user!.id, id);
      res.status(204).end();
    } catch (error) {
      next(error);
    }
  });

  // 204 = right (or the profile has no PIN), 403 = wrong (never 401: the web server reads a 401 as "the session is over"), 429 = locked out.
  profilesRouter.post("/profiles/:id/verify-pin", requireAuth, pinLimiter, validate({ params: profileIdParamsSchema, body: verifyPinBodySchema }), async (req, res, next) => {
    try {
      const { id } = req.validated!.params as IdParams;
      const { pin } = req.validated!.body as z.infer<typeof verifyPinBodySchema>;
      await profileService.verifyPin(req.user!.id, id, pin);
      res.status(204).end();
    } catch (error) {
      next(error);
    }
  });

  return profilesRouter;
}
