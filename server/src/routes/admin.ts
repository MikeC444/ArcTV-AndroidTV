import { Router } from "express";
import { z } from "zod";
import { HttpError } from "../lib/httpError.js";
import { requireAuth } from "../middleware/auth.js";
import { validate } from "../middleware/validate.js";
import { FEATURE_INTROS } from "../schemas/featureIntros.js";
import * as adminService from "../services/adminService.js";

/**
 * The developer panel's read-only data. Every route needs a signed-in account with users.is_admin (set by hand in the database).
 * Anyone else gets the same 404 as an unknown path, so the panel's existence is not confirmed to other accounts.
 */
export const adminRouter = Router();

adminRouter.use(requireAuth, (req, _res, next) => {
  if (!req.user?.isAdmin) return next(new HttpError(404, "Not found"));
  next();
});

adminRouter.get("/summary", async (_req, res, next) => {
  try {
    res.json(await adminService.summary());
  } catch (error) {
    next(error);
  }
});

const listQuery = z.object({
  q: z.string().trim().max(100).optional(),
  plan: z.enum(["free", "monthly", "yearly", "lifetime"]).optional(),
  device: z.string().trim().max(80).regex(/^[a-z_]+\|[0-9A-Za-z.+_-]+$/, "Not a device filter.").optional(),
  webSystem: z.string().trim().max(30).regex(/^[A-Za-z0-9 ]+$/, "Not a system.").optional(),
  addons: z.enum(["with", "none"]).optional(),
  watching: z.enum(["with", "none"]).optional(),
  seen: z.enum(["1h", "24h", "7d", "30d", "older", "never"]).optional(),
  limit: z.coerce.number().int().min(1).max(200).default(50),
  offset: z.coerce.number().int().min(0).max(1_000_000).default(0),
});

adminRouter.get("/users", validate({ query: listQuery }), async (req, res, next) => {
  try {
    res.json(await adminService.listUsers(req.validated!.query as z.infer<typeof listQuery>));
  } catch (error) {
    next(error);
  }
});

const idParams = z.object({ id: z.uuid("Not a user id.") });

adminRouter.get("/users/:id", validate({ params: idParams }), async (req, res, next) => {
  try {
    const { id } = req.validated!.params as z.infer<typeof idParams>;
    const detail = await adminService.userDetail(id);
    if (!detail) throw new HttpError(404, "Not found");
    console.info(`[admin] ${req.user!.email} viewed ${detail.user.email as string}`);
    res.json(detail);
  } catch (error) {
    next(error);
  }
});

const introParams = z.object({ feature: z.enum(FEATURE_INTROS) });
const introQuery = z.object({
  limit: z.coerce.number().int().min(1).max(200).default(50),
  offset: z.coerce.number().int().min(0).max(1_000_000).default(0),
});

// Who has clicked a one-off "what's new" pop-up away, e.g. GET /admin/feature-intros/torrent_intro.
adminRouter.get("/feature-intros/:feature", validate({ params: introParams, query: introQuery }), async (req, res, next) => {
  try {
    const { feature } = req.validated!.params as z.infer<typeof introParams>;
    const { limit, offset } = req.validated!.query as z.infer<typeof introQuery>;
    res.json(await adminService.featureIntroAcks(feature, limit, offset));
  } catch (error) {
    next(error);
  }
});
