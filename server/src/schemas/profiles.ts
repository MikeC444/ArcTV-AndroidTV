import { z } from "zod";

/**
 * The avatar ids the apps know; each app maps an id to its own artwork. The web app now offers the illustrated set (NEW_AVATAR_IDS);
 * the older colour-tile ids stay valid because existing profiles (and the Firestick app) still carry them.
 */
export const NEW_AVATAR_IDS = ["fox", "cat", "dog", "panda", "frog", "owl", "ghost", "robot", "alien", "astronaut", "raccoon", "penguin", "octopus", "dragon", "retro-tv", "lion"] as const;
export const LEGACY_AVATAR_IDS = ["sunrise", "ocean", "forest", "violet", "ember", "mint", "astro", "monster", "wave", "bolt"] as const;
export const AVATAR_IDS = [...NEW_AVATAR_IDS, ...LEGACY_AVATAR_IDS] as const;

/** An account can have this many profiles, in any mix of adult and kids. */
export const PROFILE_LIMIT = 5;

const nameSchema = z.string().trim().min(1, "Give the profile a name.").max(24, "Profile names are at most 24 characters.");
const pinSchema = z.string().regex(/^\d{4}$/, "A PIN is 4 digits.");

export const profileCreateBodySchema = z
  .object({
    name: nameSchema,
    avatar: z.enum(AVATAR_IDS),
    kind: z.enum(["adult", "kids"]),
    pin: pinSchema.optional(),
  })
  .strict();
export type ProfileCreateInput = z.infer<typeof profileCreateBodySchema>;

// pin: a string sets (or changes) it, null removes it, absent leaves it.
export const profileUpdateBodySchema = z
  .object({
    name: nameSchema.optional(),
    avatar: z.enum(AVATAR_IDS).optional(),
    kind: z.enum(["adult", "kids"]).optional(),
    pin: pinSchema.nullable().optional(),
  })
  .strict();
export type ProfileUpdateInput = z.infer<typeof profileUpdateBodySchema>;

export const profileIdParamsSchema = z.object({ id: z.string().regex(/^[A-Za-z0-9_-]{1,64}$/, "Not a profile id.") });

export const verifyPinBodySchema = z.object({ pin: pinSchema }).strict();
