-- ArcTV Plus entitlement: one row per account that has ever paid, written only by the Stripe webhook
-- (services/plusService.ts) and read by GET /user/plus. A subscription is "active" until valid_until; the one-time
-- Lifetime plan has no end date. last_event_at is the Stripe event time of the last write, so an event that
-- arrives late (Stripe retries and does not guarantee order) can never undo a newer one.
CREATE TABLE user_plus (
    user_id uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    plan text NOT NULL CHECK (plan IN ('monthly', 'yearly', 'lifetime')),
    status text NOT NULL CHECK (status IN ('active', 'canceled')),
    valid_until timestamptz,
    stripe_customer_id text,
    stripe_subscription_id text,
    last_event_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX user_plus_customer_idx ON user_plus (stripe_customer_id) WHERE stripe_customer_id IS NOT NULL;
CREATE INDEX user_plus_subscription_idx ON user_plus (stripe_subscription_id) WHERE stripe_subscription_id IS NOT NULL;
