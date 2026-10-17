import { STREAMING_CATALOGS_ADDON_ID } from "../data/streamingCatalogsManifest.js";

/**
 * Which apps can cope with a server-given addon, so deploying the server never breaks an app that is still behind.
 *
 * An addon here is only listed to a device whose platform is named and whose app version is at least the one given ("any" means every version).
 * A device on another platform, or one whose version isn't known, is not sent it: it simply never sees the addon, and a device that already has it
 * drops it on its next sync (a pull replaces the device's addon list). An addon NOT in this table is listed to everyone.
 *
 * Whenever the server starts giving every account something an older app can't handle (a catalogue-only addon, a new kind of addon), add it here
 * with the first version of each app that handles it, and add an app to its entry only once that app has shipped the support. Platforms are what the
 * apps send at sign-in: "web", "fire_tv"; the phone, Mac and Windows apps are added when they support the addon.
 */
export const ADDON_SUPPORT: Record<string, Record<string, string>> = {
  // Catalogue-only (no details of its own): titles open through other addons, which the Fire TV app only does from 0.6.1; the web app always has.
  [STREAMING_CATALOGS_ADDON_ID]: { web: "any", fire_tv: "0.6.1" },
};

/** Numeric compare of dotted versions ("0.6.1" < "0.10.0"); anything after a non-digit in a part is ignored. */
export function compareVersions(a: string, b: string): number {
  const parts = (v: string) => v.split(".").map((p) => Number.parseInt(p, 10) || 0);
  const x = parts(a);
  const y = parts(b);
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    const d = (x[i] ?? 0) - (y[i] ?? 0);
    if (d !== 0) return d < 0 ? -1 : 1;
  }
  return 0;
}

/** Whether a device on `platform` with app `version` should be sent this addon. */
export function addonSupportedBy(addonId: string, platform: string | null, version: string | null): boolean {
  const support = ADDON_SUPPORT[addonId];
  if (!support) return true;
  const minimum = platform ? support[platform] : undefined;
  if (minimum === undefined) return false;
  if (minimum === "any") return true;
  return version !== null && compareVersions(version, minimum) >= 0;
}
