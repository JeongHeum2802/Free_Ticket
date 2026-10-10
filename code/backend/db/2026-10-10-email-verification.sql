ALTER TABLE users ADD COLUMN email_verified_at datetime(6) NULL;

CREATE TABLE email_verifications (
    id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE,
    token_hash varchar(64) NOT NULL,
    code_hash varchar(100) NOT NULL,
    expires_at datetime(6) NOT NULL,
    sent_at datetime(6) NOT NULL,
    window_started_at datetime(6) NOT NULL,
    send_count int NOT NULL,
    failed_attempts int NOT NULL,
    verified_at datetime(6) NULL,
    consumed_at datetime(6) NULL
);
