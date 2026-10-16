-- When the person last pressed "Reset stats" for this profile: Your stats then counts only what was watched from this moment on.
-- Nothing is deleted (the watch history, Continue Watching and the watched ticks are untouched); null means "count everything".
ALTER TABLE user_settings ADD COLUMN stats_reset_at timestamptz;
