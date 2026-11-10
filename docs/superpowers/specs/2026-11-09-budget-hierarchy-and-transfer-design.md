# Hierarchical budgets + wallet transfers — design

Date 2026-11-09. Approved rules from the product owner. Overrides the conflicting parts of
`docs/api/FINANCE_CRUD_CONTRACT.md` §4 (Budget) and the "TRANSFER deferred" notes in §5 and the
transaction plan. Money is integer VND (`*Minor`, `long`), no currency field. Timezone = user timezone.

Two features, built in parallel; they meet only at the seam in §3.

## 1. Hierarchical budgets

### Rules
- A **monthly budget** is a container for one month: `(user, month)`, at most one active per user/month.
- The monthly total is **derived and locked**: `monthlyTotalMinor = SUM(limitMinor of its active category budgets)`.
  It is never entered or edited directly and is not stored.
- Creating the monthly budget is one request: month + ≥1 category rows `{categoryId, limitMinor}`,
  written atomically. After that, category budgets are added/edited/removed under it.
- A category budget cannot exist without an active monthly budget for its month
  (409 `BUDGET_MONTH_MISSING`). Still one active budget per category/month (existing `budgets_no_overlap`).
- No allocation concept anywhere (no "unallocated"/"available to allocate" in API, DB or UI).
- Removing the last category budget keeps the monthly budget with total 0.
- **Delete monthly budget**: rejected for the current month in the user's timezone
  (409 `BUDGET_MONTH_CURRENT`); allowed for past and future months. It archives all of that month's active
  category budgets in the same DB transaction. Android confirms first with exactly:
  "Nếu bạn xóa ngân sách tháng, các ngân sách theo từng danh mục cũng sẽ bị xóa. Hãy kiểm tra trước khi xóa."
- Budgets never modify wallets or transactions.

### Spending
- `monthlySpentMinor` = all POSTED EXPENSE transactions in the month (any category). TRANSFER never counts.
- `categorySpentMinor` = POSTED EXPENSE in that category and month.
- `remaining = limit − spent`, may be negative (overspending). Usage status SAFE/WARNING/EXCEEDED as today.
- Computed by the backend in one SQL snapshot; Android displays server values (mock flavor computes the same).

### Balance block
- `availableMinor` = Σ balance of active wallets with `include_in_assets = true AND wallet_type <> 'SAVINGS'`.
- `stillToSpendMinor = max(0, monthlyTotalMinor − monthlySpentMinor)` after the change.
- On create, and on any category change that **increases** the monthly total: if
  `stillToSpendMinor > availableMinor` → 409 `BUDGET_EXCEEDS_AVAILABLE_BALANCE`, nothing saved, response body
  includes `shortfallMinor = stillToSpendMinor − availableMinor`. Decreases/removals are never blocked.
- Point-in-time check: later spending may make it exceed; that is just overspending, not re-validated.
- Concurrency: every write under a monthly budget locks its `monthly_budgets` row `FOR UPDATE` first
  (lock order: monthly budget → category budget → category), so two concurrent increases cannot both pass.

### Data
- New migration: table `fintrack.monthly_budgets(id UUID PK, user_id, month DATE (first day), archived_at,
  created_at, updated_at, version)`, unique `(user_id, month) WHERE archived_at IS NULL`.
- `budgets.monthly_budget_id UUID NOT NULL REFERENCES monthly_budgets(id)`. No legacy data migration:
  if existing active category budgets have no parent, the migration fails with a clear message (do not
  generate parents).

### API (keep existing conventions: strict JSON, 404 for unowned, error envelope)
| Method | Route | Result |
|---|---|---|
| POST | `/api/v1/monthly-budgets` `{month:"YYYY-MM", categories:[{categoryId, limitMinor}]}` | 201 `MonthlyBudgetResponse` |
| GET | `/api/v1/monthly-budgets?month=YYYY-MM&status=ACTIVE\|ARCHIVED\|ALL` | 200 `MonthlyBudgetResponse[]` |
| GET | `/api/v1/monthly-budgets/{id}` | 200, archived included |
| DELETE | `/api/v1/monthly-budgets/{id}` | 204 (idempotent), 409 `BUDGET_MONTH_CURRENT` |
| POST/PUT/DELETE | `/api/v1/budgets…` (existing) | unchanged shape; POST resolves the parent by month (409 `BUDGET_MONTH_MISSING`); balance block on increases |

`MonthlyBudgetResponse`: `monthlyBudgetId, month, totalLimitMinor, spentMinor, remainingMinor, usagePercent,
warningThresholdPercent, status, archivedAt, createdAt, updatedAt, categories: BudgetResponse[]`.
`BudgetResponse` gains `monthlyBudgetId`.

### Android
- Budget list = monthly budgets (month, total, spent, remaining, progress).
- Create form "Tạo ngân sách tháng": month picker + category rows (active EXPENSE categories, each once),
  live total = sum.
- Monthly detail: total/spent/remaining + category budgets with limit/spent/remaining/progress; add, edit
  and remove category budgets; delete monthly budget (hidden/disabled for current month) with the warning
  above.
- Home budget card uses the current month's monthly budget values from the server.
- On `BUDGET_EXCEEDS_AVAILABLE_BALANCE`: keep the form, show the shortfall and a "Chuyển từ tiết kiệm"
  button that opens the transfer form via the seam in §3 (source = a SAVINGS wallet, amount = shortfall).
- Vietnamese string resources only; reuse FinanceActivity/FinanceViewModel/FinanceRepository patterns.

## 2. Wallet transfers (TRANSFER)
- `transactionType = TRANSFER`, `walletId` = source, `destinationWalletId` = destination (different, both
  active, same user), `categoryId` must be null. Entries: −amount on source, +amount on destination.
- No-negative: source candidate balance ≥ 0 on create/update; on DELETE (void) the **destination** must not go
  negative (409 `WALLET_INSUFFICIENT_FUNDS`). Update may change amount/wallets; lock all involved wallets in
  the existing `LOCK_ORDER`. Void keeps entries, reverses exactly once (existing pattern).
- Idempotency: same clientRequestId rules; fingerprint includes `destinationWalletId`.
- Transfers are not expense/income: excluded from budget spent, monthly totals and income/expense reports;
  total assets unchanged (savings→spending only moves money between buckets).
- List/filter: `type=TRANSFER` allowed; `walletId` filter matches source or destination.
- Android: re-enable the transfer type in form, list filter and detail; mock flavor supports it.

## 3. Seam between the two features
`TransactionFormActivity` accepts these Intent extras (transfer agent implements, budget agent only
launches it):

```text
"prefillType"              = "TRANSFER"
"prefillWalletId"          = source wallet id (savings), optional
"prefillDestinationWalletId" = destination wallet id, optional
"prefillAmountMinor"       = long, optional
```
Prefill only fills the form; the user still confirms and saves. Constant names live in
`TransactionFormActivity` as `public static final String EXTRA_PREFILL_*`.

## 4. Testing
Backend unit + PostgreSQL ITs (Docker may be down: ITs must compile, report as not run). Android unit tests
for spending/derived total/prefill parsing; `testMockDebugUnitTest testDevDebugUnitTest assembleDevDebug lintDevDebug`.
