-- Explicit taste feedback (Like / Not for me) per account and profile,
-- the input to the "Picked for you" recommendations. One row per
-- (user, profile, addon, title) slot, kept and toggled via deleted_at
-- rather than deleted-and-reinserted, exactly like watchlist_items (see
-- migrations/0009): clearing and re-giving feedback on the same movie is
-- an update to one durable slot, and a later device's pull can tell
-- "cleared" from "never existed".
--
-- profile_id is a free-form text label ("main" until profiles exist) so
-- the same table can serve per-profile feedback later without a change.
-- updated_at is the client's own local timestamp (last-write-wins).
CREATE TABLE movie_feedback (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    profile_id text NOT NULL DEFAULT 'main',
    provider_id text NOT NULL,
    content_id text NOT NULL,
    content_type content_type NOT NULL,
    title text NOT NULL,
    feedback text NOT NULL CHECK (feedback IN ('like', 'dislike')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CONSTRAINT movie_feedback_slot_key UNIQUE (user_id, profile_id, provider_id, content_id, content_type)
);

CREATE INDEX movie_feedback_active_idx ON movie_feedback (user_id, profile_id) WHERE deleted_at IS NULL;
