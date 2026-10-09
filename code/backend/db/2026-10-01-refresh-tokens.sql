CREATE TABLE refresh_tokens (
    id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);
