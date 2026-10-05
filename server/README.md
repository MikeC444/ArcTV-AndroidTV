# MangoTV server

Backend API sitting between the Fire TV app and Neon Postgres. The Fire TV
app never receives database credentials or talks to Postgres directly —
see [`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) at the repo root for
the full architecture writeup (system overview, every database table,
authentication flow, and how cloud synchronization works), and
[`docs/DEPLOYMENT.md`](../docs/DEPLOYMENT.md) for running a real instance
against Neon.

Endpoints:

- `GET /health` — unauthenticated liveness/DB-connectivity check.
- `POST /auth/register`, `POST /auth/login` — `{ email, password,
  deviceId, deviceName?, platform? }` → `{ accessToken,
  accessTokenExpiresAt, refreshToken, refreshTokenExpiresAt, user }`.
- `POST /auth/refresh` — `{ refreshToken }` → a fresh token pair; rotates
  and invalidates the one it was given.
- `POST /auth/logout` (authenticated) — revokes the calling session.
- `GET /auth/sessions` (authenticated) — the caller's own active sessions.
- `DELETE /auth/sessions/:id` (authenticated) — revoke one of them.
- `GET /user/me` (authenticated) — the caller's own profile.
- `POST /auth/qr/create` — `{ deviceId, deviceName?, platform? }` → `{
  token, activationUrl, expiresAt }`. Unauthenticated (the TV has no
  account yet); what the Fire TV app renders as a QR code.
- `GET /auth/qr/resolve?token=` — read-only status peek for the
  activation page (never issues tokens).
- `GET /auth/qr/status?token=` — the TV's poll endpoint. Returns real
  session tokens exactly once, the moment the activation page completes
  sign-in; every call after that reports the token as expired.
- `POST /auth/qr/complete` — `{ token, mode: "login"|"register", email,
  password, displayName? }`, called by the activation page.
- `GET /activate` — the activation page itself (`public/activate.html` +
  `activate.js`), served by this same backend so its calls to
  `/auth/qr/*` are same-origin and need no CORS configuration.
- `GET /user/settings` / `PUT /user/settings` (authenticated) — this
  account's Home Rows order/hidden state + Player autoplay/skip-intro
  preferences, plus the account's blocked genres (`blockedGenres`, a list
  of genre names; optional on `PUT` — an older client that omits it leaves
  the stored list alone), one row per account, last-write-wins on the
  client's own `updatedAt`.
- `GET /user/plus` (authenticated) — `{ active, plan, validUntil, paywall }`: whether this account has ArcTV Plus.
  With `PLUS_PAYWALL` off (the default) everyone is `active` with plan `early_access`; on, only paying accounts.
  `POST /user/plus/checkout` `{ plan: "monthly"|"yearly"|"lifetime" }` returns `{ url }`, a Stripe Checkout page tied
  to the account. `POST /stripe/webhook` (Stripe only, signature-verified) is what switches Plus on and off. See
  [`docs/PAYWALL.md`](../docs/PAYWALL.md).
- `GET /user/feedback?profileId=` / `POST /user/feedback` /
  `DELETE /user/feedback?profileId=&providerId=&contentId=&contentType=&updatedAt=`
  (authenticated) — Like / Not for me on movies, per profile (`main` by
  default), the input to "Picked for you". Same shape and last-write-wins
  rules as the watchlist: `GET` returns only feedback that is currently
  set, `POST` returns the slot's authoritative state, `DELETE` clears it
  (`204` if it never existed).
- `POST /user/player-events/external` (authenticated) — `{ providerId, contentId, contentType, title, releaseTitle?, resolution?, codec?, trigger: "button"|"error", outcome: "opened"|"no_player", errorMessage? }` → `204`. Reported when someone confirms "Play in external player"; read back by admins in `GET /admin/summary` (`externalPlayer`). Never carries the stream address.
- `GET /user/profiles` / `POST /user/profiles` / `PUT /user/profiles/:id` /
  `DELETE /user/profiles/:id` / `POST /user/profiles/:id/verify-pin`
  (authenticated) — ArcTV Plus profiles: up to 5 per account (any adult /
  kids mix), each with its own library. `GET` returns `{ profiles: [{ id,
  name, avatar, kind, hasPin, isDefault }] }`, the account's own profile
  (`main`, created on first use, never removed, never a kids profile)
  first. `POST` `{ name, avatar, kind, pin? }`, `PUT` `{ name?, avatar?,
  kind?, pin? | null }` (a string sets the 4-digit PIN, `null` removes it)
  and `DELETE` need Plus; `DELETE` also deletes the profile's library.
  `verify-pin` `{ pin }` answers `204` (right, or no PIN), `403` (wrong,
  never `401`) or `429` + `Retry-After` once five wrong tries in a row have
  locked guessing for five minutes. PINs are stored as argon2id hashes and
  never returned. The apps take care of asking for the PIN; this server
  only checks it.
- **`X-ArcTV-Profile: <id>`** (request header) — on `/user/settings`,
  `/user/watchlist`, `/user/watch-progress`, `/user/history`,
  `/user/continue-watching`, `/user/addons` and `/user/feedback`, names the
  profile the request is for; no header means the account's own profile
  (`main`), so every client that predates profiles is unchanged and all
  data saved before profiles belongs to `main`. The id must be one of the
  caller's own profiles (`404` otherwise, same answer for "unknown" and
  "somebody else's"), and any profile but `main` needs Plus (`403`). On
  `/user/feedback` the header wins over a `profileId` in the body/query.
  Account-level calls (`/user/me`, `/user/plus`, `/user/profiles…`) ignore
  it. The contract is written up in the web repo's `docs/PROFILES.md`.
- `GET /user/watchlist` (authenticated) — this account's active My List
  items.
- `POST /user/watchlist` (authenticated) — add (or un-remove/refresh) one
  item, keyed on `(providerId, contentId, contentType)`, last-write-wins
  on `updatedAt`.
- `DELETE /user/watchlist?providerId=&contentId=&contentType=&updatedAt=`
  (authenticated) — remove one item, same last-write-wins rule. `204` if
  the item was never synced from any device; otherwise `200` with the
  item's current state (which may mean the removal lost a race to a
  newer write elsewhere, reflected via a `null` `deletedAt`).
- `POST /user/watch-progress` (authenticated) — records one playback
  position report against both `watch_history` (durable per-episode log)
  and `continue_watching` (per-title resumable pointer) in a single
  transaction. `completed: true` clears the title's `continue_watching`
  row instead of updating it — and for a movie, so does crossing 85% of
  its `durationMs`, even if the report itself says `completed: false`.
  Returns `{ historyEntry, continueWatching }` (`continueWatching` is
  `null` when there was nothing to clear).
- `GET /user/continue-watching` (authenticated) — this account's active
  (resumable) titles, most recently watched first.
- `GET /user/history?limit=&before=` (authenticated) — this account's
  watch history, newest first, keyset-paginated on `watched_at` (`before`
  is an ISO-8601 timestamp cursor from a previous page's oldest item). No
  Fire TV screen consumes this yet — the app has no History browse screen
  — but the data is captured and available for one.
- `GET /user/addons` (authenticated) — this account's active installed
  addons, ordered by `sortOrder`.
- `POST /user/addons` (authenticated) — install (or un-remove/update) one
  addon, keyed on `manifestUrl`, last-write-wins on `updatedAt`.
  `manifestJson` caches the addon's manifest as opaque JSONB — validated
  only as "a JSON object," never interpreted server-side.
- `DELETE /user/addons?manifestUrl=&updatedAt=` (authenticated) — remove
  one addon, same last-write-wins rule and `204`/`200` contract as
  `DELETE /user/watchlist`.

## Setup

```
cd server
npm install
cp .env.example .env
# edit .env: set DATABASE_URL to your Neon (or local) Postgres connection string
```

## Running migrations

```
npm run migrate
```

Applies every file in `migrations/` that hasn't already been recorded in
the `schema_migrations` table, in filename order, each inside its own
transaction. Safe to re-run — already-applied migrations are skipped.

## Verifying the schema

```
npm run verify-schema
```

Confirms every expected table/index exists, then proves foreign keys,
unique constraints, and `ON DELETE CASCADE` actually behave as designed by
attempting real inserts/deletes that must succeed or fail. Everything runs
inside one transaction that's rolled back at the end, so this is safe to
run against a real (even shared) database — it never leaves data behind.

## Local development database

If you don't want to point at Neon while iterating, any local Postgres 16+
works identically (Neon is standard wire-compatible Postgres) — just point
`DATABASE_URL` at it instead, e.g.:

```
DATABASE_URL=postgresql://<user>:<password>@localhost:5432/<database>
```

## Running the API locally

```
npm run dev
```

Starts the server on `PORT` (default 3000) with auto-restart on file
changes. `npm run build && npm start` runs the compiled, non-watching
version the same way a deployment would.

## Running the automated API tests

Tests need their own disposable database — **never** point them at the
same database as `DATABASE_URL`, since the suite runs `TRUNCATE` between
every test:

```
cp .env.test.example .env.test
# edit .env.test: TEST_DATABASE_URL=postgresql://<user>:<password>@localhost:5432/<a throwaway database>
npm test
```

`npm test` always migrates that test database first (the `pretest`
script), then runs the suite against it. `TEST_DATABASE_URL` is
completely separate from `.env`'s `DATABASE_URL` — the test suite never
reads `DATABASE_URL` at all, so a real database configured there is never
at risk just because it was sitting in `.env` when tests ran. CI runs the
same suite against a throwaway `postgres:16` service container instead of
a local database, and needs no secret to do it.

## Running the full end-to-end test

```
npm run e2e
```

Drives a **real, running** server (start one first with `npm run dev`)
over real HTTP through all eight of this project's named multi-device
test scenarios in one continuous session — a genuinely different kind of
check than the unit tests above, which each verify one endpoint in
isolation. Safe to re-run against a persistent database; see
[`docs/TESTING.md`](../docs/TESTING.md) for the full walkthrough,
including the parts of those eight scenarios that need a real Fire TV
device rather than this script.
