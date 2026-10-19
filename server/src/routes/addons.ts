import { Router } from "express";
import type { z } from "zod";
import { cleanAppVersion, requireAuth } from "../middleware/auth.js";
import { profileOf, resolveProfile } from "../middleware/profile.js";
import { validate } from "../middleware/validate.js";
import { addonBodySchema, addonDeleteQuerySchema } from "../schemas/addons.js";
import * as addonService from "../services/addonService.js";
import { addonSupportedBy } from "../services/addonCompatibility.js";
import { pool } from "../db/pool.js";

export const addonsRouter = Router();

function serialize(addon: addonService.UserAddon) {
  return {
    manifestUrl: addon.manifestUrl,
    addonId: addon.addonId,
    name: addon.name,
    manifestJson: addon.manifestJson,
    enabled: addon.enabled,
    sortOrder: addon.sortOrder,
    updatedAt: addon.updatedAt.toISOString(),
    deletedAt: addon.deletedAt ? addon.deletedAt.toISOString() : null,
  };
}

addonsRouter.get("/addons", requireAuth, resolveProfile, async (req, res, next) => {
  try {
    const addons = await addonService.listActiveAddons(req.user!.id, profileOf(req));
    // Only the addons this device's app can cope with (see services/addonCompatibility.ts): the version it says it is now, else the one on record.
    const device = await pool.query<{ platform: string | null; app_version: string | null }>("SELECT platform, app_version FROM devices WHERE id = $1", [req.session!.deviceId]);
    const platform = device.rows[0]?.platform ?? null;
    const version = cleanAppVersion(req.header("x-arctv-app-version")) ?? device.rows[0]?.app_version ?? null;
    // A developer account (users.is_admin) is listed everything whatever app it is on, so a debug build or an emulator (which reports version 0.1.0) still has the addon.
    const listed = req.user!.isAdmin ? addons : addons.filter((addon) => addonSupportedBy(addon.addonId, platform, version));
    res.json({ items: listed.map(serialize) });
  } catch (error) {
    next(error);
  }
});

addonsRouter.post("/addons", requireAuth, resolveProfile, validate({ body: addonBodySchema }), async (req, res, next) => {
  try {
    const input = req.validated!.body as z.infer<typeof addonBodySchema>;
    const addon = await addonService.upsertAddon(req.user!.id, profileOf(req), input);
    res.json(serialize(addon));
  } catch (error) {
    next(error);
  }
});

addonsRouter.delete("/addons", requireAuth, resolveProfile, validate({ query: addonDeleteQuerySchema }), async (req, res, next) => {
  try {
    const input = req.validated!.query as z.infer<typeof addonDeleteQuerySchema>;
    const addon = await addonService.removeAddon(req.user!.id, profileOf(req), input);
    if (!addon) {
      res.status(204).end();
      return;
    }
    res.json(serialize(addon));
  } catch (error) {
    next(error);
  }
});
