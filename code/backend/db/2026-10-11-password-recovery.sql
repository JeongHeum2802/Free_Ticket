ALTER TABLE email_verifications
    ADD COLUMN purpose varchar(32) NOT NULL DEFAULT 'EMAIL_VERIFICATION';
