CREATE EXTENSION IF NOT EXISTS hstore;

CREATE FUNCTION schema_version() RETURNS INT AS 'SELECT 2' LANGUAGE SQL;
CREATE FUNCTION currency_fraction_digits() RETURNS INT AS 'SELECT ?{currency_fraction_digits}' LANGUAGE SQL;

CREATE TABLE users (
    id TEXT PRIMARY KEY NOT NULL,
    last_login TIMESTAMPTZ NOT NULL
);

CREATE TABLE active_users (
    id TEXT PRIMARY KEY NOT NULL REFERENCES users(id),
    display_name TEXT NOT NULL,
    profile_picture_url TEXT
);

CREATE TABLE groups (
    id UUID PRIMARY KEY NOT NULL,
    invite_token TEXT UNIQUE NOT NULL,
    display_name TEXT NOT NULL
);

CREATE TABLE membership (
    user_id TEXT NOT NULL REFERENCES users(id),
    group_id UUID NOT NULL REFERENCES groups(id),
    PRIMARY KEY (user_id, group_id)
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES groups(id),
    name TEXT NOT NULL,
    comment TEXT,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    originating_user TEXT NOT NULL REFERENCES users(id),
    expense_total DECIMAL(1000, ?{currency_fraction_digits})
);

CREATE TABLE balance_changes (
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id),
    amount DECIMAL(1000, ?{currency_fraction_digits}) NOT NULL,
    PRIMARY KEY (transaction_id, user_id)
);

CREATE VIEW recent_transaction_timestamps (group_id, transaction_timestamp) AS SELECT
    transactions.group_id AS group_id,
    MAX(transactions.timestamp) AS transaction_timestamp
FROM transactions GROUP BY transactions.group_id;

CREATE TABLE refresh_tokens (
    user_id TEXT NOT NULL REFERENCES users(id),
    token TEXT PRIMARY KEY NOT NULL
);

CREATE INDEX groups_invite_token ON groups USING HASH(invite_token);
CREATE INDEX membership_users ON membership USING HASH(user_id);
CREATE INDEX membership_groups ON membership USING HASH(group_id);
CREATE INDEX transactions_groups ON transactions USING HASH(group_id);
CREATE INDEX balance_changes_transactions ON balance_changes USING HASH(transaction_id);
CREATE INDEX refresh_tokens_users ON refresh_tokens USING HASH(user_id);
