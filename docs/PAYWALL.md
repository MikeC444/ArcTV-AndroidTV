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
