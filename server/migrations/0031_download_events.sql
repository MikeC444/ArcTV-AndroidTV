-- One row each time someone on the web app uses Download: opens the Downloads panel, presses a file in it, or (without Plus) is shown the Plus
-- popup instead. Lets the developer panel show how many people try it and how many actually start a download. A web download is a plain
-- browser link, so a pressed file is the last thing the app can know: it never learns whether the browser finished it. Never stores a title,
-- a content id, a release name or the file address (it can carry a debrid key).
CREATE TABLE download_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 'popup' = no Plus, shown the Plus popup; 'opened' = the Downloads panel opened; 'file' = a file in the panel was pressed.
    kind text NOT NULL CHECK (kind IN ('popup', 'opened', 'file')),
    content_type text NOT NULL CHECK (content_type IN ('MOVIE', 'TV_SHOW')),
    -- True when the download was for one episode of a show.
    episode boolean NOT NULL DEFAULT false,
    -- Only for kind = 'file': the source's short quality label (4K, 1080p...) and whether the debrid service already had it cached.
    resolution text,
    cached boolean,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX download_events_created_idx ON download_events (created_at DESC);
CREATE INDEX download_events_user_idx ON download_events (user_id, created_at DESC);
