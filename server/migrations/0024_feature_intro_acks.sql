-- One row per user per one-off "what's new" pop-up they have clicked away in the app (so far only 'torrent_intro': "Addons now support torrents").
-- The developer panel reads it to show who has seen and dismissed it. The first click is kept (re-reports change nothing).
CREATE TABLE feature_intro_acks (
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    feature text NOT NULL CHECK (feature IN ('torrent_intro')),
    device_id uuid REFERENCES devices (id) ON DELETE SET NULL,
    app_version text,
    acknowledged_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, feature)
);

CREATE INDEX feature_intro_acks_feature_idx ON feature_intro_acks (feature, acknowledged_at DESC);
