-- A refresh token is replaced on every use. If the reply carrying the new one never reaches the app (a dropped connection, a restart of the web
-- server, an app closed mid-request), the app still holds the old one and the next refresh would sign the person out. The token just replaced is
-- kept for a couple of minutes so that one retry with it is still answered.
ALTER TABLE sessions
    ADD COLUMN prev_refresh_token_hash text,
    ADD COLUMN prev_refresh_expires_at timestamptz;

CREATE INDEX sessions_prev_refresh_token_hash_idx ON sessions (prev_refresh_token_hash) WHERE prev_refresh_token_hash IS NOT NULL;
