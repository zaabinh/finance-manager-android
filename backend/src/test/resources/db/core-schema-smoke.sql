BEGIN;
SET LOCAL search_path TO fintrack, public;

DO $$
DECLARE
    user_identifier UUID := gen_random_uuid();
    cash_identifier UUID := gen_random_uuid();
    bank_identifier UUID := gen_random_uuid();
    food_identifier UUID := gen_random_uuid();
    salary_identifier UUID := gen_random_uuid();
    transaction_identifier UUID;
    cash_balance NUMERIC;
    bank_balance NUMERIC;
BEGIN
    INSERT INTO users(id, username, email, password_hash)
    VALUES (user_identifier, 'test_' || user_identifier, user_identifier || '@example.test', 'test-only-hash');
    INSERT INTO user_preferences(user_id) VALUES (user_identifier);
    INSERT INTO wallets(id, user_id, name, wallet_type, opening_balance_minor)
    VALUES (cash_identifier, user_identifier, 'Cash', 'CASH', 1000000),
           (bank_identifier, user_identifier, 'Bank', 'BANK', 2000000);
    INSERT INTO categories(id, user_id, name, category_type)
    VALUES (food_identifier, user_identifier, 'Food', 'EXPENSE'),
           (salary_identifier, user_identifier, 'Salary', 'INCOME');

    transaction_identifier := gen_random_uuid();
    INSERT INTO transactions(id, user_id, transaction_type, amount_minor, category_id, occurred_at)
    VALUES (transaction_identifier, user_identifier, 'EXPENSE', 50000, food_identifier, now());
    INSERT INTO transaction_entries(user_id, transaction_id, wallet_id, delta_minor)
    VALUES (user_identifier, transaction_identifier, cash_identifier, -50000);

    transaction_identifier := gen_random_uuid();
    INSERT INTO transactions(id, user_id, transaction_type, amount_minor, category_id, occurred_at)
    VALUES (transaction_identifier, user_identifier, 'INCOME', 300000, salary_identifier, now());
    INSERT INTO transaction_entries(user_id, transaction_id, wallet_id, delta_minor)
    VALUES (user_identifier, transaction_identifier, bank_identifier, 300000);

    transaction_identifier := gen_random_uuid();
    INSERT INTO transactions(id, user_id, transaction_type, amount_minor, occurred_at)
    VALUES (transaction_identifier, user_identifier, 'TRANSFER', 200000, now());
    INSERT INTO transaction_entries(user_id, transaction_id, wallet_id, delta_minor)
    VALUES (user_identifier, transaction_identifier, cash_identifier, -200000),
           (user_identifier, transaction_identifier, bank_identifier, 200000);

    INSERT INTO budgets(user_id, category_id, period_start, period_end, limit_minor)
    VALUES (user_identifier, food_identifier, DATE '2026-10-01', DATE '2026-11-01', 1000000);

    SELECT balance_minor INTO cash_balance FROM wallet_balances WHERE wallet_id = cash_identifier;
    SELECT balance_minor INTO bank_balance FROM wallet_balances WHERE wallet_id = bank_identifier;
    IF cash_balance IS DISTINCT FROM 750000 OR bank_balance IS DISTINCT FROM 2500000 THEN
        RAISE EXCEPTION 'Wallet balance reconciliation failed';
    END IF;
END;
$$;

SET CONSTRAINTS ALL IMMEDIATE;
ROLLBACK;
