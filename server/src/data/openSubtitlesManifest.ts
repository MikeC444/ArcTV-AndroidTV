/**
 * The official OpenSubtitles addon (https://opensubtitles-v3.strem.io/manifest.json), which the server puts on every profile once so the players'
 * Subtitles menu has files to offer. Only what the apps read is kept (it offers the "subtitles" resource for movies and series with IMDb ids), so the
 * server can add it without asking the network.
 */
export const OPENSUBTITLES_MANIFEST_URL = "https://opensubtitles-v3.strem.io/manifest.json";
export const OPENSUBTITLES_ADDON_ID = "org.stremio.opensubtitlesv3";
export const OPENSUBTITLES_MANIFEST: Record<string, unknown> = {
  id: OPENSUBTITLES_ADDON_ID,
  version: "1.0.0",
  name: "OpenSubtitles v3",
  description: "OpenSubtitles addon for Stremio: subtitles for movies and series.",
  resources: ["subtitles"],
  types: ["movie", "series"],
  idPrefixes: ["tt"],
  catalogs: [],
};
