-- Crash reports the apps send the next time they open after closing unexpectedly: an uncaught error, a native crash, a freeze (ANR) or the
-- system closing the app for low memory. The account is kept when the app was signed in (null for a guest). Kept 90 days.
CREATE TABLE crash_reports (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid REFERENCES users(id) ON DELETE CASCADE,
    platform text NOT NULL,
    app_version text NOT NULL,
    device_model text,
    os_version text,
    kind text NOT NULL,
    exception text,
    stack_trace text NOT NULL,
    signature text NOT NULL,
    happened_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX crash_reports_created_idx ON crash_reports (created_at DESC);
CREATE INDEX crash_reports_signature_idx ON crash_reports (signature, created_at DESC);
