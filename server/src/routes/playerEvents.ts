import { Router } from "express";
import type { z } from "zod";
import { requireAuth, cleanAppVersion } from "../middleware/auth.js";
import { validate } from "../middleware/validate.js";
import { externalPlayerEventSchema } from "../schemas/playerEvents.js";
import * as playerEventService from "../services/playerEventService.js";

export const playerEventsRouter = Router();

// Fire-and-forget from the app: the answer carries nothing.
playerEventsRouter.post("/player-events/external", requireAuth, validate({ body: externalPlayerEventSchema }), async (req, res, next) => {
  try {
    const input = req.validated!.body as z.infer<typeof externalPlayerEventSchema>;
    await playerEventService.recordExternalPlayerEvent(req.user!.id, req.session!.deviceId, cleanAppVersion(req.header("x-arctv-app-version")), input);
    res.status(204).end();
  } catch (error) {
    next(error);
  }
});
