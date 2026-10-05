-- One row each time someone confirms "Play in external player" in the app's player. Lets the developer panel show how often people
-- leave for another player, and how often that follows a playback error (which would point at the built-in player, not the person).
-- Never stores the stream address (it can carry a debrid key); only what the title and source looked like.
CREATE TABLE external_player_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id uuid REFERENCES devices (id) ON DELETE SET NULL,
    app_version text,
    provider_id text NOT NULL,
    content_id text NOT NULL,
    content_type content_type NOT NULL,
    title text NOT NULL,
    release_title text,
    resolution text,
    codec text,
    -- 'button' = the icon beside the timeline; 'error' = the button on the "Unable to play this source" screen.
    launched_from text NOT NULL CHECK (launched_from IN ('button', 'error')),
    -- 'opened' = an external player took it; 'no_player' = none is installed.
    outcome text NOT NULL CHECK (outcome IN ('opened', 'no_player')),
    -- The built-in player's error text when it was playing nothing (trigger 'error'), trimmed.
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX external_player_events_created_idx ON external_player_events (created_at DESC);
CREATE INDEX external_player_events_user_idx ON external_player_events (user_id, created_at DESC);
