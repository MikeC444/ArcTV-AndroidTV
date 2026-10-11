-- One row each time a viewing ends in the app's player: how it went, not what was watched. Lets the developer panel show how often people
-- leave within seconds (or hit an error) and whether that clusters on one device model, player or picture quality, which would point at
-- playback rather than taste. Never stores a title, a content id or the stream address (it can carry a debrid key).
CREATE TABLE playback_outcomes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id uuid REFERENCES devices (id) ON DELETE SET NULL,
    app_version text,
    -- The device's own model name (Build.MODEL), e.g. "AFTMM" for a Fire TV Stick 4K.
    device_model text,
    -- Which player: VLC's engine or the built-in (Media3) one.
    engine text NOT NULL CHECK (engine IN ('vlc', 'builtin')),
    -- What the source looked like (the same short labels the source list shows), if known.
    resolution text,
    codec text,
    hdr text,
    -- Milliseconds from pressing Play until the first picture; NULL when nothing ever played.
    start_ms integer,
    -- How long it actually played before the person left or it failed.
    watched_ms integer NOT NULL DEFAULT 0,
    -- 'ok' = watched for a while (or to the end); 'short' = left soon after the picture appeared; 'error' = the player reported a failure;
    -- 'never_started' = left before any picture appeared, with no error.
    outcome text NOT NULL CHECK (outcome IN ('ok', 'short', 'error', 'never_started')),
    error_kind text,
    -- How many sources the person had opened for this title in this sitting (1 = this was the first one).
    sources_tried smallint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX playback_outcomes_created_idx ON playback_outcomes (created_at DESC);
CREATE INDEX playback_outcomes_user_idx ON playback_outcomes (user_id, created_at DESC);
