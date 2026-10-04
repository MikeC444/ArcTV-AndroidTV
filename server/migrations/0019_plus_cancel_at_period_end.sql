-- A subscription the person has cancelled keeps Plus until its paid period ends. Stripe stops renewing it (cancel_at_period_end);
-- we keep that flag so the apps can say "Plus ends on <date>" instead of offering to cancel again. Always false for Lifetime.
ALTER TABLE user_plus ADD COLUMN cancel_at_period_end boolean NOT NULL DEFAULT false;
