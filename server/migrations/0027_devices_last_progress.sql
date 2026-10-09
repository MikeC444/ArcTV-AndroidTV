-- When a device last saved playback progress, by the SERVER's clock. The apps save progress about every 15 seconds while something plays,
-- so "a device saved progress in the last minute" means "someone is watching right now" for the developer panel. The progress rows
-- themselves carry the device's own clock, which can be wrong, so they are not used for this.
ALTER TABLE devices ADD COLUMN last_progress_at timestamptz;

CREATE INDEX devices_last_progress_idx ON devices (last_progress_at) WHERE last_progress_at IS NOT NULL;
