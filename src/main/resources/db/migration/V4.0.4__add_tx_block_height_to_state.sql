SET search_path TO aratiri;

ALTER TABLE invoice_subscription_state
    ADD COLUMN last_tx_block_height BIGINT NOT NULL DEFAULT 0;