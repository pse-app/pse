-- SQL schema migration from Schema 1 to Schema 2

BEGIN;

CREATE FUNCTION schema_version() RETURNS INT AS 'SELECT 2' LANGUAGE SQL;
CREATE FUNCTION currency_fraction_digits() RETURNS INT AS 'SELECT 2' LANGUAGE SQL;

CREATE VIEW recent_transaction_timestamps (group_id, transaction_timestamp) AS SELECT
    transactions.group_id AS group_id,
    MAX(transactions.timestamp) AS transaction_timestamp
FROM transactions GROUP BY transactions.group_id;

END;
