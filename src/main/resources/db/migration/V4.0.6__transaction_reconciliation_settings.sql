SET search_path TO aratiri;

ALTER TABLE node_settings
    ADD COLUMN transaction_reconciliation_min_age_ms BIGINT NOT NULL DEFAULT 300000;

ALTER TABLE node_settings
    ADD CONSTRAINT ck_node_settings_transaction_reconciliation_min_age_ms
        CHECK (transaction_reconciliation_min_age_ms >= 0);
