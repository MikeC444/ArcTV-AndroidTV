-- The person's preferred audio language (an ISO 639-1 code such as "en"; null = no preference, take the file's own default),
-- next to the subtitle language 0013 added. Opaque text for the same reason: the app owns its language list.
ALTER TABLE user_settings ADD COLUMN default_audio_language text;
