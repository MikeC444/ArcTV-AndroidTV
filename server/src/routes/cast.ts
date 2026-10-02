import { Router } from "express";
import type { z } from "zod";
import { requireAuth } from "../middleware/auth.js";
import { validate } from "../middleware/validate.js";
import { castQuerySchema } from "../schemas/cast.js";
import { findCast } from "../services/castService.js";

export const castRouter = Router();

castRouter.get("/cast", requireAuth, validate({ query: castQuerySchema }), async (req, res, next) => {
  try {
    const input = req.validated!.query as z.infer<typeof castQuerySchema>;
    res.json({ cast: await findCast(input.imdbId, input.type) });
  } catch (error) {
    next(error);
  }
});
