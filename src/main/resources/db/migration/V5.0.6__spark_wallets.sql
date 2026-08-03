SET search_path TO aratiri;

-- Non-custodial Spark wallet metadata (browser signer only).
-- Stores ONLY public metadata: identity public key, spark address, network,
-- account index, and two UX flags. Never a mnemonic, seed, or private key.
-- One wallet per user; a user may (re)register after forgetting.
CREATE TABLE spark_wallets (
    id                  VARCHAR(36) PRIMARY KEY,
    user_id             VARCHAR(36) NOT NULL,
    identity_public_key VARCHAR(66) NOT NULL,
    spark_address       VARCHAR(128) NOT NULL,
    network             VARCHAR(16) NOT NULL,
    account_index       INT         NOT NULL,
    backup_verified     BOOLEAN     NOT NULL DEFAULT FALSE,
    privacy_enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_spark_wallets_user_id UNIQUE (user_id),
    CONSTRAINT uq_spark_wallets_identity_public_key UNIQUE (identity_public_key),
    CONSTRAINT ck_spark_wallets_network CHECK (network IN ('MAINNET', 'REGTEST')),
    CONSTRAINT ck_spark_wallets_account_index_nonnegative CHECK (account_index >= 0),
    CONSTRAINT fk_spark_wallets_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_spark_wallets_user_id ON spark_wallets (user_id);

COMMENT ON TABLE spark_wallets IS
    'Spark self-custody wallet public metadata. Keys and signing live in the browser; this table holds no secrets.';
