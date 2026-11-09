-- Hierarchical budgets: a monthly container per (user, month); category budgets live under it.
-- The monthly total is derived (SUM of active category limits) and is intentionally not stored.
-- No legacy data migration: existing category budgets have no parent and parents are never generated.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM fintrack.budgets WHERE archived_at IS NULL) THEN
        RAISE EXCEPTION 'V10 monthly budgets: active category budgets exist without a monthly budget. '
            'Archive or remove them (or create their monthly budgets) before upgrading; parents are not generated.';
    END IF;
    IF EXISTS (SELECT 1 FROM fintrack.budgets) THEN
        RAISE EXCEPTION 'V10 monthly budgets: archived category budgets exist without a monthly budget and '
            'budgets.monthly_budget_id is NOT NULL. Remove or reconcile them before upgrading; parents are not generated.';
    END IF;
END $$;

CREATE TABLE fintrack.monthly_budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES fintrack.users(id) ON DELETE CASCADE,
    month DATE NOT NULL CHECK (EXTRACT(DAY FROM month) = 1),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (user_id, id),
    UNIQUE (user_id, id, month)
);

CREATE UNIQUE INDEX ux_monthly_budgets_user_month_active
    ON fintrack.monthly_budgets(user_id, month) WHERE archived_at IS NULL;

ALTER TABLE fintrack.budgets ADD COLUMN monthly_budget_id UUID NOT NULL REFERENCES fintrack.monthly_budgets(id);
-- Same owner and same month as the parent, enforced by the database rather than only by the service.
ALTER TABLE fintrack.budgets ADD CONSTRAINT budgets_monthly_budget_owner_month_fkey
    FOREIGN KEY (user_id, monthly_budget_id, period_start)
    REFERENCES fintrack.monthly_budgets(user_id, id, month) ON UPDATE NO ACTION ON DELETE RESTRICT;
CREATE INDEX ix_budgets_monthly_budget ON fintrack.budgets(monthly_budget_id) WHERE archived_at IS NULL;
