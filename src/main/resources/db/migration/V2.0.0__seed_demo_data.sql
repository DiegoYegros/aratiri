SET search_path TO aratiri;

-- Local/demo seed data for development environments.
--
-- The account balance values below belong to the original mutable-balance model from V1.
-- After V4.0.0/V4.0.1, balances are derived from account_entries instead, so these values are
-- historical bootstrap data rather than the final accounting source of truth.
INSERT INTO users (id, name, password, email, auth_provider, role)
VALUES ('123e4567-e89b-12d3-a456-426614174002', 'Alice',
        '$2a$10$0.288I1XbzewtUFqgE.hTu.b4GBRO36yIN3ZvpQby0MoPVeEbd02u', 'alice@example.com', 'LOCAL', 'USER');

INSERT INTO users (id, name, password, email, auth_provider, role)
VALUES ('123e4567-e89b-12d3-a456-426614174003', 'Bob',
        '$2a$10$0.288I1XbzewtUFqgE.hTu.b4GBRO36yIN3ZvpQby0MoPVeEbd02u', 'bob@example.com', 'LOCAL', 'USER');

INSERT INTO accounts (id, bitcoin_address, alias, balance, user_id)
VALUES ('123e4567-e89b-12d3-a456-426614174000', 'bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh', 'silentkoala91', 5000,
        '123e4567-e89b-12d3-a456-426614174002');

INSERT INTO accounts (id, bitcoin_address, alias, balance, user_id)
VALUES ('123e4567-e89b-12d3-a456-426614174001', 'bc1axy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh', 'bravecat3', 1500,
        '123e4567-e89b-12d3-a456-426614174003');
