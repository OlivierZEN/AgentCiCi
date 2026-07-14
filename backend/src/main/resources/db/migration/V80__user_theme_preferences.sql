ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS theme_code VARCHAR(32) NOT NULL DEFAULT 'gilded';

ALTER TABLE platform_account
    ADD COLUMN IF NOT EXISTS theme_code VARCHAR(32) NOT NULL DEFAULT 'gilded';
