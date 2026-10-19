-- A reset token is either emailed ('email', "Forgot password") or made for a signed-in TV to show as a QR code ('tv'). Only emailed ones count
-- towards the limit of reset emails, so opening a TV's Change password a few times can never use up an account's reset emails.
ALTER TABLE password_reset_tokens ADD COLUMN source text NOT NULL DEFAULT 'email';
