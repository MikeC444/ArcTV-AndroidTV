# ArcTV Plus paywall

How Plus is sold and checked. The apps never decide who has Plus: they ask the backend (`GET /user/plus`), and the
backend only switches Plus on from a signed Stripe webhook.

## States

| `PLUS_PAYWALL` | Who has Plus | What the apps show |
|---|---|---|
| `off` (default) | everyone (`plan: "early_access"`) | Plus features on; the Plus tab says "Early access" |
| `on` | accounts with an active subscription, or the Lifetime plan | Plus features only for those accounts; everyone else sees the Plus tab with the plans |

Switching the paywall on is one environment variable on the backend host. Nothing in the apps needs a new release for it.

## One-time Stripe setup

1. In Stripe, create a **Product** "ArcTV Plus" with three **Prices**: monthly (recurring), yearly (recurring) and
   lifetime (one-time). Use test mode first.
2. **Developers → Webhooks → Add endpoint**: URL `<API_BASE_URL>/stripe/webhook`, events
   `checkout.session.completed`, `customer.subscription.updated`, `customer.subscription.deleted`. Copy its
   **signing secret**.
3. On the backend host set `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_MONTHLY`,
   `STRIPE_PRICE_YEARLY`, `STRIPE_PRICE_LIFETIME`, then `PLUS_PAYWALL=on`.
4. Test with Stripe's test card `4242 4242 4242 4242`: pay for a plan, watch `GET /user/plus` flip to `active`.
5. When happy, repeat with live keys and live prices.

Optional but recommended: turn on Stripe's **Customer portal** so people can cancel or change plan themselves (the apps do
not build that screen; link to it from Stripe).

## How it works

- `POST /user/plus/checkout` creates a Stripe Checkout Session with the account id as `client_reference_id` and the plan
  as metadata, and returns its URL. The web app opens it; the TV shows it as a QR code.
- `GET /user/plus` also carries `prices`: the list price of each plan (`{ monthly, yearly, lifetime }`, each `{ amount, currency }`
  with the amount in pence/cents), read from Stripe's `GET /v1/prices/{id}` for the configured `STRIPE_PRICE_*` ids and kept in
  memory for six hours (a failed refresh serves the last good answer). It is `null` with the paywall off, when Stripe isn't
  configured, or when a price can't be read, and never makes the request fail. Promo codes can lower what checkout charges, so
  these are list prices.
- **The supporters board** (opt-in): a Lifetime member can choose to have their display name on a public list shown on the landing page.
  The choice is asked on the Lifetime payment page: a required drop-down ("Put my name on the supporters board?", Stripe `custom_fields` key
  `supporters_board`, "No thanks" listed first, nothing pre-selected). The webhook reads the answer from the completed session's `custom_fields`:
  only a **Yes** puts the buyer on the board; No, no answer or an older session does not. (If Stripe ever refuses the question the Lifetime
  checkout is created without it, so a purchase is never blocked; the board is then chosen later in Settings.) A member can change their mind any
  time: `GET /user/plus` carries `supporterBoard` (`true`/`false` for an active Lifetime member, `null` for everyone else) and
  `PUT /user/plus/supporter { show }` switches it (409 for anyone who is not an active Lifetime member). A Stripe retry of the purchase never undoes a
  later choice. `GET /supporters` (public, no sign-in, cached 5 minutes) lists `{ supporters: [{ name, avatar, since }], total }`:
  the display name (cut at 24 characters, "Anonymous supporter" when the account has none), the picture id of the account's own profile (or null) and
  the month they joined ("2026-10"), the earliest to join first. Never an email or an id.
- Stripe calls `POST /stripe/webhook`. The signature is checked against the raw body (5 minute tolerance); anything else
  is refused. `checkout.session.completed` switches Plus on (a Lifetime payment only once `payment_status` is `paid`);
  `customer.subscription.updated` moves the paid period; `customer.subscription.deleted` ends it.
- Events are applied by their own timestamp, so a late or repeated delivery can't undo a newer one. A Lifetime owner is
  never changed by a subscription event.
- `POST /user/plus/cancel` lets a monthly or yearly subscriber stop renewal: it sets Stripe's `cancel_at_period_end` on their
  subscription (no refund; Plus stays on until the paid period ends) and answers with the new entitlement, whose
  `cancelAtPeriodEnd` is then `true`. Lifetime is refused (409), an account with no subscription gets 404, and cancelling
  twice is harmless. Stripe's `customer.subscription.updated` event keeps the flag right if it is changed in Stripe instead.
- The table is `user_plus` (migrations `0017`, `0019`). It is written by the webhook, and by the cancel call above.

## Refunds

A refund does not switch Plus off by itself. Cancel the subscription in Stripe (that sends `customer.subscription.deleted`)
or remove the row from `user_plus`.
