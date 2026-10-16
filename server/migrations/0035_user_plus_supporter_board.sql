-- The supporters board on the landing page: Lifetime members who have chosen to be listed, by display name. It is opt-in: supporter_board is false
-- until the member switches it on in Settings (and can be switched off again), and a purchase never sets it. The list shows nothing but the display name.
ALTER TABLE user_plus ADD COLUMN supporter_board boolean NOT NULL DEFAULT false;
ALTER TABLE user_plus ADD COLUMN supporter_since timestamptz;
