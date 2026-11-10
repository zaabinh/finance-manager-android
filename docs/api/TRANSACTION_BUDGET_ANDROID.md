# Android Transaction / Budget contract

Updated 2026-11-07: [FINANCE_CRUD_CONTRACT.md](FINANCE_CRUD_CONTRACT.md) is the current agreed target.
This document describes the existing Android implementation and its integration gaps; it does not establish
that the finance backend APIs exist or that device/backend acceptance has passed. It supplements
[CONTRACT.md](CONTRACT.md). No backend implementation or migration is introduced by this documentation update.

## Running Android

- `mockDebug`: deterministic sample wallets, income, expense and a monthly budget; all commands persist in an atomic, per-user file under `noBackupFilesDir/finance`. This is development data, not server-synced data. Mock data is retained across logout and is isolated by the session user ID. Clearing app data removes it.
- `devDebug`, `staging`, `prod`: use Retrofit and the configured `BASE_URL`. They never read or fall back to the mock ledger. Network failures preserve form input and offer retry. The existing API client supplies bearer authentication and clears expired sessions on 401.
- Access: Home add button → transaction form; Home history icon → history; Home “Ngân sách” link → budget list. History and Budget share navigation. A picker can add a wallet/category without discarding the form.
- Integer VND only: 1–999,999,999,999 per transaction/budget; opening balances allow zero. Wallet types: `CASH`, `BANK`, `SAVINGS`. Target: no negative current wallet balance after any mutation, including income deletion or opening-balance reduction. Existing mock rules must be updated; permitting negative mock balances is not the target policy.
- Target: UTC timestamps and calendar months in the user's preference timezone, default `Asia/Ho_Chi_Minh`, independent of device timezone. The current fixed-zone client needs adaptation. Transfers are deferred; disable the create/edit transfer entry points for backends without support. Budgets apply to expense categories, one active budget per category/month; warning uses the user's notification threshold (default 80), reached/exceeded at 100%.

## HTTP shape

All routes below require a bearer token. Responses are direct DTO bodies, without an `ApiResponse` wrapper. Never accept a client `userId` as ownership authority. Use the authenticated principal and check ownership of every referenced wallet/category/record.

| Method | Route | Request | Success |
|---|---|---|---|
| GET | `/api/v1/wallets` | — | 200, `Wallet[]` |
| POST | `/api/v1/wallets` | `WalletRequest` | 201, `Wallet` |
| GET/PUT | `/api/v1/wallets/{walletId}` | — / full writable fields | 200, `Wallet` |
| PATCH | `/api/v1/wallets/{walletId}/status` | `{"active":true}` or false | 200, `Wallet` |
| DELETE | `/api/v1/wallets/{walletId}` | — | 204, archive |
| GET | `/api/v1/categories` | — | 200, `Category[]` |
| POST | `/api/v1/categories` | `CategoryRequest` | 201, `Category` |
| GET/PUT | `/api/v1/categories/{categoryId}` | — / full writable fields | 200, `Category` |
| PATCH | `/api/v1/categories/{categoryId}/status` | active boolean | 200, `Category` |
| DELETE | `/api/v1/categories/{categoryId}` | — | 204, archive |
| GET | `/api/v1/transactions?page=0&size=200` | zero-based pages | 200, `TransactionPage` |
| POST | `/api/v1/transactions` | `TransactionRequest` | 201 (or 200 idempotent replay), `Transaction` |
| GET | `/api/v1/transactions/{transactionId}` | — | 200, `Transaction` |
| PUT | `/api/v1/transactions/{transactionId}` | `TransactionRequest` | 200, `Transaction` |
| DELETE | `/api/v1/transactions/{transactionId}` | — | 204 |
| GET | `/api/v1/budgets` | — | 200, `Budget[]` |
| GET | `/api/v1/budgets/{budgetId}` | — | 200, `Budget` with usage |
| POST | `/api/v1/budgets` | `BudgetRequest` | 201, `Budget` |
| PUT | `/api/v1/budgets/{budgetId}` | `BudgetRequest` | 200, `Budget` |
| DELETE | `/api/v1/budgets/{budgetId}` | — | 204 |

```json
{
  "walletId": "uuid", "name": "Tiền mặt", "walletType": "CASH",
  "openingBalanceMinor": 2000000
}
```

The existing `WalletRequest` has `name`, `walletType`, `openingBalanceMinor`. Extend it with `includeInAssets` and `sortOrder`, and extend the response with server `balanceMinor`, fixed VND currency and lifecycle/timestamps per the current contract. List defaults to `status=ALL` for history; creation pickers explicitly request/filter ACTIVE. Do not infer opening balance from current balance or reconstruct current balance from one page of transactions.

```json
{"categoryId":"uuid","name":"Ăn uống","categoryType":"EXPENSE"}
```

Extend `CategoryRequest` with `iconKey`, `colorHex`, `sortOrder` and response lifecycle/timestamps. `categoryType` is EXPENSE or INCOME. Every category has an owner; initial categories are seeded once per user and are editable/archivable. Do not regenerate them when a list is empty. List defaults ALL for history, pickers use ACTIVE/type; no global ownerless categories.

```json
{
  "transactionId":"uuid", "transactionType":"EXPENSE", "amountMinor":95000,
  "categoryId":"uuid", "walletId":"uuid", "destinationWalletId":null,
  "occurredAt":"2026-10-30T08:45:00.000Z", "note":"Cà phê", "payee":"Quán Đồng"
}
```

`TransactionRequest` has the same fields except `transactionId`, plus stable `clientRequestId` for POST. Scope it to the authenticated user; compare an immutable fingerprint of the original create payload. Identical retry returns the current record without reposting (including after edit/void); changed payload with the same key gives 409. PUT uses the path ID and does not create through this key. Income/expense require one active owned wallet and a matching active owned category; destinationWalletId must be absent/null until transfers are supported. Notes max 2,000 characters, payee max 160. Timestamps are required ISO 8601 UTC. Extend responses with POSTED/VOIDED state and timestamps; a replay returning VOIDED must not be presented as a new active transaction.

`TransactionPage` is `{ "items": [Transaction], "page": 0, "totalPages": 1 }`; no results has totalPages=0. Return POSTED only; default descending occurrence/ID order. The current client reads all pages, filters locally and computes balances: this is an implementation gap. Target uses server filters/search/sort/pages and server wallet balances/budget usage. Count/page share a request snapshot; separate requests may change under concurrent writes, so refresh page0 after mutation and deduplicate by ID. Do not publish incomplete historical snapshots as full ledger truth.

```json
{"budgetId":"uuid","categoryId":"uuid","month":"2026-10","limitMinor":3000000}
```

`BudgetRequest` has `categoryId`, `month`, `limitMinor`. Map month to local first-of-month inclusive / next-month exclusive dates. Reject overlapping active budgets with 409. Extend responses with server spent/remaining/usagePercent/warningThresholdPercent/status and lifecycle/timestamps. Budget deletion archives without changing transactions or wallet balance. Transaction voiding removes its contribution once: deleting an expense restores money; deleting income reduces balance and must return 409 without mutation if funds would become negative. Budget remaining may be negative; this is distinct from forbidden negative wallet balances. Full field/filter/lifecycle/locking rules are in the current contract.

Errors follow `ApiErrorResponse` in the base contract. Android distinguishes 401, 404, 409, 400/422, network failures and other server failures. A successful command is acknowledged independently of the next refresh, so a refresh failure does not falsely report a completed save as failed. Fine-grained backend field errors and optimistic concurrency versions can be added when the backend contract is finalized.

## OCR

Uses bundled [ML Kit text recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) on-device. Document picker supports a batch; camera delegates to the system camera and a narrow cache-only FileProvider. No broad media/camera permission or image upload is needed. Each image is reviewed separately and can be skipped; no transaction is created before confirmation. Ambiguous totals remain blank. Date/category/wallet require review; category prediction is not implemented. Camera images live in the app cache until system cleanup; document grants allow process recreation. Use “Xem ảnh hóa đơn” to inspect the original.

## Validation

Local JUnit coverage: posting/reversal, transfer invariants, budget/category/month rules, Vietnamese search, money parsing, conservative OCR extraction, actual Retrofit serialization and HTTP errors. Instrumentation coverage: persistence, user isolation, failed-write prevention and corruption handling. Run:

```powershell
cd android
.\gradlew.bat :app:assembleDevDebug :app:assembleMockDebug :app:testMockDebugUnitTest :app:lintMockDebug :app:assembleMockDebugAndroidTest
# With a device/emulator:
.\gradlew.bat :app:connectedMockDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.package=com.example.fintrack.ui.finance'
```

Before backend handoff acceptance: run the client against the real endpoints, verify authentication/ownership, concurrent writes and idempotency in PostgreSQL, and exercise screen flows, rotation, system back, keyboard and OCR with real images on a device. Compiling an instrumentation APK does not establish that these checks passed.

Review result (2026-10-30): Dev and Mock builds, 42 unit/contract tests, lint, and instrumentation APK compilation passed. Ten device cases are compiled. The attempted emulator run lost window focus and disconnected before completing; a boot retry encountered a system-server pre-watchdog. Device tests and real-backend integration are not verified as passing. A review fix preserves edit-form initialization when rotation occurs before the initial data load completes.
