CREATE TABLE payment_reconciliation_issue (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id VARCHAR(14) NOT NULL,
    transaction_key VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    pg_payment_key VARCHAR(200) NOT NULL,
    issue_type VARCHAR(40) NOT NULL,
    pg_status VARCHAR(30) NOT NULL,
    db_order_status VARCHAR(30) NULL,
    db_payment_status VARCHAR(30) NULL,
    transaction_at_utc DATETIME(6) NOT NULL,
    detected_at_utc DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_recon_transaction (merchant_id, transaction_key),
    KEY idx_payment_recon_detected_at (detected_at_utc)
);

CREATE TABLE payment_reconciliation_progress (
    id TINYINT NOT NULL,
    coverage_start_utc DATETIME(6) NOT NULL,
    completed_through_utc DATETIME(6) NOT NULL,
    updated_at_utc DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);
