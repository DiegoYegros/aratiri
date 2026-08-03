SET search_path TO aratiri;

-- Spark wallet metadata is frontend-only; drop the unused backend table.
DROP TABLE IF EXISTS spark_wallets;
