/**
 * The "Streaming Catalogs" addon (a catalogue-only addon: it lists what is on Netflix, Disney+ and the other services, and offers no details or
 * streams of its own), which the server puts on every adult profile once so that Home can show the streaming-service tiles. Its author agreed to it
 * being a default. The link is the author's own configured one (the platforms are in the path, in the addon's format: nfx, dnp, amp ..., for the
 * region GB), so the addon can be updated at its host without a release here. Only what the apps read is kept, so the server can add it without
 * asking the network.
 */
export const STREAMING_CATALOGS_MANIFEST_URL =
  "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGRucCxhbXAsYXRwLGhibSxwbXAsaGx1LHBjcCxjcnUsbWdsLGN0cyxuZmssaGF5LGNsdixnb3AsamhzLHNzdCx2aWwsbmx6LHplZSxzdHosZHBlLG1iaSxzZ28sc29ueWxpdixtcDksdmlrLGNwZCxiYm8sc2hkLGJiYyxhbDQsY3JjLGlxaSxpdHYsYWN0Ojo6MTc5MjI0Njc1MzI5NTowOjA6R0I%3D/manifest.json";
export const STREAMING_CATALOGS_ADDON_ID = "pw.ers.netflix-catalog";
export const STREAMING_CATALOGS_MANIFEST: Record<string, unknown> = {
  "id": "pw.ers.netflix-catalog",
  "version": "1.1.1",
  "name": "Streaming Catalogs",
  "description": "Your favourite streaming services!",
  "catalogs": [
    {
      "id": "nfx",
      "type": "movie",
      "name": "Netflix"
    },
    {
      "id": "nfx",
      "type": "series",
      "name": "Netflix"
    },
    {
      "id": "dnp",
      "type": "movie",
      "name": "Disney+"
    },
    {
      "id": "dnp",
      "type": "series",
      "name": "Disney+"
    },
    {
      "id": "amp",
      "type": "movie",
      "name": "Prime Video"
    },
    {
      "id": "amp",
      "type": "series",
      "name": "Prime Video"
    },
    {
      "id": "atp",
      "type": "movie",
      "name": "Apple TV+"
    },
    {
      "id": "atp",
      "type": "series",
      "name": "Apple TV+"
    },
    {
      "id": "hbm",
      "type": "movie",
      "name": "HBO Max"
    },
    {
      "id": "hbm",
      "type": "series",
      "name": "HBO Max"
    },
    {
      "id": "pmp",
      "type": "movie",
      "name": "Paramount+"
    },
    {
      "id": "pmp",
      "type": "series",
      "name": "Paramount+"
    },
    {
      "id": "hlu",
      "type": "movie",
      "name": "Hulu"
    },
    {
      "id": "hlu",
      "type": "series",
      "name": "Hulu"
    },
    {
      "id": "pcp",
      "type": "movie",
      "name": "Peacock"
    },
    {
      "id": "pcp",
      "type": "series",
      "name": "Peacock"
    },
    {
      "id": "cru",
      "type": "movie",
      "name": "Crunchyroll"
    },
    {
      "id": "cru",
      "type": "series",
      "name": "Crunchyroll"
    },
    {
      "id": "mgl",
      "type": "movie",
      "name": "MagellanTV"
    },
    {
      "id": "mgl",
      "type": "series",
      "name": "MagellanTV"
    },
    {
      "id": "cts",
      "type": "movie",
      "name": "Curiosity Stream"
    },
    {
      "id": "cts",
      "type": "series",
      "name": "Curiosity Stream"
    },
    {
      "id": "nfk",
      "type": "movie",
      "name": "Netflix Kids"
    },
    {
      "id": "nfk",
      "type": "series",
      "name": "Netflix Kids"
    },
    {
      "id": "hay",
      "type": "series",
      "name": "Hayu"
    },
    {
      "id": "clv",
      "type": "movie",
      "name": "Clarovideo"
    },
    {
      "id": "clv",
      "type": "series",
      "name": "Clarovideo"
    },
    {
      "id": "gop",
      "type": "movie",
      "name": "Globoplay"
    },
    {
      "id": "gop",
      "type": "series",
      "name": "Globoplay"
    },
    {
      "id": "jhs",
      "type": "movie",
      "name": "JioHotstar"
    },
    {
      "id": "jhs",
      "type": "series",
      "name": "JioHotstar"
    },
    {
      "id": "sst",
      "type": "movie",
      "name": "SkyShowtime"
    },
    {
      "id": "sst",
      "type": "series",
      "name": "SkyShowtime"
    },
    {
      "id": "vil",
      "type": "movie",
      "name": "Videoland"
    },
    {
      "id": "vil",
      "type": "series",
      "name": "Videoland"
    },
    {
      "id": "nlz",
      "type": "movie",
      "name": "NLZIET"
    },
    {
      "id": "nlz",
      "type": "series",
      "name": "NLZIET"
    },
    {
      "id": "zee",
      "type": "movie",
      "name": "Zee5"
    },
    {
      "id": "zee",
      "type": "series",
      "name": "Zee5"
    },
    {
      "id": "stz",
      "type": "movie",
      "name": "Starz"
    },
    {
      "id": "stz",
      "type": "series",
      "name": "Starz"
    },
    {
      "id": "dpe",
      "type": "series",
      "name": "Discovery+"
    },
    {
      "id": "mbi",
      "type": "movie",
      "name": "Mubi"
    },
    {
      "id": "sgo",
      "type": "movie",
      "name": "Sky Go"
    },
    {
      "id": "sgo",
      "type": "series",
      "name": "Sky Go"
    },
    {
      "id": "sonyliv",
      "type": "movie",
      "name": "Sony Liv"
    },
    {
      "id": "sonyliv",
      "type": "series",
      "name": "Sony Liv"
    },
    {
      "id": "mp9",
      "type": "movie",
      "name": "Movistar+"
    },
    {
      "id": "mp9",
      "type": "series",
      "name": "Movistar+"
    },
    {
      "id": "vik",
      "type": "movie",
      "name": "Rakuten Viki"
    },
    {
      "id": "vik",
      "type": "series",
      "name": "Rakuten Viki"
    },
    {
      "id": "cpd",
      "type": "movie",
      "name": "Canal+"
    },
    {
      "id": "cpd",
      "type": "series",
      "name": "Canal+"
    },
    {
      "id": "bbo",
      "type": "movie",
      "name": "BritBox"
    },
    {
      "id": "bbo",
      "type": "series",
      "name": "BritBox"
    },
    {
      "id": "shd",
      "type": "movie",
      "name": "Shudder"
    },
    {
      "id": "shd",
      "type": "series",
      "name": "Shudder"
    },
    {
      "id": "bbc",
      "type": "movie",
      "name": "BBC iPlayer"
    },
    {
      "id": "bbc",
      "type": "series",
      "name": "BBC iPlayer"
    },
    {
      "id": "al4",
      "type": "movie",
      "name": "Channel 4"
    },
    {
      "id": "al4",
      "type": "series",
      "name": "Channel 4"
    },
    {
      "id": "crc",
      "type": "movie",
      "name": "Criterion Channel"
    },
    {
      "id": "iqi",
      "type": "movie",
      "name": "iQIYI"
    },
    {
      "id": "iqi",
      "type": "series",
      "name": "iQIYI"
    },
    {
      "id": "itv",
      "type": "movie",
      "name": "ITVX"
    },
    {
      "id": "itv",
      "type": "series",
      "name": "ITVX"
    },
    {
      "id": "act",
      "type": "movie",
      "name": "Acorn TV"
    },
    {
      "id": "act",
      "type": "series",
      "name": "Acorn TV"
    }
  ],
  "resources": [
    "catalog"
  ],
  "types": [
    "movie",
    "series"
  ],
  "idPrefixes": [
    "tt"
  ],
  "behaviorHints": {
    "configurable": true
  },
  "logo": "https://play-lh.googleusercontent.com/TBRwjS_qfJCSj1m7zZB93FnpJM5fSpMA_wUlFDLxWAb45T9RmwBvQd5cWR5viJJOhkI"
};
