-- Genres the person never wants to see (Settings > Blocked Genres), kept on
-- the account so every device they sign in on agrees. A plain jsonb array
-- of genre names alongside the other settings: it follows the same
-- last-write-wins rule as the rest of the row (user_settings.updated_at).
-- Not NULL with an empty default so every existing row means "nothing
-- blocked" rather than unknown.
ALTER TABLE user_settings ADD COLUMN blocked_genres jsonb NOT NULL DEFAULT '[]'::jsonb;
