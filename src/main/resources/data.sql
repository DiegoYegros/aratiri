INSERT INTO USERS (id, name, email) VALUES ('123e4567-e89b-12d3-a456-426614174002', 'Alice', 'alice@example.com');
INSERT INTO USERS (id, name, email) VALUES ('123e4567-e89b-12d3-a456-426614174003', 'Bob', 'bob@example.com');

INSERT INTO ACCOUNTS (id, bitcoin_address, balance, user_id) VALUES ('123e4567-e89b-12d3-a456-426614174000', 'bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh',5000, '123e4567-e89b-12d3-a456-426614174002');
INSERT INTO ACCOUNTS (id, bitcoin_address, balance, user_id) VALUES ('123e4567-e89b-12d3-a456-426614174001', 'bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh', 1500, '123e4567-e89b-12d3-a456-426614174003');