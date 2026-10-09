import { Router } from "express";
import type { z } from "zod";
import { pool } from "../db/pool.js";
import { requireAuth } from "../middleware/auth.js";
import { profileOf, resolveProfile } from "../middleware/profile.js";
import { validate } from "../middleware/validate.js";
import { historyQuerySchema, watchProgressBodySchema } from "../schemas/watchProgress.js";
import * as continueWatchingService from "../services/continueWatchingService.js";
import * as playbackProgressService from "../services/playbackProgressService.js";

export const historyRouter = Router();

function serializeHistoryEntry(entry: playbackProgressService.WatchHistoryEntry) {
  return {
    providerId: entry.providerId,
    contentId: entry.contentId,
    contentType: entry.contentType,
    seasonNumber: entry.seasonNumber,
    episodeNumber: entry.episodeNumber,
    episodeTitle: entry.episodeTitle,
    title: entry.title,
    posterUrl: entry.posterUrl,
    positionMs: entry.positionMs,
    durationMs: entry.durationMs,
    completed: entry.completed,
    watchedAt: entry.watchedAt.toISOString(),
  };
}

function serializeContinueWatching(entry: continueWatchingService.ContinueWatchingEntry) {
  return {
    providerId: entry.providerId,
    contentId: entry.contentId,
    contentType: entry.contentType,
    seasonNumber: entry.seasonNumber,
    episodeNumber: entry.episodeNumber,
    episodeTitle: entry.episodeTitle,
    title: entry.title,
    posterUrl: entry.posterUrl,
    backdropUrl: entry.backdropUrl,
    positionMs: entry.positionMs,
    durationMs: entry.durationMs,
    lastWatchedAt: entry.lastWatchedAt.toISOString(),
    deletedAt: entry.deletedAt ? entry.deletedAt.toISOString() : null,
  };
}

historyRouter.post(
  "/watch-progress",
  requireAuth,
  resolveProfile,
  validate({ body: watchProgressBodySchema }),
  async (req, res, next) => {
    try {
      const input = req.validated!.body as z.infer<typeof watchProgressBodySchema>;
      const { historyEntry, continueWatching } = await playbackProgressService.recordProgress(req.user!.id, profileOf(req), input);
      // "Watching now" for the developer panel: a save while playing (not the closing "finished" one) stamps the device with the server's clock.
      if (!input.completed && req.session) {
        void pool
          .query("UPDATE devices SET last_progress_at = now() WHERE id = $1", [req.session.deviceId])
          .catch((error: unknown) => console.error("failed to record device progress time", error));
      }
      res.json({
        historyEntry: serializeHistoryEntry(historyEntry),
        continueWatching: continueWatching ? serializeContinueWatching(continueWatching) : null,
      });
    } catch (error) {
      next(error);
    }
  }
);

historyRouter.get("/history", requireAuth, resolveProfile, validate({ query: historyQuerySchema }), async (req, res, next) => {
  try {
    const query = req.validated!.query as z.infer<typeof historyQuerySchema>;
    const entries = await playbackProgressService.listWatchHistory(req.user!.id, profileOf(req), query);
    res.json({ items: entries.map(serializeHistoryEntry) });
  } catch (error) {
    next(error);
  }
});

historyRouter.get("/continue-watching", requireAuth, resolveProfile, async (req, res, next) => {
  try {
    const entries = await continueWatchingService.listActiveContinueWatching(req.user!.id, profileOf(req));
    res.json({ items: entries.map(serializeContinueWatching) });
  } catch (error) {
    next(error);
  }
});
