import { Router } from "express";
import type { z } from "zod";
import { requireAuth } from "../middleware/auth.js";
import { resolveProfile } from "../middleware/profile.js";
import { validate } from "../middleware/validate.js";
import { feedbackBodySchema, feedbackDeleteQuerySchema, feedbackListQuerySchema } from "../schemas/feedback.js";
import * as feedbackService from "../services/feedbackService.js";

export const feedbackRouter = Router();

function serialize(item: feedbackService.MovieFeedback) {
  return {
    profileId: item.profileId,
    providerId: item.providerId,
    contentId: item.contentId,
    contentType: item.contentType,
    title: item.title,
    feedback: item.feedback,
    updatedAt: item.updatedAt.toISOString(),
    deletedAt: item.deletedAt ? item.deletedAt.toISOString() : null,
  };
}

feedbackRouter.get("/feedback", requireAuth, resolveProfile, validate({ query: feedbackListQuerySchema }), async (req, res, next) => {
  try {
    const { profileId } = req.validated!.query as z.infer<typeof feedbackListQuerySchema>;
    const items = await feedbackService.listActiveFeedback(req.user!.id, req.profileId ?? profileId);
    res.json({ items: items.map(serialize) });
  } catch (error) {
    next(error);
  }
});

feedbackRouter.post("/feedback", requireAuth, resolveProfile, validate({ body: feedbackBodySchema }), async (req, res, next) => {
  try {
    const input = req.validated!.body as z.infer<typeof feedbackBodySchema>;
    res.json(serialize(await feedbackService.upsertFeedback(req.user!.id, { ...input, profileId: req.profileId ?? input.profileId })));
  } catch (error) {
    next(error);
  }
});

feedbackRouter.delete("/feedback", requireAuth, resolveProfile, validate({ query: feedbackDeleteQuerySchema }), async (req, res, next) => {
  try {
    const input = req.validated!.query as z.infer<typeof feedbackDeleteQuerySchema>;
    const item = await feedbackService.clearFeedback(req.user!.id, { ...input, profileId: req.profileId ?? input.profileId });
    if (!item) {
      res.status(204).end();
      return;
    }
    res.json(serialize(item));
  } catch (error) {
    next(error);
  }
});
