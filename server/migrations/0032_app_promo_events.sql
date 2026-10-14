-- One row each time a signed-in person on the web app is offered a desktop app (the "Arc TV is on your Mac / PC" popup), and what they did with it:
-- 'shown' = the popup appeared; 'download' = they pressed Get the app (the download starts straight away); 'closed' = they dismissed it without.
-- Lets the developer panel show how many people the popup reaches and how many take it. The web app cannot know whether the download finished or
-- the app was ever run. Stores no browser details, title or address: only which app and what they did.
CREATE TABLE app_promo_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    app text NOT NULL CHECK (app IN ('mac', 'windows')),
    kind text NOT NULL CHECK (kind IN ('shown', 'download', 'closed')),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX app_promo_events_created_idx ON app_promo_events (created_at DESC);
CREATE INDEX app_promo_events_user_idx ON app_promo_events (user_id, created_at DESC);
