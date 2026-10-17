-- One row per (account, profile) once the server has dealt with giving that profile the Streaming Catalogs addon (see addonService.seedStreamingCatalogsAddon):
-- either it was added, or the profile already had it (or had had it and removed it). It is written once, so a person who later removes the
-- addon on purpose is not given it back, on any device.
CREATE TABLE streaming_catalogs_addon_marks (
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    profile_id text NOT NULL DEFAULT 'main',
    seeded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, profile_id)
);
