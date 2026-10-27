ALTER TABLE fintrack.accounts RENAME TO wallets;
ALTER TABLE fintrack.wallets RENAME COLUMN account_type TO wallet_type;
ALTER TABLE fintrack.transaction_entries RENAME COLUMN account_id TO wallet_id;

ALTER TABLE fintrack.wallets RENAME CONSTRAINT accounts_pkey TO wallets_pkey;
ALTER TABLE fintrack.wallets RENAME CONSTRAINT accounts_user_id_fkey TO wallets_user_id_fkey;
ALTER TABLE fintrack.wallets RENAME CONSTRAINT accounts_user_id_id_key TO wallets_user_id_id_key;
ALTER TABLE fintrack.wallets RENAME CONSTRAINT accounts_account_type_check TO wallets_wallet_type_check;
ALTER TABLE fintrack.wallets RENAME CONSTRAINT accounts_currency_code_check TO wallets_currency_code_check;
ALTER TABLE fintrack.wallets RENAME CONSTRAINT accounts_opening_balance_minor_check TO wallets_opening_balance_minor_check;

ALTER TABLE fintrack.transaction_entries
    RENAME CONSTRAINT transaction_entries_user_id_account_id_fkey TO transaction_entries_user_id_wallet_id_fkey;
ALTER TABLE fintrack.transaction_entries
    RENAME CONSTRAINT transaction_entries_transaction_id_account_id_key TO transaction_entries_transaction_id_wallet_id_key;

ALTER INDEX fintrack.ix_accounts_user_active RENAME TO ix_wallets_user_active;
ALTER INDEX fintrack.ix_entries_user_account RENAME TO ix_entries_user_wallet;

ALTER VIEW fintrack.account_balances RENAME TO wallet_balances;
ALTER VIEW fintrack.wallet_balances RENAME COLUMN account_id TO wallet_id;
ALTER VIEW fintrack.wallet_balances RENAME COLUMN account_type TO wallet_type;
