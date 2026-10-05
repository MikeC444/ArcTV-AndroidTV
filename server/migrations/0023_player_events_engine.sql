-- "Play in another player" now offers VLC's own engine (inside the app) as well as another app on the device, so each event says which one
-- was chosen. Older rows were all another app.
ALTER TABLE external_player_events
    ADD COLUMN engine text NOT NULL DEFAULT 'external' CHECK (engine IN ('external', 'vlc'));
