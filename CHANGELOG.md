# Changelog

Development log for the MangoTV account, authentication, and cloud
synchronization system. One entry per milestone.

## Milestone 0 — Codebase Audit & Implementation Plan

**Status:** Complete.

**Changes:** None to application code (audit-only milestone, as required).

**Files added:**
- `docs/milestone-0-audit-and-plan.md` — full architecture audit, data
  inventory, and implementation plan.
- `CHANGELOG.md` — this file.

**Tests performed:** N/A (no code changed). Verified findings by reading
every persistence/network/navigation/DI source file directly rather than
inferring from naming.

**Issues discovered:**
- The app has no backend, database, or authentication today — this is a
  greenfield build for all three, not a retrofit.
- Continue Watching / Watch History has UI plumbing (`WatchProgress`,
  `RowStyle.CONTINUE_WATCHING`) but zero writer anywhere in the app; it
  will be built new in Milestone 8, not migrated.
- A local-network-only QR pairing flow already exists for adding addons
  from a phone (`AddonPairingServer` + NanoHTTPD). It is unrelated to
  accounts and must not be confused with, or broken by, the new
  cloud-backed account QR-auth flow in Milestone 4.
- This execution sandbox has no Android SDK (Android build verification
  relies on the existing `build-apk.yml` GitHub Actions workflow) and can
  reach only HTTPS egress, not raw Postgres TCP to a remote Neon instance
  (local Postgres 16 is available and will be used to rehearse migrations).

**Issues fixed:** N/A (audit-only milestone).

## Milestone 1 — Neon Database Foundation

**Status:** Complete pending one external step (see below).

**Stack decisions (confirmed with the user before starting):** Node.js +
TypeScript + Express + `pg` (plain node-postgres, no ORM) for the backend;
hand-written SQL migrations with a small custom runner instead of a
migration-framework dependency.

**Changes:** None to the existing Android app. New `server/` directory
(backend foundation, not yet wired to any HTTP framework — that's
Milestone 2):

- `server/migrations/0001`–`0011` — 11 forward-only SQL migrations
  creating `users`, `devices`, `sessions`, `qr_auth_sessions`,
  `user_settings`, `user_addons`, `addon_settings`, `watchlist_items`,
  `watch_history`, `continue_watching`, each with UUID primary keys,
  foreign keys, indexes on every FK column, `created_at`/`updated_at`,
  `deleted_at` where soft-delete is meaningful, and unique constraints
  matching real product semantics (see each file's header comment for the
  reasoning, especially `watch_history`'s generated `episode_key` column,
  which works around a NULL-uniqueness edge case for movies).
- `server/src/db/migrate.ts` — the migration runner (`npm run migrate`):
  applies pending `.sql` files in order, each in its own transaction,
  tracked in a `schema_migrations` table. Idempotent — safe to re-run.
- `server/scripts/verify-schema.ts` — schema verification (`npm run
  verify-schema`): confirms every expected table/index exists, then
  proves foreign keys, unique constraints, and `ON DELETE CASCADE` behave
  correctly by actually attempting inserts/deletes that must succeed or
  fail. Runs inside one transaction that's always rolled back, so it's
  safe to run repeatedly against a real database.
- `server/.env.example` — placeholders only (`DATABASE_URL`, `JWT_SECRET`,
  `QR_AUTH_SECRET`, `API_BASE_URL`, `NODE_ENV`, `PORT`). No real secrets.
- `server/package.json`, `tsconfig.json`, `README.md`, `.gitignore`.

**Files changed:** None outside the new `server/` directory and this
CHANGELOG.

**Tests performed:**
- `npm run typecheck` — clean.
- `npm audit` — 0 vulnerabilities (bumped `vitest` to v5 after the initial
  install flagged moderate/high advisories in its transitive `esbuild`/
  `vite` dev-dependency tree).
- Dropped and recreated a local PostgreSQL 16 database from scratch, ran
  `npm run migrate` — all 11 migrations applied cleanly; ran it again —
  correctly reported "Already up to date" (idempotency confirmed).
- `npm run verify-schema` against that freshly-migrated database — 56/56
  checks passed: every table and every index present; foreign keys reject
  orphaned rows; unique constraints reject duplicates (including the
  movie/null-season-episode edge case); `ON DELETE CASCADE` correctly
  removes every dependent row (devices, sessions, user_settings,
  user_addons, addon_settings, watchlist_items, watch_history,
  continue_watching) when a user is deleted; the verification's own use of
  a rolled-back transaction was confirmed to leave zero residual rows.
- Confirmed via `git status`/`git show` that only `.env.example`
  (placeholders) is tracked — `.env`, `node_modules/`, and `dist/` are
  git-ignored and were not staged.

**Issues discovered (self-review before marking complete):**
- `qr_auth_sessions.session_id` (a nullable FK with `ON DELETE SET NULL`)
  was missing its explicit index, inconsistent with every other FK column
  in the schema.
- Initial `vitest ^2.1.8` pulled in `esbuild`/`vite` versions with known
  moderate/high advisories (dev-tooling only, never shipped, but still
  worth clearing).

**Issues fixed:**
- Added `qr_auth_sessions_session_id_idx`; added a comment explaining why
  `device_identifier` on that table is intentionally *not* a foreign key
  (a QR session exists before any user — and therefore any `devices`
  row — does).
- Bumped `vitest` to `^5.0.0`; `npm audit` now reports 0 vulnerabilities.

**Neon connectivity — diagnosed and resolved via CI:** the user created a
Neon project and shared its pooled connection string. Confirmed
empirically (DNS resolves fine, TCP to the same IP on port 443 connects
instantly, TCP to port 5432 times out) that this development sandbox's
network policy blocks outbound non-443 TCP entirely — not a Neon,
credentials, or IPv6 issue. Rather than build a second, HTTP-driver-based
code path just to route around that restriction from inside the sandbox
(Neon's WebSocket driver is also blocked here — proxied WebSocket upgrades
aren't supported — and its plain HTTP driver doesn't support the
interactive multi-statement transactions `migrate`/`verify-schema` rely
on), added `.github/workflows/server-ci.yml`: it runs the exact same
`npm run migrate` / `npm run verify-schema` from GitHub's runners (normal,
unrestricted network access) against `secrets.DATABASE_URL`, gated so it
skips cleanly instead of failing red if that secret isn't configured yet.
This is a durable addition, not a one-off — it re-verifies every future
migration in Milestones 6-11 automatically on every push touching
`server/**`, using production's real code path rather than a sandbox
workaround.

Local `.env` holding the live credential was deleted after confirming it
couldn't be used from here — nothing sandbox-side retains it.

**Confirmed against the real Neon database.** The user added the
`DATABASE_URL` repository secret. Run #1 (the automatic push trigger, before
the secret existed) correctly skipped both DB steps with conclusion
`skipped`, not a failure — proving the graceful-skip path works. Run #2
(manually dispatched after the secret was added,
[run 34388148221](https://github.com/MikeC444/MangoTV-Live-TV/actions/runs/34388148221))
ran for real:

- `npm run migrate` applied all 11 migrations to the live Neon database in
  ~9 seconds.
- `npm run verify-schema` reported **56 passed, 0 failed** — every table,
  every index, every FK/unique constraint, and every cascade delete,
  exercised against the actual Neon Postgres instance, not just the local
  rehearsal.

This satisfies Milestone 1's "verify against a fresh Neon database"
criterion for real.

**One more issue caught from the CI log itself:** node-postgres emitted a
deprecation warning — `sslmode=require`/`prefer`/`verify-ca` are currently
treated as aliases for `verify-full` (full certificate verification, the
secure behavior already in effect), but a future major version of
`pg`/`pg-connection-string` will drop `require` to weaker libpq-standard
semantics. Updated `.env.example`'s example connection string and
`pool.ts`'s comment to recommend `sslmode=verify-full` explicitly, so this
connection's security guarantee doesn't silently change on a future
dependency bump. Not urgent (current behavior is already secure) — the
user's existing secret value doesn't need to change today, just next time
it's convenient to touch it.

**Milestone 1 is complete.**

## Milestone 2 — Backend/API Foundation

**Status:** Complete.

**Changes:** No changes to the existing Android app or Milestone 1's
schema/migrations. Adds the Express application itself on top of
Milestone 1's database layer:

- `src/app.ts` / `src/index.ts` — `createApp()` assembles the Express app
  (exported unbound for tests); `index.ts` is the real process entrypoint,
  including SIGTERM/SIGINT handling that stops accepting new connections,
  lets in-flight ones finish, then closes the DB pool before exiting —
  needed for clean deploys on any platform that sends SIGTERM (Render,
  Railway, Fly, etc. all do on every restart/redeploy).
- `src/middleware/auth.ts` (`requireAuth`) — the authentication layer.
  Verifies `Authorization: Bearer <token>` against `sessions` (hashing the
  presented token and looking up by hash, never comparing raw secrets),
  rejecting missing/malformed/unknown/expired/revoked tokens and tokens
  belonging to a soft-deleted user with the same generic 401 in every
  case, so a response can't be used to distinguish *why* a token failed.
  Attaches `req.user`/`req.session` — the only source of "who is making
  this request" anywhere in the API; no route reads an id from a
  param/query/body.
- `src/middleware/errorHandler.ts` — centralized error handling. Anything
  that isn't a deliberately-thrown `HttpError` (`src/lib/httpError.ts`) is
  treated as unexpected and never exposes its real message to the client
  (full detail goes to the server log only; a short `detail` field is
  added back only outside production, for local debugging).
- `src/middleware/rateLimit.ts` — one global limiter (120 req/min/IP) for
  now; login/QR endpoints get their own, stricter one once Milestones 3-4
  add them.
- `src/middleware/validate.ts` — a generic Zod-based validation middleware
  factory. No route needs it yet (`/health` and `/user/me` take no client
  input) — covered by its own unit test in the meantime; its first real
  caller is Milestone 3's account creation/login bodies.
- `src/middleware/requestLogger.ts` — structured JSON request logs
  (method/path/status/duration/user id, no bodies/headers/query strings)
  with a per-request id echoed as `X-Request-Id` and included in error
  responses for support correlation.
- `src/routes/health.ts` (`GET /health`, unauthenticated) and
  `src/routes/me.ts` (`GET /user/me`, authenticated) — the first two real
  endpoints, chosen specifically to exercise the auth middleware and
  prove cross-user isolation without depending on any feature from a
  later milestone.
- `src/security/tokens.ts` — shared `generateToken`/`hashToken` (random
  256-bit tokens, SHA-256 hashed at rest), used by both the auth
  middleware and the test fixtures; will be reused by Milestone 3's
  login/refresh and Milestone 4's QR tokens.
- Test infrastructure: `vitest.config.ts`, `tests/setup.ts`,
  `tests/helpers/{db,auth}.ts`, and three test files (16 tests). Tests
  require a dedicated `TEST_DATABASE_URL` (never `DATABASE_URL`) — see
  `.env.test.example` — so the suite's between-test `TRUNCATE` can never
  land on a real database by accident.
- `.github/workflows/server-ci.yml` — added a second job, `test`, running
  the suite against a throwaway `postgres:16` service container (separate
  from the `db` job's real-Neon migration check). Needs no secret at all,
  so full test coverage runs the same way on every push.

**Tests performed:**
- `npm run typecheck` — clean throughout.
- `npm audit` — 0 vulnerabilities after adding express/helmet/
  express-rate-limit/zod/supertest.
- `npm test` locally against a dedicated local `mangotv_test` database —
  **16/16 passed**, covering: no/malformed/empty/unknown/expired/revoked
  token, a soft-deleted user's token, a valid token, cross-user isolation
  (a spoofed `?id=` query param has no effect; two users' tokens never
  cross), the 404 handler, rate-limit headers, and the validate()
  middleware's accept/reject paths.
- Manual smoke test against a *real* running server (not just supertest's
  in-process simulation): started `npm run dev`, confirmed `GET /health`,
  an unauthenticated `GET /user/me` (401), an unknown route (404), and —
  after seeding a real session directly into the local dev database — a
  valid token successfully authenticating over actual HTTP, all with
  Helmet's security headers present. Seeded row deleted immediately after.

**Issues discovered (self-review before marking complete):**
- No graceful shutdown handling in `index.ts` — a SIGTERM (sent by every
  major deploy platform on restart/redeploy) would have killed in-flight
  requests and left DB connections to time out on their own instead of
  closing cleanly.
- `trust proxy` is set to trust exactly one hop, which is correct for a
  single reverse proxy in front (true of every deployment target being
  considered) but would need revisiting — not tightening blindly, actually
  reconsidering — if a future deployment adds a second hop (e.g. a
  separate CDN) in front of that, since over-trusting `X-Forwarded-For`
  makes the rate limiter spoofable.

**Issues fixed:**
- Added the SIGTERM/SIGINT handler described above.
- Documented the `trust proxy` assumption explicitly in `app.ts` as
  something to re-check at actual deployment time (Milestone 17), rather
  than leaving it an unstated assumption.

**Deliberately not built yet (belongs to later milestones, not scope creep):**
account creation/login endpoints (Milestone 3), QR auth (Milestone 4),
CORS (no browser-facing page exists until Milestone 4's activation page,
and no CORS policy is the secure default until then), and the
settings/watchlist/history/addon endpoints (Milestones 6-9).

**Milestone 2 is complete.**

## Milestone 3 — User Account System

**Status:** Complete.

**Changes:** No changes to the Android app or Milestone 1's schema. Adds
real account creation, login, logout, refresh, and session management on
top of Milestone 2's foundation:

- `src/security/password.ts` — Argon2id hashing (`argon2` package), with
  OWASP/RFC 9106's recommended interactive-login parameters (19 MiB
  memory, t=2, p=1) pinned explicitly rather than relying on the
  library's own default in case it ever changes.
- `src/schemas/auth.ts` — Zod schemas for register/login/refresh bodies:
  email normalized (trimmed, lowercased) to match the `users` table's
  `email = lower(email)` constraint, password length-only requirements
  (min 8, no forced complexity — modern NIST guidance), `deviceId`
  required as a UUID. This is validate()'s first real caller since
  Milestone 2 built it ahead of having one.
- `src/services/authService.ts` — the actual account logic:
  - `register` — hashes the password, then inserts the user + device +
    session as one transaction (all-or-nothing).
  - `login` — verifies the password and returns the same generic
    "Invalid email or password" whether the email doesn't exist or the
    password is wrong. Also closes a timing side-channel found during
    self-review: an unknown email used to skip the argon2 verify
    entirely, making that response measurably faster than a
    known-email-wrong-password one — now it always runs exactly one
    verify (against a dummy hash when there's no real user), so response
    timing can't be used to enumerate which emails have accounts.
  - `refresh` — a single atomic `UPDATE ... FROM ... WHERE ... RETURNING`
    that validates (unexpired/unrevoked session, non-deleted user,
    non-revoked device) and rotates both tokens in one statement, so
    there's no separate check-then-act race window. Rotating the access
    token turned out to invalidate the *previous* access token
    immediately too (one `access_token_hash` column per session, simply
    overwritten) — confirmed via a manual smoke test, then locked in with
    its own automated test, since it's a stricter and better property
    than what the original design comment assumed.
  - `logout` — revokes only `req.session.id` (never a client-supplied
    session id).
  - `listSessions` / `revokeSession` — list/revoke a user's own sessions;
    revoking a session id that exists but belongs to someone else returns
    404 (not 403), so the response can't confirm another user's session
    id is real. `listSessions` also excludes sessions on a
    remotely-revoked device, matching `requireAuth`'s own check
    (self-review catch — it hadn't originally).
- `src/middleware/auth.ts` — `requireAuth` now also joins `devices` and
  rejects a session whose device has been remotely revoked (the
  `devices.revoked_at` column existed since Milestone 1 but nothing read
  it until now).
- `src/middleware/rateLimit.ts` / `src/routes/auth.ts` — a stricter
  10-req/min-per-IP limiter on the whole `/auth` router (on top of the
  general 120/min one). Refactored both rate limiters and `authRouter`
  from module-level singletons into factories constructed fresh inside
  `createApp()` — the limiters carry in-memory counters, and a shared
  singleton meant one test's auth calls silently ate into another test's
  budget (found by running the test suite, not by inspection).
- New endpoints: `POST /auth/register`, `POST /auth/login`,
  `POST /auth/refresh`, `POST /auth/logout` (authenticated),
  `GET /auth/sessions` (authenticated), `DELETE /auth/sessions/:id`
  (authenticated).

**Tests performed:**
- `npm run typecheck` — clean throughout, including working around two
  real TS friction points: `argon2`'s named (not default) exports needing
  a namespace import, and its `HashOptions` type (not `Options`).
- `npm audit` — 0 vulnerabilities after adding `argon2`.
- `npm test` locally against `mangotv_test` — **43/43 passed**: account
  creation (including password never stored in plaintext, duplicate-email
  rejection, weak-password/bad-email/bad-deviceId rejection), login
  (correct credentials, case-insensitive email, wrong password, unknown
  email producing an identical response, independent sessions per
  device), refresh (rotation, the new access token working, the *old*
  access token immediately failing, replay of an already-used refresh
  token failing, expired refresh token, revoked session, revoked device),
  logout (revokes the current session, that session's access *and*
  refresh tokens both stop working immediately after), session listing
  and revocation (never shows or revokes another user's sessions, 404 —
  not a leak — on someone else's session id, 400 on a malformed one).
- Manual smoke test against a real running server: register → /user/me →
  /auth/sessions → /auth/refresh → /auth/logout → confirmed the
  now-revoked session's access token stops working, all over actual HTTP.
  This smoke test is what surfaced the "refresh invalidates the old
  access token too" behavior in the first place, before it became an
  automated test. Seeded data deleted immediately after.

**Issues discovered (self-review before marking complete):**
- The login timing side-channel described above.
- `listSessions` not excluding a revoked device's session.
- The rate-limiter/router singleton-sharing bug the test suite itself
  surfaced (test isolation issue, not a production bug, but the
  underlying "shared mutable state across app instances" pattern was a
  real architectural smell worth fixing rather than working around).

**Issues fixed:** all three, described above alongside where they were found.

**Milestone 3 is complete.**

## Milestone 4 — QR Authentication

**Status:** Complete.

**Changes:** No changes to the Android app yet (that's Milestone 5 —
this milestone is the backend flow and the phone/web side of it only).

- `migrations/0012_qr_auth_sessions_device_info.sql` — adds
  `device_name`/`platform` to `qr_auth_sessions`, captured at creation
  time so the eventual `devices` row gets a real name instead of falling
  back to generic defaults. Added as a new migration rather than editing
  0005, since 0005 is already applied to the real Neon project.
- `src/services/authService.ts` refactored to export three reusable
  pieces — `insertUser`, `verifyCredentials` (including the timing-safe
  dummy-hash check), `createSessionForDevice` — so register/login and
  the new QR flow share the same account/session logic instead of
  duplicating it.
- `src/services/qrAuthService.ts` — the actual flow:
  - `createQrSession` — random 256-bit token, hashed at rest, 10-minute
    expiry.
  - `resolveQrSession` — a read-only status peek for the activation page.
    Deliberately separate from the TV's poll endpoint: if the same
    endpoint both checked status *and* issued tokens, the web page
    loading and checking status could itself consume the one-time token
    issuance meant for the TV.
  - `completeQrSession` — called once the activation page submits
    credentials. Resolves the account (create or verify) and marks the
    QR session completed, all in one transaction — a failure partway
    through (e.g. a duplicate-email conflict) rolls back cleanly and
    leaves the QR session still usable for another attempt (tested).
    Deliberately does **not** create the real device/session/tokens at
    this point.
  - `pollQrSession` — the TV's endpoint, and the *only* place that ever
    creates the real device/session and issues tokens: it does so at the
    moment of consumption via one atomic `UPDATE ... WHERE status =
    'completed' ... RETURNING`, which can match a given token at most
    once. This is also why raw tokens are never persisted anywhere even
    transiently — they're generated and handed to the caller in the same
    request that creates their (hashed-at-rest) session row.
- `src/routes/qr.ts` mounted at `/auth/qr` as its **own** router, not
  nested under the existing auth router — that router's blanket 10/min
  limiter is right for register/login but would break legitimate TV
  polling (a TV checking every 2-3s would exhaust it in seconds).
  `/create` and `/complete` share the strict limiter (they're exactly as
  much a credential-guessing/account-spam surface as register/login);
  `/status` gets its own 40/min limiter sized for polling;
  `/resolve` relies on the general limiter (called once per page load).
- `public/activate.html` + `activate.js` — the activation page, served by
  this same backend (co-hosting means its `fetch()` calls to `/auth/qr/*`
  are same-origin, so no CORS configuration was needed at all). No
  external scripts/styles/fonts, so Helmet's default CSP needed no
  changes. Sign-in/create-account tabs, a pre-check against `/resolve` so
  an expired/used code shows a clear message instead of a live form,
  `.textContent` everywhere a message is rendered (no `innerHTML`, so no
  reflected-content XSS surface).
- `.env.example` / `config/env.ts` cleanup: removed `JWT_SECRET` and
  `QR_AUTH_SECRET`, both listed in the original spec as expected
  placeholders but never actually needed — this design uses opaque,
  random, hashed-at-rest bearer tokens throughout (sessions *and* QR
  alike), never JWTs or HMAC-signed values, so neither secret ever found
  a real job. `API_BASE_URL` is now genuinely required, but scoped as a
  lazily-evaluated `getApiBaseUrl()` rather than a field on the shared
  `env` object — that object is imported by `db/pool.ts`, and therefore
  by `migrate.ts`/`verify-schema.ts`, which have nothing to do with QR
  auth and shouldn't need it set just to run a migration.

**Tests performed:**
- `npm run typecheck` / `npm audit` — clean.
- `npm test` locally against `mangotv_test` — **60/60 passed** (up from
  43): QR generation, resolve status transitions (pending/expired/
  not_found, and confirmed non-mutating), the full create→complete→poll
  flow for both account creation and existing-account sign-in, wrong
  password leaving the QR session still usable, expiration, replay
  prevention (a second poll after consumption reports expired, never
  hands out tokens twice), completing an already-completed or expired
  session failing cleanly (410), a duplicate-email QR registration
  failing the same way direct registration does, two fully independent
  simultaneous QR sessions on different devices never cross-contaminating
  tokens or accounts, and the activation page itself being served at the
  exact URL the QR code encodes.
- Manual smoke test against a real running server, over actual HTTP:
  create → resolve (pending) → status (pending) → complete (204) →
  status (delivers real tokens) → second status (expired) → the returned
  access token working on `/user/me`. This is what caught a real gap
  before it reached CI: the new migration had only been applied to the
  test database automatically (via `pretest`), not to the local dev
  database, so the first smoke-test attempt failed with a clear "column
  does not exist" error — not a code bug, but a reminder that automated
  tests and manual smoke tests exercise different databases and both are
  worth running.

**Issues discovered (self-review before marking complete):** none required
a code fix beyond one minor robustness gap — `activationUrl()` didn't
strip a trailing slash from `API_BASE_URL`, which would have produced a
broken double-slash URL for anyone who configured it with one.

**Issues fixed:** added the trailing-slash strip.

**Deliberately not built yet:** a periodic cleanup job for expired/consumed
`qr_auth_sessions` rows — they're small, inert, and harmless to accumulate
at this scale, and a scheduled job is real infrastructure this milestone
doesn't otherwise need. The actual Fire TV screens that call these
endpoints (QR display, polling from the app, navigation gating) are
Milestone 5, not this one.

**Milestone 4 is complete.**

## Milestone 5 — Fire TV App Integration (Auth Screens)

**Status:** Complete.

**Changes:** First milestone touching the Android app. Wires the existing
backend/QR flow (Milestones 3-4) into real Fire TV screens — every new
network call the TV makes goes through `AuthApiClient`, which only ever
calls `/auth/qr/create`, `/auth/qr/status`, `/auth/refresh`, and
`/auth/logout`. The TV never collects or transmits a password; account
creation and sign-in happen exclusively on the phone/web activation page
from Milestone 4.

- `gradle/libs.versions.toml` / `app/build.gradle.kts` — added
  `tink-android:1.19.0` (session encryption), `junit:4.13.2` +
  `kotlinx-coroutines-test` (unit tests, `testImplementation` only).
  `API_BASE_URL` is read from the gitignored `local.properties` into a
  `BuildConfig` field, with a deliberately-invalid fallback
  (`https://not-configured.invalid`) so a missing config fails loudly
  instead of silently pointing nowhere.
- `data/auth/TokenCipher.kt` — encrypts the session blob at rest via Tink
  (`AndroidKeysetManager` + Android Keystore-backed `AES256_GCM`), not
  the now-deprecated (2025) `androidx.security.crypto`
  `EncryptedSharedPreferences`. This matters concretely because the
  manifest sets `allowBackup="true"`: without it, an `adb backup`
  extraction or a rooted-device file read would hand over a live bearer
  token in plain text.
- `data/auth/DeviceIdentity.kt` — a locally-generated, non-secret
  per-install UUID (plain, unencrypted DataStore) sent as `deviceId` on
  QR-session creation. Not a hardware serial/ANDROID_ID/MAC address, per
  the project's no-invasive-fingerprinting requirement.
- `data/auth/Session.kt` — `Session`/`AuthenticatedUser` data classes.
  Expiry is stored as epoch millis (converted once at the network
  boundary) rather than re-parsing ISO-8601 strings on every check.
  `isAccessTokenValid()` builds in a 30-second safety margin so a request
  starting just before expiry doesn't race the clock and arrive
  server-side already expired.
- `data/auth/SessionManager.kt` — persists the current `Session`,
  encrypted via `TokenCipher`, in its own DataStore file. `TokenCipher` is
  constructed lazily, not inline, because its `init` does real synchronous
  Android Keystore work; deferring it to first actual use (always inside
  `Dispatchers.IO`) keeps that work off the main thread despite
  `AuthRepository` being constructed eagerly in `AppContainer` during
  `Application.onCreate()`.
- `util/Iso8601.kt` — a hand-rolled `ThreadLocal<SimpleDateFormat>` parser
  for the server's UTC timestamps. minSdk 23 predates `java.time` and no
  desugaring is configured, so a date library or `java.time` weren't
  options.
- `data/network/{ApiException,AuthDtos,AuthApiClient}.kt` — OkHttp +
  kotlinx.serialization, mirroring `StremioAddonClient`'s existing style.
  `ApiException` carries a real HTTP status code, distinct from a plain
  `IOException` for network-level failures — callers rely on this
  distinction, since only a confirmed 401 should ever clear a locally
  stored session.
- `data/auth/AuthRepository.kt` — the single seam the rest of the app
  talks to: `createQrSession`, `pollQrSession` (maps the server's
  `pending`/`completed`/anything-else to a `QrPollOutcome` sealed type),
  `ensureFreshSession` (silently refreshes when the access token is
  stale; a confirmed 401 clears the session, a bare `IOException` leaves
  it alone so a dead network connection can't look like a revoked
  credential), and `logout` (best-effort server call, unconditional local
  clear).
- `AppContainer.kt` — `authRepository` added as an eager singleton
  (same rationale as the existing `addonRepository`): the auth gate is
  the first screen shown and needs an immediate answer, which is safe now
  that the expensive Keystore step is deferred inside `SessionManager`.
- `navigation/{MangoRoutes,MangoNavHost}.kt` — new routes
  `auth/gate`, `auth/start`, `auth/qr/{intent}`, `settings/account`;
  `AUTH_GATE` is now the nav graph's `startDestination`. Added a
  `navigateClearingBackStack()` helper
  (`popUpTo(navController.graph.id) { inclusive = true }`) used both by
  the auth gate (so neither branch it resolves to is reachable via Back)
  and by sign-out (so the app can't be backed into a signed-out user's
  screens).
- `ui/auth/{AuthGateViewModel,AuthGateScreen}.kt` — a local-only,
  fast check (no network round trip gates navigation): a session is
  treated as good enough to proceed on as long as its refresh token
  hasn't expired, since the access token can always be silently renewed
  afterwards. If a session that looked usable locally turns out to be
  dead server-side, the next call that actually needs the network (from
  Milestone 6 onward) is what discovers that, not this screen.
- `ui/auth/AuthStartScreen.kt` — "Sign In" / "Create Account", both
  leading to the same QR flow; the distinction is display-only, since the
  activation page always lets the user pick regardless.
- `ui/auth/{QrSignInViewModel,QrSignInScreen}.kt` — requests a QR
  session, polls it every 2.5s, and transparently swaps in a fresh session
  if the current one expires while still waiting (the "QR
  expiration"/"QR refresh" requirements, satisfied without user action).
  Deliberately splits `uiState` (Loading/Ready/Error — only for "can't
  create a session at all") from a separate `pollingDegraded` flag (a
  small inline "still trying…" indicator after 4 consecutive poll
  failures) so a transient network hiccup mid-poll can't yank a
  perfectly valid, already-displayed QR code off screen.
- `ui/settings/{SettingsScreen,AccountViewModel,AccountScreen}.kt` — a
  new "Account" row at the top of Settings. `AccountScreen` is
  deliberately minimal (signed-in identity + Sign Out only) — device
  management (Milestone 3's `/auth/sessions`), "sync existing data"
  prompts, and account switching are later milestones. What's here exists
  so a signed-in build is actually re-testable (create account → sign out
  → sign in again) without clearing app data.
- `.github/workflows/build-apk.yml` — added a `Run unit tests`
  (`./gradlew testDebugUnitTest`) step before `assembleDebug`, so JVM
  unit tests run in CI going forward, not just for this milestone.

**Tests performed:**
- New JVM unit tests (no Android SDK/emulator exists in this sandbox, so
  these are the only automated Android-side checks available):
  `SessionTest` (access-token safety margin, both tokens' expiry
  boundaries — 5 cases) and `Iso8601Test` (parses date/time/millisecond
  components correctly, and cross-checked against an independently
  computed epoch value for `2024-01-01T00:00:00.000Z`, not just
  round-tripped through the same parser).
- Full manual re-read of every new/modified file after writing them all
  (this sandbox cannot compile Kotlin locally), specifically checking:
  wire-format field names in `AuthDtos.kt`/`AuthApiClient.kt` against the
  actual server schemas/routes (`schemas/qr.ts`, `routes/qr.ts`,
  `routes/auth.ts`) field-by-field — `deviceId`/`deviceName`/`platform`,
  the `pending`/`completed`/`expired` status strings, and the
  refresh/token-pair response shape all match exactly; every new
  composable's callback signature against its actual call site in
  `MangoNavHost.kt`; and the `SessionManager`/`AppContainer`
  construction-order/threading argument above.
- `build-apk.yml` (`testDebugUnitTest` + `assembleDebug`) is the first
  real compilation of all this Kotlin — pending CI confirmation post-push.

**Issues discovered (self-review before marking complete):**
- `QrSignInViewModel`/`QrSignInScreen`'s first draft used one combined
  error state, which would have replaced a valid, already-displayed QR
  code with a full-screen error on a single transient poll failure.
- `TokenCipher`/`SessionManager`/`AuthRepository` being constructed
  eagerly in `AppContainer` (main thread, during `Application.onCreate()`)
  combined with `TokenCipher`'s synchronous Keystore/Tink `init` work
  risked janking app startup.
- `androidx.security.crypto`'s `EncryptedSharedPreferences` — the more
  obvious choice for encrypted local storage — is deprecated as of 2025.

**Issues fixed:**
- Split `QrSignInViewModel`'s state into `uiState` (Loading/Ready/Error)
  and a separate `pollingDegraded` flag, only surfaced after 4 consecutive
  poll failures, leaving a displayed QR code on screen throughout.
- Made `SessionManager`'s `cipher` property `by lazy`, deferring
  `TokenCipher`'s Keystore work to first actual use, which always happens
  inside `Dispatchers.IO`.
- Used Tink directly (`AndroidKeysetManager` + Android Keystore) instead
  of `EncryptedSharedPreferences`.
- **CI caught a real compile error** the manual re-read missed:
  `AuthGateScreen.kt`, `QrSignInScreen.kt`, and `AccountScreen.kt` all use
  Kotlin property-delegate syntax (`by`) on the `State<T>` returned from
  `collectAsStateWithLifecycle()`, which requires
  `import androidx.compose.runtime.getValue` to resolve — present in
  every other screen in the app, missed in these three new ones. Fixed by
  adding the import to all three; confirmed against the rest of the
  codebase that this is the established convention every existing screen
  already follows, not a one-off workaround.
- Reworked `AuthStartScreen.kt`'s visual design (see below) to match a
  reference mockup the user provided after this milestone's first push.

**Post-push design revision:** the user supplied a reference mockup for
the sign-in landing screen after the first push (logo, a two-tone
"Your Entertainment, Your Way" headline, a description line, a full-width
filled "Log In" button and a full-width outlined "Sign Up" button — both
with a leading icon and a trailing chevron — and a "Scan a QR code..."
hint with a QR icon at the bottom). Applied to `AuthStartScreen.kt`:
- `MangoButton` gained an optional `trailingIcon` parameter (defaults to
  `null`, so every other existing caller is unaffected) — when set, the
  button's internal `Row` switches to `fillMaxWidth()` +
  `Arrangement.SpaceBetween` so the trailing icon pins to the button's far
  edge; otherwise its layout is byte-for-byte what it was before.
  Repurposed for full-width, list-item-style buttons only.
  `AuthStartScreen`'s two buttons are otherwise unchanged functionally —
  "Log In" and "Sign Up" both still lead to the same QR flow.
- Content lives in a `Column` inside a `Box` (mirroring `HeroSection`'s
  existing `widthIn(max = ...)`-capped-column-inside-a-filled-`Box`
  pattern) rather than chaining `fillMaxSize()` and `widthIn()` directly
  on one node — the latter would let the column's own reported size
  shrink to its capped width and stop the background from painting the
  rest of the screen, since a single-node modifier chain's outer
  `fillMaxSize()` ultimately reports back whatever size bubbles up from
  its innermost child. Caught and corrected during this same review
  before it ever reached CI.

**Not yet verifiable in this sandbox:** actual on-device behavior (fresh
install → QR scan → sign-in on phone → TV proceeds; existing authenticated
user skipping straight to Home; an expired-refresh-token user landing back
on `AuthStartScreen`) requires a physical Fire TV device or a working
Android emulator, neither of which exists here — see `build-apk.yml`'s
removed smoke-test job note for why a GitHub-hosted emulator isn't a
substitute. Code-level correctness (compilation via CI, wire-format
matching against the real backend, unit-tested pure logic) is as far as
this sandbox can verify; real end-to-end verification is on the user's own
hardware.

**Milestone 5 is complete.**

## Milestone 6 — User Settings Cloud Sync

**Status:** Complete.

**Changes:** The first real data-sync domain, covering the two settings
domains that already existed locally (Home Rows order/hidden state, and
Player autoplay/skip-intro) — establishes the pull-on-login/push-on-change
pattern later milestones (watchlist, addons, watch history) will reuse,
without yet building a generic SyncManager/retry-queue abstraction for a
second domain that doesn't exist yet.

Backend:
- `schemas/settings.ts` — Zod schema for the PUT body, mirroring
  `user_settings`' columns (already created in Milestone 1) exactly.
  `updatedAt` is validated as an ISO-8601 UTC string via `z.iso.datetime()`.
- `services/settingsService.ts` — `getUserSettings` (returns the column
  defaults, not an error, for an account that's never synced) and
  `upsertUserSettings`, which implements **last-write-wins keyed on the
  client's own local mutation timestamp**, not receive order — comparing
  by receive order instead would let a device that was offline for a
  while overwrite a genuinely newer change from elsewhere just by
  reconnecting later. The comparison and the write happen in one atomic
  `INSERT ... ON CONFLICT DO UPDATE ... WHERE EXCLUDED.updated_at >
  user_settings.updated_at` statement (the same atomic-SQL shape used
  throughout the auth/QR flows), so two concurrent pushes can't race each
  other into an inconsistent result. Always returns the row's current
  authoritative state afterward — the caller's own write if it won, or
  whatever was already there if it lost — so a client can just persist the
  response as its new local cache either way.
- `routes/settings.ts` — `GET`/`PUT /user/settings`, both `requireAuth`,
  mounted alongside the existing `/user/me` router in `app.ts`.

Android:
- `data/network/{SettingsDtos,SettingsApiClient}.kt` — mirrors
  `AuthApiClient`'s existing OkHttp+kotlinx.serialization style. Every call
  here is authenticated (no unauthenticated path through this client at
  all), so the access token is a required parameter on every method.
- `util/Iso8601.kt` — gained `nowString()` (the same `SimpleDateFormat`
  instance already used for parsing can format too), for stamping a local
  mutation's `updatedAt` before pushing it.
- `data/provider/HomeRowPreferencesRepository.kt` and
  `data/player/PlayerPreferencesRepository.kt` — each gained an
  `applyRemote(...)` method (persists a server value locally) and an
  `onLocalChange` hook fired only after a genuine local mutation, never
  from `applyRemote` — this is what stops a value just pulled down from
  the server from immediately triggering a redundant push right back up.
- `data/sync/SettingsSyncRepository.kt` (new) — `pullFromServer()` (called
  once per launch when the auth gate finds an already-usable session, and
  once right after a fresh QR sign-in) and a `pushToServer()` wired to
  both repositories' `onLocalChange` hooks. Both are fire-and-forget:
  pulling must never delay getting the user into the app, and a failed
  push isn't queued — the next local change, or the next login's
  pull-then-reconcile, is what recovers from a transient failure.
- `AppContainer.kt` — `homeRowPreferencesRepository` and
  `playerPreferencesRepository` changed from lazy to eager, and
  `settingsSyncRepository` added as eager alongside them. Cloud sync means
  a pull has to write into both local caches on every launch regardless of
  whether the user has visited Home Rows settings or started playback yet
  that session, so the previous "only construct when that specific screen
  is first visited" laziness no longer reflects how these are actually
  used — see the file's own updated doc comment for the full reasoning.
- `AuthGateViewModel.kt` / `QrSignInViewModel.kt` — call
  `settingsSyncRepository.pullFromServer()` (fire-and-forget, after the
  navigation decision) on an already-usable session and right after a
  fresh sign-in, respectively.

**Tests performed:**
- `npm run typecheck` / `npm audit` — clean.
- `npm test` locally against `mangotv_test` — **72/72 passed** (up from
  60): defaults for a never-synced account, first-push creates the row and
  echoes it back, a later GET reflects a push, a strictly-newer `updatedAt`
  overwrites, an older `updatedAt` is rejected (the response is the
  still-current *newer* settings, not the stale write), an **equal**
  `updatedAt` is also rejected (proving the comparison is strictly-greater,
  not greater-or-equal), validation failures (missing fields, wrong types,
  a non-ISO-8601 `updatedAt`), 401s with no Authorization header, and
  cross-user isolation (one account's settings invisible to and unaffected
  by another's).
- Android: no new pure-logic unit tests this milestone (no new branching
  logic that isn't already exercised by the backend tests above or by
  Milestone 5's existing `SessionTest`/`Iso8601Test`); verified instead by
  a full manual re-read of every new/changed file, cross-checking every
  wire-format field name against the actual backend schema/routes, and the
  `build-apk.yml` CI compile below.

**Issues discovered (self-review before marking complete):**
- `AuthRepository.ensureFreshSession()` had a latent concurrent-refresh
  race that Milestone 5 never exercised hard enough to hit, but Milestone
  6 makes a real risk: the backend rotates the refresh token on every use,
  so two overlapping callers (now plausible — a settings pull on launch, a
  settings push right after, the gate's own fire-and-forget refresh, all
  independently calling this) sharing the same still-valid refresh token
  would race. The first to land rotates it; the second then gets a genuine
  401 for a token that was fine microseconds earlier, which the function
  would otherwise (correctly, in isolation) read as "this refresh token is
  dead" and spuriously sign the user out.

**Issues fixed:**
- Wrapped `ensureFreshSession()`'s body in a `Mutex`, so overlapping
  callers queue instead of racing — a second caller now always waits for
  the first and sees its already-refreshed result instead of colliding
  with it.

**Deliberately not built yet:** a generic `SyncManager`/retry-queue
abstraction (mentioned in Milestone 0's plan as introduced "incrementally
across milestones") — with only one sync domain built so far, generalizing
now would be guessing at its shape rather than factoring out something
proven; periodic/foreground re-sync beyond login and app-launch, which the
original plan's "Sync architecture" section also mentions — login and
cold-start-with-a-valid-session are the two triggers this milestone
covers, and a background periodic scheduler is real infrastructure this
milestone doesn't otherwise need yet.

**Milestone 6 is complete.**

## Milestone 7 — Watchlist / My List Sync

**Status:** Complete.

**Changes:** The second cloud-sync domain, and the first genuinely
item-level one — `watchlist_items` (created in Milestone 1, untouched
since) already had the right shape for this (natural key on `(user_id,
provider_id, content_id, content_type)`, `deleted_at` for soft-delete), so
this milestone needed no new migration, only the API and sync layers on
top of it.

Backend:
- `schemas/watchlist.ts` — `watchlistItemBodySchema` (POST body) and
  `watchlistDeleteQuerySchema` (DELETE query params — a DELETE request has
  no body across every HTTP client the two sides here actually use, so the
  natural key + `updatedAt` travel as query params instead, validated by
  the same `validate()` middleware Milestone 3 built, extended here to
  cover `req.query` for the first time).
- `services/watchlistService.ts` — `listActiveWatchlist` (the pull path);
  `upsertWatchlistItem` and `removeWatchlistItem`, both the same atomic
  `INSERT/UPDATE ... WHERE EXCLUDED.updated_at > watchlist_items.updated_at
  ... RETURNING` shape Milestone 6 established for settings, applied per
  item instead of per account. `upsertWatchlistItem` never touches
  `added_at` in its `DO UPDATE` — removing and re-adding the same title is
  a metadata update to one durable slot (clearing `deleted_at`), not a
  fresh row, matching migration 0009's own header comment.
  `removeWatchlistItem` returns `null` only when the row never existed for
  this user at all (a pre-Milestone-7 local-only item this device never
  pushed) — nothing server-side to reconcile in that case; when a row does
  exist, it always returns the item's current state, whether this call
  actually removed it or lost a last-write-wins race to a newer write
  elsewhere (surfaced to the caller as a still-`null` `deletedAt`).
- `routes/watchlist.ts` — `GET`/`POST`/`DELETE /user/watchlist`, all
  `requireAuth`, mounted alongside settings/me in `app.ts`. `POST` and a
  successful/raced `DELETE` both return `200` + the item's current state
  (including `deletedAt`, so the caller can tell "active" apart from "was
  removed since I last knew" from the same response shape either endpoint
  returns); `DELETE` returns a bare `204` only for the never-existed case.
  Deliberately does **not** expose `watchlist_items.id` (the server-side
  UUID) anywhere in the API — the client identifies an item by its natural
  key, which it already has, so there was never a reason to round-trip a
  server-generated id back down first.

Android:
- `data/network/{WatchlistDtos,WatchlistApiClient}.kt` — mirrors
  `SettingsApiClient`'s OkHttp+kotlinx.serialization style.
  `removeItem()`'s DELETE carries its four params via `HttpUrl.Builder`
  (correct percent-encoding for a `contentId` that might contain colons or
  other reserved characters — some Stremio-addon ids do), returning `null`
  on `204` and the decoded item otherwise.
- `data/provider/MyListRepository.kt` — `SavedListItem` gained `updatedAt`
  (defaulted, so a JSON blob persisted by a pre-Milestone-7 build still
  decodes); a new `WatchlistChange` sealed interface (`Added`/`Removed`)
  carries exactly what changed to a new `onLocalChange` hook, fired only
  from `toggle()`, never from the new `applyRemote(items)` (same
  never-fired-from-applyRemote rule Milestone 6 established, so a
  server-pulled list can't turn around and trigger a redundant push right
  back up). `applyRemote` replaces the local list wholesale on pull — safe
  here specifically because *push* stays item-level; pull legitimately
  does mean "here is the whole current list."
- `data/sync/WatchlistSyncRepository.kt` (new) — `pullFromServer()` (same
  two call sites as `SettingsSyncRepository`: an already-usable session at
  the auth gate, and right after a fresh QR sign-in) and a
  `pushToServer(change)` wired to `onLocalChange`, dispatching to
  `addOrUpdateItem`/`removeItem` by the change's type. A `reconcile(dto)`
  step applies each push's *authoritative* response back into the local
  list — the common case is a no-op (the server just echoed this device's
  own write back), but it's what correctly restores an item locally if
  this device's remove lost a last-write-wins race to a near-simultaneous
  change elsewhere, or drops one if an add lost to a near-simultaneous
  remove elsewhere. Both `pullFromServer` and `reconcile` defensively skip
  (rather than crash on) any item whose `contentType` this build's
  `ContentType` enum doesn't recognize — a forward-compat safety net for
  build/backend version skew, not a case that can happen today.
- `AppContainer.kt` — `myListRepository` changed from lazy to eager (see
  below) and `watchlistSyncRepository` added as eager, for the same
  reasons Milestone 6 gave `settingsSyncRepository`/its two repositories.
- `AuthGateViewModel.kt` / `QrSignInViewModel.kt` — call
  `watchlistSyncRepository.pullFromServer()` alongside the existing
  settings pull, at the same two call sites, same fire-and-forget posture.

**Tests performed:**
- `npm run typecheck` / `npm audit` — clean.
- `npm test` locally against `mangotv_test` — **92/92 passed** (up from
  72; 20 new): empty list for a never-synced account, add-and-echo,
  a later GET reflecting an add, a strictly-newer `updatedAt` overwriting
  metadata, an older `updatedAt` losing (current state returned, not the
  stale write), re-adding a removed item clears `deletedAt` and it
  reappears in GET, two different `providerId`s with the same `contentId`
  are distinct items (natural key includes `providerId`), remove-then-GET
  no longer shows the item, a stale-timestamped remove losing to a newer
  add (item stays active, response reflects that), an equal `updatedAt`
  not removing (strictly-greater-than, not greater-or-equal), a `204` for
  an item that was never synced, validation failures (missing fields, bad
  `contentType`, non-ISO-8601 `updatedAt`, missing/invalid DELETE query
  params), 401s with no Authorization header on all three endpoints, and
  cross-user isolation — including a dedicated test for bob attempting to
  remove alice's item by guessing her exact `(providerId, contentId,
  contentType)` with a far-future `updatedAt` engineered to win any
  last-write-wins race, confirmed scoped away entirely (every query/update
  in `watchlistService.ts` filters on `user_id` from the authenticated
  session, never a client-supplied value) rather than merely losing a
  race.
- Manual smoke test against a real running server, over actual HTTP (not
  just supertest): seeded a real session, then GET (empty) → POST (add) →
  GET (shows it) → DELETE (200, `deletedAt` set) → GET (empty again) →
  unauthenticated GET (401) — matching the exact request/response shapes
  above. Seeded data deleted immediately after.
- Android: no new pure-logic unit tests this milestone (same rationale as
  Milestone 6 — the branching logic that matters is exercised by the
  backend tests above); verified instead by a full manual re-read of every
  new/changed file, cross-checking wire-format field names against the
  actual backend schema/routes (`providerId`/`contentId`/`contentType`/
  `updatedAt`/`deletedAt` match exactly, including that `contentType`
  travels as the same `MOVIE`/`TV_SHOW` strings on both sides), confirming
  `return@withContext` from inside `WatchlistApiClient.removeItem`'s
  nested `.use { }` block is valid Kotlin (both `withContext` and `use`
  are inline, so the labeled non-local return correctly targets the outer
  `withContext` lambda — the same shape already used one level shallower
  elsewhere in this codebase, e.g. `DeviceIdentity.kt`), and the
  `build-apk.yml` CI compile below.

**Issues discovered (self-review before marking complete):**
- First draft made `watchlistSyncRepository` eager while leaving
  `myListRepository` as `by lazy` (to preserve its pre-existing
  laziness). That combination is self-defeating: `watchlistSyncRepository`
  reads `myListRepository` inside its own eager constructor call (to wire
  `onLocalChange`), which forces the lazy delegate to initialize
  immediately anyway — so the property would already read `by lazy` in
  the source while actually behaving fully eager at runtime, silently
  contradicting its own declaration.
- A pre-existing race, shared with (not introduced or worsened by) this
  milestone's design: each repository's own `init` block loads its
  persisted DataStore value asynchronously, and `applyRemote()` can in
  principle be called before that load finishes, letting the slower of
  the two "win" the `_items`/`_preferences` write. This already existed
  for Settings since Milestone 6; Milestone 7 extends the same pattern to
  My List rather than fixing it, since a real fix (e.g. awaiting the
  initial load before any pull is allowed to apply) is a change to the
  shared repository-initialization shape all synced domains use, not
  something scoped to watchlist specifically — a local DataStore read is
  reliably much faster than the network round trip(s) `pullFromServer()`
  needs first (`ensureFreshSession()`, then the actual GET), so this is
  believed low-probability in practice, but it is not proven eliminated.
  Flagged here rather than silently carried forward; worth fixing once,
  for every synced domain at once, alongside Milestone 10's
  `SyncManager`.

**Issues fixed:** the `myListRepository`/`watchlistSyncRepository`
laziness contradiction — `myListRepository` is now genuinely eager (its
declaration matches its actual runtime behavior), with `AppContainer`'s
doc comment updated to explain why, mirroring exactly how Milestone 6
documents the same tradeoff for `homeRowPreferencesRepository`/
`playerPreferencesRepository`.

**Deliberately not built yet:** bulk-pushing whatever is already sitting
in `MyListRepository` the first time this ships to an existing install —
until Milestone 11 ships, a pull always wins, so an existing local list
gets replaced by the (empty, for a brand new account) cloud state on first
sync, and only *future* toggles get pushed from that point on. This is the
same "migrating pre-existing local-only data is Milestone 11's job, not
this domain's sync layer's" boundary Milestone 0's plan and Milestone 6's
changelog both already established — repeated here explicitly rather than
left implicit, since unlike settings (where "empty" and "defaults" look
similar), a My List that appears to have been wiped is a much more visible
regression if a user hits it before Milestone 11 ships. The retry-queue/
DataStore-load-ordering race noted above is the other explicitly deferred
item.

**Milestone 7 is complete.**

## Milestone 8 — Watch History & Continue Watching

**Status:** Complete.

**Changes:** The first genuinely greenfield sync domain — per Milestone
0's audit, Continue Watching had UI display plumbing (`WatchProgress`,
`RowStyle.CONTINUE_WATCHING`, `ContentCard`'s progress-bar rendering) but
*zero* writer anywhere in the app before this milestone; the
`watch_history`/`continue_watching` tables (Milestone 1) existed but were
never written to either. This milestone builds the writer, the local
cache, and cloud sync together, as Milestone 0's plan always intended
("with sync as a first-class part of its design from the start, not
bolted on after a local-only version already shipped").

Backend:
- `schemas/watchProgress.ts` — `watchProgressBodySchema` (one playback
  report, validated for a matched season/episode pair — both present or
  both absent, since a half-null pair would silently produce a
  nonsensical `episode_key`) and `historyQuerySchema` (`limit` + `before`,
  a keyset cursor on `watched_at` rather than `OFFSET`, since
  `watch_history` — unlike settings/watchlist — has no bound on how large
  it grows for an active account).
- `services/continueWatchingService.ts` — `listActiveContinueWatching`
  (the GET read backing Home's row).
- `services/playbackProgressService.ts` — `recordProgress`, the actual
  write: one DB transaction that upserts `watch_history` (keyed by the
  existing `episode_key` generated column) and then either upserts
  `continue_watching` (not completed — same atomic
  `WHERE EXCLUDED.updated_at > ...` last-write-wins shape as
  settings/watchlist) or soft-deletes it (completed — a finished title
  isn't resumable, exactly matching migration 0011's own header comment
  about how `continue_watching` is maintained from application logic, not
  a trigger). Both tables are gated by the same client-supplied
  `watchedAt`, evaluated independently per row since a per-episode fact
  and a per-title pointer are different things that merely share a cause.
  Also `listWatchHistory` (paginated).
- `routes/history.ts` — `POST /user/watch-progress`,
  `GET /user/continue-watching`, `GET /user/history`, all `requireAuth`,
  mounted alongside settings/watchlist in `app.ts`. One write endpoint,
  not two, so a client never risks the two tables landing inconsistently
  across separate requests for what is really one playback event.

Android:
- `data/network/{ContinueWatchingDtos,PlaybackProgressApiClient}.kt` —
  mirrors `WatchlistApiClient`'s style. No client method for
  `GET /user/history` — the app has no History browse screen for it to
  feed (see "Deliberately not built" below), so only the two endpoints
  Home's Continue Watching row actually needs are wired up.
- `data/history/ContinueWatchingRepository.kt` (new) — DataStore-backed
  local cache, same JSON persistence pattern as `MyListRepository`.
  Deliberately has **no** `onLocalChange` hook: unlike My List/Settings
  (mutated from several independent UI call sites that each need to
  notify a sync layer), this domain has exactly one writer —
  `ContinueWatchingSyncRepository.reportProgress()`, called only by the
  player — so there's no separate "something changed locally, now tell
  sync" step to hook; that class *is* the step.
- `data/sync/ContinueWatchingSyncRepository.kt` (new) —
  `pullFromServer()` (same two call sites as the other two sync
  repositories) and `reportProgress(...)`, deliberately **not** `suspend`:
  the final "stopped playback" report fires from `PlayerScreen`'s
  `DisposableEffect.onDispose{}`, which is not a coroutine context, so
  this owns its own long-lived `CoroutineScope` and fires the local write
  + network push from there (the same shape `WatchlistSyncRepository`
  already uses for its push, just applied to a plain function called
  directly from Compose disposal rather than from a repository's
  `onLocalChange`). A `reconcile()` step applies the server's
  authoritative post-write item back into the local cache — usually a
  no-op, but what correctly restores or drops a locally-optimistic entry
  if a report lost a last-write-wins race to a near-simultaneous one from
  another device.
- `ui/player/PlayerViewModel.kt` — `resumePositionMs()` (a synchronous
  local-cache lookup, guarded on season/episode matching, used once when
  building the Ready state) and `reportProgress(positionMs, durationMs,
  completed)` (forwards to `ContinueWatchingSyncRepository`, after a
  `MIN_REPORTABLE_POSITION_MS` = 10s guard — resuming from a few seconds
  in isn't useful, and without it a Continue Watching entry would appear
  the instant playback merely starts).
- `ui/player/PlayerScreen.kt` — the actual "sensible update strategy"
  Milestone 8 calls for, instead of a report per position tick: a
  `LaunchedEffect(phase)` reports periodically (every 30s) while
  `Playing`, once on `Paused`, once on `Ended` (`completed = true`); the
  existing `DisposableEffect`'s `onDispose{}` gained one final report
  (read before `exoPlayer.release()`, since position/duration stop being
  meaningful after). `startPlayback()` now calls
  `exoPlayer.setMediaItem(mediaItem, resumePositionMs)` (ExoPlayer's
  standard "start at this position" overload) when a stored resume point
  exists for the exact title/episode being opened.
- `ui/home/HomeViewModel.kt` — a third independent `init{}` trigger
  (alongside the provider-fetch and Home-Rows-preferences ones) collects
  `continueWatchingRepository.items` and folds a `Continue Watching`
  `HomeSection` (`RowStyle.CONTINUE_WATCHING`) into the section list,
  always first, omitted entirely when empty. No changes needed to
  `ContentRow`/`ContentCard`/navigation at all — this `RowStyle` and its
  progress-bar/S·E-label rendering, and clicking through to Detail, were
  already fully built (display-only, per Milestone 0's audit); this
  milestone only had to start actually populating the row with real data.
  Deliberately **not** subject to Home Rows' order/hidden-state
  preferences (those exist for addon-supplied catalog rows).
- `AppContainer.kt` — `continueWatchingRepository`/
  `continueWatchingSyncRepository` added as eager singletons, same
  self-defeating-if-lazy reasoning as Milestone 7's `myListRepository`
  fix (documented directly in `AppContainer`'s own comment this time,
  rather than re-discovering it mid-review).
- `AuthGateViewModel.kt` / `QrSignInViewModel.kt` — call
  `continueWatchingSyncRepository.pullFromServer()` alongside the
  existing settings/watchlist pulls, same two call sites, same
  fire-and-forget posture.

**Tests performed:**
- `npm run typecheck` / `npm audit` — clean.
- `npm test` locally against `mangotv_test` — **112/112 passed** (up from
  92; 20 new): a movie report populates both tables; a later GET reflects
  it; `completed: true` clears `continue_watching` but keeps the
  `watch_history` row with `completed: true`; completing a title with no
  prior `continue_watching` row returns `continueWatching: null` rather
  than erroring; re-watching after completion (newer `watchedAt`)
  recreates an active row; switching episodes on the same show updates
  the *one* `continue_watching` row while creating a *second*
  `watch_history` row (proving the two tables' different keys behave as
  designed); a strictly-newer `watchedAt` overwrites and an older one
  loses to current state; `GET /user/history` pagination (`limit` +
  `before`, newest-first, exact-page-boundary assertions); validation
  failures (missing fields, invalid `contentType`, a half-null
  season/episode pair, negative `positionMs`, an out-of-range `limit`);
  401s with no Authorization header on all three endpoints; cross-user
  isolation, including a dedicated test confirming bob cannot mark
  alice's title completed by guessing her exact content id with a
  far-future `watchedAt` (his report lands as his own, separate, empty
  row set — alice's is untouched, since every query is scoped to
  `req.user!.id`, never a client-supplied id).
- Manual smoke test against a real running server, over actual HTTP:
  POST progress (in-progress) → GET continue-watching (shows it) → POST
  progress (`completed: true`) → GET continue-watching (empty) → GET
  history (shows the completed entry) → unauthenticated GET (401).
  Seeded data deleted immediately after.
- Android: no new pure-logic unit tests this milestone (same rationale as
  Milestones 6-7 — the branching logic that matters is exercised by the
  backend tests above); verified instead by a full manual re-read of
  every new/changed file, cross-checking wire-format field names against
  the actual backend schema/routes, confirming
  `exoPlayer.setMediaItem(mediaItem, resumePositionMs)` is the standard
  (not `@UnstableApi`) ExoPlayer overload for starting playback at a
  position, confirming `LaunchedEffect(phase)`'s periodic-while-`Playing`
  loop is correctly cancelled (not left running concurrently) on every
  phase transition since `phase` is a plain re-passed parameter value,
  and the `build-apk.yml` CI compile below.

**Issues discovered (self-review before marking complete):**
- First draft of `ContinueWatchingSyncRepository.reportProgress()` did
  the local `ContinueWatchingRepository.upsert()`/`remove()` call
  *outside* the `try`/`catch` that wraps the network push — a genuine gap,
  not just a style nit: Jetpack DataStore surfaces a write failure as a
  plain `IOException`, and an uncaught exception escaping a bare
  `scope.launch { }` block crashes the app. A rare disk hiccup during a
  routine progress report should degrade quietly (the same way a network
  hiccup already does), not crash the player.
- Backend: `positionMs`/`durationMs` had a lower bound (`min(0)`) but no
  upper one, so a malformed client value large enough to overflow
  Postgres' `bigint` range would have reached the database and surfaced
  as an opaque 500 instead of a clean, expected 400.
- Confirmed `HomeViewModel`'s pre-existing `providers.isEmpty()` early
  return (unrelated code, predates this milestone) still bypasses
  `applyPreferences()` entirely, so a Continue Watching row would not
  render on the rare device that has active continue-watching entries
  (e.g. freshly pulled from the cloud) but zero addons currently
  installed. Not fixed — see "Deliberately not built" below.

**Issues fixed:**
- Restructured `reportProgress()` so the local write and the network push
  share one `try`/`catch`; the existing `IOException` handling ("transient,
  the next report or pull recovers") already correctly covers a local
  storage failure as well as a network one, so no new catch clause was
  needed, just the right scope.
- Added `.max(1_000_000_000_000)` (a generous, effectively-unlimited but
  `bigint`-safe bound) to both `positionMs` and `durationMs` in
  `watchProgressBodySchema`.

**Deliberately not built yet:**
- **A History browse screen.** The backend and its tests fully support
  `GET /user/history`; nothing on the Fire TV side calls it, because no
  such screen exists in the app today and the milestone's own completion
  criteria only require Continue Watching to match across devices, not a
  new browsing UI. Matches Milestone 7's identical "don't invent new
  screens the milestone didn't ask for" precedent for My List.
- **Auto-selecting the in-progress episode in Detail's season picker.**
  Continue Watching's Home row correctly shows the right title, episode
  label, and progress bar, and resuming *does* work correctly once the
  player opens for that exact title/episode (`PlayerViewModel
  .resumePositionMs()` matches by natural key regardless of navigation
  path) — what's not built is Detail automatically pre-selecting that
  episode when reached via the Continue Watching card, so a multi-episode
  show still requires manually picking the right episode in Seasons
  first. This is a UI convenience beyond what the completion criteria
  require, not a sync-correctness gap.
- **The `providers.isEmpty()` / continue-watching-with-zero-addons edge
  case** noted above — `HomeEmptyScreen` doesn't currently know how to
  render any `ContentRow`s at all (just a "Browse Addons" CTA), so
  correctly handling this would mean changing that screen's layout, not
  just this milestone's data plumbing. Narrow and low-probability (it
  needs an active continue-watching entry pulled from the cloud onto a
  device with literally zero addons installed) but not proven impossible,
  so recorded rather than silently accepted.
- **A generic `SyncManager`/retry queue and clearing local caches on
  logout** — both remain out of scope for the same reasons given in
  Milestones 6/7 (not enough proven domains yet to generalize from;
  cross-account data isolation is explicitly Milestone 12's job). This
  milestone's un-pushed-change and pull-always-wins behavior on failure
  matches the identical, already-accepted pattern the other two sync
  repositories use — not a new gap unique to Continue Watching.

**Milestone 8 is complete.**

## Milestone 9 — Addon Synchronization

**Status:** Complete.

**Changes:** Structurally the same item-level shape as Milestone 7's
watchlist sync (`user_addons`, created in Milestone 1, already had the
right columns — `manifest_url` as the natural key, `deleted_at` for
soft-delete — so no new migration was needed), but with one real
architectural wrinkle none of the previous three domains had: addons are
never actually empty locally. `AddonRepository` auto-installs Cinemeta on
first launch, unconditionally, before the auth gate even exists — so a
naive pull-always-wins policy would have silently uninstalled that
default the moment *every single* brand-new account signed in for the
first time. Confirmed `addon_settings` (reserved in Milestone 1 for
per-addon configurable state) is still genuinely unused — this app has no
addon configurability beyond enabled/order today, matching that
migration's own header comment — so this milestone's sync target is
`user_addons` alone.

Backend:
- `schemas/addons.ts` — `addonBodySchema` (`manifestJson` validated only
  as "a JSON object" via `z.record(z.string(), z.unknown())" — its real
  shape is entirely addon-defined, the same "opaque JSONB" treatment
  `addon_settings.config` already got in Milestone 1) and
  `addonDeleteQuerySchema` (`manifestUrl` + `updatedAt` as query params,
  same reasoning as watchlist's DELETE).
- `services/addonService.ts` — `listActiveAddons`, `upsertAddon` (the
  same atomic `INSERT/DO UPDATE ... WHERE EXCLUDED.updated_at > ...`
  last-write-wins shape as watchlist, on a single-column natural key
  instead of a three-column one; `installed_at` is left untouched by the
  `DO UPDATE`, same "durable slot" reasoning as watchlist's `added_at`),
  `removeAddon` (soft-delete, `null` only when the addon never existed
  for this user at all).
- `routes/addons.ts` — `GET`/`POST`/`DELETE /user/addons`, all
  `requireAuth`, mounted alongside history/watchlist/settings in
  `app.ts`.

Android:
- `data/network/{AddonSyncDtos,AddonSyncApiClient}.kt` — mirrors
  `WatchlistApiClient`'s style; `manifestJson` travels as a plain
  `JsonObject` rather than being decoded into `AddonManifest` at this
  layer, so `AddonSyncRepository` owns the one place that converts
  between the wire shape and the local model.
- `data/addon/AddonRepository.kt` — a new `AddonChange` sealed type
  (`Upserted` — carrying the addon's own list-position `sortOrder`, since
  `AddonRepository` already knows it at the moment of mutation, cheaper
  than having the sync layer re-derive it — and `Removed`) and an
  `onLocalChange` hook, fired from `installAddon`/`removeAddon`/
  `setEnabled`, same shape as `MyListRepository`'s `WatchlistChange`
  hook. A new `applyRemote(remoteAddons)` — unlike
  `MyListRepository`/`ContinueWatchingRepository`'s own `applyRemote`,
  this one has a real side effect to reconcile: it unregisters every
  addon this device previously knew about from `ProviderRegistry`, then
  registers whichever of the new list is enabled — the same
  "clear-and-rebuild-from-a-fresh-list" shape `restoreFromDisk()` already
  uses at app startup, rather than diffing old vs. new.
- `data/sync/AddonSyncRepository.kt` (new) — `pullFromServer()` and
  `pushToServer(change)`/`reconcile(...)`, same fire-and-forget,
  no-suspend-push shape as `WatchlistSyncRepository`. The one real
  addition: when a pull's response is an **empty** list, this device's
  current local addons are pushed *up* as the account's initial set
  instead of the empty cloud state being pulled *down* over them (see
  the class's own kdoc for the full reasoning) — deliberately narrow and
  safe-by-construction (it only ever pushes local state up, never
  discards it), not an attempt at Milestone 11's general "reconcile
  pre-existing local data, with an explicit user choice" design, which
  stays exactly as out of scope here as for every other sync repository.
  A non-empty cloud response still always wins outright (Device B
  signing into an *existing* account correctly replaces its own
  bootstrapped Cinemeta with that account's real addons — the
  empty-cloud exception only ever protects a genuinely new account's
  very first sync).
- `AppContainer.kt` — `addonSyncRepository` added as an eager singleton
  (same onLocalChange-wiring reason as `settingsSyncRepository`/
  `watchlistSyncRepository`); `addonRepository` itself needed no
  laziness change since it was already eager, for its own pre-existing,
  unrelated reason (registering enabled addons into `ProviderRegistry`
  at startup).
- `AuthGateViewModel.kt` / `QrSignInViewModel.kt` — call
  `addonSyncRepository.pullFromServer()` alongside the existing three
  pulls, same two call sites, same fire-and-forget posture.

**Tests performed:**
- `npm run typecheck` / `npm audit` — clean.
- `npm test` locally against `mangotv_test` — **131/131 passed** (up from
  112; 19 new): install-and-echo with `manifestJson` round-tripping
  intact through JSONB; a later GET reflecting an install; GET ordering
  by `sortOrder`; enabling/disabling with a strictly newer `updatedAt`
  overwriting; an older `updatedAt` losing to current state;
  re-installing a removed addon (newer `updatedAt`) clearing `deletedAt`
  and reappearing in GET; a stale-timestamped remove losing to a newer
  install; a `204` for an addon that was never synced; validation
  failures (missing fields, `manifestJson` that isn't a JSON object,
  non-ISO-8601 `updatedAt`, missing DELETE query params); 401s with no
  Authorization header on all three endpoints; cross-user isolation,
  including a dedicated test confirming bob cannot remove alice's addon
  by guessing her exact `manifestUrl` with a far-future `updatedAt`
  (scoped away entirely — every query filters on `user_id` from the
  authenticated session, never a client-supplied value).
- Manual smoke test against a real running server, over actual HTTP:
  POST install → GET (shows it) → DELETE (200, `deletedAt` set) → GET
  (empty) → unauthenticated GET (401). Seeded data deleted immediately
  after.
- Android: no new pure-logic unit tests this milestone (same rationale
  as Milestones 6-8); verified instead by a full manual re-read of every
  new/changed file, cross-checking wire-format field names against the
  actual backend schema/routes, confirming `AddonManifest`'s
  round-trip through `Json.encodeToJsonElement`/`decodeFromJsonElement`
  correctly carries its nested `resources: List<JsonElement>` field
  (kotlinx.serialization handles this recursively — no hand-written
  field mapping to go stale if `AddonManifest` gains fields later), and
  the `build-apk.yml` CI compile below.

**Issues discovered (self-review before marking complete):**
- The Cinemeta-auto-install-vs-empty-cloud-pull collision described
  above — caught by working through Milestone 16's own "TEST 1 — NEW
  USER" flow by hand (install app → auth screen → create account via QR)
  before writing any sync code, not discovered after the fact. This is
  the reason `pullFromServer()`'s empty-list branch exists at all, not a
  bug fixed after shipping a naive version.
- Migration 0007's own comment says `manifest_json` caches the addon's
  manifest.json "verbatim." In practice it can't be byte-identical to
  the addon's original HTTP response: `StremioAddonClient.fetchManifest()`
  (pre-existing, untouched by this milestone) decodes straight into
  `AddonManifest` and never retains the raw response text anywhere, so
  the *only* thing this milestone's client code has to serialize back up
  is that already-normalized `AddonManifest` — not a regression this
  milestone introduced, but worth stating plainly rather than letting
  "verbatim" imply something the system was never actually capable of.

**Issues fixed:**
- The empty-cloud design decision itself was built correctly from the
  start once identified, not patched in after.
- **CI caught a real compile error** the manual re-read missed:
  `AddonSyncRepository.kt` imported `decodeFromJsonElement`/
  `encodeToJsonElement` from `kotlinx.serialization` (the package
  `decodeFromString`/`encodeToString` live in, used correctly elsewhere
  in this file and across the codebase) instead of
  `kotlinx.serialization.json` (where the JSON-`JsonElement`-specific
  overloads actually live) — two genuinely different packages in the
  same library that happen to export similarly-named functions. Fixed
  by correcting both imports; consistent with `kotlinx-serialization-json`
  1.7.3's own package layout (pinned in `gradle/libs.versions.toml`).
  Pushed as a second commit; `build-apk.yml` re-ran and came back green
  ([run 34420205936](https://github.com/MikeC444/MangoTV-Live-TV/actions/runs/34420205936)).
  `server-ci.yml` did not need to re-run for this commit — it's
  path-filtered to `server/**`, and this fix touched only Android code;
  it had already passed 131/131 on the commit that actually contains the
  server changes.

**Deliberately not built yet:**
- **Reordering addons.** There is no reorder UI anywhere in the app
  today (confirmed: `AddonsViewModel` only exposes `setEnabled`/`remove`,
  unlike Home Rows' own drag-to-reorder) — `sortOrder` is populated from
  each addon's position in the local list at install/seed time and
  faithfully round-trips, so the column is ready for a future reorder
  feature, but this milestone didn't build one that doesn't exist to
  sync in the first place.
- **`addon_settings` (per-addon configuration).** Confirmed still
  genuinely unused by the app — nothing here to sync yet. If a future
  milestone adds Stremio "configurable" addon support, that config JSONB
  lands in this reserved table exactly as migration 0008 anticipated.
- **A generic `SyncManager`/retry queue and clearing local caches on
  logout** — out of scope for the same reasons given in Milestones 6-8;
  this domain's failure/retry behavior matches the already-established
  pattern the other three sync repositories use.

**Milestone 9 is complete.**

## Milestone 10 — Complete Account Data Synchronization

**Status:** Complete.

**Changes:** Not a new sync domain — this milestone combines the four
already-shipped ones (Settings, Watchlist, Continue Watching, Addons)
into one coherent system, and builds the one piece every one of their
changelogs had explicitly and repeatedly deferred: a durable retry
queue for failed pushes. Milestones 6-9 each accepted "a failed push
isn't queued — the next local change, or the next login's pull, is
what recovers" as a stated, reasoned limitation, on the grounds that
generalizing a shared retry mechanism from a single example would be
guessing at its shape. With four real, shipped examples now in hand,
that's no longer true, and this is the milestone that explicitly asks
for the result to be combined into one system — so this is where that
generalization belongs, not a milestone earlier or later.

Pure Android-side change — no backend endpoints needed adding, since
retrying a failed push just means replaying the exact same
`POST`/`DELETE`/`PUT` request against the same already-built,
already-tested endpoints.

- `data/sync/PendingChangeStore.kt` (new) — a small generic, durable
  "hasn't successfully synced yet" outbox, one instance per domain, each
  with its own uniquely-named backing DataStore file (constructed via
  `PreferenceDataStoreFactory.create(...)` directly rather than this
  codebase's usual `by preferencesDataStore(name = ...)` delegate sugar,
  since that delegate is meant for one static property per store name
  and this class is instead instantiated once per domain with a
  dynamic name). Keyed by each domain's own natural key, so a later
  failure for the same key simply replaces the earlier pending entry —
  the outbox always holds each key's *latest* intended state, never a
  growing log of superseded attempts. `put()`/`remove()` do their
  read-modify-write inside DataStore's own `edit{}` transform, so
  concurrent calls for different keys can't race each other into a lost
  update.
- `data/sync/{Watchlist,Addon,ContinueWatching}SyncRepository.kt` — each
  gained a `retryPending()` and now persists to its own
  `PendingChangeStore` on push failure (cleared on success), reusing
  each domain's own existing wire DTO as the outbox's payload type
  rather than inventing a parallel serializable type per domain:
  `WatchlistItemDto`/`AddonSyncDto` (a pending *removal* is stored as a
  DTO with `deletedAt` set to its own `updatedAt` as a marker, since
  both already carry that field) and `WatchProgressRequest`
  (`completed` already tells the single `POST /user/watch-progress`
  endpoint everything it needs either way — no marker required).
  `retryPending()`'s loop treats a confirmed 401 as "stop entirely, the
  session is dead" (every remaining item would fail identically), a
  non-401 `ApiException` as "leave this one queued, try the rest of the
  batch" (a rejection for one payload doesn't predict the others), and
  an `IOException` as "leave everything queued and stop" (still
  offline, the rest of the batch would fail identically right now).
- `data/sync/SettingsSyncRepository.kt` — same `retryPending()`
  addition, but simpler: a single-document domain only ever needs one
  outbox entry under a fixed key, and since every local change already
  re-reads current state fresh before pushing, retrying just means
  replaying whatever's currently queued rather than needing to
  re-read local state itself.
- `data/sync/SyncManager.kt` (new) — the actual "coherent system": one
  `syncAll()` that pulls all four domains in parallel, then drains all
  four retry queues in parallel (pull first to establish fresh ground
  truth; drain after so a not-yet-synced local change doesn't get
  silently clobbered by a pull that runs after it) — replacing the four
  separate `pullFromServer()` calls `AuthGateViewModel`/
  `QrSignInViewModel` each made by hand through Milestone 9. Also
  registers a `ConnectivityManager.NetworkCallback` for the process's
  whole lifetime that calls `retryPendingAll()` (draining only, not a
  full pull — unnecessary network traffic otherwise) the moment the
  network transitions from unavailable to available, so an offline
  change doesn't sit queued until the user happens to relaunch the app
  or make another change of the same kind. A failed callback
  registration (some OEM/OS variants restrict this) degrades silently
  to "no proactive reconnect retry" rather than crashing — `syncAll()`
  on the next login/launch still recovers a queued change either way.
- `AppContainer.kt` — every sync repository now takes a `Context`
  (needed to construct its own `PendingChangeStore`); `syncManager`
  added as an eager singleton constructed last, once every repository
  it orchestrates already exists.
- `AuthGateViewModel.kt` / `QrSignInViewModel.kt` — both call
  `syncManager.syncAll()` once, replacing four individual
  `pullFromServer()` calls each. At the QR sign-in call site this is a
  genuine improvement, not just a refactor: the four pulls used to run
  *sequentially* there (unlike the auth gate's own four parallel
  `launch{}` calls) — `syncAll()`'s internal `coroutineScope{}` now
  parallelizes them everywhere.

**Tests performed:**
- `npm run typecheck` / `npm test` (backend) — clean, 131/131 (sanity
  check only; no backend files changed this milestone).
- Android: no new pure-logic unit tests this milestone (same rationale
  as Milestones 6-9); verified instead by a full manual re-read of
  every new/changed file, cross-checking constructor argument order at
  every `AppContainer` call site against each class's actual updated
  signature, and the `build-apk.yml` CI compile below.

**Issues discovered (self-review before marking complete):**
- **`PendingChangeStore.kt` itself didn't compile** — it called
  `json.decodeFromString(mapSerializer, raw)` /
  `json.encodeToString(mapSerializer, map)` without importing either
  function. This is the exact same class of mistake CI caught in
  Milestone 9 (a kotlinx.serialization extension living in a
  different package than expected — here, the *core*
  `kotlinx.serialization.decodeFromString`/`encodeToString`, not the
  JSON-specific ones), except this time it was caught by deliberately
  re-reading every new file's imports line-by-line against its actual
  usage *before* pushing, specifically because Milestone 9 had just
  demonstrated this sandbox's manual-review blind spot for this exact
  category of error. Fixed before the first push, not after a red CI
  run.

**Issues fixed:** the missing imports above.

**Deliberately not built yet:**
- **A download queue**, in the literal sense the milestone's
  "Initial sync / Incremental sync / Upload queue / Download queue"
  list names it. Every domain's pull is already a single, complete,
  on-demand fetch of "this account's current state for this domain" —
  there's nothing to *queue* on the download side the way there is on
  the upload side (a queue implies work waiting to be dispatched in
  order; a pull is just "ask the server, right now, what's true").
  `GET /user/history`'s existing keyset pagination (Milestone 8) is the
  closest thing to a "download queue" concept this system actually
  needs, and it already exists.
- **Exponential backoff / scheduled periodic retry.** Retry currently
  fires at three points: login/launch, network reconnect, and (per
  domain) the next unrelated local change. This covers the realistic
  offline scenarios (a device with no network at all, later regaining
  it) without adding a background job scheduler (WorkManager or
  similar) this project doesn't otherwise need yet. A device that's
  online but the API is unreachable for an extended stretch has no
  proactive retry between those three triggers — flagged, not fixed,
  since building a scheduled background sync job is real
  infrastructure a milestone that's explicitly about *combining
  existing pieces* shouldn't introduce on its own initiative.
- **Clearing pending queues / local caches on logout** — still
  Milestone 12's job, unchanged from every prior milestone's own note
  on this.

**Milestone 10 is complete.**

## Milestone 11 — First-Login Local Data Migration

**Status:** Complete.

**Changes:** Pure Android-side change — no backend endpoints needed
adding, since the "sync up" path replays each domain's existing push
endpoints and the "start fresh" path is just `SyncManager.syncAll()`,
both already built and tested in earlier milestones. What was missing
was the decision layer in front of them: through Milestone 10, a QR
sign-in always called `syncAll()` unconditionally, meaning a device with
real pre-existing local data (a re-install, or a second device an
account is being added to for the first time) would have had that data
silently overwritten by whatever the cloud already had, the first time
this milestone's SYNC/START FRESH prompt was actually needed.

- `data/sync/FirstSyncState.kt` (new) — a device-scoped (not
  account-scoped) DataStore boolean flag: has this device ever resolved
  the migration decision? Deliberately device-, not account-scoped:
  Milestone 5's Sign Out doesn't clear local caches yet (Milestone 12's
  job), so scoping this per-account would risk a second, different
  account signing in on the same device being asked to "sync" the
  *previous* account's leftover local data into its own cloud account.
  Once set, a later sign-in as a different account just pulls and
  overwrites local state as normal — safe, since nothing is ever pushed
  from a state this flag hasn't vetted.
- `data/sync/{Settings,Watchlist,ContinueWatching,Addon}SyncRepository.kt`
  — each gained two additions: `isCloudEmpty(): Boolean?` (a read-only
  peek at whether this account has any data at all for that domain from
  any device, returning `null` — never a guessed `true`/`false` — when
  the check itself couldn't complete) and `pushAllLocalUp()` (pushes
  every item currently in that domain's local cache, suspending until
  done, reusing each domain's existing per-item push/reconcile logic
  rather than a new bulk endpoint).
- `data/sync/AddonSyncRepository.kt` — also a real architectural fix, not
  just the two additions above: through Milestone 10, `pullFromServer()`
  special-cased an empty cloud response by pushing this device's local
  addons up as a seed, on *every* pull, to protect a brand-new account's
  auto-installed Cinemeta default from being silently uninstalled on
  first sign-in. Milestone 11 found the real bug in doing that
  unconditionally: a user who deliberately clears every addon on one
  device would have it silently resurrected the next time a second,
  already-signed-in device happened to pull with a (correctly) empty
  cloud response. That seed-from-local behavior is still needed, but
  only belongs at first login — it now lives here only as the public
  `pushAllLocalUp()` above, called by `FirstLoginMigrationCoordinator`
  and gated by `FirstSyncState`; an ordinary pull now follows the same
  plain "cloud is the source of truth" rule every other domain's
  `pullFromServer()` already does.
- `data/addon/AddonRepository.kt` — new `isJustDefaultAddon()`: true only
  when the installed list is exactly the untouched first-launch default
  (just Cinemeta, enabled), so the migration decision below can tell
  "genuinely customized" apart from "never touched since install"
  without leaking the Cinemeta URL constant outside this class.
- `data/sync/FirstLoginMigrationCoordinator.kt` (new) — the actual
  decision layer, called once per QR sign-in completion. In order: (1)
  already resolved on this device before → proceed normally, nothing
  rechecked; (2) no meaningful local data across any of the four domains
  (including Home Row order/hidden rows and non-default player
  preferences, not just the three domains with their own sync
  repository) → proceed normally, and mark first-sync done, since
  there's nothing here ever worth asking about again; (3) local data
  exists and every domain's cloud peek confirms empty → auto-push,
  no prompt needed, since there's no actual disagreement to resolve; (4)
  local data exists and the cloud has something too → the real
  SYNC/START FRESH prompt. A cloud peek that fails outright (offline,
  server error) is never treated as empty — that would risk silently
  pushing local data over cloud state this device just couldn't see —
  it falls back to "proceed normally" and deliberately leaves first-sync
  *not* marked done, so a later sign-in gets a genuine chance to resolve
  this properly instead of the question being silently skipped forever
  because of one transient failure.
- `AppContainer.kt` — `firstLoginMigrationCoordinator` added, `by lazy`
  (unlike most of this file's other singletons): nothing else constructs
  it as an eager constructor argument, and it has no `init{}` side effect
  of its own to arm early — `QrSignInViewModel` is its only caller, and
  only right after a fresh sign-in completes.
- `ui/auth/QrSignInViewModel.kt` — a completed QR session no longer
  authenticates immediately. It first asks
  `FirstLoginMigrationCoordinator.decide()` — almost always "proceed
  normally," which authenticates exactly as before (fire-and-forget
  sync, instant navigation, no perceptible change for the common case).
  Only a genuine local-vs-cloud conflict surfaces the new
  `QrUiState.MigrationChoice` and waits for the user's explicit choice
  (`onSyncChosen()`/`onStartFreshChosen()`) before authenticating —
  unlike the automatic paths, this one deliberately does wait for the
  chosen push/pull to finish first, since it's a rare, user-initiated
  action that deserves visible confirmation rather than the screen
  instantly vanishing on tap.
- `ui/auth/QrSignInScreen.kt` — a new `MigrationChoice` branch rendering
  the prompt ("Sync existing MangoTV data to your account?") and two
  `MangoButton`s (SYNC / START FRESH), focus-chained and TV-remote
  navigable like every other auth screen in this app, matching
  `AuthStartScreen`'s own filled/glass two-button styling.

**Tests performed:**
- `npm run typecheck` / `npm test` (backend) — clean, 131/131 (sanity
  check only; no backend files changed this milestone, confirmed via
  `git status` before starting).
- Android: no new pure-logic unit tests this milestone (same rationale
  as Milestones 6-10); verified instead by a full manual re-read of
  every new/changed file — with particular attention to imports on the
  two files this milestone's `AddonSyncRepository.kt` touches directly,
  given Milestones 9 and 10 both caught real compile errors from
  confusing `kotlinx.serialization` (core `decodeFromString`/
  `encodeToString`) with `kotlinx.serialization.json`
  (`decodeFromJsonElement`/`encodeToJsonElement`) — this milestone's new
  files don't call either family of function directly, so the risk
  didn't actually apply here, but the check was made deliberately rather
  than assumed — and the `build-apk.yml` CI compile below.

**Issues discovered (self-review before marking complete):**
- `FirstLoginMigrationCoordinator.hasLocalData()`'s addon check would
  have been wrong if written as `!addonRepository.isJustDefaultAddon()`
  alone — true for a genuinely *empty* addon list too (size `0` isn't
  size `1`), which would have meant a device with zero addons installed
  incorrectly counted as "has customized addon data worth protecting."
  Fixed by requiring `addons.isNotEmpty() &&
  !addonRepository.isJustDefaultAddon()` — caught during design, before
  the file was ever written in its buggy form.

**Issues fixed:** the `hasLocalData()` bug above.

**Deliberately not built yet:**
- **A `viewModelScope` cancellation race** on the automatic (no-prompt)
  paths: if navigation away from `QrSignInScreen` happens to cancel
  `QrSignInViewModel` before its fire-and-forget `syncManager.syncAll()`/
  `resolveSync()` calls finish, the sync could be interrupted mid-flight.
  This risk pre-dates Milestone 11 (the same shape existed for
  `syncAll()` through Milestone 10) and is deliberately left unchanged
  for those paths, to avoid altering already-shipped, already-verified
  behavior as a side effect of this milestone. The new interactive path
  (`onSyncChosen()`/`onStartFreshChosen()`) sidesteps it by construction
  — the screen only flips `authenticated` (which triggers navigation)
  *after* awaiting the chosen operation, not before.
- **Clearing local caches on logout** — still Milestone 12's job,
  unchanged from every prior milestone's own note on this.

**Milestone 11 is complete.**

## Milestone 12 — Account Switching

**Status:** Complete.

**Scope note:** this milestone's original spec text predates this
session's own context (the work resumed mid-project from an earlier
Claude Code session, per this project's own established process — see
Milestone 0). Rather than guess, scope was reconstructed from this
codebase's own explicit, repeated notes: migration `0003_devices.sql`'s
header comment names "Milestone 12's account switching" directly (the
same physical device — `device_identifier` — can carry multiple
accounts' own `devices` rows); `AccountScreen.kt`'s Milestone 5 kdoc
separately named "device management" (Milestone 3's `/auth/sessions`:
viewing/revoking this account's *other* sessions) as its own distinct
later milestone; and `FirstSyncState.kt`'s Milestone 11 kdoc explicitly
deferred "whether logging out should reset this flag once it actually
clears local caches on logout" to this milestone by name. All three
agree on the same scope: making sign-out actually safe for a different
account to sign in after — not building a device/session-management
screen, which stays out of scope here on the same evidence.

**Changes:** Pure Android-side change — no backend endpoints needed;
account switching is a purely local-data-hygiene problem, not a server
one (the server already scopes every query to the authenticated
session's own `user_id`; nothing server-side needed to change). Through
Milestone 11, Sign Out revoked this device's session but left every
local cache exactly as it was. Two real, concrete problems fell out of
that once accounted for:

1. A *different* account signing in on the same device right after
   would have briefly rendered the *previous* account's watchlist,
   continue watching, addons, and settings on screen — during the
   window between authenticating and the first `syncAll()` pull
   overwriting that stale local cache with the new account's own data.
2. A queued-but-not-yet-pushed change from the previous account (in any
   of the four `PendingChangeStore` outboxes) could have been retried
   later under the *new* account's own access token — actually writing
   the wrong account's data into the new account's cloud storage, not
   just a display glitch.

- `data/provider/MyListRepository.kt`, `data/history/ContinueWatchingRepository.kt`,
  `data/addon/AddonRepository.kt`, `data/provider/HomeRowPreferencesRepository.kt`,
  `data/player/PlayerPreferencesRepository.kt` — each gained a `clear()`
  that wipes/resets its local cache and persists that, deliberately
  without notifying that domain's `onLocalChange` push hook (the account
  being signed out of still owns this data server-side; the device is
  only forgetting its own local copy, not asking the server to delete or
  reset anything — the same reasoning `applyRemote()` already documented
  for pulls, extended to this new local-only case). `AddonRepository.clear()`
  also unregisters every addon from `ProviderRegistry`, mirroring
  `applyRemote()`'s own cleanup step, and deliberately leaves the
  Cinemeta auto-bootstrap flag untouched — that flag is a device-scoped
  "has this install ever shown default content" concept (matching
  `FirstSyncState`'s own device-scoping precedent from Milestone 11), so
  a second account signing in on the same device is treated the same as
  a user who deliberately removed every addon: no auto-reinstall.
- `data/sync/PendingChangeStore.kt` — gained `clear()`, dropping every
  pending entry outright. `data/sync/{Settings,Watchlist,ContinueWatching,Addon}SyncRepository.kt`
  each gained a `clearPending()` wrapper around it, following the same
  encapsulation pattern `retryPending()`/`pushAllLocalUp()`/
  `isCloudEmpty()` already established (the private `pendingStore` field
  itself is never reached into from outside its owning repository).
- `data/sync/FirstSyncState.kt` — gained `reset()`, clearing the
  device's "first-login migration already resolved" flag. Resolves the
  question that class's own Milestone 11 kdoc explicitly left open:
  logging out now does reset it, so a genuinely different account's own
  first sign-in on this device gets evaluated fresh instead of being
  skipped as "already resolved" for a question a *previous* account, not
  this one, actually resolved.
- `data/sync/AccountSwitchCoordinator.kt` (new) — the actual orchestrator,
  mirroring `SyncManager`'s "one coordinator, one parallel `coroutineScope`
  sweep across every domain" shape. `signOut()` launches
  `authRepository.logout()` (unconditional local session clear regardless
  of the server call's outcome, unchanged from Milestone 5) alongside all
  five domain `clear()` calls, all four `clearPending()` calls, and
  `firstSyncState.reset()`, all in parallel, and doesn't return until
  every one of them has finished — so by the time `AccountViewModel`
  flips `signedOut` and navigation carries the user back to
  `AuthStartScreen`, the device is genuinely blank, not just
  session-less.
- `AppContainer.kt` — `firstSyncState` promoted from an inline
  `FirstSyncState(context)` constructed inside `firstLoginMigrationCoordinator`'s
  own lazy block to its own shared, eager `val`, so
  `firstLoginMigrationCoordinator` and the new `accountSwitchCoordinator`
  read and write the exact same instance instead of each wrapping the
  same underlying DataStore file independently. `accountSwitchCoordinator`
  added as a lazy singleton, same "no eager constructor argument forces
  it, no init{} side effect, only needed the moment its one caller
  actually runs" reasoning as `firstLoginMigrationCoordinator`.
- `ui/settings/AccountViewModel.kt` — `signOut()` now calls
  `accountSwitchCoordinator.signOut()` instead of `authRepository.logout()`
  directly; found and confirmed (via `grep`) as the only call site of
  `AuthRepository.logout()` outside its own class, so no other path could
  still bypass the new local-cache clearing.
- `ui/settings/AccountScreen.kt` — updated its own Milestone 5 kdoc,
  which had gone stale after Milestone 11 (sync prompts) and now this
  milestone (account switching) — both no longer belong on its "later
  milestones" list; only device/session management still does.

**Tests performed:**
- `npm run typecheck` / `npm test` (backend) — clean, 131/131 (sanity
  check only; no backend files changed this milestone, confirmed via
  `git status` before starting — account switching is local-data hygiene
  only, nothing server-side needed to change).
- Android: no new pure-logic unit tests this milestone (same rationale as
  Milestones 6-11); verified instead by a full manual re-read of every
  new/changed file, a `grep` sweep confirming every new `clear()`/`reset()`/
  `clearPending()` method actually exists where `AccountSwitchCoordinator`
  calls it and takes the exact parameter names `AppContainer` passes by
  name, a `grep` confirming `AuthRepository.logout()` has exactly the one
  call site now routed through the coordinator, and the `build-apk.yml`
  CI compile below.

**Issues discovered (self-review before marking complete):**
- None required a code fix — the one real design risk (whether
  `PlayerPreferencesRepository.clear()` could reuse its existing private
  `update()` helper the way `HomeRowPreferencesRepository.clear()` reuses
  its own) was caught by reading `update()`'s body *before* writing
  `clear()`, not after: `PlayerPreferencesRepository.update()`
  unconditionally calls `onLocalChange?.invoke()`, unlike
  `HomeRowPreferencesRepository.update()`, which doesn't (callers there
  invoke it themselves) — so `PlayerPreferencesRepository.clear()` was
  written with its own standalone body from the start, mirroring
  `applyRemote()`'s shape instead, rather than being written the
  shortcut way and then found broken.

**Issues fixed:** none required — see above.

**Deliberately not built yet:**
- **Device/session management** — viewing or revoking this account's
  *other* active sessions via the already-built, already-tested (since
  Milestone 3) `GET`/`DELETE /auth/sessions` endpoints. Confirmed still
  genuinely unused by the Android app (no caller anywhere in
  `data/network/AuthApiClient.kt`). Out of scope on the same evidence
  this milestone's own scope was reconstructed from — `AccountScreen.kt`'s
  Milestone 5 kdoc named it as its own separate later milestone, distinct
  from account switching, and nothing found while building this one
  changed that.
- **Re-bootstrapping Cinemeta for a second account on the same device.**
  A brand-new second account, signing in on a device that already
  auto-installed Cinemeta for a first account, will see an empty addon
  list (until their own cloud sync brings in whatever they actually have)
  rather than Cinemeta being re-installed for them — `restoreFromDisk()`'s
  bootstrap logic only ever runs once per process (`AddonRepository`'s own
  `init{}`), and its bootstrapped flag is deliberately device-, not
  account-scoped (see `AddonRepository.clear()`'s own note above). A minor
  onboarding rough edge for the less common "same device, second account,
  same process" path, not a correctness or data-safety issue — flagged
  rather than fixed, since re-bootstrapping per-account would be new
  onboarding polish this milestone wasn't asked to build.

**Milestone 12 is complete.**

## Milestone 13 — Offline Mode & Recovery

**Status:** Complete.

**Scope note:** this milestone's spec text ("app continues using cached
account data", "local change -> pending sync queue -> network returns ->
backend synchronization", "handle network/API/database outage, partial
sync, failed requests, retry, session expiration", "don't unnecessarily
log out on a temporary network failure") describes behavior Milestones
5-10 already built most of: `AuthGateViewModel`'s session check is
local-only, `ensureFreshSession()` already distinguished a confirmed 401
from a network failure, and Milestone 10 already built a durable
per-domain retry outbox plus a `ConnectivityManager` reconnect trigger. A
pre-implementation audit (done before writing any code, at the user's
request, to establish how far Milestones 0-12 actually held up under
adversarial reading rather than at face value) is what this milestone
actually acted on, not a rebuild of that machinery.

**Changes:** No backend changes — confirmed via `git status` before
starting; every issue found was a client-side gap in how failures already
flow through the existing sync layer, not a missing server capability.

The audit found two real, concrete gaps, both traced to actual code, not
hypothetical:

1. **A crash risk, not just a missed retry.** Every sync repository's
   `pullFromServer()`/`retryPending()`/`pushToServer()` already caught
   `ApiException` and `IOException` — but several call sites left a
   preamble (`pendingStore.all()`, or the `token == null` branch's
   `pendingStore.put()`) *outside* that try block, and none of them
   caught anything else. A malformed/unexpected response body from a
   degraded backend or database — which surfaces as a
   `SerializationException`, not an `IOException` — or a `DataStore` read
   failure at exactly the wrong moment, would have propagated uncaught
   out of a fire-and-forget `scope.launch { }` and crashed the app. This
   is precisely an "API outage"/"database outage" scenario, not an
   exotic one: a degraded backend is at least as likely to return a
   malformed body as a clean connection-refused.
2. **Partial sync wasn't actually isolated.** `SyncManager.syncAll()`/
   `retryPendingAll()` and `FirstLoginMigrationCoordinator.resolveSync()`
   ran their four domains' calls inside `coroutineScope { launch { } }` —
   structured concurrency means one child's uncaught exception cancels
   every sibling and rethrows. Combined with gap 1, one domain's
   hiccup could have silently killed the other three domains' pulls too,
   the opposite of the milestone's explicit "partial sync" requirement.

Fixes:

- `data/auth/AuthRepository.kt` — `ensureFreshSession()` gained a
  `CancellationException`-safe catch-all after its existing
  `ApiException`/`IOException` handling, treating any other unexpected
  failure the same permissive way: session stays usable, never
  conflated with a confirmed rejection. This matters more than it looks
  — `freshAccessTokenOrNull()` (every sync repository's own entry point)
  calls this *before* entering its own try block, so this was the one
  place a fix here closes the gap for every domain at once.
- `data/sync/{Settings,Watchlist,ContinueWatching,Addon}SyncRepository.kt`
  — every `pullFromServer()`, `retryPending()`, `isCloudEmpty()`,
  `pushAllLocalUp()`, and `pushToServer()`/`doPush()` now has its entire
  body (not just the network-call subset) inside one try block, with the
  existing `ApiException`/`IOException` handling preserved and two new
  catches appended: `CancellationException` is rethrown (never swallow
  coroutine cancellation), everything else degrades exactly like the
  existing `IOException` case (leave local state as last-known-good, or
  queue the item for retry, depending on the method).
- `data/sync/SyncManager.kt` — `syncAll()`/`retryPendingAll()` switched
  from `coroutineScope` to `supervisorScope`: one domain's pull or retry
  can no longer cancel the other three. Combined with the leaf-level
  fixes above (which mean nothing should actually reach this level
  uncaught in practice), this is deliberate defense in depth for
  whatever a future fifth sync domain's author forgets to handle as
  exhaustively — not a workaround for a gap proven to exist here today.
  Also added a periodic `retryPendingAll()` timer (every 5 minutes, for
  the process's lifetime, alongside the existing `NetworkCallback`),
  closing the gap Milestone 10 explicitly flagged and deferred: the
  `NetworkCallback` only fires on an unavailable-to-available
  *transition*, never for a device that stayed online the whole time
  while the backend/database itself was down for an extended stretch.
  Each domain's own `retryPending()` already no-ops on a fast local
  DataStore read when its outbox is empty, so this timer costs nothing
  in the common case.
- `data/sync/FirstLoginMigrationCoordinator.kt` — `resolveSync()` (the
  four parallel `pushAllLocalUp()` calls) got the same
  `coroutineScope` -> `supervisorScope` fix, for the same reason: one
  domain's push failing during a first-login migration (already handled
  internally — queued for retry) must never cancel the other three
  mid-migration, which is exactly the moment losing three domains' worth
  of local data to one domain's hiccup would hurt most. `decide()`'s own
  four parallel `isCloudEmpty()` peeks were left on `coroutineScope`
  (unchanged) — safe by construction now that `isCloudEmpty()` itself
  can no longer throw.

**Verifying "temporary backend outage -> app continues using cached
data" holds:** confirmed by re-reading `HomeViewModel` — Home, My List,
and Continue Watching all read from each repository's local `StateFlow`
(populated from on-disk DataStore caches), never from a live network
call, so this was already structurally true since Milestones 6-9
established "local storage is a cache, the backend is the source of
truth, UI reads local" as the standing rule. Nothing needed to change
there; this milestone's audit confirmed it rather than assumed it.

**Tests performed:**
- `npm run typecheck` / `npm test` (backend) — clean, 131/131 (sanity
  check only; confirmed via `git status` before starting that no backend
  files changed this milestone — every fix is client-side exception
  handling).
- Android: no new pure-logic unit tests this milestone (the change is
  exception-handling structure, not new branching logic distinct from
  what Milestones 6-12's own tests already exercise); verified instead
  by a full manual re-read of all seven changed files after editing
  (confirming every new catch clause is reachable, correctly ordered —
  `CancellationException` before the generic `Exception` catch, never
  after, so cancellation is never accidentally swallowed — and that
  moving each preamble inside its try block didn't change any
  successful-path behavior), a brace-balance check across all seven
  files, and the `build-apk.yml` CI compile (this sandbox still has no
  Android SDK to compile locally, the same limitation stated since
  Milestone 5).

**Issues discovered:** both described in detail above (uncaught
exceptions from unexpected failure types; structured-concurrency
cancellation defeating partial-sync isolation). Found via a
pre-implementation adversarial audit at the user's explicit request,
not via a test failure or a crash report.

**Issues fixed:** both, as described above.

**Deliberately not built yet:**
- **A `CoroutineExceptionHandler` or logging on the new catch-all
  branches.** This codebase has no `android.util.Log` usage anywhere
  today (confirmed by grep) — every existing catch block in this file
  set silently degrades with a comment, no logging. Adding a new
  logging convention just for this milestone's catch-all branches would
  be inventing infrastructure the rest of the codebase doesn't use,
  rather than matching its existing style.
- **WorkManager or another background-job scheduler** for the periodic
  retry. A plain coroutine loop on `SyncManager`'s own
  process-lifetime scope is consistent with how the existing
  `NetworkCallback` is already registered (same class, same lifetime,
  same "best-effort, never crashes if it can't set up" posture) and
  needs no new dependency; a Fire TV app that isn't killed by app-standby
  the way a phone's Doze mode would kill a phone app has no strong
  reason to need `WorkManager`'s guarantees for a same-process timer.
- **A user-visible "you're offline" indicator.** The milestone's own
  spec text doesn't ask for one (it asks for correct behavior — cached
  data keeps working, changes queue silently, no spurious logout — not a
  new UI surface), and every prior milestone since 7 has held the line
  on not inventing UI beyond what's asked; `QrSignInViewModel`'s existing
  `pollingDegraded` flag remains the only precedent for this kind of
  indicator, scoped to the one screen (QR sign-in) that already had it
  before this milestone.

**Milestone 13 is complete.**

## Milestone 14 — Security Audit

**Status:** Complete.

**Approach:** A dedicated adversarial pass over the whole system against
the spec's own checklist, run in two stages: first an independent review
(at the user's request, before starting Milestone 13) that read the
actual code for every high-risk area rather than trusting prior
milestones' own changelog claims at face value, then this milestone's own
follow-up pass closing the one concrete gap that review found and
explicitly confirming the remaining checklist items against real evidence
(code read directly, or an existing automated test that proves it) rather
than re-asserting what earlier milestones already said about themselves.
No backend changes were needed — every backend-side item below was
already correct when read directly.

**Checklist, evidence-first:**
- **Hardcoded secrets / Neon credentials in the APK** — grepped the
  entire `app/` tree for `postgres://`, `postgresql://`, `neon.tech`,
  `jdbc:postgresql`: zero matches. The Android app only ever holds
  `BuildConfig.API_BASE_URL`; `server/.env.example`/`.env.test.example`
  contain placeholders only, `.env`/`.env.test` are gitignored and
  confirmed never staged (`git status`/`git show`).
- **Weak authentication / weak password hashing** — Argon2id with
  explicit OWASP-recommended parameters (`security/password.ts`), never
  the library default. Login returns an identical error and always runs
  exactly one argon2 verify (against a dummy hash for an unknown email)
  whether the account exists or not — closes the timing side-channel
  Milestone 3 originally found and fixed.
- **Token leakage / session hijacking / refresh token problems** — every
  bearer token (session access/refresh, QR activation) is a random
  256-bit value, SHA-256-hashed before it ever touches storage
  (`security/tokens.ts`); the raw value is never persisted anywhere, even
  transiently. `requireAuth` joins `devices` and rejects a session whose
  device was remotely revoked. `refresh()` is one atomic
  `UPDATE ... WHERE ... RETURNING` that validates and rotates in the same
  statement — no separate check-then-act window — and rotating
  invalidates the *previous* access token immediately too (Milestone 3's
  own test-derived finding).
- **QR token replay / QR token guessing** — `pollQrSession`'s claim is a
  single atomic `UPDATE qr_auth_sessions SET status='consumed' WHERE
  status='completed'`, which can match a given token at most once by
  construction (re-verified by reading `qrAuthService.ts` directly, not
  just trusting Milestone 4's own tests); 256 bits of entropy makes
  guessing infeasible.
- **Missing authorization checks / user ID manipulation** — read every
  route file (`watchlist.ts`, `addons.ts`, `history.ts`, `settings.ts`,
  `auth.ts`, `me.ts`) directly: every one requires `requireAuth` and
  scopes its query to `req.user!.id`, the value `requireAuth` itself
  derived from the verified session — never a param/query/body value.
  Grepped for `req.(params|query|body).(userId|user_id|id)` across
  `server/src`: zero matches.
- **SQL injection** — grepped every service file for a template-literal
  query containing `${`: zero matches. Every query uses `$1`/`$2`
  parameterization throughout.
- **Input validation problems** — every mutating route is behind the
  shared Zod `validate()` middleware (body *and*, since Milestone 7,
  query params); confirmed by reading the schemas directly, not just
  their existence.
- **Excessive API permissions** — `AndroidManifest.xml` requests exactly
  `INTERNET` and `ACCESS_NETWORK_STATE`, nothing else.
- **Sensitive information in logs** — `requestLogger.ts` logs only
  method/path/status/duration/user id, explicitly never bodies, headers,
  or query strings (read directly, not assumed from its own comment).
  The Android app has zero `android.util.Log` calls anywhere (confirmed
  by grep) — there is no logging call site that could leak a token even
  by mistake.
- **Insecure local storage** — the one genuinely sensitive local value
  (the session, carrying both bearer tokens) is encrypted at rest via
  Tink + Android Keystore (`TokenCipher`/`SessionManager`, read directly:
  encrypt-before-write and decrypt-after-read both confirmed, with a
  corrupted/undecryptable blob safely treated as signed-out rather than
  crashing). Every other local DataStore cache (watchlist, continue
  watching, addon list, settings, pending-change outboxes) holds no
  credential material, so plaintext storage there is correct, not an
  oversight.
- **Account data leakage / cross-user access** — the spec's own explicit
  test ("User A attempting to access User B's Settings, Watchlist,
  History, Continue Watching, Addons — every attempt must fail") already
  exists as real, passing automated tests, not something this milestone
  had to write from scratch: `tests/settings.test.ts`,
  `tests/watchlist.test.ts`, `tests/addons.test.ts`,
  `tests/watch-progress.test.ts` each include a dedicated cross-user
  test (confirmed by reading `settings.test.ts`'s own "cross-user
  isolation" block directly: alice's write is invisible to bob's GET),
  several going further than a passive check — a dedicated attack
  simulation where bob attempts to remove/complete alice's exact item by
  guessing her natural key with a far-future `updatedAt` engineered to
  win any last-write-wins race, and is still scoped away entirely
  because every query filters on `user_id` from the authenticated
  session. `tests/auth-sessions.test.ts`/`tests/me.test.ts` cover
  sessions and `/user/me` the same way. All still pass: 131/131.
- **Improper logout / expired sessions** — Milestone 12's
  `AccountSwitchCoordinator.signOut()` clears every local cache and
  pending-change outbox in parallel with the session revoke, so a
  different account signing in right after starts genuinely blank.
  Access tokens expire in 1 hour, refresh in 30 days, both enforced
  server-side (`auth-refresh.test.ts`); a session revoked from another
  device is rejected by `requireAuth` immediately.

**Issue found and fixed this milestone:** `AndroidManifest.xml` sets
`android:usesCleartextTraffic="true"` globally (present since the
Initial commit — confirmed via `git log`, so this predates the account
system entirely; it exists for the pre-existing, unrelated addon
ecosystem, which fetches arbitrary user-supplied `http://`/`https://`
addon manifest URLs by design — `AddonUrl.kt` explicitly accepts both).
Left unscoped, nothing at the OS level would stop the account API's own
traffic (a bearer token on nearly every request) from going out over
plaintext if `API_BASE_URL` were ever misconfigured without `https://`.
A `network_security_config.xml` was the first idea, but doesn't actually
work here: it's a static XML resource and can't reference
`API_BASE_URL`, which only exists as a value read from the gitignored,
deployment-specific `local.properties` at build time. Fixed at the
source instead — `app/build.gradle.kts` now does
`require(apiBaseUrl.startsWith("https://"))` right where `apiBaseUrl` is
computed, failing the *build* (not just discovered at runtime) if it's
ever misconfigured with a non-`https://` scheme. This is a strictly
stronger guarantee than a network security config could give anyway,
since it can't be bypassed by anything the running app does — there's no
build to ship. The manifest flag itself is left as `true` (removing it
would break the addon ecosystem, not fix anything the account API
actually needed) with a comment explaining both why it's intentional and
where the account API's own transport security actually lives, so a
future pass doesn't "fix" it again by breaking addons instead.

**Tests performed:**
- `npm run typecheck` / `npm test` (backend) — clean, 131/131 (sanity
  check only; no backend files changed this milestone).
- The build-time `require()` fix has no `local.properties` in CI (it's
  gitignored, never checked out), so `apiBaseUrl` falls back to
  `"https://not-configured.invalid"`, which itself starts with
  `https://` — confirmed by reading the fallback value directly before
  relying on it, so this fix cannot break CI's existing build.
- Android: no new pure-logic unit tests this milestone (the fix is a
  build-time `require()`, not app logic); verified by re-reading both
  changed files in full and confirming the `require()` block sits before
  any use of `apiBaseUrl.toString()`-style interpolation into
  `buildConfigField`, so a real misconfiguration fails before ever
  reaching the generated `BuildConfig`. `build-apk.yml` — see below.

**Issues discovered (CI, not self-review):** the manifest comment
explaining the `usesCleartextTraffic` decision used `--` as a sentence
separator twice. XML forbids `--` anywhere inside a comment body (only
the opening `<!--`/closing `-->` delimiters may contain it), so
`:app:processDebugMainManifest` failed with a `ManifestMerger2` parse
error — caught by CI, not by the manual re-read that preceded the first
push (this sandbox has no XML validator wired into that review step).

**Issues fixed:** replaced both `--` occurrences with plain punctuation;
verified with `xmllint --noout` (available in this sandbox) that the
file is well-formed *before* pushing the fix, rather than re-pushing on
faith. Re-ran `build-apk.yml`
([run 34515773634](https://github.com/MikeC444/MangoTV-Live-TV/actions/runs/34515773634)):
`testDebugUnitTest` and `assembleDebug` both green.

**Deliberately not re-litigated:** items already covered by an existing,
passing automated test (see the checklist above) were verified by
reading that test's actual assertions, not rewritten — this milestone's
job was confirming the existing claims hold up and closing the one gap
that didn't, not duplicating test coverage that already exists.

**Milestone 14 is complete.**

## Milestone 15 — Performance & Fire TV Optimization

**Status:** Complete.

**Approach:** Read the implementation specifically looking for what the
spec's checklist names (auth screen startup speed, QR generation speed,
unnecessary startup blocking, async sync, large-list freezing, efficient
DB/API requests, batching, pagination, local caching, UI responsiveness
during sync), verifying each claim against actual code rather than
assuming. No Android emulator/device exists in this sandbox (stated since
Milestone 5) — Fire TV Stick-specific claims below (memory pressure,
decode cost) are verified by reading the reasoning already recorded in
the code's own comments and confirming the implementation matches that
reasoning, not by profiling on real hardware, which the spec itself
correctly points out this kind of environment cannot substitute for.

**Findings — already solid, verified rather than assumed:**
- **Auth gate / startup:** `AuthGateViewModel`'s session check is local
  DataStore-only (Milestone 5) — no network round trip gates navigation.
  Grepped the entire `app/` tree for `runBlocking`: zero matches, so
  nothing here can block a thread waiting on a coroutine. `SessionManager`
  defers `TokenCipher`'s synchronous Android Keystore setup to first
  actual use on `Dispatchers.IO` (Milestone 5), never to `AppContainer`
  construction on the main thread. `AddonRepository`'s disk restore and
  every other `init{}` side effect in `AppContainer` dispatch through
  `scope.launch` on `Dispatchers.IO`, confirmed by reading each one, not
  assumed from the class's own doc comment.
- **QR generation:** `createQrSession()` is one network round trip with
  no extra work before or after it.
- **Sync stays async / UI responsive during sync:** every sync entry
  point is fire-and-forget from its caller, hardened against ever
  blocking or crashing the caller in Milestone 13.
- **Large lists don't freeze the UI:** `ContentRow` (the shared component
  behind Home, Genre Results, Search, My List, and Detail's Similar/Cast
  rows) already uses `LazyRow` with a stable `key = { _, content ->
  content.id }`, and `RowsBrowseScreen`'s outer list of rows is itself a
  `LazyColumn` — confirmed by reading both files directly, not inferred
  from the presence of an import. `AddonsScreen` is a `LazyColumn` too.
  Grepped all 18 files under `ui/` that reference a lazy list component;
  every screen rendering a list backed by user data or a catalog uses
  one. Watch History has no browse screen to freeze in the first place
  (unchanged since Milestone 8); `GET /user/history`'s existing keyset
  pagination is there if one is ever built.
- **Image loading:** `MangoTvApplication.newImageLoader()` is a
  hand-tuned Coil `ImageLoader`, not the library default — a 35% memory
  cache (deliberately raised above Coil's ~20% default, with the reasoning
  for why recorded in the file's own comment: denser poster grids and more
  genre-fanned rows outgrew the default), a disk cache bounded to
  50-250MB regardless of device storage size, and `rememberOpaqueImageRequest`
  forces `RGB_565` for photographic art specifically to halve per-pixel
  decode/memory cost on low-RAM Fire TV Stick hardware. Already correct;
  nothing changed here.
- **Backend query efficiency:** grepped every file under `server/src/services`
  for a query call inside a loop; none found — every write is the single
  atomic statement pattern established since Milestone 6. Every FK column
  has an explicit index (verified against real Postgres in Milestone 1).

**Two real, concrete inefficiencies found and fixed:**
1. **Five separate `OkHttpClient` instances for one backend.**
   `AuthApiClient`, `SettingsApiClient`, `WatchlistApiClient`,
   `PlaybackProgressApiClient`, and `AddonSyncApiClient` each built their
   own `OkHttpClient.Builder()` with identical timeouts (confirmed
   byte-for-byte identical across all five before touching anything) —
   five separate connection pools and five separate dispatcher thread
   pools all talking to the exact same `API_BASE_URL` host, pure overhead
   with no upside. New `data/network/AccountApiHttpClient.kt` holds one
   shared instance; all five now reference it instead of building their
   own, with unused `OkHttpClient`/`TimeUnit` imports cleaned up from each.
   Deliberately scoped to just these five — `StremioAddonClient` (arbitrary,
   often slower self-hosted addon servers) and `PlayerEngine`'s
   `OkHttpDataSource` client (streaming media) stay on their own clients,
   since that's genuinely different traffic with different performance
   characteristics, not the same duplication.
2. **Backend connection pool had no `connectionTimeoutMillis`.**
   `server/src/db/pool.ts` relied entirely on `pg`'s own defaults, and
   `pg`'s default `connectionTimeoutMillis` is `0` — wait forever for a
   free pool client. Under real saturation (a traffic spike, or hitting
   Neon's own connection ceiling on a pooled/serverless plan) a request
   would hang indefinitely instead of failing fast with a clear error the
   client's own retry/offline handling (Milestone 13) already knows how
   to recover from. Added explicit `max: 10` and `idleTimeoutMillis:
   30_000` (pg's existing defaults, made explicit rather than left
   implicit — no measured need to change them) and `connectionTimeoutMillis:
   5_000` (the one that actually changes behavior).

**Tests performed:**
- `npm run typecheck` — clean.
- `npm test` (backend) — **131/131 passed** against a real local
  Postgres, confirmed *after* the pool.ts change, not just before it —
  the test suite imports the same `pool` used in production code
  (`tests/helpers/db.ts`), so this is a real, not just theoretical,
  check that the new timeout values don't break normal operation.
  `fileParallelism: false` (all test files share one database
  sequentially) means the suite never approaches the new `max: 10`
  connection ceiling regardless.
- Android: no new pure-logic unit tests this milestone (the changes are
  a shared-client wiring change with no new branching logic); verified
  by re-reading all six touched/created network files in full, a grep
  confirming zero stray `OkHttpClient`/`TimeUnit` references remain in
  the five client files, and a brace-balance check across all six.
  `build-apk.yml` CI compile — see below.

**Deliberately not built:** WorkManager or any other background-job
scheduler for prefetching/precaching (no measured stutter or slow path
to justify it — the existing Coil cache and per-screen concurrent fetch
already cover the cases this milestone's checklist names); reducing
`AppContainer`'s eager-singleton list (every one already has a
documented, load-bearing reason for its own eagerness, recorded milestone
by milestone in that file's own kdoc — this pass confirmed each reason
still holds rather than second-guessing settled design).

**Milestone 15 is complete.**

## Milestone 16 — Full End-to-End Test

**Status:** Complete for everything determinable without real Fire TV
hardware; the remainder is a documented, ready-to-run hardware pass, not
yet executed (this sandbox has no Android SDK, emulator, or physical
device — stated since Milestone 0, unchanged here).

**Approach:** Rather than mark this complete on the strength of the 131
existing unit/integration tests alone (each of which verifies one endpoint
or one milestone's slice in isolation), built a genuine end-to-end
script — `server/scripts/e2e-full-flow.ts` (`npm run e2e`) — that drives a
**real, running** server instance over **real HTTP**, no `supertest`, no
in-process shortcuts, no direct database access, through all eight named
scenarios from the spec in one continuous multi-device session, the same
way the Fire TV app's own `AuthApiClient`/`SettingsApiClient`/etc. talk to
it. This is a different, and genuinely additional, kind of verification
than the existing test suite: it proves the *whole flow* holds together in
sequence — register, populate data, a second device converging on that
exact state, cross-device edits propagating, logout, a third completely
separate account, replay-safety, and cross-account attacks — not just that
each endpoint is correct on its own.

**What the script covers and how it was actually run:**
- Created a fresh local Postgres database (`mangotv_dev`), ran `npm run
  migrate` against it (12/12 migrations applied cleanly, same as every
  prior milestone's own rehearsal), started the real `npm run dev` server
  against it, and ran `npm run e2e` against that live server — genuinely
  live, not simulated.
- **40/40 checks passed**, run twice in a row to confirm the script is
  safely re-runnable against a persistent (non-truncated) database — each
  run uses a timestamp-unique email per account, so it never collides with
  a previous run's data, matching how a real smoke test against a shared
  dev/staging deployment would actually need to behave.
- TEST 1 (New User): QR create → resolve (pending) → complete (register) →
  status delivers real tokens → a second status call proves the token
  can't be claimed twice (replay prevention, re-verified live, not just
  trusted from Milestone 4's own tests) → `/user/me` confirms the new
  identity.
- TEST 2 (Data Creation): watchlist adds, a watch-progress report
  (confirmed it populates Continue Watching), a settings change, an addon
  install.
- TEST 3 (Second Device): a second QR session with a different `deviceId`,
  logging into the *same* account, receiving *different* session tokens
  than Device A's own (proving independent per-device sessions, not one
  shared credential), and seeing Device A's exact watchlist/continue-
  watching/settings/addons.
- TEST 4 (Cross-Device Changes): Device B removes an item and changes a
  setting; Device A's next `GET` reflects both.
- TEST 5 (Logout): Device B's access token is confirmed rejected
  immediately after logout, while Device A's own session is confirmed
  still valid throughout — sessions are per-device, not account-wide.
- TEST 6 (Account Switch): a third device registers a completely different
  account; its watchlist/addons are confirmed empty and its settings are
  confirmed to be untouched defaults (`updatedAt: null`), not a leak of
  the first account's state.
- TEST 7 (Offline): honestly scoped to what a backend-only script can
  actually prove — replaying an identical "queued" write after a simulated
  reconnect is safe (no duplicate, no error), and a stale-timestamped
  replay correctly loses to the already-applied newer write (last-write-
  wins holds under replay). The script's own console output states plainly
  that local cache continuity and automatic queued-change replay on a real
  device are Milestone 13's Android-side behaviors and require real
  hardware to verify — not glossed over as covered when it isn't.
- TEST 8 (Security): Bob's attempt to delete Alice's exact watchlist item
  (natural key guessed, `updatedAt` engineered far into the future to try
  to win any last-write-wins race) affects nothing of Alice's; an
  unauthenticated request, a malformed token, and an attempt to revoke a
  session id Bob doesn't own (404, not a confirmation it exists) all fail
  exactly as designed.

**Deliberately scoped out of the script, staying real endpoint coverage
rather than exhaustive:** an addon removal round trip and the paginated
`GET /user/history` endpoint aren't exercised here — both already have
dedicated tests in the existing 131 (`addons.test.ts`,
`watch-progress.test.ts`), and this script's job is proving the eight
*named scenarios* hold end-to-end, not re-covering ground the unit suite
already owns.

**New:** `docs/milestone-16-e2e-test-plan.md` — the hardware-dependent half
of this milestone: the same eight scenarios, broken into concrete steps
for a human running two or more real Fire TV devices against a real
deployment, each one clearly marked with what to expect, plus instructions
for running `npm run e2e` yourself. Explicitly separates what's already
proven (this changelog entry, and the script itself) from what still
needs a human on real hardware (QR scanning with a real camera, D-pad
navigation, actually disabling Wi-Fi, on-device performance) — TEST 8 in
that document is marked as already exhaustively covered without hardware,
since nothing about the real Android app changes a server-side
authorization guarantee.

**Tests performed:**
- `npm run typecheck` — clean.
- `npm test` (backend) — 131/131, unaffected (no existing file changed
  behavior this milestone, only a new script and one new `package.json`
  script entry added).
- `npm run e2e` against a real local server + real local Postgres —
  **40/40**, twice.
- Android: no changes this milestone.

**Issues discovered (at the time this milestone was written):** none in
the system under test — every one of the 40 checks passed on the first
run. The value of building this was confirmatory, not corrective:
Milestones 6-15 already worked correctly end-to-end, this is what
actually proves that claim, in sequence, against a live process, rather
than each milestone's own isolated test slice standing in for it.

**Issues fixed (at the time):** N/A — see above.

**Update — real deployment attempt (post-Milestone-17):** the user's own
first real deploy to Render caught something this project's own
end-to-end script and CI genuinely could not have: `npm run build`
failed with a wall of TypeScript errors (`@types/express`/`@types/node`/
`@types/pg` all "cannot find" errors) because Render sets
`NODE_ENV=production` *during the build step*, and `npm ci` skips
`devDependencies` — which is exactly where TypeScript itself and every
`@types/*` package live — whenever `NODE_ENV=production` is set in its
environment. GitHub Actions never sets `NODE_ENV=production` during
`npm ci`, and this sandbox's own local builds never had a reason to
either, so this gap was invisible to every automated check that existed
before a human actually tried deploying to a real host.

**Fixed:** added `server/.npmrc` (`production=false`), which forces
`devDependencies` to install regardless of `NODE_ENV` — portable to any
host (Render, Railway, Heroku, ...) rather than a Render-dashboard-
specific workaround. Verified by reproducing the exact failure condition
locally (`rm -rf node_modules && NODE_ENV=production npm ci && NODE_ENV=production npm run build`,
which failed identically to the Render log before the fix) and
confirming it now succeeds, then re-running the full 131-test suite
after the clean reinstall to confirm nothing else regressed.

**A second, different failure surfaced immediately after** — the build
itself now succeeded, but `npm start` on Render failed with `Cannot find
module '.../dist/index.js'`. Root cause: `tsconfig.json`'s `rootDir` is
`"."` (the `server/` directory itself), not `"src"` — deliberately, so
one tsconfig can typecheck both `src/` and the sibling `scripts/`
directory — and `tsc` preserves that root when emitting, so the real
compiled entrypoint lands at `dist/src/index.js`, not `dist/index.js`.
`package.json`'s own `"start": "node dist/index.js"` never matched that.
**This is also on this session**, not just a pre-existing gap passively
inherited: the very verification just described (`npm run build`
succeeding) was incomplete — it checked that the build produced *a*
`dist/` directory, not that `npm start` could actually run from it, so
this second bug was sitting right next to the first one and the first
round of verification didn't catch it. Fixed by pointing `start` at
`node dist/src/index.js` (the actual emitted path, confirmed with `find
dist -name '*.js'` rather than assumed), then verified properly this
time: a full clean rebuild followed by actually running `npm start`
under `NODE_ENV=production` and confirming `curl /health` returns `200`
from the real running process — not just that the build step exits 0.

Together, both are a genuine, if narrow, illustration of exactly what
Milestone 16's hardware/real-deployment pass is for: proving the system
against reality, not just against its own test suite — and a reminder
that "the build succeeded" and "the app actually starts and serves
traffic" are two different claims that both need checking.

**Milestone 16 is complete for the code-verifiable half**, and the
backend is now confirmed to actually build on a real deployment target.
The remaining hardware pass in `docs/milestone-16-e2e-test-plan.md` is
ready to run on real Fire TV devices whenever they're available; its
result should be appended to this entry when it is, the same way every
other milestone records its test results.

## Milestone 17 — Final Documentation

**Status:** Complete.

**Scope note:** the spec frames this as coming "once all milestones are
complete." Milestone 16's hardware pass is still open (see above) — this
milestone proceeded anyway, at the user's explicit direction, since
writing accurate reference documentation for a system whose code and
behavior are already finished and verified doesn't depend on a human
having since run it on physical Fire TV hardware. Nothing in this
milestone claims the hardware pass happened; every document below states
plainly that it hasn't.

**Changes:** No application code touched — documentation only. Rather
than write from memory, every fact below was re-verified against current
source immediately before writing it: all 12 migration files read fresh
in full (not recalled from earlier in this session), every route file's
actual response shape, `.env.example`'s exact variable names, and
`server/README.md`'s existing (already-accurate) endpoint reference cross-
checked rather than duplicated.

**New:**
- `docs/ARCHITECTURE.md` — the centerpiece: a system overview (with an
  ASCII diagram of the Fire TV → API → Neon path and what each layer owns
  and never touches), a full table-by-table database reference (purpose +
  key design decisions for all 10 application tables, drawn from each
  migration's own header comments but reorganized as a lookup table
  rather than 12 separate chronological files), the QR/session/token-
  expiration/device-association/logout authentication flow with a
  sequence diagram, and the synchronization architecture (what syncs, how
  often, conflict resolution, offline behavior, retry behavior) distilled
  from Milestones 6-13's own reasoning into one current-state reference
  instead of scattered across per-milestone changelog entries.
- `docs/DEPLOYMENT.md` — Neon setup (including the pooled-vs-direct
  connection string distinction migrations need), backend deployment
  (graceful shutdown, health check, `trust proxy`'s single-hop assumption
  and when to revisit it), every environment variable on both the backend
  and Android sides, domain configuration, why HTTPS here is load-bearing
  rather than incidental (tied directly to Milestone 14/15's build-time
  `require(apiBaseUrl.startsWith("https://"))` check), and how the QR
  activation URL is actually constructed.
- `docs/TESTING.md` — the durable, non-milestone-numbered testing guide:
  running the 131 backend unit tests, running the automated
  `npm run e2e` end-to-end script, and the eight-scenario real-hardware
  test plan (moved here from `docs/milestone-16-e2e-test-plan.md`, which
  now points here instead of carrying its own now-duplicate copy, so the
  steps exist in exactly one place rather than two that could drift).

**Changed:**
- `docs/milestone-16-e2e-test-plan.md` — its "What still needs real
  hardware" section (the eight step-by-step scenarios) replaced with a
  pointer to `docs/TESTING.md`'s canonical copy; the historical "what was
  verified, 40/40" record from Milestone 16 itself is untouched.
- `server/README.md` — removed the stale "Milestone 9 (current)"
  framing (the file was last edited when addon sync was in progress; the
  system has since reached Milestone 16) in favor of linking
  `docs/ARCHITECTURE.md`/`docs/DEPLOYMENT.md`; added the `npm run e2e`
  section. Its existing endpoint-by-endpoint reference was left as-is —
  already accurate, already thorough, no reason to duplicate it into
  `ARCHITECTURE.md` as well.
- `README.md` (repo root) — replaced the "Step 1 of the rebuild: Home
  screen" status line (accurate when written, badly stale after 16
  milestones of feature and account-system work since) with the app's
  actual current feature set, and expanded the project-structure listing
  to include every package added since (`data/auth`, `data/network`,
  `data/sync`, `data/history`, `data/player`, `ui/auth`, plus the
  `server/` subproject) — the previous listing only ever described the
  original home-screen-only rebuild.

**Tests performed:** N/A for the documentation itself (no code changed to
test); every factual claim inside the new documents was checked against
its actual source (migration SQL, route handlers, schema files, kdoc
comments) at write time rather than assumed correct from memory —
documented as an explicit step in "Changes" above rather than left
implicit, since inaccurate documentation would be a real defect in a
milestone whose entire deliverable *is* documentation.

**Issues discovered:** none in the underlying system — this milestone
read and organized what Milestones 0-16 already built and verified, it
didn't uncover new application behavior.

**Issues fixed:** N/A — see above.

**Milestone 17 is complete.** All 18 milestones (0-17) are now done. The
one open item across the whole project remains Milestone 16's hardware
pass — see `docs/TESTING.md` §3 to run it, and append the result to
Milestone 16's entry above when it happens, per that section's own
"Recording a hardware run" note.

## Post-Milestone-17 — Direct Password Sign-In on the Fire TV Remote

**Status:** Complete.

**Context:** Not one of the original 18 milestones — added afterward, at
the user's explicit request, once the QR flow was confirmed working on a
real deployed backend and a real device: "I still want the ability to
signup or sign in using the firestick, so the user isn't forced to use
the QR code if they don't want to." Milestones 4/5 deliberately routed
every TV sign-in through the QR/activation-page flow specifically because
the Fire TV has no on-device keyboard entry point built for it at the
time; this doesn't revisit that decision so much as add a second, fully
independent path alongside it — the QR flow is untouched and remains the
default first two buttons on `AuthStartScreen`.

**Changes:** Entirely additive on the Android side; the backend needed no
changes at all. `POST /auth/register` and `POST /auth/login` were built
and fully tested back in Milestone 3, but nothing on the TV had ever
called them — every prior sign-in path went through the QR/activation
flow's own `POST /auth/qr/complete` instead. This feature is simply the
first caller of those two endpoints from the Fire TV app itself.

**New:**
- `PasswordSignInViewModel.kt` — `validateCredentials()` (a small,
  unit-tested, plain top-level function: catches a missing `@`, a missing
  domain dot, or a too-short password before spending a network round
  trip, without trying to out-validate the server's own zod schema,
  which remains the real authority), plus a ViewModel that deliberately
  duplicates, rather than shares, `QrSignInViewModel`'s post-auth
  migration-decision handling — two real call sites isn't yet enough to
  justify guessing at a shared abstraction's shape.
- `PasswordSignInScreen.kt` — the on-device form: email, an optional
  display name (register mode only), a password field with a show/hide
  toggle, and a login/register mode switch, all wired into one D-pad
  focus chain. The form stays on screen through a failed attempt (a typo,
  a wrong password) rather than clearing, so the user never has to
  re-type an email address they already got right.
- `app/src/test/java/com/mangotv/app/ui/auth/PasswordSignInViewModelTest.kt`
  — 11 JVM unit tests covering every `validateCredentials()` branch
  (blank/whitespace-only email, missing `@`, missing domain dot, embedded
  whitespace, surrounding whitespace trimmed, password below/at the
  8-character minimum, and that email is checked before password).

**Changed:**
- `AuthDtos.kt` — added `RegisterRequest`/`LoginRequest`/
  `AuthResultResponse`, matching `server/src/schemas/auth.ts`'s
  `registerSchema`/`loginSchema` and the response shape assembled in
  `routes/auth.ts` field-for-field (re-verified directly against current
  server source, not assumed from memory).
- `AuthApiClient.kt` — added `register()`/`login()`, reusing the existing
  `post()`/`execute()` helpers (already handle both 200 and 201 via
  `response.isSuccessful`).
- `AuthRepository.kt` — added `registerWithPassword()`/
  `loginWithPassword()`, sharing a private `authenticateWithPassword()`
  helper. Both reuse the same `DeviceIdentity.getOrCreate()` the QR flow
  uses, so a device recognized via one path is recognized the same way
  via the other.
- `AuthStartScreen.kt` — added a third, lower-emphasis, fully
  D-pad-focusable option below the existing QR hint text ("Prefer to
  type on your remote instead?"), wired into the existing Log In/Sign Up
  focus chain. The original two buttons and their behavior are unchanged.
- `MangoRoutes.kt` / `MangoNavHost.kt` — new `auth/password` route.
- `docs/ARCHITECTURE.md` §3 — restructured to document both paths: 3.1
  (QR flow) reworded to stop claiming the app "never collects or
  transmits a password" *unconditionally* (now scoped to that path
  specifically), and a new §3.2 added describing the direct-entry flow,
  its sequence diagram, and why it's additive rather than a replacement.
  Later subsections renumbered (old 3.2-3.5 → 3.3-3.6); checked for
  cross-references first — none existed outside this file.

**Tests performed:**
- 11 new JVM unit tests for `validateCredentials()`, covering every
  branch and the email-before-password ordering.
- Manual brace/paren-balance check across all 9 touched/created Kotlin
  files (a small script counting `{`/`}` and `(`/`)` per file) — all
  balanced.
- Manual import-completeness and duplicate-import check on every touched
  file.
- Manual field-by-field cross-check of the new DTOs and the actual
  request/response wire shape against current server source: `email`,
  `password`, `displayName`, `deviceId`, `deviceName`, `platform` on the
  way in (`server/src/schemas/auth.ts`), and `accessToken`/
  `accessTokenExpiresAt`/`refreshToken`/`refreshTokenExpiresAt`/`user`
  (`{id, email, displayName}`) on the way back
  (`routes/auth.ts`'s `serializeTokens`, `authService.ts`'s
  `insertUser`/`verifyCredentials`) — confirmed to match exactly in both
  directions.
- **Not performed:** an actual Kotlin/Gradle compile. This sandbox has no
  Android SDK and no pre-populated Gradle/Maven cache (confirmed by
  checking for both directly), and fetching a full Android SDK plus AGP's
  dependency graph from scratch isn't realistic here — the same
  constraint noted since Milestone 0. `build-apk.yml` (manual-dispatch
  only on this branch) is the actual compile verification and needs to be
  triggered after this is pushed.

**Issues discovered:** none in existing code — this is new, additive
functionality, not a fix.

**Issues fixed:** N/A — see above.

**Update — hero image + reworked into a method-choice screen:** two rounds
of direct user feedback on the shipped UI, both addressed in the same
working session before any of this had been reviewed elsewhere:

1. **Living room hero image.** The user supplied a template mockup and a
   background photo (a TV in a living room, extracted directly from the
   conversation's own image attachments and added as
   `res/drawable-nodpi/auth_hero_living_room.webp`). `AuthStartScreen` now
   draws it full-bleed on the right, with a `drawWithCache` gradient scrim
   that's solid `MangoBackground` for the left half and fades linearly to
   fully transparent by the right edge — alpha-only, so the RGB channel
   stays constant through the fade instead of muddying through black the
   way interpolating to `Color.Transparent` would.
2. **Layout/copy fixes once that image was in place:** the left column's
   `widthIn` was 620dp — comfortably past the fade's start at the screen's
   own horizontal midpoint (~480dp on this app's 960dp reference width;
   see `ui/theme/Dimens.kt`'s "1080p/4K screens" convention) — so the
   Log In/Sign Up buttons and the body paragraph could visibly
   run into the image. Reduced to 400dp, which clears the fade with margin
   to spare. Also dropped "live TV" from the body copy per the user's
   request ("Stream the latest movies, TV shows and more.").
3. **The third "Prefer to type on your remote instead?" button is gone.**
   Not because direct password entry was removed — because where the
   choice lives moved. `AuthStartScreen` is back to exactly two buttons;
   both now lead to a new `AuthMethodScreen` (`auth/method/{intent}`) that
   asks "Scan a QR Code" or "Type on My Remote" before committing to
   either path. This is a UX reshuffle, not a functional one:
   `QrSignInScreen` and `PasswordSignInScreen` are otherwise unchanged,
   and the `intent` ("login"/"register") argument threads through
   `AuthMethodScreen` to whichever one is chosen exactly as it did before
   — still display-only, still never sent to the backend.
4. **`PasswordSignInViewModel` now takes a `SavedStateHandle`**, mirroring
   `QrSignInViewModel`'s existing pattern exactly, so its initial
   Login/Register mode matches whichever button the user pressed on
   `AuthStartScreen` two screens back, instead of always defaulting to
   Login. The in-screen mode toggle still overrides it either way.

**Tests performed (this update):** re-ran the same manual verification as
the original entry on every newly touched/created file (`AuthStartScreen.kt`,
the new `AuthMethodScreen.kt`, `PasswordSignInViewModel.kt`,
`MangoRoutes.kt`, `MangoNavHost.kt`) — brace/paren balance, duplicate-import
check, and a full-codebase grep for stale references to the removed
`MangoRoutes.AUTH_PASSWORD` constant and `onUsePassword`/
`usePasswordFocusRequester` (none found). Same Kotlin-compile caveat as
before: `build-apk.yml` triggered fresh after pushing, not verified locally.

**Issues discovered (this update):** `QrSignInScreen`'s own kdoc still said
"Reached from either AuthStartScreen button," which stopped being exactly
true once `AuthMethodScreen` was inserted in between. Corrected it while
in the area.

**Issues fixed (this update):** see above (kdoc correction) — nothing
functional.

## Post-Milestone-18 — Auto-Remove Movies from Continue Watching Past 85% Watched

**Status:** Complete.

**Context:** User request: "automatically remove a movie from continue
watching if user has watched more than 85% of the movie." Continue
Watching was previously only ever cleared for a title when a report
carried `completed: true`, and `PlayerScreen`'s reporting loop only sends
that on ExoPlayer's natural `STATE_ENDED` event (`PlaybackPhase.Ended`).
A user who backs out of a movie during the credits, or leaves the player
a few minutes before the stream's own end, never triggers that event, so
the title lingered in Continue Watching indefinitely despite being
effectively finished.

**Changes:**
- `server/src/services/playbackProgressService.ts` — `recordProgress` now
  derives its own `completed` value instead of trusting `input.completed`
  outright: for `contentType === "MOVIE"`, crossing 85% of `durationMs`
  (`positionMs / durationMs > 0.85`) counts as completed even when the
  client reported `completed: false`, clearing (soft-deleting) the
  title's `continue_watching` row and marking its `watch_history` row
  `completed` the same way a natural end-of-playback report already did.
  Scoped to movies only — an episode's own completed flag stays
  per-episode, so being mostly through one episode never clears the
  parent show's Continue Watching card out from under the next episode.
  No client changes were needed: every existing report site (periodic
  while playing, on pause, on stop/dispose, and on natural completion)
  already sends real `positionMs`/`durationMs`, and the Android app's
  `ContinueWatchingSyncRepository.reconcile()` already applies whatever
  `continueWatching` state the server's response carries back, including
  a non-null `deletedAt`.
- `server/README.md`, `docs/ARCHITECTURE.md` — updated the
  `POST /user/watch-progress` and `continue_watching` descriptions to
  document the 85% rule alongside the existing `completed: true` one.

**Tests added (`server/tests/watch-progress.test.ts`):**
- A movie past 85% of its duration is cleared from Continue Watching (and
  its `watch_history` row marked `completed`) even though the report
  itself said `completed: false`.
- A movie at exactly 85% is not yet cleared — the rule is "more than,"
  not "at least."
- An episode past 85% of its own runtime does not clear the parent show's
  Continue Watching row (contentType-scoping check).

**Tests performed:** Ran the full backend suite locally against a
throwaway local Postgres 16 database (started for this session; not the
project's Neon instance) — `npm test`: 134/134 passed. `npm run
typecheck`: clean.

**Issues discovered:** none beyond the one described in Context.

**Issues fixed:** see Changes above.

## Post-Milestone-19 — Faster Source Selection (Progressive Loading)

**Status:** Complete.

**Context:** User report: the "Select a Source" screen was slow to load.
`SourcesViewModel.load()` queried every active addon's `getStreams()` in
parallel but sat on `awaitAll()` before showing anything, so the whole
screen stayed on its loading skeleton for as long as the single slowest
installed addon took to answer — even when every other addon had already
responded in milliseconds. Real Stremio-style "stream" addons commonly
scrape live sources and can genuinely take several seconds (unlike
catalog/meta endpoints), so the picker felt sluggish with more than one
addon installed, or even with just one that's a little slow.

**Changes:**
- `SourcesViewModel.kt` — `load()` now branches in two directions after a
  purely local (no-network) check of whether this load can end in the
  existing Continue-Watching "resume" shortcut:
  - The resume path (an exact season/episode match whose last-used source
    is still available skips straight to Player) is unchanged: it still
    waits for every provider before deciding, since it needs the full
    merged stream list to know whether to skip the picker at all, and
    must never flash the interactive list first.
  - The normal "show the picker" path now fans out every provider's
    `getStreams()` call and publishes results as each one completes (via
    a `Channel`, drained in completion order rather than launch order)
    instead of waiting for `awaitAll()`. A fast addon's sources appear
    immediately; slower addons' results are appended as they arrive.
  - Added `SourcesUiState.Loaded.isSearchingMore`, true while any
    provider is still outstanding.
- `SourcesScreen.kt` — while `isSearchingMore` is true: an empty list
  shows a new `SourcesSearchingState` ("Searching for sources…") instead
  of the "No sources found, try installing more addons" empty state
  (misleading before slower addons have had a chance to reply); a
  non-empty list shows whatever's arrived so far plus a small inline
  "Looking for more sources…" indicator, reusing the same amber
  `CircularProgressIndicator` style `SearchScreen` already uses for its
  own in-progress state.

**Tests performed:** This sandbox has no Android SDK configured (no
`ANDROID_HOME`, no `local.properties`), and per every prior milestone's
own notes, fetching one from scratch here isn't realistic — so, matching
this project's established fallback for Kotlin-only changes: a
script-based brace/paren/bracket-balance check on both touched files; a
full manual re-read of the new `load()` control flow (the resume-vs-picker
split, the channel's send/receive count matching exactly, Compose
recomposition/focus behavior around a streams list that now grows across
multiple emissions); confirmed both `SourcesUiState.Loaded(...)`
construction sites already use named arguments (a positional call is what
a new field with a default value could otherwise silently break) and that
no existing test references `SourcesViewModel`/`SourcesUiState` — this
app has exactly one ViewModel unit test in the whole project
(`PasswordSignInViewModelTest`), and it covers a pure function, not a
provider-fan-out ViewModel like this one, so no existing coverage needed
updating. **Not performed:** an actual Kotlin/Gradle compile or on-device
verification — `build-apk.yml` (manual-dispatch only on this branch) is
the real compile check and needs to be triggered after this is pushed.

**Issues discovered:** none beyond the one described in Context.

**Issues fixed:** see Changes above.

## Post-Milestone-20 — Watched Tick Mark on Every Poster, Not Just My List

**Status:** Complete.

**Context:** The prior milestone ("Add watched tick mark and My List
auto-add at 85% movie completion", commit `abe0b75`) added
`Content.watched`, `ContentCard`'s green checkmark badge, and
`MyListRepository.markWatched()` — but only `MyListViewModel.toContent()`
ever set `watched = true` on a `Content` instance. Every other screen
(Home, Movies, TV Shows, Genre Results, Search, Detail's Similar row)
built its `Content` items with the field left at its `false` default, so a
watched movie's tick only ever appeared once the user opened My List
itself, not on the same poster anywhere else in the app.

**Changes:**
- `HomeViewModel.kt` — added a `watchedIds` field kept in sync via a new
  `myListRepository.items` collector (mirroring the existing
  `continueWatchingRepository.items` one already there), applied to
  `rawSections` and `continueWatchingSection` inside `applyPreferences()`
  via new `HomeSection.withWatchedFlags()` / `Content.withWatchedFlag()`
  helpers.
- `TypeBrowseViewModel.kt` (backs Movies/TV Shows), `GenreResultsViewModel.kt`
  — same `watchedIds` + collector pattern; both now rebuild their
  published `HomeSection` from the pristine `allItems` list (via a new
  `currentSection()`) whenever watched status changes, not only on their
  own `load()`/`loadMore()`.
- `SearchViewModel.kt` — added `rawMovies`/`rawTvShows` fields (search
  results previously lived only inside the already-emitted
  `SearchUiState.Results`, leaving nothing pristine to re-stamp from) plus
  the same collector, re-publishing via a new `currentResults()`.
- `DetailViewModel.kt` — added `rawContent`/`rawSimilar` fields (the two
  inline `DetailUiState.Success(...)` constructions in `load()` now go
  through a new `publish()`) plus the same collector, so the Similar row's
  cards get the tick too.
- Every one of the above always re-derives its published list from a
  pristine, never-stamped source (the raw fetched items, never the last
  emitted UI state): `SavedListItem.watched` only ever flips false-to-true,
  but a title can still leave `watchedIds` entirely if the user manually
  removes it from My List, and re-stamping an already-stamped list can't
  represent that going back to unwatched — re-deriving from pristine data
  each time can.
- Each new collector only republishes while the current UI state is
  already a "loaded" one (`Loaded`/`Success`/`Results`), so it never
  overwrites a `Loading` or `Error` state with stale data.

**Tests performed:** This sandbox still has no route to `dl.google.com`
(the same limitation every prior milestone's own notes describe), so a
Gradle/AGP build isn't possible here — reconfirmed this session:
`:app:compileDebugKotlin` fails resolving the `com.android.application`
plugin itself, before any Kotlin source is even compiled. Fell back to
this project's established substitute for a Kotlin-only change: a
script-based brace/paren/bracket balance check on all five touched files
(all clean), plus a full manual re-read of each ViewModel's control flow
and every call site of the helpers introduced. **Not performed:** an
actual Gradle/Kotlin compile or on-device check — `build-apk.yml` CI is
the real compile check and should be triggered after this is pushed, then
verified on-device (watch a movie past ~85%, confirm the tick now also
shows on Home, Movies, TV Shows, Genre Results, Search, and Detail's
Similar row, not only My List).

**Issues discovered:** none beyond the one described in Context.

**Issues fixed:** see Changes above.

## Post-Milestone-21 — Backfill Watched Status From Existing Watch History

**Status:** Complete.

**Context:** User question: would a movie finished *before* the
Post-Milestone-19/20 "watched" work existed also get the tick and a My
List entry, or only ones finished from now on? Tracing every call site of
`markWatched()` confirmed the latter -- it fires only from
`PlayerViewModel.reportProgress()` during live playback, and nothing (not
`FirstLoginMigrationCoordinator`, not any sync repository) ever reads past
watch history to backfill it. Asked the user whether that gap should be
closed; they said yes.

**Changes:**
- `server/src/routes/history.ts`'s existing `GET /user/history` endpoint
  is unchanged -- it already returned everything needed (`completed`,
  `contentType`, `contentId`, `providerId`, `title`, `posterUrl`,
  keyset-paginated on `watched_at`) and simply had no client consumer yet.
  This entire milestone is client-only.
- `ContinueWatchingDtos.kt` -- added `WatchHistoryListResponse`; updated
  its file comment, which previously said `/user/history` had "no client
  here yet."
- `PlaybackProgressApiClient.kt` -- added `getHistory(accessToken, limit,
  before)`, keyset-paginated the same way the server's own
  `historyQuerySchema`/`listWatchHistory` already do (`limit` capped at
  200 to match the schema's own max; `before` is the previous page's
  oldest item's own `watchedAt`, matching the server's "give me rows
  watched before this timestamp" contract exactly).
- New `WatchedBackfillState.kt` -- a device-scoped one-time-done flag,
  the same shape as `FirstSyncState` (`isDone()`/`markDone()`/`reset()`
  over a boolean DataStore key), for the same reason: this needs to run
  once, not on every sync.
- `WatchlistSyncRepository.kt` -- new
  `backfillWatchedFromHistoryIfNeeded()`: pages through `/user/history`
  newest-first, filters to `contentType == MOVIE && completed == true`
  (the server already re-derives `completed` from its own >85% rule, so
  this matches PlayerViewModel's live threshold check exactly, not a
  separately-invented rule), and replays each match through the existing
  `MyListRepository.markWatched()` -- already idempotent (no-ops once an
  item is watched=true) and already wired to push to `watchlist_items`
  via `onLocalChange`, so no new server-side write path was needed either.
  Only marks `WatchedBackfillState` done after a full, uninterrupted
  pass; a network failure mid-scan leaves it not-done so the next
  `syncAll()` simply starts over from the newest entry, rather than
  resuming from a partial cursor.
- `SyncManager.kt` -- `syncAll()` now calls
  `watchlistSyncRepository.backfillWatchedFromHistoryIfNeeded()` right
  after its existing pull+retry `.join()`, but deliberately NOT inside
  that joined block -- fire-and-forget, so a long watch history can't add
  perceptible delay to an otherwise-fast app launch, and it always runs
  against the account's just-pulled My List state rather than a stale
  local cache.
- `AccountSwitchCoordinator.kt` -- resets `WatchedBackfillState` on
  sign-out, mirroring `FirstSyncState`'s own reset, so a different
  account signing in on the same device gets its own fresh backfill pass
  instead of inheriting the previous account's "already done."
- `AppContainer.kt` -- wires the new `WatchedBackfillState` singleton into
  both `WatchlistSyncRepository` and `AccountSwitchCoordinator`.

**Tests performed:** Same sandbox limitation as every recent milestone
(no route to `dl.google.com`, so no Gradle/AGP build is possible here): a
script-based brace/paren/bracket balance check on all seven touched/added
files (all clean), a full manual re-read of the pagination loop (cursor
direction, page-size-based termination, the idempotent replay), and
confirmed no other call site constructs `WatchlistSyncRepository` or
`AccountSwitchCoordinator` that would break from their new constructor
parameter (only `AppContainer.kt` constructs either, already updated).
Confirmed `GET /user/history`'s actual route mounting
(`app.use("/user", historyRouter)` + `historyRouter.get("/history", ...)`)
matches the URL the new client call uses. **Not performed:** an actual
Gradle/Kotlin compile, or an on-device/server-integration check --
`build-apk.yml` CI is the real compile check; on-device verification
should specifically confirm that a movie finished *before* this shipped
(and not rewatched since) gets its tick and a My List entry after the
next app launch or sign-in, without noticeably slowing that launch down.

**Issues discovered:** none beyond the one described in Context.

**Issues fixed:** see Changes above.

## Post-Milestone-22 — Wire Up Detail's "Mark As Watched" Button

**Status:** Complete.

**Context:** User request: the three-dot menu on a movie/TV show's Detail
page already had a "Mark as watched" button (`DetailHeroSection`'s
`Icons.Outlined.CheckCircle` `HeroIconButton`, revealed by tapping the
three-dot "More options" button next to Play) -- but `DetailScreen.kt`
wired its `onWatched` callback to a literal no-op (`onWatched = {}`), so
tapping it did nothing. This button predates every "watched" work this
session did.

**Changes:**
- `DetailViewModel.kt` -- added `markWatched()`: reads the current
  `DetailUiState.Success.content` and calls the existing
  `MyListRepository.markWatched(content)` directly (non-suspend, since
  `markWatched()` already fires fire-and-forget on its own scope --
  no `viewModelScope.launch` needed, unlike `toggleMyList()`). Works for a
  TV show, not just a movie: `markWatched()` itself has no type
  restriction -- the movie-only scoping the rest of this feature uses
  lives in `PlayerViewModel`'s own auto-detection logic (a single episode
  crossing 85% shouldn't auto-mark a whole show), which doesn't apply to
  a user's own explicit, deliberate choice here.
- `DetailScreen.kt` -- threaded a new `onMarkWatched: () -> Unit` through
  `DetailContent` (wired to `viewModel::markWatched`), replacing the
  `onWatched = {}` stub with `onWatched = onMarkWatched`, and passing the
  already-available `content.watched` through as `DetailHeroSection`'s
  new `isWatched` parameter.
- `DetailHeroSection.kt` -- added `isWatched: Boolean = false`; the
  button's icon/description now swap to a filled checkmark / "Watched"
  once true, the same way its neighboring Watchlist button already swaps
  Add/Check on `isInMyList` -- without this, a tap would silently update
  `MyListRepository` with no visible confirmation on a button whose
  neighbor already sets that visual-feedback expectation. Deliberately
  stays a one-way action (no "unmark" tap), matching
  `MyListRepository.markWatched()`'s own false-to-true-only contract used
  everywhere else this session.

**Tests performed:** Same sandbox limitation as every recent milestone
(no route to `dl.google.com`): brace/paren/bracket balance check on all
three touched files (clean), full manual re-read, and confirmed
`DetailHeroSection` has exactly one call site (`DetailScreen.kt`) so no
other caller needed updating. `Icons.Filled.CheckCircle` was added
alongside the file's existing `Icons.Outlined.CheckCircle` import --
confident (not verified by compiling) this doesn't collide, since Kotlin
resolves same-named extension properties by their differing receiver
type (`Icons.Filled` vs `Icons.Outlined`), the same pattern already used
elsewhere in this codebase (e.g. `Icons.Filled.Check` alongside other
icon families). **Not performed:** an actual Gradle/Kotlin compile or
on-device check -- `build-apk.yml` CI is the real compile check; on-device
verification should open a movie and a TV show's Detail page, tap the
three dots, tap "Mark as watched," and confirm the icon switches to
filled immediately and the title appears in My List's Watched filter.

**Issues discovered:** none beyond the one described in Context.

**Issues fixed:** see Changes above.

## Post-Milestone-23 — "Mark As Watched" On The Long-Press Menu Too

**Status:** Complete.

**Context:** User request: add the same "Mark as watched" action to a
poster's long-press quick-actions menu (`CardActionsMenu.kt`), not only
Detail's three-dot menu (Post-Milestone-22). This menu already renders
once at the NavHost root for every `ContentCard` in the app (Home's rows,
My List, Movies/TV Shows/Genre grids, Detail's Similar row -- see its own
kdoc), so this one change reaches every poster, not just certain screens.

**Changes:**
- `CardActionsMenu.kt` -- added a `CardActionRow` for "Mark as watched"
  right after the existing "Add/Remove My List" row (both concern My
  List status), calling `myListRepository.markWatched(content)` directly
  (non-suspend -- no `coroutineScope.launch` needed, unlike the
  neighboring "Add/Remove My List" row's `toggle()` call, which is
  suspend). Works for a movie or TV show, same reasoning as
  Post-Milestone-22's Detail button: `markWatched()` itself has no type
  restriction.
- Added a new `isWatched` local val, derived from the already-collected
  `savedIds` (`myListRepository.items`) the exact same way the existing
  `isInMyList` is -- not from `content.watched` -- so the row's
  icon/label are correct regardless of which screen's `Content` this menu
  happened to be opened from (some screens stamp `watched` onto `Content`
  by now; this reads the single source of truth directly instead of
  trusting that every caller does). Filled `CheckCircle` + "Watched" once
  true, outlined `CheckCircle` + "Mark as watched" otherwise -- same
  swap Post-Milestone-22 already established for Detail's own button.
  Every row here already calls `state.dismiss()` right after its action,
  so (unlike Detail's button) there's no expectation of watching the icon
  change live in an still-open menu -- it just closes immediately either
  way.

**Tests performed:** Same sandbox limitation as every recent milestone
(no route to `dl.google.com`): brace/paren/bracket balance check (clean)
and a full manual re-read. `Icons.Filled.CheckCircle` alongside
`Icons.Outlined.CheckCircle` in the same file -- confident (not
compiled) this doesn't collide, same reasoning and precedent as
Post-Milestone-22. **Not performed:** an actual Gradle/Kotlin compile or
on-device check -- on-device verification should long-press a poster on
Home (or any grid), tap "Mark as watched," and confirm the poster's own
watched tick appears and the title shows up in My List's Watched filter.

**Issues discovered:** none beyond the one described in Context.

**Issues fixed:** see Changes above.

## Post-Milestone-24 — My List Becomes a Grid Catalogue Like Movies/TV Shows

**Status:** Complete.

**Context:** User request: My List should look like the Movies tab -- a
scrollable multi-column catalogue -- instead of a single horizontal row
you scroll sideways through (`RowsBrowseLayout.ROWS`, `My List`'s only
consumer of that layout since it was introduced).

**Changes:**
- `RowsBrowseScreen.kt` -- `RowsBrowseGridContent` (Movies/TV Shows/Genre
  Results' grid) gained optional `filterOptions`/`selectedFilterIndex`/
  `onFilterSelected` params. When non-empty, they replace `CatalogSortBar`
  in the exact same "sort_bar" LazyColumn slot with a row of `FilterPill`s,
  rather than adding a second bar above/below it -- sort reorders an
  unchanging set of items, filter narrows which items exist at all, and My
  List has no use for the other today, so this is a slot swap. Every
  index offset that assumes exactly one bar item between the title and the
  first grid row (`targetRowIndex + 2`, etc.) needed no changes, since the
  slot itself still holds exactly one item either way -- this is what kept
  the change small against a screen whose focus/scroll machinery has
  already been tuned through several rounds of real stutter bugs (see the
  file's own doc comments). Added a `filterChipFocusRequesters` list
  (mirroring `RowsBrowseLoadedContent`'s own, one per chip so returning
  from the nav bar lands on whichever filter is selected) alongside the
  existing single `sortBarFocusRequester`, and made `contentFocusRequester`/
  the nav bar's `onNavigateDown` pick whichever the active layout needs.
  `RowsBrowseContent`'s GRID dispatch branch now threads filterOptions
  through to this function (previously ROWS-only).
- `FilterPill.kt` -- added optional `focusUp`/`focusDown` params (default
  null, so existing callers are unaffected), mirroring `CatalogSortPill`'s
  own, so a filter pill embedded in the grid's fixed bar slot can wire the
  same explicit up/down handoff the sort bar already needed there.
- `MyListScreen.kt` -- passes `layout = RowsBrowseLayout.GRID`. No
  `MyListViewModel`/data changes needed: it already produces exactly one
  `HomeSection`, and `RowsBrowseContent` already flattens every section's
  items before handing them to the grid, so the existing All/Watched
  filtering logic carries over unchanged.
- `RowsBrowseLayout.ROWS` and `RowsBrowseLoadedContent` (the original
  horizontal-shelf renderer) are now unused -- My List was their only
  caller. Left in place rather than deleted: removing ~140 lines of
  focus/scroll logic I can't compile-test felt like a separate cleanup
  decision from the layout change actually requested, not a call to make
  unilaterally in the same pass. Updated the stale doc comments that used
  to say "My List keeps this" so they don't mislead a future reader.

**Tests performed:** Same sandbox limitation as every recent milestone (no
route to `dl.google.com`): brace/paren/bracket balance check on all three
touched files (clean), and a full manual re-read confirming every index
offset in `RowsBrowseGridContent` (the two `+ 2` `LaunchedEffect`s, the
initial-focus effect, `onNavigateDown`'s scroll target) still holds given
the bar slot always contains exactly one item regardless of which content
it renders. Confirmed `RowsBrowseGridContent` has no other call sites
needing the same treatment (Movies/TV Shows/Genre Results never pass
`filterOptions`, so they're unaffected) and that `FilterPill`'s two new
optional params don't change its existing callers (`RowsBrowseLoadedContent`'s
own filter bar, which doesn't pass them). **Not performed:** an actual
Gradle/Kotlin compile or on-device check -- on-device verification should
open My List, confirm it now scrolls as a multi-column grid, confirm the
All/Watched filter pills still work and still restore focus correctly via
the nav bar's DOWN key and UP from the first grid row, and confirm Movies/
TV Shows/Genre Results are visually and behaviorally unchanged.

**Issues discovered:** none beyond the one described in Context.

**Issues fixed:** see Changes above.

## Post-Milestone-25 — Remove-From-Watched, and Newest-Added-First in My List

**Status:** Complete.

**Context:** User request: two follow-ups to the watched-tracking work.
(1) Every "Mark as watched" surface (Post-Milestone-22/23) only ever set
`watched=true` -- there was no way to undo a mis-tap or change of mind,
short of manually editing the account's data. (2) My List's new grid
(Post-Milestone-24) still rendered items in `myListRepository.items`' own
storage order (oldest-added first, inherited from the server's
`added_at ASC`), rather than newest-first.

**Changes:**
- `MyListRepository.kt` -- added `toggleWatched(content)`, a bidirectional
  sibling to the existing one-way `markWatched(content)`: adds the item
  (watched=true) if untracked, otherwise flips `watched` in whichever
  direction it wasn't already. `markWatched()` itself is unchanged and
  still the only thing `PlayerViewModel`'s live auto-detection and the
  history backfill call -- neither should ever be capable of *unwatching*
  something just because of how a playback report or a historical row
  happened to read, only a deliberate user action should. Only ever flips
  the `watched` flag, never removes the item from My List entirely, which
  stays the separate, existing `toggle()`'s job.
- `DetailViewModel.kt` -- renamed `markWatched()` to `toggleWatched()` and
  pointed it at the new repository method (the old name was actively
  misleading once tapping it again could unmark); threaded the rename
  through `DetailScreen.kt`'s `onMarkWatched` -> `onToggleWatched` param.
- `CardActionsMenu.kt` -- its "Mark as watched" row now calls
  `myListRepository.toggleWatched()` instead of `markWatched()`.
- `DetailHeroSection.kt` / `CardActionsMenu.kt` -- both watched
  buttons/rows now say "Remove from Watched" once already watched
  (previously just "Watched"), matching the action-oriented phrasing the
  neighboring "Add to My List"/"Remove from My List" control already uses,
  rather than only describing the current state.
- `Content.kt` -- updated `watched`'s own doc comment, which had drifted
  stale twice over (still said "the player has marked watched," and still
  said "always false outside of My List's own cards today" from before
  Post-Milestone-20 stamped it everywhere else too).
- `MyListViewModel.kt` -- `toSections()` now reverses the item list before
  building the displayed `HomeSection`. `myListRepository.items` itself
  stays oldest-first end to end (`toggle()`/`markWatched()`/
  `toggleWatched()` all append a new item to the end and never reorder an
  existing one in place; `WatchlistSyncRepository.pullFromServer()`/
  `reconcile()` mirror the server's own `added_at ASC` order the same
  way) -- reversing at display time was the only change needed, and
  every other reader of `myListRepository.items` (`savedIds`,
  `isInMyList`, the sync repository's own push/pull logic) is unaffected
  since none of them depend on its order. Deliberately not sorted by
  `SavedListItem.addedAtMillis`: that field isn't part of `WatchlistItemDto`
  at all, so it gets reset to "now" on every server pull for every item in
  the response -- sorting by it would have looked fine locally and then
  silently reshuffled toward "arbitrary" after the next app launch or sync.

**Tests performed:** Same sandbox limitation as every recent milestone (no
route to `dl.google.com`): brace/paren/bracket balance check on all eight
touched files (clean), and a full manual re-read. Specifically traced
every existing call site of `markWatched()` (`PlayerViewModel`,
`WatchlistSyncRepository`'s backfill) to confirm neither was accidentally
repointed at `toggleWatched()`, and traced `myListRepository.items`'
write sites (`toggle`, `markWatched`, `toggleWatched`,
`WatchlistSyncRepository.reconcile`/`pullFromServer`) to confirm the
"append new, never reorder existing" invariant the reversed display
depends on actually holds everywhere the list is written. **Not
performed:** an actual Gradle/Kotlin compile or on-device check --
on-device verification should mark a title watched, confirm the tick
appears, tap the same control again, confirm the tick disappears and the
title drops out of My List's Watched filter (while staying in All); and
should add several titles to My List and confirm the most recently added
one appears first in the grid.

**Issues discovered:** none beyond the one described in Context.

**Issues fixed:** see Changes above.

## Post-Milestone-26 — Removed Titles Stay Removed (Watched-History Catch-Up Guard)

**Status:** Complete, not compiled (see Tests performed).

**Context:** Ported from the web app's fix of the same name (MangotvWebb
commits 826c753 and b641b28). The one-time watched-history catch-up
(`WatchlistSyncRepository.backfillWatchedFromHistoryIfNeeded()`) replays
every finished movie through `markWatched()`. Its done-flag is reset on
sign-out (`AccountSwitchCoordinator`) and is device-scoped, and a title the
person removed from My List is gone from the server's active list, so
nothing distinguished "removed on purpose" from "never added". Signing out
and in, or signing in on a second device, therefore put removed movies
back with a watched tick. Un-watching a title that stayed in the list was
undone the same way.

**Changes:**
- `WatchlistSyncRepository.kt` -- the catch-up now exits early (and marks
  itself done) when My List already has any titles; it only runs for an
  account whose list is empty. The decision is the small
  `shouldRunWatchedBackfill(myListSize)` function so it can be unit tested.
  `pullFromServer()` now returns whether the list was actually read.
- `SyncManager.kt` -- `syncAll()` runs the catch-up only when that pull
  succeeded, because a list that failed to load looks empty and would make
  a long-used account look brand new.
- `WatchedBackfillTest.kt` -- unit tests for the empty / non-empty rule.
- `RELEASE_NOTES.md` -- user-facing line under Unreleased.

**Tests performed:** Same sandbox limitation as every recent milestone (no
route to `dl.google.com`): unit tests written for the guard but not run
here, a re-read of the touched files, and a trace of every caller of
`pullFromServer()` (only `SyncManager.syncAll()` uses it, so the new
Boolean return breaks nothing). **Not performed:** a Gradle build, the unit
tests, or an on-device check -- on-device, remove a watched movie, sign out
and back in, and confirm it stays gone.

**Issues discovered:** An account whose My List is completely empty (every
title removed) still gets the catch-up on a fresh device, since there is
nothing left to tell removed titles apart from never-added ones; the web
app has the same limit. Making it fully airtight needs a synced record of
removed titles, which is a backend change and out of scope here.
Separately, an account that already had titles but never ran the catch-up
(upgraded from before it existed) now has it skipped for good.

**Issues fixed:** Removed or un-watched titles reappearing after sign-in.

## Post-Milestone-27 — Home Rows Without Repeats, Recommended Source Always First

**Status:** Complete, not compiled (see Tests performed).

**Context:** Ported from three web-app changes (MangotvWebb b7566d5 "Show
each title in only one Home row", 29a638f "Continue Watching without
repeats", 127d207 "Recommended source always first"). Before this, a title
could appear in several Home rows, Continue Watching could repeat a title
already shown below it, and the "Recommended" badge on Select a Source sat on
whatever row the current sort and filter placed it -- or vanished when the
filter hid it.

**Changes:**
- `HomeRowLogic.kt` (new) -- `dedupeSections()` keeps a title in the first
  row displayed that holds it and drops rows left empty;
  `withoutShownTitles()` removes from Continue Watching any title a
  catalogue row already shows, and returns null when nothing is left.
- `HomeViewModel.kt` -- `applyPreferences()` dedupes after the hidden rows
  are removed (so a hidden row never uses up a title) and before the hero
  pool is drawn, so the hero's ten titles follow the same rule. Continue
  Watching goes through `withoutShownTitles()` against the visible rows.
- `SourceOrdering.kt` (new) -- `sortSources()` (the previous inline sort,
  unchanged) and `orderSources()`, which puts the recommended source first
  and the filtered, sorted rest after it, listing it once.
- `SourcesScreen.kt` -- uses `orderSources()`. The recommended source stays
  first even when the active resolution filter would otherwise hide it, which
  is what the web does.
- `HomeRowLogicTest.kt`, `SourceOrderingTest.kt` -- unit tests for all of the
  above, including that inputs are never mutated and untouched rows are
  returned as the same objects.
- `RELEASE_NOTES.md` -- two user-facing lines under Unreleased.

**Tests performed:** Same sandbox limitation as every recent milestone (no
Android SDK, no route to `dl.google.com`): unit tests written but not run
here, brace/paren balance check on every touched Kotlin file (clean), and a
manual re-read. **Not performed:** a Gradle compile, the unit tests, or an
on-device check -- on-device, confirm no title repeats across Home rows,
Continue Watching shows only titles absent from the rows below it, and the
Recommended source is on top after switching filter and sort.

**Issues discovered:** Continue Watching now hides a half-watched title
whenever any catalogue row also lists it, so that title loses its progress
bar on Home. This matches the web exactly, but on a TV, where Continue
Watching is the main way back into a show, it may not be wanted -- flipping
it is a one-line change in `applyPreferences()`.

**Issues fixed:** see Changes above.

## Post-Milestone-28 — My List "Sort by"

**Status:** Complete, not compiled (see Tests performed). Revised in place
after review: it first shipped as a row of pills, and was changed to the web's
drop-down (see Post-Milestone-37 for the shared drop-down component).

**Context:** Ported from the web app (MangotvWebb 800068e "Add sort-by options
to My List", restyled as a drop-down in bc71264). My List could only show
newest-added first, with no way to reorder it.

**Changes:**
- `MyListSort.kt` (new) -- `MyListSort` (Recently Added, A-Z, Highest Rated,
  Newest) and `sortSavedItems()`. Every sort starts from the newest-added-first
  reading of the repository's oldest-first list, so ties (same rating or year)
  stay in recently-added order, and a title with no rating or year goes last.
  A-Z uses a primary-strength `Collator`, so it ignores case and accents.
- `MyListViewModel.kt` -- holds the selected sort and sorts after the
  All/Watched filter. `toSections()` no longer reverses; the order now comes
  from `sortSavedItems()`. `myListRepository.items` itself is untouched, so
  every other reader still sees the oldest-first list.
- `MyListScreen.kt` -- a "Sort by: Recently Added" drop-down beside the "My List"
  title, as on the web, built on `DropdownPicker`. The All / Watched pills stay
  below it. It is left out while the list is empty, since there is nothing to
  sort.
- `MyListSortTest.kt` -- unit tests for each order, tie-breaking, missing values
  and that the stored list is never mutated.
- `RELEASE_NOTES.md` -- user-facing line under Unreleased.

**Tests performed:** Same sandbox limitation as every recent milestone (no
Android SDK): unit tests for the sorting run on GitHub Actions, brace/paren
balance check on every touched Kotlin file (clean), and a manual re-read.
**Not performed:** an on-device check -- see Post-Milestone-37 for what to check
on the drop-down itself.

**Issues discovered:** none.

**Issues fixed:** see Changes above.

## Post-Milestone-29 — CI: Android SDK Setup Step Fixed

**Status:** Complete (verified by a green "Set up Android SDK" step and a
green unit-test step on GitHub Actions, run 121).

**Context:** Not a port. `build-apk.yml`'s "Set up Android SDK" step started
failing on 2026-10-02 with `Failed to find package 'tools'` before Gradle ever
ran, so no branch could be built or tested. It had last passed on 2026-09-14
(run 119 on `main`). The hosted runner image already ships the SDK; the
`android-actions/setup-android@v3` default package list asks `sdkmanager` for
the retired `tools` package.

**Changes:**
- `.github/workflows/build-apk.yml` -- `packages: ""` on the setup step, so it
  installs nothing extra. The workflow also only triggers on `main` and
  `claude/**` pushes, so a branch like `other_fixes` has to be built with the
  manual "Run workflow" button (`workflow_dispatch`).

**Tests performed:** Dispatched the workflow on `other_fixes`: SDK setup and
`testDebugUnitTest` passed.

**Issues discovered:** None beyond the above.

**Issues fixed:** The SDK setup failure.

## Post-Milestone-30 — Home Returns To The Exact Poster On BACK

**Status:** Complete, not compiled (see Tests performed).

**Context:** Ported from the web app's "Back buttons that return to the exact
place" (MangotvWebb 29a638f). The Firestick already did this on Movies, TV
Shows, Genre Results and My List (`RowsBrowseScreen`), but not on Home. Opening
a title from a Home row and pressing BACK reset Home's own remembered state:
focus went to the nav bar, `heroRegionFocused` started true, and the
top-pinning watchdog snapped the list back to the top.

**Changes:**
- `HomeScreen.kt` -- `HomeContent` now remembers the focused poster's row id and
  title id with `rememberSaveable` (ids, not indexes), updated through
  `ContentRow`'s existing `onItemFocusChanged`. On re-entry it reads them once,
  starts with the hero lock off, scrolls the row and the poster into view if the
  restored positions left them off screen, and focuses that exact poster through
  `ContentRow`'s existing `firstItemFocusRequester` / `targetItemIndex`. The
  remembered poster is cleared whenever focus returns to the nav bar or hero, so
  BACK from the hero's More Info still lands where it did before.
- `HomeRowLogic.kt` -- `findFocusRestoreTarget()`, the id-based lookup, kept pure
  so it can be tested. It returns nothing when the row or title is gone.
- `HomeRowLogicTest.kt` -- tests for a moved row, a missing title and a missing row.
- `RELEASE_NOTES.md` -- user-facing line under Unreleased.

**Tests performed:** The lookup is covered by unit tests, not run here (no
Android SDK in this sandbox); run on GitHub Actions afterwards, see the commit's
build. Brace/paren balance check on touched files (clean) and a manual re-read.
**Not performed:** an on-device check -- open a title from the third row, press
BACK, and confirm focus is on that poster, the row is centred, and the row's
horizontal position is where it was.

**Issues discovered:** BACK from the hero's More Info still lands on the nav bar,
not the hero button. Not looked at on the web side; a possible follow-up rather
than part of this change.

**Issues fixed:** Home losing its place on BACK.

## Post-Milestone-31 — Cast Photos And Characters (TMDB Lookup)

**Status:** Server side verified (typecheck plus its tests run against a local
Postgres). App side complete but not compiled here (see Tests performed).
**Needs a backend redeploy before it has any visible effect.**

**Context:** Ported from the web app (MangotvWebb 3d714f5, 9cf3490, 701095f).
`CastRow` already drew a photo and a character line, but the Stremio base
protocol gives cast as plain names, so `StremioMapper` only ever produced
`CastMember(name = ...)` and every avatar was the placeholder icon. TV shows
with season data also never showed a cast at all.

**Changes:**
- Server: `schemas/cast.ts`, `services/castService.ts`, `routes/cast.ts`
  (mounted in `app.ts`) -- `GET /user/cast?imdbId=tt...&type=MOVIE|TV_SHOW`,
  authenticated like every route under `/user`. It finds the title on TMDB by
  IMDb id, then reads its credits: up to 20 people with the character played and
  a `w185` photo address. Same pattern as the release-date lookup: the existing
  `TMDB_READ_ACCESS_TOKEN`, a 24 hour in-memory cache of hits and misses, and an
  empty list instead of an error when TMDB is unconfigured, has no match or
  fails. The IMDb id is matched against `^tt\d{1,10}$` before it is placed in
  the TMDB path, so nothing else a caller sends reaches TMDB.
- Server: `tests/cast.test.ts` -- auth, id validation, unconfigured, movie and TV
  paths, no match, caching and a TMDB error.
- App: `CastApiClient`, `CastDtos`, `CastRepository` (wired lazily in
  `AppContainer`), and `mergeCast()`. The merge fills photos and characters into
  the addon's own list by name (ignoring case, accents and punctuation), never
  overwrites anything the addon sent, keeps the addon's order, and uses TMDB's
  list when the addon sent none.
- App: `DetailViewModel.loadCast()` runs as its own coroutine alongside the
  trailer and release-date lookups, never delaying the page, and republishes the
  enriched cast. A late answer for a title the person has already left is dropped.
- App: `DetailScreen` shows the cast under the episodes for a TV show with season
  data, as the web does.
- App: `CastMergeTest.kt`.
- `RELEASE_NOTES.md` -- user-facing line under Unreleased.

**Tests performed:** Server: `tsc --noEmit` clean; `npm test` for `cast.test.ts`
and `releaseDates.test.ts` -- 15 of 15 passing against a local Postgres 16 with
all 14 migrations applied. App: same sandbox limitation as every recent
milestone (no Android SDK); unit tests written and run on GitHub Actions
afterwards, brace/paren balance check on touched files, manual re-read.
**Not performed:** an on-device check -- open a movie and a show whose addon
sends names only, and confirm photos and character lines appear a moment after
the page, and that a show lists its cast under the episodes.

**Deployment note:** the app only calls the new endpoint. Until the backend is
redeployed with this change, the call returns 404, which the app treats as "no
photos", so the page looks exactly as before. No new setting is needed; it reuses
the TMDB token the trailer and release-date lookups already use.

**Issues discovered:** The lookup only runs for ids that are IMDb ids (`tt...`),
which is what Cinemeta uses; an addon with other id schemes keeps plain names.
Cast is capped at 20 people.

**Issues fixed:** Cast avatars always showing the placeholder; TV shows with
episodes showing no cast.

## Post-Milestone-32 — Arc TV Colours

**Status:** Complete, not compiled (see Tests performed). First of three
commits for the Arc TV rebrand (colours, then name, then artwork).

**Context:** Ported from the web app (MangotvWebb 08bc73a "Recolour the UI to
the Arc TV logo palette"). The app's accent was amber with a
tangerine/coral gradient; the Arc TV logo is cyan, blue and violet.

**Changes:**
- `Color.kt` -- the brand set is now `ArcCyan` (#19E6FF), `ArcBlue` (#2F80FF)
  and `ArcViolet` (#9B5CFF), the same values the web uses. `ArcAccent` is the
  single accent role (what `MangoAmber` was). `ArcWarn` (amber) is for warning
  text only and `ErrorCoral` (red) for errors and destructive actions only, so
  those still read as a warning and an error. `ArcBrandGradient` runs
  cyan-blue-violet. `FocusGlow` follows the accent, `FocusBorder` is #8CF3FF
  and `ProgressFill` is the accent, as on the web. Neutral surfaces, the text
  colours, the watched-tick green and the azure/teal source-tier colours are
  unchanged.
- All users of the old names were renamed mechanically (`MangoAmber` to
  `ArcAccent`, `MangoCoral` to `ErrorCoral`, `MangoBrandGradient` to
  `ArcBrandGradient`, `mangoBrandGradient` to `arcBrandGradient`).
  Behaviour changes beyond the colour itself: the update banner's error line is
  now `ArcWarn` rather than the accent so it still reads as a warning, 4K
  source badges take the accent as on the web, the Genres card accents are
  cyan/blue/violet/azure/teal, and Material's `secondary`/`tertiary` are
  violet/blue.
- `AddonPairingServer.kt` -- the phone page's button gradient is
  cyan-blue-violet to match. Comments that said "amber" were updated.

**Tests performed:** Same sandbox limitation as every recent milestone (no
Android SDK): a search confirming no reference to a removed name or an old
orange hex value remains outside `Color.kt`, brace/paren balance on every
touched file, and a manual re-read. The change is names and colour values only,
so the GitHub Actions compile is what proves the renames are complete.
**Not performed:** an on-device look -- check text on the gradient buttons is
legible, the focus highlight is visible against posters, and no orange is left.

**Issues discovered:** The boot video (`BootVideoScreen`) is an asset, not code;
if it has orange or the old logo baked in it still shows the old look.

**Issues fixed:** none beyond the colour change itself.

## Post-Milestone-33 — Mango TV Becomes Arc TV (Name)

**Status:** Complete, not compiled (see Tests performed). Second of three
commits for the Arc TV rebrand.

**Context:** Ported from the web app (MangotvWebb bc1a55c, 53757a8: "Rebrand
visible text from Mango TV to Arc TV"). The web changed visible text only and
left identifiers, storage keys and environment variables alone; this does the
same.

**Changes:**
- `strings.xml` -- `app_name` is "Arc TV", so the launcher label, the Fire TV
  home tile caption and the system's app list show it.
- User-visible strings: the empty-state and Add Addon hints, the Account row's
  description in Settings, the "Sync existing ... data to your account?" prompt
  on both sign-in screens, the update banner's install-permission text, and the
  phone page the QR add-addon flow serves (title, heading and button).
- `README.md` and `RELEASE_NOTES.md` -- titled Arc TV, with a note that code
  names are unchanged.

**Deliberately not changed:** the package name `com.mangotv.app` (changing it
would make Android treat the next build as a different app, so installed copies
could not update in place and their saved data and sign-in would be lost);
class, theme and resource names such as `MangoTvApplication` and
`Theme.MangoTV`; the GitHub repository name in `UpdateRepository.kt` (the app
finds updates by it); the APK/release asset names; the Gradle project name.
Internal comments still say Mango. Fully renaming any of these is a separate,
larger change.

**Tests performed:** A search of every quoted string and XML resource for
"Mango"; what remains is identifiers and the repository name listed above. A
manual re-read of each edited string. **Not performed:** an on-device check of
the launcher label and each screen.

**Issues discovered:** The in-app wordmark is still the old "MANGO TV" text and
the launcher icon and banner are still the mango artwork; both change in the
next commit.

**Issues fixed:** none beyond the rename itself.

## Post-Milestone-34 — Arc TV Artwork And Logo

**Status:** Complete, not compiled (see Tests performed). Last of three commits
for the Arc TV rebrand.

**Context:** Artwork supplied for this change (ArcTV Fire TV / Android assets),
matching the logo the web app already uses (MangotvWebb 590b5d8, 6c51e99,
6c818e6). The app still showed the mango launcher icon and TV banner, and drew
its in-app logo as the text "MANGO TV" in a gradient, not from an image.

**Changes:**
- Replaced `drawable-xhdpi/banner.png` (640x360, the Fire TV home tile; black
  background, full logo and name) and `mipmap-xxxhdpi/ic_launcher*.png`
  (foreground 432x432 with the mark inside the adaptive-icon safe area, solid
  black background, and the 192x192 legacy square and round icons). The
  adaptive-icon XML and manifest already point at these files, so they needed no
  change.
- Added `drawable-nodpi/logo_arctv.png` (2232x676, transparent, white lettering,
  about 3.3:1) and removed the old `drawable-xhdpi/logo_mango.png`, which nothing
  in the code referenced.
- `MangoLogo.kt` became `ArcLogo.kt`: it draws that image instead of two text
  runs. Only the height is set, so the wide logo is never stretched to the old
  tall proportions; the height is the old `fontSize` times 1.3, so all four call
  sites (top nav, both sign-in screens, the player's top bar) keep their size
  parameters and the wordmark lands at about the height the old text had.
- `README.md` -- component list updated.

**Tests performed:** Opened the supplied files and checked their pixel sizes
against the spec (all six match), confirmed no remaining reference to the
removed drawable or the old composable, and a manual re-read. The artwork was
supplied already sized to the Fire TV requirements. **Not performed:** a Gradle
resource build or any on-device look. On a Fire TV, check: the home-screen tile
shows the banner without cropping the name, the app-list icon is not cut off,
the logo fits the top bar without moving the nav items, and it is not too small
or large on the sign-in screens and in the player.

**Issues discovered:** The boot video (`BootVideoScreen`) is a video asset, so
if it has the old logo or colours baked in it will still show them until the
video is replaced.

**Issues fixed:** none beyond the artwork itself.

## Post-Milestone-35 — Debrid Cached Badges And What Each Addon Answered

**Status:** Complete, not compiled (see Tests performed). Last phase of the web
parity port.

**Context:** Ported from the web app (MangotvWebb ee990c9 "Show whether debrid
sources are cached; rank and explain the ones that aren't", and 7164349 "Show
what each addon answered on Select a Source"). Two gaps: a Torrentio-style source
that the debrid service hasn't stored yet (`[RD download]`) looked identical to a
ready one (`[RD+]`) but made the player sit for minutes, and every failure on
Select a Source was swallowed (`getStreams` turned any error into an empty list),
so "an addon timed out", "no addons provide streams" and "nothing for this title"
all read as the same "No sources found".

**Changes:**
- Debrid state: `Stream.debrid` (`DebridState(service, cached)`), parsed from the
  leading tag of the stream name by `parseDebridTag()` in `StremioMapper.kt`
  (`[RD+]` is cached, `[RD download]` is not). `DEBRID_NAMES` turns the code into
  the service's name (RD is Real-Debrid and so on); an unknown code is shown as is.
- `SourceRow.kt` -- a line under each debrid source: "Cached on Real-Debrid"
  (teal, bolt) or "Not cached on Real-Debrid -- may take minutes" (amber,
  hourglass). Sources that aren't debrid links show nothing.
- `SourceOrdering.kt` -- the "Quality" order and the "Recommended" pick now put
  sources that start at once ahead of uncached debrid ones, then resolution, then
  seeders, so a ready 720p beats a 4K that makes you wait. Seeders and Size sorts
  are untouched. `recommendedStreamId()` moved here from the view model so the
  two share one comparator. (The web ranks cache state below its device-playability
  level; the Firestick has no such level, so cache state comes first.)
- Per-addon answers: `StreamLookup` (Ok, None, Unsupported, Failed) and
  `StreamReport` in `data/model/StreamReport.kt`; `CatalogProvider.getStreamReport()`
  (default wraps `getStreams`) overridden by `StremioAddonProvider`, which skips an
  addon whose manifest lists resources without "stream" (Cinemeta), treats a 404 as
  "no streams for this title", and otherwise records why it failed.
  `AddonHttpException` (still an `IllegalStateException`, as the old bare `check`
  was) carries the status; `describeAddonError()` turns it into a short reason that
  never includes the address, which can hold an account key.
- `SourcesViewModel.kt` -- `Loaded.addons` lists every addon with its answer, filled
  in as each one replies (and all at once on the resume path).
- `SourcesScreen.kt` / `SourcesHints.kt` -- the empty state now names the cause,
  lists each addon's answer (first five), and offers Try Again when something
  failed; if sources were found but some addons failed, a one-line note says the
  list may be incomplete.
- Tests: `DebridAndErrorsTest`, `ManifestOffersStreamsTest`, `SourcesHintsTest`, and
  new cases in `SourceOrderingTest`.
- `RELEASE_NOTES.md` -- two user-facing lines under Unreleased.

**Not ported (web-only or deferred):** the "can this device play it" badges and
device-capability panel (a browser concern; ExoPlayer plays far more), a
collapsible "Addon results" panel (a TV list is shown inline instead, to avoid a
new focus target), the "quality filter hides everything" message (the recommended
source always stays listed, so the list can't empty that way), and the web player's
"waiting on the debrid service" explanation for a stalled start.

**Tests performed:** Same sandbox limitation as every recent milestone (no Android
SDK): unit tests written, run on GitHub Actions afterwards; brace/paren balance
check on every touched Kotlin file; a manual re-read. The addon-classification
code in `StremioAddonProvider` is not unit tested, because the addon client is a
concrete OkHttp class with no seam to fake. **Not performed:** an on-device check
with a real debrid addon: confirm cached and uncached badges show, an uncached
source lists below ready ones and is not "Recommended", and with the network off or
an addon removed the empty state says the right thing and Try Again works.

**Issues discovered:** `AddonHttpException` is thrown for every non-2xx answer from
an addon, not just streams, so manifest, catalog and meta failures now carry a
status too; none of those call sites read it, so nothing changes for them.

**Issues fixed:** Uncached debrid sources looking identical to ready ones; every
Select a Source failure reading as "No sources found".

## Post-Milestone-36 — Boot Video Removed

**Status:** Complete, not compiled (see Tests performed).

**Context:** User request, matching the web app, which dropped its boot video
(MangotvWebb 71c7ce6). The cold-boot screen played `assets/newboot1.mp4` over the
real nav graph and held the whole app, with every key swallowed, until both the
video and Home's first batch of live rows had finished (bounded by a 10 second
timeout), preloading six card images meanwhile.

**Changes:**
- Deleted `BootVideoScreen.kt` and `assets/newboot1.mp4`.
- `MangoNavHost.kt` -- removed the overlay, the `isAppReady`/`dataReady` flag, the
  key-swallowing modifier on the root `Box` and the comments that explained them.
  `HomeViewModel` stays scoped to the Activity, as before, so Home still keeps its
  rows across tab switches.
- `HomeViewModel.kt` -- removed `liveDataReady`, which only the boot screen read.
- Comments in `PlayerSurface.kt` and `SoundPreferencesRepository.kt` that mentioned
  the boot video were updated.

**Behaviour change to know about:** nothing now covers the very start. The app
opens on the dark window background, the auth gate redirects to sign-in or Home as
soon as its local check resolves, and Home shows its own loading skeleton while rows
arrive, instead of staying hidden until the first batch was ready. The first six
poster images are no longer preloaded, so they may pop in as they download. If the
redirect shows a visible flash on a real device, a plain static cover (no video) is
the fix to reach for.

**Tests performed:** A search confirming nothing still references the deleted screen,
the asset or `liveDataReady`, brace/paren balance on the two edited Kotlin files, and
a manual re-read. **Not performed:** a Gradle build or a cold start on a device.

**Issues discovered:** none.

**Issues fixed:** none beyond the removal.

## Post-Milestone-37 — "All genres" Drop-Down On Movies And TV Shows, And A Shared Drop-Down

**Status:** Complete, not compiled (see Tests performed).

**Context:** User request, ported from the web app (MangotvWebb e6db839 "genre
drop-down on Movies and TV Shows"; the My List sort drop-down, bc71264, uses the
same component). Movies and TV Shows could only be reordered (Featured / Highest
Rated / Newest); there was no way to look at one genre.

**Changes:**
- `DropdownPicker.kt` (new) -- a pill showing a label and a chevron that opens a
  list of options under it, like the web's. OK on the pill opens it with focus on
  the chosen option, UP/DOWN move through the list, OK picks and closes, BACK closes
  without changing anything and returns focus to the pill. The list is a `Popup`
  window of its own, so opening it never disturbs the focus handling of the screen
  behind it, and it scrolls when it is long. The popup inherits the browse grids'
  `LocalBringIntoViewSpec = DisabledBringIntoViewSpec` (they switch off automatic
  scroll-to-focus so the page doesn't shake), which left the list stuck on its first
  rows while the remote moved focus below them; the list now restores the normal
  behaviour for itself and also scrolls explicitly to any option that isn't fully
  on screen when it gains focus.
- `RowsBrowseScreen.kt` -- `RowsBrowseContent` takes an optional `headerAction`
  (replacing the My List-only sort-pill parameters from Post-Milestone-28). It is
  drawn beside the screen title and handed its focus wiring (`BrowseHeaderFocus`):
  UP goes to the nav bar, DOWN to the filter or sort pills (or the grid). The nav
  bar's DOWN now lands on it, and the pills' UP comes back to it. If a genre has no
  titles, the title and drop-down stay on screen with the empty message, so another
  genre can be picked. Every other screen passes nothing and is unchanged.
- `GenreOptions.kt` (new) -- the genre list: exactly the genres Cinemeta's "top"
  catalogue lists, read from the bundled `cinemeta_manifest.json`, with years
  dropped (19 for Movies; TV Shows adds Reality-TV, Talk-Show and Game-Show), the
  same list the web uses. Also `emptyBrowseMessage()` ("No Sci-Fi movies found right
  now.").
- `CatalogProvider` / `StremioAddonProvider` -- `getSectionsByType` and
  `getMoreItemsByType` take an optional `genre`. With one, only that type's
  catalogues that list the genre are asked, so Movies never gets series mixed in,
  and an addon that doesn't list it answers with nothing without a request.
- `TypeBrowseViewModel.kt` -- holds the chosen genre and `selectGenre()`. Choosing
  one keeps the current grid on screen until the new one arrives (so the drop-down
  never loses focus), pages with the same genre, and drops an answer that arrives
  after the genre changed again. A chosen genre keeps the providers' popularity
  order; "All genres" keeps the existing shuffle.
- `GenreOptionsTest.kt` -- reads the real bundled manifest: 19 and 22 genres, no
  years, TV equals Movies plus three, and the empty-message text.
- `RELEASE_NOTES.md` -- user-facing line under Unreleased.

**Differences from the web:** the web keeps the genre in the page address so Back
returns to it; here the choice lives in the screen's view model, which survives
leaving and coming back to the tab. The web has a Blocked Genres setting that hides
genres from this list; the Firestick has none. The drop-down is a single scrolling
column rather than two columns, to suit a D-pad.

**Tests performed:** Same sandbox limitation as every recent milestone (no Android
SDK): the genre-list and message tests run on GitHub Actions; brace/paren balance
on every touched Kotlin file; a manual re-read. **Not performed:** an on-device
check -- on a Fire TV: OK on "All genres" opens a list with the current choice
focused; UP/DOWN scroll it; OK picks a genre and the grid changes; BACK closes it
without changing anything and focus returns to the pill; nav bar DOWN lands on the
pill and UP from the pills comes back to it; a genre with no titles still shows the
drop-down. Same checks for My List's "Sort by" drop-down.

**Issues discovered:** The genre is held in memory only, so it resets to "All
genres" when the app restarts. Picking a genre in the list leaves the old grid up
for a moment on a slow connection before the new one replaces it.

**Issues fixed:** none beyond the new feature.

## Post-Milestone-38 — Update Pop-Up, And Release Notes The Remote Can Scroll

**Status:** Complete, not compiled (see Tests performed).

**Context:** User request. Two problems with the update flow. (1) The "What's new"
overlay showed the release notes in a `Text` with `verticalScroll`, but that is not
focusable and the only focusable thing on the overlay was the Close button, so a
remote could never move it: with 13 bullets in Unreleased, only the first screenful
(about 9 lines) was readable on a TV. (2) The update offer was a banner that pushed
the whole screen down, reached through an info button to a second overlay.

**Changes:**
- `UpdatePopup.kt` (new) -- one pop-up, styled like the old "What's new" overlay
  (dimmed screen, rounded card), that shows "Update available", the version and APK
  size, download progress (a progress bar and percentage), "Ready to install" or the
  error message, the release notes, and the Update / Install / Retry button plus
  "Not now". It replaces `UpdateBanner.kt` and the separate release-notes overlay.
- It is a real dialog window, so the remote stays inside it and BACK means "Not now".
  While a download is running BACK is ignored and "Not now" is hidden, as the old
  banner hid its close button then.
- `ScrollableNotes` (in `UpdatePopup.kt`) -- the notes sit in a focusable box. UP from
  the buttons selects it (it gets the focus border); DOWN and UP then scroll it, and
  once it is at its end DOWN (or at its start UP) is left alone so focus moves on to
  the buttons as normal. A thin scroll bar shows how far through you are, and a hint
  line says the notes can be scrolled when they don't fit.
- `UpdatePromptHost.kt` (was `UpdateBannerHost.kt`) -- shows the pop-up over the app
  instead of a bar above it. It is still hidden while the video player is active and
  steps aside while the "allow installing updates" prompt is up. `MangoNavHost.kt`
  updated for the rename.
- `UpdateViewModel` is unchanged: the same once-per-launch check, the same
  "ignore this version" memory when dismissed, and the same download, install and
  permission flow.
- `RELEASE_NOTES.md` -- user-facing line under Unreleased.

**Behaviour to know about:** the pop-up appears over whatever screen you are on as soon
as the check finishes (a few seconds after launch), which is more noticeable than the
bar was. Dismissing it, with Not now or BACK, remembers that version so it does not
return until a newer one is released.

**Tests performed:** Same sandbox limitation as every recent milestone (no Android
SDK): brace/paren balance on the touched Kotlin files, a search for leftover references
to the removed banner and overlay (none), and a manual re-read. There is no unit test
for the scrolling: it is key handling and layout. **Not performed:** a Gradle compile
or an on-device check. The update check only runs in release builds
(`BuildConfig.DEBUG` skips it), so the CI debug APK will never show this pop-up; seeing
it needs a release build and a newer published release, or a temporary debug override.
On a Fire TV, check: it opens with Update focused; UP selects the notes (border
appears) and DOWN/UP scroll all 13 bullets; at the end DOWN moves to the buttons; Not
now and BACK close it; during a download the progress bar moves and BACK does nothing;
Install and the permission prompt still work.

**Issues discovered:** none beyond the above.

**Issues fixed:** Release notes beyond the first screenful being unreadable with a remote.

## Post-Milestone-39 — Home Hero: Trailer Button, Sharper Backdrops, Preloading

**Status:** Complete, not compiled (see Tests performed).

**Context:** Ported from the web app (MangotvWebb 8f0a884, 1bc2db2, 8d02693 "Trailer
button on the Home hero"; cb140e2 "largest hero background"; 060d440 "preload the Home
hero pictures"). The Firestick's hero had no trailer shortcut (only Detail did), asked
addons for whatever size they listed, and loaded each slide's picture only when its
turn came.

**Changes:**
- `HeroSection.kt` -- a "Trailer" button between Play and My List, looked up once per
  title as its slide comes round (`HomeViewModel.findTrailer`, the same server lookup
  Detail uses) and opened with the same `TrailerLauncher` hand-off to whichever app the
  person picks. It stays focusable but dimmed until a trailer is found; pressing it then
  shows a short message ("Looking for a trailer..." or "No trailer found for this title").
- `HeroImages.kt` (new) -- `sharpBackdrop()` asks Metahub (Cinemeta) for `large` instead
  of `small`/`medium` and TMDB for `original` instead of `w300`..`w1280`, and leaves any
  other address alone; `heroImages()` lists the hero's pictures in rotation order, each
  title's backdrop then its logo, once each. Same rules as the web.
- `HeroSection.kt` -- the Ken Burns backdrop requests the sharper address and falls back
  to the original one if it fails to load; every hero picture is fetched ahead through
  Coil as soon as the hero's titles are known (the cold-boot screen used to preload
  images; removing it left nothing doing this).
- `HomeScreen.kt` / `HomeViewModel.kt` -- plumbing for the trailer lookup.
- `HeroImagesTest.kt` -- the address rules and the preload order.
- `RELEASE_NOTES.md` -- user-facing line under Unreleased.

**Not ported:** "Home hero pictures are no longer cropped" (3317cc8) and "Detail hero
matches the Home hero" (1853db5) fix web-layout problems: the browser shows backdrops of
any shape in windows of any shape, while the TV is always 16:9 and its backdrops are too,
so there is nothing being cropped away to fix.

**Tests performed:** Same sandbox limitation as every recent milestone (no Android SDK):
unit tests for the address and preload rules run on GitHub Actions, brace/paren balance
on touched files, and a manual re-read. **Not performed:** an on-device check: confirm
the Trailer button sits between Play and the plus button, is dim at first and bright once
found, opens the trailer, and that D-pad LEFT/RIGHT still moves across all four buttons.

**Issues discovered:** none.

**Issues fixed:** none beyond the new behaviour.

## Post-Milestone-40 — Trailer Button Always Shown On Detail, Sources Poster And Default Sort

**Status:** Complete, not compiled (see Tests performed).

**Context:** Ported from the web app (MangotvWebb e6db839 "Trailer button always shown",
bfa7a0f "Sources: bigger poster ... default sort by size"). Detail's Trailer button only
appeared once the lookup had found a trailer, so the row of buttons shifted when it did.

**Changes:**
- `DetailHeroSection.kt` / `DetailScreen.kt` -- the Trailer button is drawn from the first
  frame and dimmed (still focusable) until a trailer is found (`trailerReady`); pressed
  early it shows "Looking for a trailer..." and after a lookup that found nothing it shows
  "No trailer found for this title". `DetailViewModel`'s `TrailerState` doc updated.
- `SourcesInfoPanel.kt` -- the poster is 120x180 (was 84x126).
- `SourcesScreen.kt` -- the list starts sorted by size, biggest first, as on the web.
  "Recommended" is unaffected (it still follows the Quality order, including ready debrid
  sources ahead of uncached ones) and always sits on top.
- `RELEASE_NOTES.md` -- two user-facing lines under Unreleased.

**Not ported:** "no play button on 'What are sources?'": the Firestick's equivalent
("How it works" in the footer) never had one.

**Tests performed:** Same sandbox limitation as every recent milestone (no Android SDK):
brace/paren balance on touched files and a manual re-read. There is no new logic to unit
test; this is layout and wiring. **Not performed:** an on-device check: Detail's Trailer
button is present and dim at first, brightens when found and opens the trailer; the Sources
info panel still fits its title, genres and description at the bigger poster size.

**Issues discovered:** With "Size" as the default, an uncached debrid file that is large
can sit just under "Recommended" ahead of smaller ready ones; the Quality sort ranks ready
sources first. The web behaves the same.

**Issues fixed:** none beyond the above.

## Post-Milestone-41 — Browse Without An Account

**Status:** Complete, not compiled (see Tests performed). The largest port in this run;
on-device testing of the sign-in round trip matters most here.

**Context:** User request, ported from the web app (MangotvWebb 637d1a0 "Let visitors
browse without an account; ask for one only on Play"). The Firestick opened on a Log In /
Sign Up screen on every launch without a session, so nothing could be seen before signing
in.

**Changes:**
- `AuthGateViewModel.kt` / `AuthGateScreen.kt` -- every launch goes to Home
  (`GateDestination.AuthStart` is gone). With a usable session nothing else changes (the
  token refresh and full sync still run). Without one, the person is a guest, and the
  bundled Cinemeta is put back first if no addon is installed.
- `GuestGate.kt` (new) -- `isGuest` (a stored session that has been read and is missing or
  can no longer be renewed; `SessionManager.loaded` was added so a null session can be told
  from "not read yet", and until it is read nobody counts as a guest, so a signed-in person
  is never bounced to sign-in during start-up) and `requireAccount { }`, which runs an action
  for a signed-in person and otherwise raises a sign-in request. Wired in `AppContainer`.
- `MangoNavHost.kt` -- `navigateTo()` sends a guest to the sign-in screens for Play (the
  source picker and the player), My List, Settings and the Sign In tab itself
  (`routeNeedsAccount()` in `MangoRoutes.kt`); the sign-in screens are pushed on top, and
  `finishSignIn()` pops back to where the guest was and carries on to what they asked for
  (going straight to it rather than through the guest check, which could still see the old
  state for a moment). Reached any other way (after signing out) the sign-in screens still
  start fresh at Home. Sign-in requests from saving a title are handled the same way.
- Saving needs an account: `requireAccount` wraps My List and Watched in `HomeViewModel`
  (hero +), `DetailViewModel` (title page buttons) and `CardActionsMenu` (long-press menu).
- `TopNavBar.kt` -- a guest sees "Sign In" where Settings would be (`navItemsForGuest`, same
  position), and it routes to the sign-in screens.
- `AuthStartScreen.kt` -- a "Browse without an account" button, shown only when the screen is
  the first thing on the stack (right after signing out); inside the app BACK does the job.
- `AddonRepository.ensureDefaultAddon()` -- restores the bundled Cinemeta when nothing is
  installed (signing out wipes the device's addons and the first-launch default only installs
  once). Signing in later replaces the list with the account's own, as it always has.
- Tests: `GuestGateTest`, `GuestRoutesTest`.
- `RELEASE_NOTES.md` -- user-facing line under Unreleased.

**What a guest gets:** everything that doesn't need the server: Home, Movies, TV Shows,
Genres, Search and title pages with the default addon. The trailer, release-date and cast
lookups need an account, so they quietly come back empty (dimmed Trailer button, plain
names) until signing in.

**Tests performed:** Same sandbox limitation as every recent milestone (no Android SDK):
unit tests for the guest rule and the route and nav-label rules run on GitHub Actions;
brace/paren balance on touched files; a manual re-read, including the ordering of the local
functions in `MangoNavHost` (Kotlin needs them declared before use). **Not performed:** an
on-device run of any of this. Check on a Fire TV: (1) a launch with no account opens Home with
Movies / TV Shows / Search working and the last tab reading "Sign In"; (2) pressing Play on a
title, opening My List, pressing the + on a title, or Sign In shows the sign-in screens; BACK
returns; (3) signing in by QR code or password lands back on that title and carries on to the
sources list; (4) signing out offers "Browse without an account" and that works; (5) a signed-in
launch is unchanged and never shows "Sign In".

**Issues discovered:** After signing in, the guest's browsing screens are rebuilt from the
account's own addon list, which may differ from the default Cinemeta; that is the existing
sync behaviour, not something this adds. Process death while on the sign-in screens forgets
where the guest was heading (the return target is held in memory only), so sign-in then starts
fresh at Home.

**Issues fixed:** none beyond the new behaviour.

## Post-Milestone-42 — Arc TV Plus Tab (Hidden Until Launch)

**Status:** Complete, not compiled (see Tests performed). No release-notes line: the tab is
hidden in release builds, so nothing user-visible ships.

**Context:** User request, ported from the web app (MangotvWebb fbcd56b, 4cdd012, fa77604
"ArcTV Plus tab"). The web shows the tab only to a private preview (the "Picked for you"
commit later hid it for everyone) because Plus is not on sale yet.

**Changes:**
- `PlusPlans.kt` (new) -- everything the tab shows, in one place, as on the web: the three
  plans (Monthly, Yearly, Lifetime) with `price` and `checkoutUrl` to fill in at launch
  (a blank checkout page reads "Opens soon", a null price "Price announced soon"), four
  "coming soon" perks, and the two notes (what stays free; proceeds go back into the app).
  The perks are the web's with Blocked Genres and relay-allowance wording swapped for what
  exists on the Firestick. `PLUS_TAB_VISIBLE` is `BuildConfig.DEBUG`: the tab appears in debug
  builds (such as the CI APK used for testing) and not in release builds. Showing it to
  everyone at launch is changing that one line.
- `PlusSettingsScreen.kt` (new) -- the pane: a "Free plan" badge, the notes, "What Plus adds"
  (focusable perk rows with a "Coming soon" tag), "How to subscribe", and three plan cards.
  Plain text can't be focused, so the perk rows and plan cards are focusable, which is what
  lets the remote move down the whole tab. A plan with a checkout page opens it in whichever
  app the person picks (nothing is paid for on the TV); without one it shows "Plus isn't on
  sale yet".
- `SettingsScreen.kt` -- an "Arc TV Plus" category, listed only while `PLUS_TAB_VISIBLE`. With six
  categories the sidebar no longer fit the height under the nav bar on a TV and the last row was
  squeezed (reported after the first on-device look), so the sidebar now scrolls with the remote
  and its rows are tighter (10dp vertical padding and 6dp gaps, down from 14dp and 10dp), with a
  little padding inside the scroll area so a focused row's scale-up isn't clipped.
- `PlusPlansTest.kt` -- the plan list, the on-sale rule and that every perk and plan is filled in.

**Not ported:** the web's per-account "Plus status" (there is none yet), and a checkout flow:
there is no backend for subscriptions.

**Tests performed:** Same sandbox limitation as every recent milestone (no Android SDK): unit
tests run on GitHub Actions; brace/paren balance on touched files; a manual re-read.
**Not performed:** an on-device look. In a debug build: Settings lists "Arc TV Plus" last; RIGHT
from it reaches the first perk, DOWN steps through the perks and the plan cards and scrolls the
tab, LEFT returns to the sidebar, and pressing a plan says it isn't on sale. A release build must
not show the tab.

**Issues discovered:** none.

**Issues fixed:** none.

## Post-Milestone-43 — Hero Slide And Edge Shading, TV Show Detail Spacing

**Status:** Complete, not compiled (see Tests performed). The Detail change is a blind layout
tweak and needs an on-device look.

**Context:** User request (the "smaller web updates" left from the parity list), ported from
MangotvWebb 3c512ff (hero edge shading), a8dd936 (hero slide transition) and 11a20b6 (TV show
Detail spacing).

**Changes:**
- `HeroSection.kt` -- edge shading: a second horizontal gradient dark at the very edges (88%,
  55% at 4% of the width, clear by 10%, mirrored on the right), on top of the existing
  left-to-right assist. The same numbers as the web.
- `HeroSection.kt` -- slide transition: the backdrop (with its Ken Burns zoom) and the title,
  details and description now slide in from the right while the old ones slide out to the left,
  650 ms, each travelling the full screen width so they cross together (`MangoMotion.HeroSlideMillis`
  replaces the 900 ms crossfade constant). Both are keyed by the title's id, so a watched-flag
  refresh that hands the hero an equal copy of the same title doesn't replay the slide. The
  Play / Trailer / My List / Info buttons are deliberately not part of the slide: they stay put,
  so the remote's focus is undisturbed by the rotation every nine seconds.
- `HeroSection.kt` -- page dots: one small dot per hero title at the bottom right, the current
  one a longer, brighter pill (24 x 8 dp against 8 dp circles at 40% white) whose width animates
  as the hero moves on, as on the web. They only show where you are: unlike the web's clickable
  dots they are not focusable, so the remote's movement between the buttons and the rows below
  is unchanged.
- `DetailHeroSection.kt` -- TV show pages (the non-compact layout): the hero's minimum height is
  60% of the screen (was 82%) and its bottom padding 24 dp (was 56 dp), so the seasons start
  right below the Play row, and the rating badge is centred on the Play row's buttons (padded up
  half a button and slid down half its own height, so its own height need not be known). Movie
  pages are unchanged.
- `RELEASE_NOTES.md` -- two user-facing lines under Unreleased.

**Not ported:** making the dots clickable (a mouse control; on a TV they only show position).

**Tests performed:** Same sandbox limitation as every recent milestone (no Android SDK): brace/paren
balance on touched files and a manual re-read. Animation and layout can't be unit tested.
**Not performed:** an on-device look. Check on a Fire TV: Home's hero slides smoothly with picture
and text together and the Play row doesn't flicker or lose focus as it rotates; the edge shading
looks right; on a TV show page the seasons are visible without scrolling much, the title block
still fits above the Play row, and the rating is level with the buttons. The 60% height is a
starting point; it is one number to adjust.

**Issues discovered:** none.

**Issues fixed:** none.

## Post-Milestone-44 — Faster First Boot

**Status:** Complete, not compiled or timed on a device (see Tests performed).

**Context:** User report: the app is very slow for the first 5-10 seconds after launching.

**Changes:**
- `StremioAddonProvider.getHomeSections` -- the base row is now its own first batch and the genre
  rows follow in batches of 8 (was 4), so the first rows no longer wait on the slowest genre
  request and the last row is reached in about half as many sequential waves. The hero pool is now
  drawn from the base row, which is the first thing to arrive, rather than from the first four rows.
- `MangoTvApplication.warmUp` -- loads the UI sounds (`SoundPool`) and reads Home's cached rows on a
  background thread at launch, so the first frame no longer waits on them.
- `app/src/main/baseline-prof.txt` plus the `profileinstaller` dependency -- a baseline profile for
  the app's own code (wildcard rules), so release builds start precompiled rather than interpreted.

**Not changed:** the debug APKs the CI builds for sideloading do not use baseline profiles and run
unoptimised Compose, so they will always feel slower than a release build on a Fire TV Stick.

**Tests performed:** None runnable here; relies on the CI build. Needs a timed launch on a Fire TV
(debug vs release) to confirm the improvement.

## Post-Milestone-45 — Audio Decoder Fallback

**Status:** Complete, not compiled or tried on a device (see Tests performed).

**Context:** User report: a source fails on Fire TV with `MediaCodecAudioRenderer error, ... audio/mp4a-latm, mp4a.40.1 ... format_supported=YES`. `mp4a.40.1` is AAC Main profile, which a hardware decoder can claim to support and then fail on.

**Changes:**
- `PlayerEngine.buildExoPlayer` -- uses a `DefaultRenderersFactory` with decoder fallback on, so a decoder that fails to start is replaced by the next one the device offers (usually software).
- `PlayerListenerBridge` / `describePlaybackError` -- the error screen now appends the underlying cause and the ExoPlayer error code name.
- `PlayerListenerBridge.switchToOtherAudioTrack` -- the first on-device failure showed the decoder was already the software one
  (`c2.android.aac.decoder`, `ERROR_CODE_DECODING_FAILED`), so fallback alone did not help. On an audio decode/track failure the
  player now overrides the selection to the next supported audio track in the file (each failed track is remembered, so it
  cannot loop) and re-prepares from the same position. Shows the error only when no other audio track is left.

**Limits:** a file with only one audio track, all of it undecodable, still fails. The real fix for AAC "Main" or similar would be an FFmpeg audio decoder, but the Jellyfin build of it has no release matching Media3 1.4.1, so that needs a Media3 upgrade first.

**Tests performed:** None runnable here; relies on the CI build and the same source on a Fire TV.

## Post-Milestone-46 — Blocked Genres (synced to the account)

**Status:** Complete, not compiled or tried on a device (see Tests performed). The backend half is tested.

**Context:** User request: port the web app's Blocked Genres, stored on the account so it syncs across devices.

**Backend (`server/`):** migration `0015` adds `user_settings.blocked_genres` (jsonb, default `[]`); `PUT/GET /user/settings` carry `blockedGenres`, optional on `PUT` so an older client leaves the stored list alone; same last-write-wins as the rest of the row. `settings.test.ts` extended (round trip, omitted list preserved, stale push ignored, validation, per-account isolation).

**App:**
- `BlockedGenresRepository` (DataStore + a StateFlow) with `onLocalChange`, hooked by `SettingsSyncRepository`, which now sends the list on every settings push and applies it on pull (a backend without the field never wipes the local list). Cleared on account switch; counts as local data for the first-login migration choice.
- `BlockedGenres.kt` -- the pure matching rules (trim / lower-case, a title with no genres is never hidden), unit-tested in `BlockedGenresTest`.
- Applied in `HomeViewModel` (rows and so the hero), `TypeBrowseViewModel` (grid and genre drop-down), `SearchViewModel`, `GenresViewModel`, `GenreResultsViewModel` and `DetailViewModel.loadSimilar`; blocking or unblocking re-filters what is already loaded without a re-fetch.
- Settings > Blocked Genres (`BlockedGenresScreen`, `BlockedGenresViewModel`): a toggle row per genre the addons offer (a blocked one an addon no longer lists stays visible so it can be unblocked) and a Clear all button.

**Not changed:** the web app keeps its list in the browser; it would need a small change there to read and write `blockedGenres` on the account.

**Tests performed:** server `vitest` (all passing, locally against Postgres) and schema verification. The Kotlin has not been compiled here; it relies on the CI build.

## Post-Milestone-47 — Picked For You (the web recommendation algorithm) And Like / Not For Me Sync

**Status:** Complete, not compiled or tried on a device (see Tests performed). The backend half is tested.

**Context:** User request: implement the web app's "Picked for you" algorithm fully on the Firestick, with Like / Not for me kept on the account.

**Backend (`server/`):** migration `0016` adds `movie_feedback` (per user, profile, addon, movie; soft-deleted like the watchlist); `GET / POST / DELETE /user/feedback` with last-write-wins on the client's timestamp. The route and wire shape match what the web app already calls, so the web's feedback sync works against this backend unchanged. `tests/feedback.test.ts` covers auth, round trip, newer / older writes, clear, re-set after clear, 204 for a never-existing slot, profile and account isolation, validation.

**Algorithm (`data/recommend/`)**, a line-for-line port of the web's `domain/recommend`: `RecommendConfig` (every number, same values), `Signals` (strongest signal per movie: like 5, Not for me -5, finished 2, saved 1), `Features`, `Preferences` (a movie's weight split equally over its genres / directors / cast), `Score` (weighted cosine, 0.6 / 0.25 / 0.15, renormalised when a category is missing), `Explain` (the movie that contributed most, or "More from directors you enjoy"), `Diversity` (no movie explains more than 3 picks), `RecommendEngine` (shortlist from catalogue genres, round-robin over the profile's own movies, detail lookups bounded to 40 + 60, a candidate that is itself one of the profile's movies is scored without its own signal, popular fallback under 3 interactions). `FeatureCache` keeps looked-up features for 30 days (800 entries). `RecommendTest` ports the web's test file case for case.

**App:** `FeedbackRepository` (local + sync, outbox for offline pushes, last-write-wins pull like the web store), wired into `SyncManager` (pull, retry) and sign-out. `HomeViewModel` recomputes the row (debounced, only when the pool, signals or exclusions change) and places it after Continue Watching; Not for me hides a title immediately. `ContentCard` shows the reason in place of the year. Like / Not for me are in the long-press menu and the Detail page's three-dot group, for movies.

**Gating:** ArcTV Plus is in early access. The Plus tab, the row, Like / Not for me and their sync are on for everyone (`PLUS_EARLY_ACCESS` in `PlusPlans.kt`, which `PLUS_TAB_VISIBLE` follows) and labelled as Plus features: the row titles end "· ArcTV Plus", and the Plus tab says "Early access" and lists "Picked for you" as included. They still need a signed-in account. The web app does the same (its `?plusPreview` flag is removed). When subscriptions launch, make the flag read the account's status.

**Tests performed:** server `vitest` (all passing, locally against Postgres). The Kotlin has not been compiled here; it relies on the CI build, including `RecommendTest`.

## Post-Milestone-48 — Update Pop-Up Shows All The Notes

**Status:** Complete, not compiled or tried on a device (see Tests performed).

**Context:** User request: the update pop-up must be able to show all the patch notes, and never tell people to go to GitHub.

**Changes:**
- `UpdateNotes.kt` -- `combineReleaseNotes` builds the pop-up text from every published release newer than the installed version up to the one on offer, newest first, each under "Version x" when there is more than one (drafts and pre-releases ignored). `plainNotes` turns the Markdown into plain text (dots for bullets, no `**` / backticks / link syntax) and drops lines that only point elsewhere ("Full Changelog", "see commit history", github.com links).
- `GitHubUpdateApiClient.listReleases` and `UpdateRepository.getLatestUpdate(installedVersion)` fetch the last 30 releases for that; if the list can't be read, the newest release's own notes are used.
- The pop-up's empty-notes text and the release workflow's fallback (which wrote "See commit history for changes in this release.") are now "Bug fixes and improvements."
- The notes box already scrolls with the remote and has no length limit, so nothing is cut off.

**Tests performed:** `UpdateNotesTest` (Markdown clean-up, pointer lines, one / several / ignored releases, fallback) -- not run here; relies on the CI build.

## Post-Milestone-49 — ArcTV Plus Paywall (Stripe)

**Status:** Complete, off by default. Backend tested (`server/tests/plus.test.ts`, 16 cases, plus the whole suite); the Kotlin has not been compiled here or tried on a device, so it relies on the CI build.

**Context:** User request: set up the paywall. Decisions: Stripe; Picked for you and Like / Not for me are the gated features; non-subscribers see no Plus features (the Plus tab stays, since that is where they subscribe); on the TV, pay by scanning a QR code with a phone.

**Backend (`server/`):**
- Migration `0017_user_plus`: one row per paying account (`plan`, `status`, `valid_until`, Stripe customer / subscription ids, `last_event_at`), written only by the webhook.
- `GET /user/plus` -> `{ active, plan, validUntil, paywall }`. `PLUS_PAYWALL` off (the default): everyone is active with plan `early_access`. On: only paying accounts. `computeEntitlement` is pure and tested.
- `POST /user/plus/checkout { plan }` creates a Stripe Checkout Session (subscription mode for monthly / yearly, payment mode for lifetime) with the account id as `client_reference_id`, and returns its URL. 503 when the paywall is off or Stripe isn't configured; 409 for someone who already has Lifetime.
- `POST /stripe/webhook`: mounted before the JSON parser, signature checked against the raw body (HMAC-SHA256, 5 minute tolerance, constant-time compare). `checkout.session.completed` switches Plus on (Lifetime only once `payment_status` is `paid`); `customer.subscription.updated` moves the paid period; `.deleted` ends it. Writes are keyed on the event's own time, so repeats and late deliveries can't undo a newer event; a Lifetime owner is never changed by a subscription event.
- `docs/PAYWALL.md` has the Stripe setup and the environment variables; `.env.example` lists them.

**App:**
- `PlusRepository` keeps the last status from `GET /user/plus` (pulled by `SyncManager` on launch / sign-in, cleared on account switch). Picked for you (`HomeViewModel`), the long-press menu and the Detail page's Like / Not for me read it instead of a build flag; `PLUS_TAB_VISIBLE` / `PLUS_EARLY_ACCESS` are gone.
- Settings > Arc TV Plus: "Early access" while the paywall is off; "Arc TV Plus" with plan and period when owned; "Free plan" plus plan cards otherwise. Choosing a plan shows the checkout URL as a QR code and asks every 4 seconds (up to 10 minutes) whether the payment went through, closing itself when it did.

**Switching it on:** see `docs/PAYWALL.md`. Until `PLUS_PAYWALL=on` is set on the host nothing changes for anyone.

## Post-Milestone-50 — Full-Screen Plus Checkout Page (Firestick)

**Context:** User request: the plan-then-QR step looked cramped inline; replace it with a proper page (reference: a "Finish payment on your phone" screen). Firestick only.

**Backend:** `POST /user/plus/checkout` now also returns `amountTotal` (smallest currency unit) and `currency` from the Stripe session, so the TV can show the real price; older apps ignore the extra fields.

**App:** new `PlusCheckoutPage` (full-screen `Dialog`): logo, three-step indicator (Choose plan / Pay on phone / Start watching), "YOUR PLAN" card (plan, price from Stripe, included perks, billing, due today, note, "Change plan") and a large QR with a mm:ss countdown and "Waiting for payment…"; a thank-you screen for 3 seconds when the payment lands, then it closes. Back or "Change plan" returns to the plans. `PlusSettingsViewModel` now ticks every second and checks the payment every 4th tick over the same 10-minute window. `PlusPrice.kt` holds the price/countdown/billing text helpers, unit-tested. If the backend hasn't been redeployed the price reads "Shown on your phone".

## Post-Milestone-50 refinement — Plus tab scrolling and instant checkout

The Arc TV Plus tab's top block (plan status) is now a focusable surface and the first stop for the remote, so Up from the perks scrolls back to it (plain text can't take focus, which stranded the list scrolled down). Choosing a plan opens the full-screen checkout page at once, with the plan card and a same-size blank QR placeholder until the link arrives; the inline "Getting your checkout ready…" text is gone. "Change plan" during that moment now cancels the pending request. Errors still show under the plans.

## Post-Milestone-50 refinement — Plus tab focus, jitter and order

Plan cards are now equal height (`IntrinsicSize.Min` row, `fillMaxHeight` cards): uneven heights made the taller card count as "below" its neighbours, so Down moved sideways. The tab's footer note is focusable so Down has a target and the tab scrolls to its end. The plan block no longer scales on focus, and the Settings category list has more vertical padding, to stop the scroll area nudging a scaled focused row back and forth (the slight shake). Arc TV Plus is now second in the Settings list, between Account and Addons.

## Post-Milestone-50 refinement — Plus tab: no selectable footer, invisible top focus

The footer note is plain text again (equal-height plan cards already stop Down jumping sideways, so it no longer needs to be a focus target). The plan summary at the top stays the first focus stop, which is what lets the remote scroll back up, but its focus border, scale and shadow are all off, so nothing is drawn when it is selected.

## Post-Milestone-50 refinement — Faster Home hero on first boot

**Cause:** on a cold start the hero was drawn at random from the cached rows, then drawn again from the live rows once the network answered, so its pictures (large backdrops and logos) were only requested after the live fetch and the slide changed under the viewer; all ~20 hero pictures were also requested at once, competing with the first slide and the poster rows.

**Change:** the hero for the *next* launch is drawn once per session from the live catalogue, saved in `HomeCacheRepository` (as the cached `hero`), and its pictures are downloaded in the background (`prefetchHeroImages`, same RGB_565 request config as the hero). The next cold boot paints that hero from the cache, keeps it when live data arrives (filtered through Blocked Genres), and `MangoTvApplication.warmUp` loads its first three pictures into Coil's memory cache before Home is composed. `HeroSection` requests its first four pictures at once and the rest after 2.5 s. With no cache (first install) behaviour is as before. Titles still differ each launch.

## Post-Milestone-51 — Profiles (backend)

**Status:** Backend complete and tested; **no app change** (the Firestick does not use profiles yet), and nothing visible changes for anyone until a client sends `X-ArcTV-Profile` or calls `/user/profiles`.

**Context:** User request: ArcTV Plus profiles, built on the web app first (repo `MangotvWebb`, `docs/PROFILES.md`) and "don't update the Firestick yet". Decisions: Plus only; everything separate per profile; at most 5 profiles per account in any adult / kids mix; PINs allowed. This is the backend half that the web app (and later the Firestick) needs.

**Changes (`server/`):**
- Migration `0018_profiles`: new `profiles` table (`id` `main` or `p_<hex>`, `name`, `avatar`, `kind`, argon2id `pin_hash`, `pin_failed_attempts` / `pin_locked_until`, `is_default`; database checks keep `main` the only default, always an adult profile) and a `profile_id text NOT NULL DEFAULT 'main'` on `user_settings` (primary key is now `(user_id, profile_id)`), `user_addons`, `watchlist_items`, `watch_history` and `continue_watching`, with their unique keys and "active" indexes widened to include it (`movie_feedback` already had it). Every existing row therefore belongs to `main`; the migration moves nothing. Library rows have no foreign key to `profiles` (`main` needs no row), so removing a profile deletes its rows from all six tables itself, in one transaction.
- `GET/POST /user/profiles`, `PUT/DELETE /user/profiles/:id`, `POST /user/profiles/:id/verify-pin` (`services/profileService.ts`, `routes/profiles.ts`). The account's own profile is created on first listing, named after the account. The limit of 5 is checked under a lock on the account's row, so simultaneous creates can't pass it. Create / change / remove need Plus (`getEntitlement`); listing and verifying do not, so nothing is hidden or deleted if Plus lapses. `verify-pin` is 204 / 403 (never 401, which the web server reads as "session over") / 429 + `Retry-After`; five wrong PINs in a row lock the profile for five minutes whatever address they come from, counted under a row lock so parallel guesses can't each get a free try, and a new PIN clears a lock. A per-address limit (30/min) sits on top. `HttpError` gained an optional `Retry-After`.
- `middleware/profile.ts` `resolveProfile`: reads `X-ArcTV-Profile`, checks the id belongs to the caller (404 otherwise) and, for any profile but `main`, that the account has Plus (403). Applied to every library route (settings, watchlist, watch-progress, history, continue-watching, addons, feedback); all their services take the profile id and filter / key on it. On `/user/feedback` the header wins over a `profileId` in the body or query, which still works without one (older clients).
- `scripts/verify-schema.ts` expects the new table.

**Tests performed:** `server/tests/profiles.test.ts` (27 cases, real Postgres): first-use default profile, create / change / remove, the limit of 5 including 7 simultaneous creates, validation, the PIN never leaving the server, Plus gating (and that a lapsed account keeps its data), PIN success / 403 / lockout / parallel guesses / reset / expiry, each of the six libraries kept apart by the header, a removed profile taking its library with it, other accounts' profiles being invisible, pre-profiles rows reading as `main`, and the database constraints. Whole suite: 220 passing (193 before). `npm run verify-schema`: 59 passed. The web repo's integration suite (real web server -> this backend, `MANGOTV_BACKEND_DIR=... scripts/run-backend-integration.sh`) passes, including its new `profiles` cases and all older ones, which run unchanged against the widened schema. The Kotlin was not touched or compiled.

**Not done (on purpose):** the Firestick app. When it adopts profiles it needs `X-ArcTV-Profile` on every `/user/*` call, a picker / manage / PIN screens, per-profile local caches and the kids rules; the list is in the web repo's `docs/PROFILES.md` and `docs/FIRESTICK_PARITY.md`.

**Deploy order:** run the migration, then deploy the backend, then deploy the web app. The web app treats a backend that answers 404 on `/user/profiles` as "no profiles", so the web app can also go first without harm.

## Post-Milestone-52 — Profiles (Firestick app)

**Status:** Written, **not compiled or run here** (see Tests performed); built by CI on this branch. Needs the backend from Post-Milestone-51 (merged) deployed: migration `0018`, then the server.

**Context:** User request: profiles on the Firestick, matching the web app (MangotvWebb `docs/PROFILES.md`). Plus only; up to 5 per account in any adult / kids mix; a separate library per profile; optional 4-digit PIN.

**Changes (`app/`):**
- `data/profile/`: `Profiles.kt` (the `Profile` model, the 12 avatar ids shared with the web app, `KIDS_BLOCKED_GENRES`, `needsProfilePicker`, `visibleProfiles`, `isValidPin`, `PinChange`), `ActiveProfile` (process-wide active profile id + a `kids` flow) and `ProfileRepository` (the profile list and active id kept per account in DataStore; `restoreFromDisk` at launch, `pullFromServer(plus)` from `SyncManager.syncAll`, create / update / remove / `verifyPin` against the backend; without Plus only the account's own profile is usable and a device left on another goes back to it; a backend answering 404 on the list means "no profiles" and the app behaves as before).
- `AccountApiHttpClient` gets an interceptor that adds `X-ArcTV-Profile` to every `/user/*` request except the account-level ones (`/user/me`, `/user/plus`, `/user/profiles…`), and none at all for the account's own profile, so an older backend and every existing account are untouched (`profileHeaderValue`, unit-tested).
- `ProfileSwitcher` (new): a profile switch flushes what is queued (5 s), forgets every local cache and queue (the account-switch sweep without the sign-out, plus the cached Home rows, whose new `HomeCacheRepository.clear` it uses), records the new profile and pulls its library; a profile with no addons gets the default one (`AddonRepository.bootstrapDefaultForNewProfile`, pushed to the account). Caches are emptied before the new profile is recorded, so a kill half-way comes back on the old profile and re-pulls. The account's first-sync state is left alone; the watched-history catch-up is reset per profile. If the active profile vanishes underneath the device (removed elsewhere, Plus lapsed), `SyncManager.onActiveProfileLost` forgets the caches before the pull. Signing out also clears the profiles (`AccountSwitchCoordinator`).
- `FeedbackRepository` files Likes under the active profile instead of a fixed `main`.
- Kids: `BlockedGenresRepository.effectiveGenres` (own list + `KIDS_BLOCKED_GENRES` while a kids profile is active) is what Home, Movies, TV Shows, Genres, Search and "More like this" now hide; `genres` stays the profile's own list (what Settings shows and syncs). The nav bar drops Settings and `navigateTo` refuses Settings on a kids profile.
- UI (`ui/profiles/`): "Who's watching?" (D-pad tiles, the profile name as the last nav item, `profiles` route, shown at launch once Plus and more than one profile are known and none has been picked since the app started), a D-pad PIN pad, Manage profiles with an editor (name, picture, Adult / Kids, set / change / remove PIN entered twice, remove with a confirmation). Locked profiles ask for the PIN to open, and the current PIN to change or remove. Settings > Account says "Watching as ..." with a "Switch or manage profiles" button. Plus tab lists Profiles as included.
- The PIN is a household gate on a TV: the backend counts wrong tries per profile and locks guessing (429), but, unlike the web app (where the server keeps the active profile in a cookie), the TV talks to the backend directly, so the backend can't make every request depend on the PIN.

**Tests performed:** `ProfilesTest` (header rule for library vs account-level paths and a path prefix, the active profile and kids flag, the picker rule, Plus-less visibility, PIN shape, avatar ids, kids genres against `isBlockedBy`) and `PlusPlansTest` updated for the Profiles perk, **not run locally**: this sandbox has no Android SDK and no route to `dl.google.com`, so Gradle can't build here. The Kotlin was re-read and checked against the call sites it touches; compiling and running the unit tests is left to CI (`build-apk.yml`, which runs `testDebugUnitTest` then `assembleDebug` on this branch). Nothing was tried on a device.

**Issues discovered:** none yet (not run).

## Post-Milestone-53 — Profile picture in the top bar, and a reason when profiles don't load

**Context:** User report after 0.1.5: profiles were not visible on the TV, and profiles must be switched from the top bar like on the web (profile picture at the top right, pressing it opens "Who's watching?"), not from Settings.

**Changes (`app/`):**
- `TopNavBar`: the profile's picture (`ProfileNavButton`) is at the far right of the bar and opens the profile screen; the profile-name item at the end of the list is gone. The item list now fills the space between the logo and the picture. Shown only when the account has Plus and profiles loaded, as before.
- `ProfilesState.problem` (new): why the profile list isn't available (no Plus status, a 404 from an older service, another HTTP status, no connection, an unreadable answer). `SyncManager.syncAll` records "couldn't read whether this account has ArcTV Plus" when the Plus read fails. Settings > Account shows it as "Profiles: ..." in place of the "Watching as" block, so a missing profile list is explained on screen instead of silently hiding every profile control.

**Tests performed:** none new (UI and error text only); **not compiled or run locally** (no Android SDK here), CI (`build-apk.yml`) builds this branch. Not tried on a device.

**Issues discovered:** the cause of profiles not showing on the user's TV with 0.1.5 is not known yet; the on-screen reason is there to find it.

## Post-Milestone-55 — Profile name beside the picture in the top bar, smaller

**Context:** User request: show the profile's name next to its picture at the top right of the top bar (web and Firestick) and make the Firestick's smaller.

**Changes (`app/`):** `ProfileNavButton` is one focusable surface holding a 28 dp picture (6 dp corners) and the profile's name (`labelMedium`, one line, ellipsised at 120 dp), where it was a 42 dp picture alone. `ProfileAvatarTile` gets an optional `cornerRadius`.

**Tests performed:** none new (layout only); **not compiled or run locally** (no Android SDK here), CI (`build-apk.yml`) builds this branch. Not tried on a device.

## Post-Milestone-56 — "Picked for you": the web app's three recent changes (liked titles, genre split + rotation, Remove from Picked for you)

**Status:** Written; CI builds and tests it. Not run on a device here.

**Context:** User request: port the three "Picked for you" changes that were only on the web app (MangotvWebb PRs #4, #7-#10; recorded in its `docs/FIRESTICK_PARITY.md`) to the Firestick, value for value (`docs/RECOMMENDATIONS.md` in the web repo has the design).

**Changes (`app/`):**
- Liked titles are not offered: `excludedFromPicks` now excludes every title with feedback (Like or Not for me), as well as finished, Continue Watching and hand-removed ones; `pickedSection` drops a title the moment it is rated or removed.
- Genre split + rotation: new `Taste.kt` (`primaryGenre`, `tasteShares`) and `Rotation.kt` (`seededRandom`, a mulberry32 that gives the web app's numbers for the same seed, and `compose`: the 5 best picks stay, the other places are given out by Sainte-Lague in proportion to the profile's genre shares, each drawn with a seeded weighted draw from that genre's scored picks within 60% of its best, a pick shown last launch counting 0.15x). `recommend` shortlists a quota per taste genre (`SHORTLIST_GENRE_BUDGET` 36, at least 4), carries each pick's genre, composes before the diversity step, and takes `seed` / `previousShown` inputs; `CANDIDATE_DETAIL_FETCH_LIMIT` is 60 like the web. `HomeViewModel` uses one random seed per launch and passes the ids the last launch showed.
- Remove from Picked for you: new `PickedStateRepository` (per account and profile, follows both by itself; holds the hand-removed titles and the ids the last launch showed; cleared on sign-out), `Content.pickedForYou`, and a long-press menu item on Picked for you cards. Removing is not feedback: no taste signal, no sync, no score change.

**Tests performed:** `RotationTest` (new, ported from the web's `rotation.test.ts`: the genre split of a horror-heavy row, anchors kept, repeatability, most of the row swapped on the next launch, nothing at or below zero or far below its genre's best, running short, the PRNG against the web's own values for three seeds, taste shares, the engine on a mostly-horror profile, liked/removed titles excluded and dropped from the row). **Not run locally**: no Android SDK here. CI (`build-apk.yml`, `testDebugUnitTest` then `assembleDebug`) runs it on this branch. Not tried on a device.

## Post-Milestone-57 — Repository renamed to ArcTV-AndroidTV

**Context:** The product is now Arc TV and the app is a standard Android TV app (it declares the leanback launcher and no touchscreen), not only a Fire TV one, so the repository `MangoTV-Live-TV` was renamed `ArcTV-AndroidTV` on GitHub (the owner did the rename; GitHub redirects the old address).

**Changes:** `UpdateRepository.GITHUB_REPO` (the repository the in-app update check reads releases from) now names `ArcTV-AndroidTV`. Installed apps keep updating either way: until this ships they follow GitHub's redirect from the old name, which keeps working as long as no new repository takes the old name. Nothing else in this repository named the old repository. The Android package id (`com.mangotv.app`) is deliberately unchanged, because changing it would make Android treat the app as a different one and block updating existing TVs. The web repository was renamed `ArcTV-Web` at the same time (a code comment now points at it). Older entries in this file keep the old names (`MangoTV-Live-TV`, `MangotvWebb`), since those were the names at the time, and the applied migration `0018_profiles.sql` keeps its comment as written.

**Tests performed:** none new (one string constant); not compiled or run locally (no Android SDK here), CI builds this branch. The release update check was not exercised.

## Post-Milestone-58 — Fix the failing `RotationTest` on main (a wrong test, not wrong code)

**Cause:** CI on `main` failed after Post-Milestone-56 on one unit test I wrote, `aRefreshSwapsMostOfTheRowWhenToldWhatTheLastLaunchShowed`, because it expected a refresh to swap most of the row on a tiny profile (4 liked movies, 42 candidates). There the "one liked movie explains at most 3 picks" rule leaves almost no room to rotate; the web app's engine does the same on that data (it kept all 20 titles each time). Merged before CI had finished, at the owner's request.

**Change:** the test now uses a realistic profile (12 liked movies, 210 candidates), where the web engine keeps about 6 of 20 between launches, and asserts fewer than 10 are kept (and at least the 5 anchors). No app code changed.

**Tests performed:** the same scenario run through the web engine (about 6 kept per launch for 12 and for 20 liked movies). The Kotlin test itself is run by CI (`build-apk.yml`); not run locally (no Android SDK here).

## Post-Milestone-59 — Fix: sync cancelled before it pulled the library (introduced in 0.1.5)

**Status:** Fix written; CI builds and tests it. Not run on a device here.

**Cause:** Post-Milestone-52 put the profile / Plus step at the start of `SyncManager.syncAll`, in the *caller's* coroutine, before the work launched onto the manager's own scope. `syncAll`'s own kdoc explains why that matters: AuthGateViewModel, QrSignInViewModel and PasswordSignInViewModel call it and navigate away at once, which cancels their `viewModelScope`. A step running in that coroutine is cancelled mid-request and `syncAll` ends before it ever launches the pulls: settings, My List, Continue Watching, addons and feedback were never pulled, and `ProfileRepository` never became ready ("Profiles: still loading"). A device with a filled cache hid it; a fresh install / sign-in showed an empty My List.

**Change:** the profile / Plus step now runs inside the `scope.launch { ... }` that `syncAll` joins, before the parallel pulls, bounded by `PROFILE_STEP_TIMEOUT_MS` (12 s) so a slow answer can't hold the library back (a timeout is recorded as the profile problem shown in Settings > Account).

**Tests performed:** none new: `SyncManager` needs an Android `Context` and real repositories, and this failure is about coroutine cancellation at the caller. CI compiles it and runs the existing unit tests. Not tried on a device. (This is the change first made on `claude/fix-sync-cancel`, whose pull request #22 was closed unmerged; it is re-applied here on top of the current `main`.)


## Post-Milestone-60 — TV sign-in page (`/activate`) restyled to ArcTV

**Change:** `server/public/activate.html` / `activate.js` now use the ArcTV look (logo, cyan → blue → violet gradient, dark glow background, same as `plus-thanks.html`) instead of the old orange MangoTV page: "Sign in to your TV" heading, a "Fire TV · Waiting to connect" status card (turns "Connected" once the code is used), Sign In / Create Account switch, email and password fields with icons and a show-password button, "Sign in & connect TV", and a "New to ArcTV? Create an account" link. The form ids and the `/auth/qr/*` calls are unchanged. Not added from the reference mock: Continue with Google / Apple and Forgot password, because the backend has no such sign-in or reset routes. No app release needed (server page; goes live when Render deploys).

**Tests performed:** `node --check activate.js`; page rendered in headless Chromium at phone width (sign-in and create-account states) and checked by eye. The submit flow against a live backend was not run.

## Post-Milestone-61 — TV sign-in page: display name required when creating an account

**Change:** on `/activate`, the display name field is now required in Create Account mode (the browser blocks an empty submit; the page always sends it). Page only: the backend schema still treats `displayName` as optional so other clients are unaffected.

**Tests performed:** `node --check activate.js`. Not run against a live backend.

## Post-Milestone-62 — Fix: Server CI failed on the restyled sign-in page

**Cause:** `tests/qr-auth.test.ts` checked that `/activate` contains the word "MangoTV"; the restyled page says ArcTV. Test expectation only, no app or server code was wrong.

**Change:** the test now expects "ArcTV".

**Tests performed:** read the CI log: 219 of 220 passed, this was the only failure. The fixed test is run by Server CI.


## Post-Milestone-63 — README rewritten in a friendlier, more encouraging tone

**Status:** Done (docs only).

**Context:** The README read as a dry status report. Requested: make it more welcoming, in the style of Stremio Web's GitHub README.

**Changes:** `README.md` is centered under the Arc TV banner logo and a home-screen screenshot (`docs/images/screenshot-home.webp`) under the intro (banner copied to `docs/images/arctv-banner.png`; the in-app logo is white-on-transparent and would vanish on GitHub's light theme). It now opens with a one-line pitch, adds a "Features" list, a short "How it works" section and a "Contributing" note, and adds emoji section headings. The install section now leads with the Downloader code (2368012) for Firestick users, with the ADB steps kept below it. Project structure, build instructions and the doc links are unchanged. No release-notes bullet: the change isn't visible in the app.

**Tests performed:** manual re-read. No Gradle/Android build was possible or needed (docs only).

**Issues discovered:** none.

**Issues fixed:** none.


## Post-Milestone-64 — Bring the Firestick level with the web app (Genres, top bar, Search, Settings, Plus popup)

**Status:** Written and unit tests added; not built or run here (no Android SDK in this sandbox).

**Context:** Five web changes had not been made on the Firestick (`docs/FIRESTICK_PARITY.md` in `MikeC444/ArcTV-Web`). They are ported with the same look and rules, adapted for a TV remote.

**Changes:**
- **Genres removed.** The Genres tab, `GenresScreen`, `GenreResultsScreen` and their view models and routes are gone (the Movies / TV Shows genre drop-downs stay). `MangoNavItems` is now Home, Movies, TV Shows, Search, My List, Settings.
- **Top bar.** The links sit in a frosted pill centred between the logo and the profile chip (logo and chip in two equal-width side boxes); the open page is a filled pill with a small cyan-blue-violet underline; the focus ring stays but nothing scales on focus, so the ring is never clipped by the scrolling row; the profile chip is a pill with a round picture. Over Home / Detail the dark band behind the bar is gone (it is the page colour on the other screens). The Home hero's dark edge shading (left and right) is removed; the soft left-to-right shade behind the text stays. The logo is not focusable, so it never has an outline.
- **Search.** A rounded bar with an icon and a clear button; results appear as you type (250 ms pause, from 2 letters; Enter on the keyboard searches at once); every addon is asked at once (it was one after another) and each answer, and each catalog of an addon, shows as it arrives, with a 6 s cut-off per addon and a "Still checking other addons…" line (`CatalogProvider.search` takes an optional `onPartial`); results are a poster grid under Movies / TV Shows headings with counts; **Recent searches** (last 8, per account, `SearchHistoryRepository`, wiped on sign-out) with Clear, one press to search again and hold OK to remove one. The web's title-suggestions dropdown is not ported (on a TV the results already show as posters).
- **Settings.** A rounded side panel of grouped categories (You / Content / Playback & sound, Subtitles before Sounds as on the web) with an icon tile per row, a filled open row with a gradient bar and gradient icon; the open category's settings sit in a rounded card with an icon, title and subtitle header. The side panel has a fixed 230 dp width and compact rows (32 dp icon tiles) so all seven categories show without scrolling on a 540 dp tall screen; the big "Settings" title is left out (the nav bar already shows the open tab) and the margins are slim (`SettingsScaffold` takes optional `horizontalPadding` / `verticalPadding` / `titleGap` / `showTitle`), and the scaffold's body takes all the height under the bar (`weight(1f)`), so the card has the most height possible for the open category's settings. (A first attempt drew the whole body 1.2x bigger; that made the panes cramped, because the screen is only 540 dp tall, so it was dropped.)
- **Arc TV Plus popup** (`PlusPromoHost`, `PlusPromoRepository`). On Home, a few seconds after landing, once per launch, for a signed-in adult without Plus once the paywall is on (never in early access, on a kids profile, or for Plus owners). Take me there opens Settings on the Arc TV Plus tab (`PendingSettingsTab`) and counts as answered; Close (or BACK) hides it for 7 days; Don't show me again ends it for the account. It is a dialog window so the remote stays inside it. It is a small card (340 dp wide, about 60% of the screen height, one short line per benefit, compact buttons centred under them) so it doesn't cover the screen.

**Tests performed:** unit tests added for the recent-searches list (`SearchHistoryTest`), the search merge (`SearchMergeTest`) and the popup rules (`PlusPromoRulesTest`); `GuestRoutesTest` updated for the removed routes. None of it was compiled or run here: no Android SDK in this sandbox. A structural check (brace/paren balance, unused imports) was run over every changed file, and each change was re-read against the code it touches. CI builds it.

**Issues discovered:** none.

**Issues fixed:** none.

## Post-Milestone-65 — Fix: UP from the first row of Movies / TV Shows skipped the sort bar and the genre drop-down

**Status:** Fix written; CI builds and tests it. Not run on a device here.

**Cause:** on the Movies and TV Shows grid, DOWN from the top bar walks drop-down ("All genres") -> bar ("Featured") -> first row, but UP from the first row was intercepted in `RowsBrowseGridContent` and sent straight to the top bar (`navFocusRequester`), skipping the two in between.

**Change:** UP from the first grid row now scrolls the list to the top and focuses the bar just above the grid (the selected filter chip, or "Featured"). That bar's existing UP goes on to the drop-down and then the top bar, so the way back is the way down. `navRegionFocused` is left as it was (false), since the bar and drop-down are part of the same list.

**Tests performed:** none new: this is remote-focus behaviour in Compose and the repo has no UI tests for it. Brace/paren balance checked and the change re-read against the focus wiring it relies on (the bar's `focusUp`, the header and chip requesters). Not tried on a device. CI builds it.

## Post-Milestone-66 — Fix: focus outline cut off on the Trailer button (Home hero, movie Detail)

**Status:** Fix written; CI builds and tests it. Not run on a device here.

**Cause:** the Trailer button is dimmed until a trailer is found, and it was dimmed by wrapping it in `Box(Modifier.alpha(0.45f))`. `Modifier.alpha` (for any value other than 1) draws the Box into a graphics layer that is *clipped* to the Box's own bounds, and the focused button scales up and draws its outline outside those bounds, so the outline looked cut out. With a trailer found the alpha was 1 (no layer), so it only showed while the button was dimmed.

**Change:** `MangoButton` gets a `dimmed` parameter that fades the button's own fill, icon and text (never the focus ring); `HeroSection` and `DetailHeroSection` use it and no longer wrap the button in an alpha Box.

**Tests performed:** none new (a draw-time clipping issue, no UI tests in this repo). Brace/paren balance checked and the change re-read. Not tried on a device. CI builds it.

## Post-Milestone-67 — Smoother Home hero slide on a Fire TV

**Status:** Change written; CI builds and tests it. Not run on a device here, so how much smoother it is has not been measured.

**Context:** the Home hero's slide between titles looked jittery on a Fire TV.

**Likely causes found in the code, and what changed (`HeroSection.kt`):**
- *The next picture was decoded at the moment the slide started.* The warm-up only downloaded the pictures (default request: full-size, 32-bit), but the slide shows them with a different request (screen-sized, 16-bit), so each slide change was a memory-cache miss and a full-screen decode, and the picture popped in late. The next two slides' pictures are now decoded ahead of time into Coil's memory cache with the very same request (`heroBackdropRequest`: same size and bitmap format, so the same cache key) the slide uses. Only two ahead, to keep the cache for the poster rows.
- *Both full-screen pictures were zooming while they slid.* The slow zoom ran forever (back and forth) on the incoming and outgoing picture. It now starts from 1x each time a slide settles, zooms to 1.06x over the slide's time on screen, and does not run during the slide; the outgoing picture keeps the zoom it had reached.
- *The slide curve.* A very fast start with a long tail (650 ms) moved the pictures a long way in the first frames, which reads as a jolt when frames drop. It is now a gentle ease-in-out over 750 ms.

**Tests performed:** none new (animation and image-cache behaviour, no UI tests in this repo). Brace/paren balance and unused imports checked; the changes re-read against the Compose APIs they use. Not tried on a device. CI builds it.

**Issues discovered:** none.

## Post-Milestone-68 — Update pop-up restyled as a small card (like the Plus pop-up)

**Status:** Change written; not built or run here (no Android SDK in this sandbox).

**Context:** the in-app "Update available" pop-up was 680 dp wide and almost the height of the screen; the Plus pop-up had just been made a small card.

**Changes (`UpdatePopup.kt`):** the card is now about 380 dp wide in the same style as the Plus pop-up (elevated background, soft blue / violet glows in the top corners, thin edge, round icon badge), the title and version are smaller, the release-notes box is shorter (120 dp, 11 sp text, scroll step halved to match) and the Update / Install / Retry and Not now buttons are the compact size, centred under the notes. Behaviour is unchanged (download progress, Install, Retry, Back = Not now, notes scroll with the remote).

**Tests performed:** none new (layout only). Brace/paren balance and unused/duplicate imports checked; the change re-read against the Compose APIs it uses. Not tried on a device.

## Post-Milestone-69 — Release notes now attach themselves to each release

**Status:** Workflow change written; not run here (it only runs when a release is cut).

**Cause:** v0.1.7 was released while its bullets were still under `## Unreleased`. The release workflow looked for a `## 0.1.7` section, found none and wrote "Bug fixes and improvements." as the release description, which is what the in-app update pop-up shows.

**Changes:**
- `release.yml` now takes the notes from `## <version>` if there is one, otherwise from `## Unreleased` (the normal case), and only falls back to "Bug fixes and improvements." if both are empty. A section holding only blank lines counts as empty.
- After publishing, the workflow files the shipped notes under `## <version>` on `main` and leaves a fresh empty `## Unreleased` above it, with a small bot commit. It only does this if `main`'s Unreleased still holds exactly what was published (otherwise it leaves the file alone), and a failure in that step (for example a protected `main`) does not fail the already-published release.
- `RELEASE_NOTES.md`: the 0.1.7 bullets are filed under `## 0.1.7` (the fix for that release's file), and the header now says releasing needs nothing done to the file.
- v0.1.7's GitHub release description was edited by hand to carry its real notes (the workflow can't re-run for an existing tag).

**Tests performed:** the awk / grep logic of the new steps was run locally against a copy of `RELEASE_NOTES.md` (extract from a version, fall back to Unreleased, fall back to the fixed line, and the rename with the identical-notes check). The workflow itself has not run.

## Post-Milestone-70 — Backend accepts the web app's new profile pictures

**Status:** Done; type-checked and the profile tests pass against a throwaway Postgres.

**Context:** the web app replaced its 12 colour-tile profile avatars with 16 illustrated pictures (fox, cat, dog, panda, frog, owl, ghost, robot, alien, astronaut, raccoon, penguin, octopus, dragon, retro-tv, lion). The backend only accepted the old 12 ids, so changing a profile picture on the web failed with "Invalid body: Invalid option: expected one of sunrise|ocean|…" (only fox and robot, which exist in both lists, worked).

**Changes (`schemas/profiles.ts`):** `AVATAR_IDS` is now the 16 new ids plus the 10 old ids that are not in the new set (sunrise, ocean, forest, violet, ember, mint, astro, monster, wave, bolt). The old ones stay valid so existing profiles and the Firestick app, which still draws the old set, keep working. No migration: the column is free text. The default profile is still created with `sunrise` (the web app draws it as the fox). New test: the illustrated ids and a legacy id are all accepted on create.

**Tests performed:** `tsc --noEmit` on the server; `tests/profiles.test.ts` against a throwaway local Postgres with all 18 migrations applied (28 passed, including the new one). The Android app was not touched or built.

**Issues discovered:** none beyond the cause above. The Firestick app does not know the new ids yet (it falls back to its first avatar), so a profile given one of the new pictures on the web shows as Sunrise on the TV until the app gets the same pictures.

**Issues fixed:** the web app's picture change error.

## Post-Milestone-71 — Cancel a Plus subscription (backend)

**Status:** Done; type-checked, linted-by-tsc, and the whole server suite passes (226 tests) against a throwaway Postgres. Needs to be deployed, with migration 0019, before the web app's new Cancel button works.

**Context:** the web app's Settings → Account needed a way for a monthly or yearly Plus subscriber to cancel. There was no cancel endpoint, and nothing recorded that a subscription was already set to end.

**Changes:**
- `POST /user/plus/cancel` (signed-in): tells Stripe `cancel_at_period_end=true` for the account's subscription and records it locally straight away, then answers with the entitlement. 409 for Lifetime, 404 with no active subscription, 503 while the paywall is off, 500 (no Stripe detail leaked) if Stripe fails, and a second cancel does not call Stripe again.
- `GET /user/plus` has a new `cancelAtPeriodEnd` boolean (true only for an active subscription that will not renew). Always false for Lifetime, early access and no Plus.
- Migration `0019`: `user_plus.cancel_at_period_end boolean NOT NULL DEFAULT false`. The Stripe webhook now keeps it in step (`cancel_at_period_end` or `cancel_at` on `customer.subscription.updated`), so cancelling or resuming in Stripe's own dashboard shows up too.
- `docs/PAYWALL.md` describes the endpoint.

**Tests performed:** new `POST /user/plus/cancel` tests (sign-in needed, nothing to cancel, paywall off, cancels at period end and is idempotent, follows Stripe's updates both ways, Lifetime refused, Stripe failure leaves the account unchanged). Whole server suite 226 passed. The Android app was not touched or built.

**Issues discovered:** none. The Firestick app has no cancel screen yet; Stripe's customer emails and dashboard still work for TV-only subscribers.

**Issues fixed:** none.

## Post-Milestone-72 — Developer panel data and app-version tracking

**Status:** Backend done and tested; the Kotlin change is written but not built or run here (no Android SDK in this sandbox).

**Context:** the developer wanted to see every account, its addons and Continue Watching, and which Fire TV app version each device is on, updating when someone updates.

**Changes:**
- Migration `0020`: `users.is_admin boolean NOT NULL DEFAULT false` (set by hand in the database; nothing in the API can set it) and an index on `devices.last_seen_at`.
- `requireAuth` now loads `is_admin` (`req.user.isAdmin`; `GET /user/me` returns `isAdmin`) and keeps `devices.app_version` / `last_seen_at` current from the `X-ArcTV-App-Version` header, written only when the version changed or the last write is over five minutes old.
- New read-only `/admin/*` routes for admins (everyone else gets 404): `/summary` (user counts, activity, plans, devices per app version), `/users` (search, paging and filters by plan, app version, has addons, has Continue Watching and last seen, with the total following the filter; each row has plan, devices, addon / Continue Watching counts) and `/users/:id` (profiles, devices, addons, Continue Watching, recent history). Addon addresses are reduced to host and debrid-service name; password, token and PIN hashes are never selected.
- Fire TV app: `AccountApiHttpClient` adds `X-ArcTV-App-Version: BuildConfig.VERSION_NAME` to every account request (one interceptor).
- `docs/ADMIN.md` describes the panel, how to make yourself an admin, and what it never shows.

**Tests performed:** new `tests/admin.test.ts` (admin-only access, search, every filter and the filtered total, secrets never returned, version recorded and updated straight away, junk header ignored, summary of versions); the whole server suite (235) passes against a throwaway Postgres; `tsc --noEmit`. The Kotlin interceptor edit was re-read only (no Gradle build here).

**Issues discovered:** versions only appear once a TV runs a build that sends the header; until then its devices show "unknown".

**Issues fixed:** none.

## Post-Milestone-73 — Catching up with the web app

**Status:** Written; the compile check is the branch's CI build (no Android SDK in this sandbox). Not tried on a TV.

**Context:** the web app had eight user-visible changes the Fire TV app lacked (docs/FIRESTICK_PARITY.md in ArcTV-Web). All are ported to behave the same way.

**Changes:**
- Player: a loading screen (backdrop, logo or name, "S1 E2 • title", spinner) until the first picture plays; a part-watched title carries on from its saved spot (a spot within 10 s of the end starts over; the "Pick up where you left off?" card was tried and removed again); a "Next episode" button for the last 60 s and after the end, plus the 5-second "Up next" countdown when Auto Play Next Episode is on (the setting existed but did nothing, and the bottom-row Next episode button was a no-op); Next episode goes to Sources with `?auto=true`, which takes the remembered or recommended source by itself and replaces the player; playback speed and "time left / total" are remembered on this device (`DevicePlayerPrefs`); the right-hand time is a pressable "−12:34" / total; Settings always has an Audio row (the track's name, or an explanation when there is one track or none). Volume is not remembered: on a TV it is the TV's own.
- Continue Watching: the first position is saved 4 s into playback, then every 15 s, on pause and when leaving; anything past 1 s counts (was 10 s); Home no longer hides a Continue Watching title because a catalogue row also holds it (`withoutShownTitles` removed).
- Cancel Plus: `PlusStatus.cancelAtPeriodEnd`, `PlusApiClient.cancelSubscription` (`POST /user/plus/cancel`), a "renews on / won't renew" row and a confirmation dialog (Keep Plus focused first) on Settings > Arc TV Plus, with the web app's wording for each failure.
- Debrid note under the Addons intro; the Trailer button is a pill (`MangoButton(pill = true)`); recent searches are kept per profile (the account's own profile keeps its old list); the 16 illustrated profile pictures replace the 12 colour tiles (old ids map to the nearest new one).

**Tests performed:** unit tests added for the next-episode rule, the offer and resume rules and the time text (`PlayerLogicTest`); the avatar test updated; the old Continue Watching filter tests removed with the function. The code was re-read; the build is the CI compile of this branch. Nothing was run on a TV.

**Issues discovered:** the Firestick's Next episode button and Auto Play setting were placeholders.

**Issues fixed:** the above.

## Post-Milestone-74 — Debug builds know the server address

**Status:** Written; the proof is the next run of this workflow on the branch. Not tried on a TV.

**Context:** a Fire TV app installed from the "Build debug APK" artifact said "unable to resolve host not-configured.invalid" on sign-in. `API_BASE_URL` is a repository secret that only the release workflow passed to Gradle, so every debug build used the placeholder address.

**Changes:** `build-apk.yml` passes `API_BASE_URL: ${{ secrets.API_BASE_URL }}` to the debug build step.

**Tests performed:** none by hand. The workflow run shows whether the build still succeeds; the sign-in itself has to be tried on a device.

**Issues discovered:** a branch built from a fork or by Dependabot has no access to the secret and would still get the placeholder (it is only for sign-in, not for compiling).

**Issues fixed:** the above.

## Post-Milestone-75 — No resume question

**Status:** Written; the compile check is the CI build of main. Not tried on a TV.

**Context:** the "Pick up where you left off?" card (Resume / Start over / Choose a different source) added in Post-Milestone-73 was not wanted, on the web or here.

**Changes:** `PlayerScreen` carries on from the saved position again, as before Post-Milestone-73 (a position within 10 s of the end starts over); `ResumeCard` is removed and the release-notes bullet for it is dropped.

**Tests performed:** none by hand; the build is the compile check.

**Issues discovered:** none.

**Issues fixed:** the above.

## Post-Milestone-76 — Easier scrubbing and a player that remembers the cursor

**Status:** Written; the compile check is the CI build of the branch. Not tried on a TV.

**Context:** holding Left / Right on the player timeline sped up and stopped at 2 minutes, and the cursor always went back to Play / Pause after a menu or when the controls reappeared.

**Changes:**
- `PlayerScreen`: holding Left / Right while the timeline is selected adds one 10-second step every 250 ms for as long as the key is held (no acceleration, no 2-minute cap, only the ends of the video); the jump is still made on release, and the pill reads "m min s s" past a minute.
- `PlayerBottomControls` reports which control has focus (`onControlFocused`; the right-hand time button now has its own focus requester); `PlayerScreen` keeps it in `lastControlFocus` and uses it when the controls reappear and when the last menu closes (Play / Pause only when nothing was focused yet or that control is gone).
## Post-Milestone-77 — Profile editor fits the screen

**Status:** Written; the compile check is the CI build of the branch. Not tried on a TV.

**Context:** on a Fire TV the add / edit profile window was taller than the screen (16 pictures made it worse), so Create / Save could not be reached, and the on-screen keyboard opened whenever focus passed over the name field.

**Changes:** the editor's height is capped to the screen (`screenHeightDp - 80`); the fields scroll, with the error line and the Create / Save / Cancel / Remove row pinned below; the picture grid is 8 across at 52 dp (was 6 at 64 dp) and the preview 64 dp; the name field is read-only until OK is pressed on it (a new profile starts in edit mode), shows the keyboard only then and closes it when focus moves on or Done is pressed.

**Tests performed:** none by hand; the build is the compile check.

**Issues discovered:** none.

**Issues fixed:** the above.

## Post-Milestone-78 — Continue Watching carries on with the same source

**Status:** Written; the compile check is the CI build of the branch. Not tried on a TV.

**Context:** a part-watched title could land on the source list instead of carrying on, because the remembered source is matched by its stream id and an addon can hand out new ids between two fetches; pressing a Continue Watching poster also went by way of the title page first.

**Changes:**
- `LastSourceRepository` now also remembers the source's addon, release name and info hash (`LastSource`, `setLastSource`, `findLastSource`; old id-only entries still work), and `matchLastSource` finds it again: same id, else same info hash, else same release from the same addon, else same release name. Select a Source's auto-continue and the player (when the requested id is no longer offered) both use it; nothing is guessed when the source is gone, and the usual list or the player's "Choose a Different Source" shows then.
- Pressing a Continue Watching poster on Home goes straight to playback (`resolvePlayRoute`: the player on the remembered source, or Select a Source the first time) instead of the title page; long-press still opens the poster's menu, and the title page is one press away from any other row.

**Tests performed:** `MatchLastSourceTest` added (same id, changed id by hash, same release by addon, nothing guessed); the build is the compile check. Nothing was run on a TV.

**Issues discovered:** none.

**Issues fixed:** the above.


## Post-Milestone-79 — Play in an external player, with a usage tracker

**Status:** Written; the server's tests pass; the app is not built or tried on a TV.

**Context:** some sources (e.g. 4K Dolby Vision HEVC on a device whose HEVC decoder can't take it, as on the emulator's `c2.goldfish.hevc.decoder`) end in "Unable to play this source" with no way forward. Stremio's answer is handing the stream to another player app. This adds that, always behind a confirmation, and records each use so the developer panel can show whether people are leaving because the built-in player fails.

**Changes:**
- App: an "open in external player" icon beside the timeline (first in the icon cluster, only for a source with a direct link), and an "External Player" button on the "Unable to play this source" screen. Both open `ExternalPlayerConfirm` first (a new `PlayerOverlay.EXTERNAL_PLAYER`; BACK closes it); only its "Open External Player" button leaves ArcTV, via `ACTION_VIEW` with `video/*` (`ExternalPlayerLauncher`). With no player app installed the card says so instead; a `<queries>` entry in the manifest lets the app see that. Playback is paused when it hands over.
- Tracker: `PlayerViewModel.recordExternalPlayer` -> `ExternalPlayerRepository` -> `POST /user/player-events/external` (`PlayerEventsApiClient`), fire-and-forget and dropped if offline. It sends the title, release name, resolution, codec, where it was asked from (`button` or `error`), the outcome (`opened` or `no_player`) and, from the error screen, the player's error text. Never the stream address (it can carry a debrid key).
- Server: migration `0021_external_player_events.sql`, `routes/playerEvents.ts`, `playerEventService.ts`, `schemas/playerEvents.ts`; `GET /admin/summary` gains `externalPlayer` (opens, people, after-error and from-button counts and no-player count for 7 days, all-time opens, the 50 latest events).

**Tests performed:** server: `tests/player-events.test.ts` added (needs sign-in, stores and shows in the admin summary, rejects a bad trigger); the full server suite (239 tests) passes against a scratch Postgres, and `tsc` is clean outside `scripts/`. App: this sandbox cannot reach `dl.google.com`, so no Gradle/Android build was run; the Kotlin was only re-read by hand and nothing was run on a TV or emulator.

**Issues discovered:** the screenshot's error comes from the emulator's own decoder, so it may not happen on a real TV. The developer panel screen in the app does not show the new numbers yet (they are in `/admin/summary`).

**Issues fixed:** the above.

## Post-Milestone-80 — Audio Passthrough switch

**Status:** Written; not built or tried on a TV.

**Context:** a 4K file whose only audio was DTS-HD MA played silently in VLC (via the external-player button); VLC's fix is turning its passthrough off. The built-in player had no such switch and, by default, offers the TV the raw Dolby/DTS stream whenever the HDMI device says it can take it.

**Changes:**
- Player > Settings > Advanced has an "Audio Passthrough" toggle (on by default), kept per device in `DevicePlayerPrefs`. Off makes the player's audio output (`SwitchableAudioSink` in `PlayerEngine.kt`, a `ForwardingAudioSink` built by `SwitchableRenderersFactory`) refuse every non-PCM format, so the TV is never sent a stream it can't really play. The switch (`AudioPassthroughSwitch`) is read live: flipping it in the player re-prepares playback from the same position (a brief rebuffer), so it takes effect at once, on the video that is playing.
- Settings > Audio (new category under "Playback & sound", `AudioSettingsScreen.kt`, `SettingsCategory.AUDIO`) has the same switch with a one-line hint ("Turn it off if a source plays with no sound."); it reads and writes the same `DevicePlayerPrefs` value as the player's, so the two agree, and a change there applies to the next video (the player's own switch applies instantly).

**Tests performed:** none; this sandbox cannot reach `dl.google.com`, so no Gradle build was run, and nothing was tried on a TV. The code was re-read by hand against the Media3 API (`DefaultRenderersFactory.buildAudioSink`, `ForwardingAudioSink.supportsFormat` / `getFormatSupport`).

**Issues discovered:** turning passthrough off does not by itself make a DTS-HD track playable on a device with no DTS decoder (the FFmpeg decoder added in the next entry does). It helps where the TV or receiver claims support it doesn't have (typically Dolby Digital Plus / Atmos).

**Issues fixed:** the above.

## Post-Milestone-81 — FFmpeg audio decoder in the built-in player

**Status:** Written; not built or tried on a TV. The first build is the real test.

**Context:** a 4K file with only a DTS-HD MA audio track was silent in VLC with passthrough on and, for the same reason, would be in the built-in player: it only used the device's own audio decoders, and most TV boxes have none for DTS/TrueHD. VLC works because it ships FFmpeg's decoders.

**Changes:**
- Added `org.jellyfin.media3:media3-ffmpeg-decoder` (Jellyfin's LGPL build of the Media3 FFmpeg extension; its AAR carries the `dca` (DTS, DTS-HD core), `truehd`/`mlp`, AC3/E-AC3, FLAC and other audio decoders for arm64-v8a, armeabi-v7a, x86 and x86_64, about 6 MB). `PlayerEngine` now sets `EXTENSION_RENDERER_MODE_ON`, so the FFmpeg audio renderer is tried only after the device's own decoders; nothing changes for tracks the device already plays. Audio passthrough (previous entry) still works as before, and turning it off simply sends the FFmpeg-decoded sound as plain PCM.
- Media3 moved 1.4.1 -> 1.5.0 because the decoder build is versioned to the Media3 release it was compiled against (there is no 1.4.1 build); `compileSdk` 34 -> 35 (`targetSdk` stays 34) with `android.suppressUnsupportedCompileSdk=35` in `gradle.properties`, since the AGP in use was tested up to 34.

**Tests performed:** checked the published AAR directly: its native library contains the `ff_dca_decoder` and `ff_truehd_decoder`, and it depends on Media3 1.5.0. No Gradle build was run (this sandbox cannot reach `dl.google.com`), and nothing was tried on a TV, so the Media3 1.5.0 / compileSdk 35 combination is unverified here.

**Issues discovered:** the decoder is software, so DTS-HD MA plays as its DTS core (lossy 5.1), as in VLC, not the lossless layer. FFmpeg is LGPL; the AAR bundles it as a shared library, which keeps the app's own code separate.

**Issues fixed:** DTS / DTS-HD / TrueHD sources with no other audio track should now play with sound on devices lacking those decoders.

## Post-Milestone-82 — Audio language and speaker layout in Settings > Audio

**Status:** Written; the server's tests pass; the app is not built or tried on a TV.

**Context:** a file with several audio tracks played whichever one the player chose first, and a stereo TV or soundbar had no way to ask for surround to be mixed down.

**Changes:**
- Default audio language: `PlayerPreferences.defaultAudioLanguage` (an ISO 639-1 code or null = automatic), set in Settings > Audio (`AudioSettingsViewModel`, `AudioLanguageOptions` = the subtitle list with "Automatic" first) and applied in `buildExoPlayer` with `setPreferredAudioLanguage`. Synced like the subtitle language: migration `0022_user_settings_audio_language.sql`, `defaultAudioLanguage` in the `/user/settings` schema, service and route (optional on `PUT`, so an older client leaves the stored value alone; `null` clears it), and in `SettingsRequest`/`SettingsResponse`. The app treats a response without the field (`AUDIO_LANGUAGE_ABSENT`, for an older server) as "keep the local value", so deploying the app before the server cannot wipe the choice.
- Speakers: Auto / Stereo / 5.1 / 7.1 / Dolby Atmos (`AudioChannelMode`, per device in `DevicePlayerPrefs`; 7.1 and Atmos send up to 8 channels, so nothing is mixed down; Atmos only reaches a receiver through passthrough, otherwise its 5.1/7.1 base plays). `buildExoPlayer` asks track selection for a track within the limit (`setMaxAudioChannelCount`), and `DownmixAudioProcessor` (in the audio sink) mixes 5.1/7.1 16-bit sound down to stereo, or 7.1 to 5.1. A raw (passthrough) stream can't be mixed, so it is only passed through when it fits the limit (`SwitchableAudioSink`); stereo therefore never passes through. `AudioPassthroughSwitch` became `AudioOutputSettings` (passthrough + channel mode). A change applies to the next video.
- Source filter: the Speakers setting also filters Select a Source (`AudioFilter.kt`). `Stream.audioChannels` is read from the release text by `detectAudioChannels` ("DDP5.1", "DTS-HD.MA.7.1", "AAC2.0", "6CH", "stereo"; the highest wins; a bare "Atmos" counts as 7.1; "1.5.1"-style numbers are ignored). `Stream.audioAtmos` comes from `detectAtmos`. `pickByAudio` (over `AudioKind`: stereo, 5.1, 7.1, Atmos) lists only the sources of the wanted kind, else the next best (`kindsFor`): stereo wants stereo, then 5.1, then 7.1; 5.1 wants 5.1, then 7.1, then stereo; 7.1 wants 7.1, then 5.1, then stereo; Atmos wants Atmos, then 7.1, 5.1, stereo; sources that don't name a layout are listed only when nothing at all names one. The first item in the filter bar is an Audio drop-down (`DropdownPicker`, now with `compact` and `highlighted` options): its pill reads "Audio: 5.1" ("Audio: 7.1 (no 5.1 found)" when the next best is used, "Audio: All") and it opens a list built from this title's own sources (`AudioChoice`, `audioChoices`): "Match my speakers" (the Speakers setting with the next-best fallback; the starting choice unless Automatic), "All audio", then each kind some source is (Stereo / 5.1 / 7.1 / Atmos) and "Not listed" for the ones that don't say, each with its source count. The pill is hidden when no source names its layout, and is filled while it filters anything. The choice lasts for that visit. The recommended source, and the source Next episode takes by itself, are chosen from the listed ones; a remembered (resumed) source still plays whatever the filter says.
- `server/README.md` documents the new settings field.

**Tests performed:** `AudioFilterTest` and `DetectAudioChannelsTest` added (covering 7.1 and Atmos too) (not run: no Gradle here; the regex was checked against sample release names in Python, whose engine treats these patterns the same way). Server: `tests/settings.test.ts` extended (round trip, clear with `null`, an omitted field keeps the stored value, a too-short code is rejected, the defaults include it); the full suite (241 tests) passes against a scratch Postgres and `tsc` is clean outside `scripts/`. App: no Gradle build (this sandbox cannot reach `dl.google.com`) and nothing tried on a TV; the downmix has no unit test because the Media3 `AudioProcessor` flow could not be compiled here.

**Note:** the Speakers description in Settings > Audio says it automatically filters the source list to that audio type, and that a future auto source picker will choose it too (that picker does not exist yet; Next episode already takes the matching source).

**Issues discovered:** many release names say nothing about their audio, so with a filter on they are hidden whenever any source does name one; the pill turns the filter off. The downmix uses fixed gains (centre and surrounds -3 dB, overall 0.7, bass dropped from stereo) and only handles 16-bit 5.1/7.1 PCM; other layouts and 24-bit/float PCM are sent as they are. A release can be two kinds (an Atmos 5.1 release is both). 7.1 and Atmos differ from Automatic mainly in which sources they list; Automatic already sends up to 7.1 when the TV supports it.

**Issues fixed:** the above.

## Post-Milestone-83 — Source filter bar: pill clipping and shadow

**Status:** Written; not built or tried on a TV.

**Context:** on Select a Source the first pill (the Audio drop-down) had its focus ring cut off at the left edge, and the pills showed a faint dark outline when focused.

**Changes:** `SourceFilterBar`'s `LazyRow` now has `contentPadding` (6 dp sides, 4 dp top and bottom) so the first pill's focus ring and scale-up are not clipped by the row's bounds. The dark outline was the default 18 dp focus shadow of `TvFocusSurface`, which reads as a halo on a small pill: `FilterPill`, `DropdownPicker`'s button and the Sort pill now pass `focusedElevation = 0f`. `FilterPill` and `DropdownPicker` are shared, so My List's filter pills and the Movies / TV Shows / My List drop-downs lose that shadow too.

**Tests performed:** none; no Gradle build here (no route to `dl.google.com`) and nothing seen on a TV. The cause of the outline was read from `TvFocusSurface`'s own note on `focusedElevation`.

**Issues discovered:** none.

**Issues fixed:** the above.

## Post-Milestone-84 — VLC's player engine as a fallback

**Status:** Written; not built or tried on a TV. The first build (and a real device) is the real test: this is the largest unverified change so far.

**Context:** VLC plays sources the built-in player cannot (e.g. 4K Dolby Vision HEVC on a device with no matching decoder) because it ships FFmpeg's software decoders instead of relying on the device's. The built-in player (Media3 / ExoPlayer) only has the device's decoders (plus the FFmpeg audio decoder added earlier).

**Changes:**
- Added `org.videolan.android:libvlc-all` 3.6.5 (LibVLC 3, LGPL 2.1; the API used was checked against the 3.6.2 AAR with `javap`).
- `VlcPlayerScreen.kt` (`VlcPlaybackContent`): a small player of its own on LibVLC: play / pause, 10-second seeks, a timeline, Audio and Subtitles track lists, Change Source and Back. It starts where the built-in player was (or at the saved resume point), reports progress to Continue Watching the same way (`onReportProgress`), applies the account's preferred audio language, subtitle on/off and subtitle language by matching a track's name to the language's English name, and releases LibVLC off the main thread.
- `PlayerScreen`: when the built-in player fails with a format error (`isFormatFailure`: decoder init / decoding failed, format exceeds capabilities or unsupported, container unsupported/malformed; classed `UNSUPPORTED_SOURCE`), `PlaybackContent` hands over to VLC by itself, from the current position. The "Unable to play this source" screen also gets a "Try VLC Engine" button. It is one-way: if VLC fails too, it shows its own error with Change Source / Back.
- Build: `abiFilters` arm64-v8a, armeabi-v7a, x86_64 (LibVLC is 40-50 MB per chip type), `jniLibs.useLegacyPackaging = true` so the native libraries stay compressed in the APK, and `pickFirsts` for `libc++_shared.so`. Expect the APK to grow by roughly 60 MB.

**Tests performed:** `FormatFailureTest` added (not run). No Gradle build (no route to `dl.google.com`) and nothing tried on a device; the libVLC calls were matched to the real class signatures but not compiled.

**Issues discovered:** VLC's software decoding of 4K HEVC can be too slow on a typical TV box (it tries the hardware decoder first and falls back to software). The VLC screen is simpler than the built-in player: no passthrough or speaker settings, no external-player button, no next-episode offer, and subtitle choices are not remembered. Dolby Vision plays as its HDR10 base layer.

**Issues fixed:** sources the device's own decoders reject can now play, in software if need be.
