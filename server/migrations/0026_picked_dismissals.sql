-- Titles a person removed from their "Picked for you" row, per account and profile, so the removal follows them to every device.
-- A removal hides the title for a few days (the client decides how long) and is NOT taste feedback: it never touches movie_feedback.
-- One row per (user, profile, title): removing the same title again later just moves dismissed_at forward (last-write-wins on the client's
-- own timestamp, like movie_feedback). Old rows are tidied up opportunistically when a new removal is saved.
CREATE TABLE picked_dismissals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    profile_id text NOT NULL DEFAULT 'main',
    content_id text NOT NULL,
    dismissed_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT picked_dismissals_slot_key UNIQUE (user_id, profile_id, content_id)
);

CREATE INDEX picked_dismissals_recent_idx ON picked_dismissals (user_id, profile_id, dismissed_at);
