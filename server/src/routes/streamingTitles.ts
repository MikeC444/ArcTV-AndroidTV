import { Router } from "express";
import type { z } from "zod";
import { requireAuth } from "../middleware/auth.js";
import { validate } from "../middleware/validate.js";
import { streamingTitlesQuerySchema } from "../schemas/streamingTitles.js";
import { findStreamingTitles } from "../services/streamingTitlesService.js";

export const streamingTitlesRouter = Router();

// `available: false` means there is no TMDB source for this service (or TMDB is not configured), so the app keeps to the addon's titles.
streamingTitlesRouter.get("/streaming-titles", requireAuth, validate({ query: streamingTitlesQuerySchema }), async (req, res, next) => {
  try {
    const input = req.validated!.query as z.infer<typeof streamingTitlesQuerySchema>;
    const page = await findStreamingTitles(input.service, input.type, input.page, input.region);
    res.json(page ? { available: true, ...page } : { available: false, items: [], page: input.page, totalPages: 0 });
  } catch (error) {
    next(error);
  }
});
