INSERT INTO USERS (id, name, password, email)
VALUES ('123e4567-e89b-12d3-a456-426614174002', 'Alice',
        '$2a$10$0.288I1XbzewtUFqgE.hTu.b4GBRO36yIN3ZvpQby0MoPVeEbd02u', 'alice@example.com');
INSERT INTO USERS (id, name, password, email)
VALUES ('123e4567-e89b-12d3-a456-426614174003', 'Bob', '$2a$10$0.288I1XbzewtUFqgE.hTu.b4GBRO36yIN3ZvpQby0MoPVeEbd02u',
        'bob@example.com');

INSERT INTO ACCOUNTS (id, bitcoin_address, alias, balance, user_id)
VALUES ('123e4567-e89b-12d3-a456-426614174000', 'bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh', 'silentkoala91', 5000,
        '123e4567-e89b-12d3-a456-426614174002');
INSERT INTO ACCOUNTS (id, bitcoin_address, alias, balance, user_id)
VALUES ('123e4567-e89b-12d3-a456-426614174001', 'bc1axy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh', 'bravecat3', 1500,
        '123e4567-e89b-12d3-a456-426614174003');