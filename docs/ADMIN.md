# Developer panel

A read-only view of every account for the developer: who is signed up, their plan, devices and **which app version each device is on**,
their addons, Continue Watching and recent history. It lives in the web app at **web.arctv.org/admin** (Settings → Account → Developer panel),
and reads `GET /admin/summary`, `GET /admin/users?q=&plan=&device=&addons=&watching=&seen=&limit=&offset=` (filters: plan `free|monthly|yearly|lifetime`; device `<platform>|<app version>`; addons / watching `with|none`; seen `1h|24h|7d|30d|older|never`; the total follows the filter) and `GET /admin/users/:id` on this backend. `GET /admin/feature-intros/torrent_intro?limit=&offset=` lists who has clicked the "Addons now support torrents" pop-up away (newest first, with when, app version and platform, plus the total). The data is stored by `POST /user/feature-intros/ack` (migration 0024).

## Who can open it

Only an account with `users.is_admin = true`. Nothing in the API can set that; do it once in the database (Neon SQL editor):

```sql
UPDATE users SET is_admin = true WHERE email = 'you@example.com';
```

Everyone else gets the same 404 as an unknown path. Sign out and in (or reload) after changing it. Each user detail view is logged
(`[admin] <you> viewed <user>`).

## What it never shows

Password hashes, session tokens, PIN hashes, or the full address of an addon (those often carry a person's debrid key): an addon shows its host and,
when the address names one, the debrid service (`realdebrid`, `torbox` …) only.

## App versions

The Fire TV app sends `X-ArcTV-App-Version: <versionName>` on every account request and the web server sends `web`. `requireAuth` writes it to
`devices.app_version` (and `last_seen_at`) whenever it changes or the last write is over five minutes old, so a device that updates shows the new
version on its first request afterwards, and the panel (which refreshes every 30 s) follows. Devices on builds that predate the header show
`unknown` until the person updates to a release that sends it (the first one after this change).

Versions are compared as dotted numbers; anything older than the newest seen is marked in amber.
