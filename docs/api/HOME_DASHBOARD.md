# Home Dashboard backend contract

Implemented on `feature/home-dashboard`, 2026-11-09. The source plan is
[home-dashboard-backend-plan.md](../development/home-dashboard-backend-plan.md).
This contract extends the existing finance APIs without changing CRUD payloads.

## Request

`GET /api/v1/dashboard?month=2026-11`, authenticated Bearer JWT. Returns a direct
`DashboardResponse` JSON object, without an `ApiResponse` envelope. Only one optional
`month` query is allowed: ASCII `YYYY-MM`, year `0001–9998`, valid calendar month.
An empty value, duplicate month, unknown query (including `userId`, `walletId`, `limit`),
or malformed month returns `400 BAD_REQUEST`. The user ID always comes from the principal.
The default month is the database transaction timestamp in the user's IANA timezone;
missing preferences use `Asia/Ho_Chi_Minh`. All timestamps are UTC ISO-8601 instants.

## Response

```json
{
  "month": "2026-11",
  "timeZone": "Asia/Ho_Chi_Minh",
  "currencyCode": "VND",
  "snapshotAt": "2026-11-09T08:00:00Z",
  "user": {"displayName": "An"},
  "assets": {"totalMinor": 5000000, "availableMinor": 3000000, "savingsMinor": 2000000, "walletCount": 2},
  "monthlyFlow": {"incomeMinor": 2000000, "expenseMinor": 900000, "netSavingsMinor": 1100000},
  "budget": {
    "count": 2, "limitMinor": 2000000, "spentMinor": 900000, "remainingMinor": 1100000,
    "usagePercent": 45.00, "progressPercent": 45, "worstStatus": "WARNING", "warningCount": 1, "exceededCount": 0
  },
  "recentTransactions": [
    {
      "transactionId": "11111111-1111-4111-8111-111111111111", "transactionType": "EXPENSE", "amountMinor": 900000,
      "occurredAt": "2026-11-08T07:00:00Z",
      "wallet": {"walletId": "22222222-2222-4222-8222-222222222222", "name": "Tiền mặt", "archived": false},
      "category": {"categoryId": "33333333-3333-4333-8333-333333333333", "name": "Ăn uống", "iconKey": "coffee", "colorHex": "#F59E0B", "archived": false}
    }
  ],
  "notifications": {"available": false, "unreadCount": null}
}
```

All `*Minor` fields are signed 64-bit JSON integers in VND, without decimal money.
Aggregates use PostgreSQL NUMERIC / Java BigDecimal and exact signed-64 conversion;
overflow is `422 FINANCE_AMOUNT_OVERFLOW`, never a wrapped or rounded amount.

| Field | Meaning |
| --- | --- |
| `snapshotAt` | PostgreSQL `transaction_timestamp()`, the start of the read transaction; snapshot visibility is established by the profile SELECT. It is not a historical valuation date or a change cursor. |
| `user.displayName` | Nonblank preference display name, then username, then `bạn`; no email returned. |
| `assets.totalMinor` | Current sum of `wallet_balances.balance_minor` for active wallets with `include_in_assets=true`. |
| `assets.savingsMinor` | Same wallet set, restricted to SAVINGS. |
| `assets.availableMinor` | Same wallet set, CASH/BANK; equals total minus savings. Does not subtract remaining budgets. |
| `assets.walletCount` | Number of wallets included in assets, including zero-balance wallets. |
| `monthlyFlow` | Sum of POSTED INCOME/EXPENSE amounts in the selected local month; includes archived and asset-excluded wallets. `netSavingsMinor=income-expense` can be negative. |
| `budget.count` | Active category budgets under an active monthly parent for the selected month. Archived categories remain eligible. An empty active monthly parent yields count 0 / NONE. |
| `budget.limitMinor/spentMinor/remainingMinor` | Sums over the selected category budgets; spent uses the same SQL projection/timezone as Budget CRUD. Remaining = limits minus spent, possibly negative. |
| `budget.worstStatus` | Worst category status: EXCEEDED, WARNING, SAFE. NONE if count=0. Does not use the monthly parent's aggregate status. |
| `budget.warningCount/exceededCount` | Mutually exclusive counts of WARNING and EXCEEDED category budgets. Notification enable toggles do not suppress financial status; threshold comes from notification settings, default 80. |
| `budget.usagePercent` | Aggregate spent × 100 / aggregate limit, HALF_UP 2 decimals, uncapped. Same `BudgetResponse.percent` helper as CRUD. Zero when no budgets. |
| `budget.progressPercent` | Display-only usage clamped to [0,100] then truncated to integer. Cannot determine risk. |
| `recentTransactions` | At most 5 POSTED INCOME/EXPENSE in the selected month, occurredAt DESC then UUID DESC. Current reference names, including archived references and flags. iconKey/colorHex may be null. No note/payee. |
| `notifications` | This independent branch returns available=false / unreadCount=null. No notification table is queried. |

Month boundaries are `[local first day 00:00, next local first day 00:00)` converted
to UTC with IANA rules, including DST, leap months and December rollover. Opening
balances, TRANSFER, ADJUSTMENT and debt transactions do not become monthly flows
or recent items. Their legitimate POSTED entries still affect current wallet balances.
Assets are always current, including when selecting a past/future month.

Budget risk deliberately summarizes **category budgets**, unlike
`MonthlyBudgetResponse.spentMinor` which includes all expenses of the month. Expenses
without a category budget still count in `monthlyFlow.expenseMinor`. For example,
category budgets 100/100 and 0/900 produce usage 10% and worstStatus EXCEEDED.
This preserves the source plan's risk requirement after the V10 monthly-parent schema.

An empty user has zero amounts/counts, usagePercent 0.00, progressPercent 0,
worstStatus NONE, recentTransactions [], and notifications available=false/unreadCount=null.

## Consistency, performance and errors

The service runs a single read-only REPEATABLE_READ database transaction. Profile,
preferences, assets, flows, budget thresholds/usage and references see one snapshot,
even when a writer commits between SELECTs. It takes no writer locks, writes no
data, seeds nothing, creates no balance cache and makes no calls back to CRUD HTTP APIs.
Five SELECT statements per request: profile, assets, flow, category budgets, recent.
Budget projection uses the existing set-based/lateral SQL from BudgetRepository;
there is no Java loop issuing queries. Existing user/date, category/date, entry and
monthly-parent indexes remain in use; no speculative migration is added.

Errors use existing `ApiErrorResponse`: unauthenticated/invalid JWT 401;
invalid query 400 BAD_REQUEST; aggregate overflow 422 FINANCE_AMOUNT_OVERFLOW;
infrastructure or invalid persisted timezone 500. A financial read failure never
returns a successful zero snapshot. No new business-resource 404 route exists.

OpenAPI is provided by DashboardController annotations and the typed response DTO
at the existing dev-only `/v3/api-docs`; staging Swagger access remains disabled.

## Android handoff and later notification integration

Android implementation is intentionally a separate review: add Retrofit GET,
Repository and ViewModel using the existing session/Resource patterns. Populate
Home cards from this single response; label assets as current and flows/budgets
with selected month. Bind worstStatus independently of progressPercent. Preserve
hide-balance/selected-month through recreation; reject stale responses after logout
or account change, and show proper loading/error/retry instead of sample values.
Recent click uses the real transaction ID and existing detail navigation. Refresh
after transaction create/update/void, wallet archive/edit, budget edit or timezone change.
Unchanged financial CRUD modules do not require Android payload changes.

Hide the unread badge when available=false; null must not become 0. When both
feature branches are reviewed and integrated, a scoped unread-count SELECT can
replace the fixed unavailable DTO **inside this service transaction**, using the
same datasource and soft-delete/read-state rules as the Notifications module.
Do not call a separately transacted HTTP/service read or catch SQL failure and
fabricate zero. available=true with unreadCount=0 means a real empty inbox.
Sending an email does not mark an inbox item read. This branch neither depends
on nor merges `feature/notification`.
