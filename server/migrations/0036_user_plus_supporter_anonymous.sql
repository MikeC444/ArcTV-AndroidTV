-- A supporter on the board can be listed as "Anonymous" (no name, no picture) instead of by display name. Only meaningful while supporter_board is true.
ALTER TABLE user_plus ADD COLUMN supporter_anonymous boolean NOT NULL DEFAULT false;
