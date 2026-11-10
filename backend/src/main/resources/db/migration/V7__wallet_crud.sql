-- Fail clearly on legacy data; never remove a user's financial records.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM fintrack.wallets WHERE opening_balance_minor > 999999999999
               OR btrim(name) = '' OR sort_order < 0) THEN
        RAISE EXCEPTION 'Invalid legacy wallet opening/name/order: reconcile before finance CRUD cutover';
    END IF;
    IF EXISTS (SELECT 1 FROM fintrack.wallets WHERE archived_at IS NULL
               GROUP BY user_id, lower(normalize(btrim(name), NFC)) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate active wallet names: resolve before finance CRUD cutover';
    END IF;
    IF EXISTS (SELECT 1 FROM fintrack.wallet_balances WHERE balance_minor < 0) THEN
        RAISE EXCEPTION 'Negative wallet balances: reconcile before finance CRUD cutover';
    END IF;
END $$;
UPDATE fintrack.wallets SET name = normalize(btrim(name), NFC);
CREATE UNIQUE INDEX ux_wallets_user_name_active ON fintrack.wallets(user_id, lower(name)) WHERE archived_at IS NULL;
