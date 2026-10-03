# Arc TV

**Your streaming, your way, on the big screen** — a premium, Netflix-inspired streaming app for Amazon Fire TV / Firestick, built with Kotlin and Jetpack Compose.

(Formerly Mango TV. Code identifiers, the package name `com.mangotv.app` and this repository's name are unchanged.)

Arc TV is a modern media center that puts everything you love to watch in one place. Content comes from Stremio-protocol addons (`data/provider`, `data/addon`) rather than a fixed built-in catalog, so you choose the addons you want, the same way Stremio itself works.

## ✨ Features

- **Addon-powered** — discover movies and series from whichever Stremio-protocol addons you install
- **Made for the remote** — every screen is built for D-pad navigation, so you never need a mouse or touchscreen
- **Sync everywhere** — sign in on one Fire TV and your My List, Continue Watching, settings and addons follow you to any other
- **Easy sign-in** — scan a QR code and sign in from your phone instead of typing on the TV
- **Smooth playback** — Media3/ExoPlayer with HLS and DASH, plus in-player menus for quality, audio and subtitle tracks, playback speed and source info
- **Browse your way** — Home, Movies, TV Shows, Genres and Search, with a Detail page for every title
- **Picks for you** — a "Picked for you" row that learns your tastes
- **Stays up to date** — the app tells you when a new version is out and what changed (see `RELEASE_NOTES.md`)

## 🚀 Status

Arc TV is full-featured and actively growing. On top of the screens above, it has a complete **account, authentication, and cloud synchronization system**. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for how that system works, [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) for running your own backend instance, and [`docs/TESTING.md`](docs/TESTING.md) for how to verify it end-to-end (including reproducing the full multi-device test). `CHANGELOG.md` has the milestone-by-milestone development history.

## 🛠️ How it works

The app is Kotlin and Jetpack Compose on the Fire TV, talking to a small Node/Express/TypeScript backend (`server/`) that keeps your account and library in Neon Postgres. Catalogs and streams come from the addons you install.

## 📂 Project structure

```
app/src/main/java/com/mangotv/app/
  data/model/          Content, Genre, Episode, Season, WatchProgress — the shared metadata model
  data/provider/       CatalogProvider interface + ProviderRegistry (Stremio-style addon architecture)
  data/addon/          Stremio addon client/mapper + local-network addon pairing
  data/audio/          Boot chime + UI navigation/click sound playback and preferences
  data/auth/           Session/device identity, encrypted-at-rest session storage (Tink/Android Keystore)
  data/network/        OkHttp + kotlinx.serialization API clients talking to the backend (server/)
  data/sync/           Per-domain cloud sync (settings, watchlist, continue watching, addons), retry queues, account switching
  data/history/        Local Continue Watching cache
  data/player/         Local player-preferences cache
  ui/theme/            Colors, typography, motion tokens, dimens — the design system
  ui/components/       Reusable focusable primitives: TvFocusSurface, ContentCard, ContentRow, MangoButton, ArcLogo, loading/error states
  ui/loading/          Branded cold-boot loading screen
  ui/home/ ui/browse/ ui/detail/ ui/genres/ ui/search/ ui/mylist/ ui/sources/ ui/player/  The main app screens
  ui/player/overlay/   In-player menus: quality, audio/subtitle tracks, playback speed, source info, settings
  ui/auth/             Authentication gate, sign-in start screen, QR sign-in flow
  ui/settings/         Settings, Home Rows, Addons, Account
  navigation/          Jetpack Navigation-Compose routes/nav host

server/                Backend API (Node/Express/TypeScript) sitting between the app and Neon Postgres —
                        see docs/ARCHITECTURE.md and server/README.md
```

## 🏗️ Building

Requires Android Studio (or the command line with an Android SDK installed):

```
./gradlew assembleDebug
```

The debug APK is also built automatically by GitHub Actions on every push (`.github/workflows/build-apk.yml`) and uploaded as a workflow artifact, so a build is available for download without needing a local Android SDK.

## 📺 Installing on a Fire TV / Firestick

1. Enable **Settings → My Fire TV → Developer Options → Apps from Unknown Sources** and **ADB Debugging**.
2. `adb connect <firestick-ip>:5555`
3. `adb install app-debug.apk`

That's it — open Arc TV from your apps list, sign in, and start watching.

## 🤝 Contributing

Ideas, bug reports and pull requests are welcome. Open an issue to say hi or to tell us what you'd love to see next.
