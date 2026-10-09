import { Router } from "express";
import type { z } from "zod";
import { requireAuth } from "../middleware/auth.js";
import { resolveProfile } from "../middleware/profile.js";
import { validate } from "../middleware/validate.js";
import { pickedDismissalBodySchema, pickedDismissalListQuerySchema } from "../schemas/pickedDismissals.js";
import * as service from "../services/pickedDismissalService.js";

export const pickedDismissalsRouter = Router();

const serialize = (item: service.PickedDismissal) => ({ profileId: item.profileId, contentId: item.contentId, dismissedAt: item.dismissedAt.toISOString() });

pickedDismissalsRouter.get("/picked-dismissals", requireAuth, resolveProfile, validate({ query: pickedDismissalListQuerySchema }), async (req, res, next) => {
  try {
    const { profileId } = req.validated!.query as z.infer<typeof pickedDismissalListQuerySchema>;
    const items = await service.listRecent(req.user!.id, req.profileId ?? profileId);
    res.json({ items: items.map(serialize) });
  } catch (error) {
    next(error);
  }
});

pickedDismissalsRouter.post("/picked-dismissals", requireAuth, resolveProfile, validate({ body: pickedDismissalBodySchema }), async (req, res, next) => {
  try {
    const input = req.validated!.body as z.infer<typeof pickedDismissalBodySchema>;
    res.json(serialize(await service.upsert(req.user!.id, { ...input, profileId: req.profileId ?? input.profileId })));
  } catch (error) {
    next(error);
  }
});
