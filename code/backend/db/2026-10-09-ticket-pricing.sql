ALTER TABLE tickets
    ADD COLUMN initial_price INT NULL,
    ADD COLUMN min_price INT NULL,
    ADD COLUMN sales_start_at DATETIME(6) NULL,
    ADD COLUMN last_price_evaluated_at DATETIME(6) NULL,
    ADD COLUMN automatic_pricing_enabled BOOLEAN NOT NULL DEFAULT FALSE;
