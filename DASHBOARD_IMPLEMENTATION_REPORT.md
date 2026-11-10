# Home Dashboard backend implementation report

Date: 2026-11-09 (Asia/Saigon). Branch: `feature/home-dashboard`.
Worktree: `.worktrees/home-dashboard`. Base: `575ae54`.
The branch is retained for review, without merge or push. Android source is unchanged.

## Implemented

`GET /api/v1/dashboard` accepts one optional strict `month=YYYY-MM`, uses JWT principal
ownership and returns a direct typed JSON snapshot: safe display name, current assets,
selected-month income/expense/net savings, category-budget risk, five recent transactions,
and explicit unavailable notification state. Unknown/duplicate/invalid query values return
400; aggregate overflow returns 422; existing authentication returns 401.

Current assets include active, asset-included wallets only. Flows and budget usage retain
archived/excluded-wallet history, exclude voided postings, and exclude transfers/opening
balances from income/expense. Net monthly savings and budget remaining may be negative.
Recent items include archived reference flags and current reference names, without note,
payee or email. Financial values are exact integer VND, including signed-64 overflow checks.

## Final architecture and plan reconciliation

`DashboardController → DashboardService → DashboardRepository` reuses existing Spring/JDBC,
principal and error patterns. The service is read-only REPEATABLE_READ: five SELECTs share
one database snapshot, including timezone/profile/threshold and reference names. Timestamp
comes from PostgreSQL. No writer locks, HTTP callbacks, new infrastructure/dependency,
cache, ledger changes or database migration were needed.

`BudgetRepository.dashboardActive` reuses the existing Budget CRUD SQL projection and
`BudgetResponse` exact percent/status math. It selects active category children only when
the V10 monthly parent is active for the selected month. Worst category status determines
risk; aggregate ratio determines only progress. Monthly-parent aggregate spending may
include unbudgeted categories, whereas dashboard budget usage sums only budgeted categories;
the separate monthlyFlow still includes all expenses. This is documented explicitly.

Notifications are independently implemented on `feature/notification`. This dashboard
branch always returns `available=false, unreadCount=null` and never queries its tables.
The contract documents how a future same-transaction count read should be integrated after
both branches are reviewed; no cross-branch merge/dependency was introduced.

## Main files

- `backend/src/main/java/com/fintrack/api/controller/DashboardController.java`
- `backend/src/main/java/com/fintrack/api/service/dashboard/DashboardService.java`
- `backend/src/main/java/com/fintrack/api/repository/DashboardRepository.java`
- `backend/src/main/java/com/fintrack/api/repository/BudgetRepository.java`
- `backend/src/main/java/com/fintrack/api/dto/response/dashboard/DashboardResponse.java`
- Unit/API tests: `DashboardControllerTest`, `service/dashboard/DashboardServiceTest`
- Real PostgreSQL tests: `DashboardPostgresIT`, extensions in existing `FinanceApiIT`
- Final API/Android handoff: `docs/api/HOME_DASHBOARD.md`; OpenAPI annotations in controller
- Source plan implementation status: `docs/development/home-dashboard-backend-plan.md`

## Verification

Environment: JDK 21.0.11, installed Maven, isolated PostgreSQL 18.4 database
`home_dashboard_review` on loopback. Flyway V1–V10 applied successfully without changes
to existing migrations. External test database is optional; tests fall back to PostgreSQL
17.7 Testcontainers. Fixtures create random users and never truncate/clean shared schemas.

Initial focused verification passed 9 unit/API tests and 8 PostgreSQL tests, zero failures
or skips. Full unit regression passed **164/164**, zero failures/errors/skips. Final PostgreSQL
regression passed **57/57**, zero failures/errors/skips: Dashboard 9, Finance API 9,
Budget 9, Category 5, Transaction 20 and Wallet 5.

The larger-ledger test measured **p95 179.99 ms** for 30 full service reads after 5 warmups,
5,000 selected-month expenses, 4 wallets, 1 category budget, concurrency 1, local JDBC / loopback
PostgreSQL, without HTTP or Android network time. The local backend target <500 ms was met
for this sample only. Recent-query EXPLAIN ANALYZE used `ix_transactions_user_occurred`, read
6 index rows to return 5, and completed in 0.194 ms. The service executes exactly five SELECT
calls by source inspection regardless of transaction/budget count; no per-item query loop exists.

Snapshot test uses a real Spring transaction proxy and two connections synchronized by
latches, without sleeps. It asserts `repeatable read` and `transaction_read_only=on`, then
commits expense/void, wallet/category rename, display name/timezone and threshold changes
between profile SELECT and later aggregates. The in-flight response keeps the complete old
state; the next request sees the new state.

Other tests cover exact threshold/rounding, worst-risk vs aggregate percentage, archived
parent/child/category lifecycle, no-budget NONE, archived/excluded assets, transfers/voids,
owner isolation, half-open timezone boundaries, DST, leap month, December rollover,
same-timestamp UUID ordering, default timezone/month, signed-long boundary and overflow.
Actual JWT HTTP integration covers auth401, direct DTO/no private fields, ownership,
query validation and overflow422.

Initial non-elevated compile attempts could not read an existing Maven-cache jar under
the sandbox. The authorized elevated run used the existing cache successfully; no dependency
versions, repository permissions or application code were changed to mask that environment issue.

## Remaining work / technical debt / Android handoff

- Android developer must add Retrofit/Repository/ViewModel integration and wire Home fields,
  actual loading/empty/retry, selected-month navigation, session guards and refresh triggers.
  Bind worstStatus separately from clamped progress and retain hide-balance across recreation.
- Keep unread badge hidden for null/unavailable. Real inbox count integration is deliberately
  deferred until the independent Notifications branch is reviewed and combined by the user.
- No Android build/device/network test, production staging deploy, or real-user smoke was run
  for this backend-only request. Those remain separate acceptance gates.
- A local single-reader JDBC performance sample is not a staging concurrent HTTP or Android
  latency guarantee. Production query plans should be revisited with representative data.
- Flyway warns that PostgreSQL 18.4 is newer than its tested PostgreSQL 17 baseline; the actual
  PostgreSQL 18 migration/integration run is reported separately from that support warning.
