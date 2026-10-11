-- A viewing can now end before any player opened (the source could not be found again, details failed to load): those are reported with
-- the engine 'none' so the developer panel can count them next to the viewings that did start.
ALTER TABLE playback_outcomes DROP CONSTRAINT playback_outcomes_engine_check;
ALTER TABLE playback_outcomes ADD CONSTRAINT playback_outcomes_engine_check CHECK (engine IN ('vlc', 'builtin', 'none'));
