-- ArcTV Plus profiles: up to 5 per account, each with its own library (My List, Continue Watching, watch history,
-- settings, addons, Like / Not for me). See docs/PROFILES.md in the web repo (MangotvWebb) for the contract.
--
-- Only the profile list is new here. Every library table gets a profile_id, defaulting to 'main', the account's own
-- profile: every row that exists today therefore belongs to 'main' and nothing has to be moved. movie_feedback already
-- has it (migration 0016).
--
-- 'main' is not required to have a row in profiles to be used: rows are created lazily (the first time an account's
-- profiles are listed) and library rows carry no foreign key to profiles, so an account that never uses profiles costs
-- nothing and the application deletes a profile's library rows itself when the profile is removed (profileService).

CREATE TABLE profiles (
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 'main' for the account's own profile, 'p_' + random hex for the others. Stable, never reused for another profile.
    id text NOT NULL,
    name text NOT NULL,
    -- One of the avatar ids the apps know (validated by the API, not here, so adding one needs no migration).
    avatar text NOT NULL,
    kind text NOT NULL DEFAULT 'adult',
    -- argon2id of the 4-digit PIN; null = not locked. Never returned by the API.
    pin_hash text,
    -- Consecutive wrong PINs, and the time until which guessing is refused (4 digits is only 10,000 tries).
    pin_failed_attempts integer NOT NULL DEFAULT 0,
    pin_locked_until timestamptz,
    is_default boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, id),
    CONSTRAINT profiles_name_length CHECK (char_length(name) BETWEEN 1 AND 24),
    CONSTRAINT profiles_kind_valid CHECK (kind IN ('adult', 'kids')),
    -- The account's own profile is 'main', is an adult profile, and is the only one that is.
    CONSTRAINT profiles_default_is_main CHECK (is_default = (id = 'main')),
    CONSTRAINT profiles_default_is_adult CHECK (NOT is_default OR kind = 'adult'),
    CONSTRAINT profiles_pin_attempts_non_negative CHECK (pin_failed_attempts >= 0)
);

-- ── Per-profile libraries ──────────────────────────────────────────────────────────────────────────────────────────

-- user_settings was one row per account (primary key user_id): now one row per (account, profile).
ALTER TABLE user_settings ADD COLUMN profile_id text NOT NULL DEFAULT 'main';
ALTER TABLE user_settings DROP CONSTRAINT user_settings_pkey;
ALTER TABLE user_settings ADD PRIMARY KEY (user_id, profile_id);

ALTER TABLE user_addons ADD COLUMN profile_id text NOT NULL DEFAULT 'main';
ALTER TABLE user_addons DROP CONSTRAINT user_addons_user_manifest_key;
ALTER TABLE user_addons ADD CONSTRAINT user_addons_user_manifest_key UNIQUE (user_id, profile_id, manifest_url);

ALTER TABLE watchlist_items ADD COLUMN profile_id text NOT NULL DEFAULT 'main';
ALTER TABLE watchlist_items DROP CONSTRAINT watchlist_items_user_content_key;
ALTER TABLE watchlist_items ADD CONSTRAINT watchlist_items_user_content_key UNIQUE (user_id, profile_id, provider_id, content_id, content_type);
DROP INDEX watchlist_items_active_idx;
CREATE INDEX watchlist_items_active_idx ON watchlist_items (user_id, profile_id, updated_at) WHERE deleted_at IS NULL;

ALTER TABLE watch_history ADD COLUMN profile_id text NOT NULL DEFAULT 'main';
ALTER TABLE watch_history DROP CONSTRAINT watch_history_user_episode_key;
ALTER TABLE watch_history ADD CONSTRAINT watch_history_user_episode_key UNIQUE (user_id, profile_id, provider_id, content_id, content_type, episode_key);
DROP INDEX watch_history_user_watched_at_idx;
CREATE INDEX watch_history_user_watched_at_idx ON watch_history (user_id, profile_id, watched_at DESC);

ALTER TABLE continue_watching ADD COLUMN profile_id text NOT NULL DEFAULT 'main';
ALTER TABLE continue_watching DROP CONSTRAINT continue_watching_user_content_key;
ALTER TABLE continue_watching ADD CONSTRAINT continue_watching_user_content_key UNIQUE (user_id, profile_id, provider_id, content_id, content_type);
DROP INDEX continue_watching_active_idx;
CREATE INDEX continue_watching_active_idx ON continue_watching (user_id, profile_id, last_watched_at DESC) WHERE deleted_at IS NULL;
