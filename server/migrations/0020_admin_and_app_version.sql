-- Developer panel: who may use /admin/* (set by hand: UPDATE users SET is_admin = true WHERE email = '…'), never by any API.
ALTER TABLE users ADD COLUMN is_admin boolean NOT NULL DEFAULT false;

-- devices.app_version and last_seen_at already exist; requireAuth now keeps them current from the app's X-ArcTV-App-Version header,
-- so the panel shows the version a device is on right now and changes as soon as the person updates.
CREATE INDEX devices_last_seen_idx ON devices (last_seen_at DESC) WHERE revoked_at IS NULL;
