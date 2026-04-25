SET search_path TO aratiri;

-- Reuse invoice_subscription_state for all LND stream cursors. add_index/settle_index resume invoice
-- updates, while last_tx_block_height resumes on-chain transaction subscriptions without scanning
-- from genesis after every reconnect.
ALTER TABLE invoice_subscription_state
    ADD COLUMN last_tx_block_height BIGINT NOT NULL DEFAULT 0;
