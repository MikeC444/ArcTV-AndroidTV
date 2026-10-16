-- True when a history row was written by hand ("mark as watched" on an episode or season) rather than by actually playing it. Your stats
-- counts only what was really played; the row still gives the episode its watched tick. Rows from before this existed read as false.
ALTER TABLE watch_history ADD COLUMN marked_manually boolean NOT NULL DEFAULT false;
