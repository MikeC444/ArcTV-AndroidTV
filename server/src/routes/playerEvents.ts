import { Router } from "express";
import type { z } from "zod";
import { requireAuth, cleanAppVersion } from "../middleware/auth.js";
import { validate } from "../middleware/validate.js";
import { externalPlayerEventSchema } from "../schemas/playerEvents.js";
import { downloadEventSchema } from "../schemas/downloadEvents.js";
import { playbackOutcomeSchema } from "../schemas/playbackOutcomes.js";
import * as downloadEventService from "../services/downloadEventService.js";
import * as playbackOutcomeService from "../services/playbackOutcomeService.js";
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

// How a viewing ended (see migrations/0028_playback_outcomes.sql). Fire-and-forget, like the one above.
playerEventsRouter.post("/player-events/playback", requireAuth, validate({ body: playbackOutcomeSchema }), async (req, res, next) => {
  try {
    const input = req.validated!.body as z.infer<typeof playbackOutcomeSchema>;
    await playbackOutcomeService.recordPlaybackOutcome(req.user!.id, req.session!.deviceId, cleanAppVersion(req.header("x-arctv-app-version")), input);
    res.status(204).end();
  } catch (error) {
    next(error);
  }
});

// Web Download use: the panel opened, a file pressed, or the Plus popup shown (see migrations/0031_download_events.sql). Fire-and-forget, like the above.
playerEventsRouter.post("/player-events/download", requireAuth, validate({ body: downloadEventSchema }), async (req, res, next) => {
  try {
    const input = req.validated!.body as z.infer<typeof downloadEventSchema>;
    await downloadEventService.recordDownloadEvent(req.user!.id, input);
    res.status(204).end();
  } catch (error) {
    next(error);
  }
});
