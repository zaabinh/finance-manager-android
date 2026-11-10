SET LOCAL search_path TO fintrack, public;

CREATE EXTENSION IF NOT EXISTS citext WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;

CREATE TABLE fintrack.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username public.CITEXT NOT NULL UNIQUE,
    email public.CITEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fintrack.user_preferences (
    user_id UUID PRIMARY KEY REFERENCES fintrack.users(id) ON DELETE CASCADE,
    display_name VARCHAR(100),
    currency_code CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency_code = 'VND'),
    time_zone TEXT NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    week_starts_on SMALLINT NOT NULL DEFAULT 1 CHECK (week_starts_on BETWEEN 1 AND 7),
    notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fintrack.refresh_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES fintrack.users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    device_label VARCHAR(160),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CHECK (expires_at > issued_at)
);

CREATE INDEX ix_refresh_sessions_user_active ON fintrack.refresh_sessions(user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE fintrack.accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES fintrack.users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    account_type TEXT NOT NULL CHECK (account_type IN ('CASH', 'BANK', 'SAVINGS')),
    currency_code CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency_code = 'VND'),
    opening_balance_minor BIGINT NOT NULL DEFAULT 0 CHECK (opening_balance_minor >= 0),
    include_in_assets BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    archived_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, id)
);

CREATE INDEX ix_accounts_user_active ON fintrack.accounts(user_id, sort_order) WHERE archived_at IS NULL;

CREATE TABLE fintrack.categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES fintrack.users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    category_type TEXT NOT NULL CHECK (category_type IN ('EXPENSE', 'INCOME')),
    icon_key VARCHAR(80),
    color_hex CHAR(7),
    sort_order INTEGER NOT NULL DEFAULT 0,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, id),
    CHECK (color_hex IS NULL OR color_hex ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE UNIQUE INDEX ux_categories_user_type_name_active
    ON fintrack.categories(user_id, category_type, lower(name)) WHERE archived_at IS NULL;
CREATE INDEX ix_categories_user_type
    ON fintrack.categories(user_id, category_type, sort_order) WHERE archived_at IS NULL;

CREATE TABLE fintrack.transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES fintrack.users(id) ON DELETE CASCADE,
    transaction_type TEXT NOT NULL CHECK (
        transaction_type IN ('EXPENSE', 'INCOME', 'TRANSFER', 'ADJUSTMENT', 'DEBT_OUT', 'DEBT_IN')
    ),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    category_id UUID,
    adjustment_direction TEXT CHECK (adjustment_direction IN ('IN', 'OUT')),
    occurred_at TIMESTAMPTZ NOT NULL,
    note TEXT,
    payee VARCHAR(160),
    status TEXT NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED', 'VOIDED')),
    client_request_id UUID,
    voided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, id),
    FOREIGN KEY (user_id, category_id) REFERENCES fintrack.categories(user_id, id) ON DELETE RESTRICT,
    CHECK (
        (transaction_type IN ('EXPENSE', 'INCOME') AND category_id IS NOT NULL AND adjustment_direction IS NULL)
        OR (transaction_type = 'ADJUSTMENT' AND category_id IS NULL AND adjustment_direction IS NOT NULL)
        OR (transaction_type IN ('TRANSFER', 'DEBT_OUT', 'DEBT_IN')
            AND category_id IS NULL AND adjustment_direction IS NULL)
    ),
    CHECK ((status = 'POSTED' AND voided_at IS NULL) OR (status = 'VOIDED' AND voided_at IS NOT NULL))
);

CREATE UNIQUE INDEX ux_transactions_idempotency
    ON fintrack.transactions(user_id, client_request_id) WHERE client_request_id IS NOT NULL;
CREATE INDEX ix_transactions_user_occurred
    ON fintrack.transactions(user_id, occurred_at DESC) WHERE status = 'POSTED';
CREATE INDEX ix_transactions_user_category_date
    ON fintrack.transactions(user_id, category_id, occurred_at DESC) WHERE status = 'POSTED';

CREATE TABLE fintrack.transaction_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    account_id UUID NOT NULL,
    delta_minor BIGINT NOT NULL CHECK (delta_minor <> 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (transaction_id, account_id),
    FOREIGN KEY (user_id, transaction_id) REFERENCES fintrack.transactions(user_id, id) ON DELETE CASCADE,
    FOREIGN KEY (user_id, account_id) REFERENCES fintrack.accounts(user_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_entries_user_account ON fintrack.transaction_entries(user_id, account_id);
CREATE INDEX ix_entries_transaction ON fintrack.transaction_entries(transaction_id);

CREATE TABLE fintrack.budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES fintrack.users(id) ON DELETE CASCADE,
    category_id UUID NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    limit_minor BIGINT NOT NULL CHECK (limit_minor > 0),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, id),
    FOREIGN KEY (user_id, category_id) REFERENCES fintrack.categories(user_id, id) ON DELETE RESTRICT,
    CHECK (period_start < period_end),
    CONSTRAINT budgets_no_overlap EXCLUDE USING gist (
        user_id WITH =,
        category_id WITH =,
        daterange(period_start, period_end, '[)') WITH &&
    ) WHERE (archived_at IS NULL)
);

CREATE INDEX ix_budgets_user_period
    ON fintrack.budgets(user_id, period_start, period_end) WHERE archived_at IS NULL;

CREATE FUNCTION fintrack.validate_transaction_entries(transaction_identifier UUID) RETURNS VOID
    LANGUAGE plpgsql
    SET search_path = fintrack, public
AS $$
DECLARE
    transaction_record RECORD;
    entry_count BIGINT;
    entry_total NUMERIC;
    minimum_entry NUMERIC;
    maximum_entry NUMERIC;
BEGIN
    SELECT transaction_type, amount_minor, adjustment_direction, status
    INTO transaction_record FROM fintrack.transactions WHERE id = transaction_identifier;
    IF NOT FOUND OR transaction_record.status = 'VOIDED' THEN
        RETURN;
    END IF;

    SELECT count(*), coalesce(sum(delta_minor::NUMERIC), 0), min(delta_minor::NUMERIC), max(delta_minor::NUMERIC)
    INTO entry_count, entry_total, minimum_entry, maximum_entry
    FROM fintrack.transaction_entries WHERE transaction_id = transaction_identifier;

    IF transaction_record.transaction_type IN ('EXPENSE', 'DEBT_OUT') THEN
        IF entry_count <> 1 OR entry_total <> -transaction_record.amount_minor THEN
            RAISE EXCEPTION 'Invalid outflow entries for %', transaction_identifier;
        END IF;
    ELSIF transaction_record.transaction_type IN ('INCOME', 'DEBT_IN') THEN
        IF entry_count <> 1 OR entry_total <> transaction_record.amount_minor THEN
            RAISE EXCEPTION 'Invalid inflow entries for %', transaction_identifier;
        END IF;
    ELSIF transaction_record.transaction_type = 'TRANSFER' THEN
        IF entry_count <> 2 OR entry_total <> 0
                OR minimum_entry <> -transaction_record.amount_minor
                OR maximum_entry <> transaction_record.amount_minor THEN
            RAISE EXCEPTION 'Invalid transfer entries for %', transaction_identifier;
        END IF;
    ELSIF transaction_record.transaction_type = 'ADJUSTMENT' THEN
        IF entry_count <> 1 OR entry_total <> (CASE WHEN transaction_record.adjustment_direction = 'IN'
                THEN transaction_record.amount_minor ELSE -transaction_record.amount_minor END) THEN
            RAISE EXCEPTION 'Invalid adjustment entries for %', transaction_identifier;
        END IF;
    END IF;
END;
$$;

CREATE FUNCTION fintrack.validate_entries_trigger() RETURNS TRIGGER
    LANGUAGE plpgsql
    SET search_path = fintrack, public
AS $$
BEGIN
    PERFORM fintrack.validate_transaction_entries(
        CASE WHEN TG_OP = 'DELETE' THEN OLD.transaction_id ELSE NEW.transaction_id END
    );
    IF TG_OP = 'UPDATE' AND OLD.transaction_id <> NEW.transaction_id THEN
        PERFORM fintrack.validate_transaction_entries(OLD.transaction_id);
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_validate_entries
    AFTER INSERT OR UPDATE OR DELETE ON fintrack.transaction_entries
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION fintrack.validate_entries_trigger();

CREATE FUNCTION fintrack.validate_transaction_trigger() RETURNS TRIGGER
    LANGUAGE plpgsql
    SET search_path = fintrack, public
AS $$
BEGIN
    PERFORM fintrack.validate_transaction_entries(NEW.id);
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_validate_transaction
    AFTER INSERT OR UPDATE ON fintrack.transactions
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION fintrack.validate_transaction_trigger();

CREATE VIEW fintrack.account_balances AS
SELECT account.id AS account_id, account.user_id, account.name, account.account_type,
       account.include_in_assets, account.archived_at,
       account.opening_balance_minor + coalesce(
           sum(entry.delta_minor::NUMERIC) FILTER (WHERE transaction.status = 'POSTED'), 0
       ) AS balance_minor
FROM fintrack.accounts account
LEFT JOIN fintrack.transaction_entries entry ON entry.account_id = account.id AND entry.user_id = account.user_id
LEFT JOIN fintrack.transactions transaction ON transaction.id = entry.transaction_id AND transaction.user_id = account.user_id
GROUP BY account.id;
