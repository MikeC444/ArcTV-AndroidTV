import { Router } from "express";
import type { z } from "zod";
import { requireAuth, cleanAppVersion } from "../middleware/auth.js";
import { validate } from "../middleware/validate.js";
import { featureIntroAckSchema } from "../schemas/featureIntros.js";
import * as featureIntroService from "../services/featureIntroService.js";

export const featureIntrosRouter = Router();

// Fire-and-forget from the app when someone clicks a one-off pop-up away: the answer carries nothing.
featureIntrosRouter.post("/feature-intros/ack", requireAuth, validate({ body: featureIntroAckSchema }), async (req, res, next) => {
  try {
    const input = req.validated!.body as z.infer<typeof featureIntroAckSchema>;
    await featureIntroService.acknowledge(req.user!.id, req.session!.deviceId, cleanAppVersion(req.header("x-arctv-app-version")), input);
    res.status(204).end();
  } catch (error) {
    next(error);
  }
});
