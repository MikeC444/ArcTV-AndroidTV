-- One row per (account, profile) once the server has made sure that profile has something to browse (see addonService.seedDefaultAddon): either
-- it already had an addon that offers catalogues, or Cinemeta was put on it. It is written once, so a person who later removes Cinemeta on
-- purpose is not given it back.
CREATE TABLE addon_seed_marks (
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    profile_id text NOT NULL DEFAULT 'main',
    seeded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, profile_id)
);
