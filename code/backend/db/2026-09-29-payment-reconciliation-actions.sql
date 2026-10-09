ALTER TABLE payment_reconciliation_issue
    ADD COLUMN resolved_at_utc DATETIME(6) NULL,
    ADD COLUMN resolved_by BIGINT NULL;

CREATE TABLE payment_reconciliation_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    result VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    error_code VARCHAR(80) NULL,
    occurred_at_utc DATETIME(6) NOT NULL,
    pg_snapshot_json LONGTEXT NULL,
    before_order_status VARCHAR(30) NULL,
    before_payment_status VARCHAR(30) NULL,
    after_order_status VARCHAR(30) NULL,
    after_payment_status VARCHAR(30) NULL,
    sold_before INT NULL,
    sold_after INT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_recon_action_issue FOREIGN KEY (issue_id) REFERENCES payment_reconciliation_issue (id)
);
