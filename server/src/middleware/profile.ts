import type { NextFunction, Request, Response } from "express";
import { HttpError } from "../lib/httpError.js";
import { DEFAULT_PROFILE_ID, profileExists, requirePlus } from "../services/profileService.js";

const PROFILE_HEADER = "x-arctv-profile";
const PROFILE_ID = /^[A-Za-z0-9_-]{1,64}$/;

/**
 * Names the profile a library request is for, from the X-ArcTV-Profile header (no header = the account's own profile, so every
 * existing client keeps working untouched). Runs after requireAuth. The id must be one of THIS account's profiles (404 otherwise,
 * the same answer for "doesn't exist" and "somebody else's"), and any profile but the account's own needs ArcTV Plus.
 * Leaves req.profileId unset when the request names no profile, so a route can tell "not said" from "main".
 */
export async function resolveProfile(req: Request, _res: Response, next: NextFunction): Promise<void> {
  try {
    const raw = req.header(PROFILE_HEADER);
    if (raw === undefined || raw === "") {
      next();
      return;
    }
    if (!PROFILE_ID.test(raw)) {
      next(new HttpError(400, "Invalid X-ArcTV-Profile header"));
      return;
    }
    if (raw !== DEFAULT_PROFILE_ID) {
      if (!(await profileExists(req.user!.id, raw))) {
        next(new HttpError(404, "Profile not found"));
        return;
      }
      await requirePlus(req.user!.id);
    }
    req.profileId = raw;
    next();
  } catch (error) {
    next(error);
  }
}

/** The profile a request is for: the one it names, else the account's own. */
export const profileOf = (req: Request): string => req.profileId ?? DEFAULT_PROFILE_ID;
