SET search_path TO aratiri;

ALTER TABLE accounts
    DROP COLUMN IF EXISTS balance;