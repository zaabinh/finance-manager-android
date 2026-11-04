# Android Transaction / Budget contract

Status: **client implemented; backend endpoints proposed, not implemented or verified here** (2026-10-30).
This supplements [CONTRACT.md](CONTRACT.md). No backend source or migration is part of this change.

## Running Android

- `mockDebug`: deterministic sample wallets, income, expense and a monthly budget; all commands persist in an atomic, per-user file under `noBackupFilesDir/finance`. This is development data, not server-synced data. Mock data is retained across logout and is isolated by the session user ID. Clearing app data removes it.
- `devDebug`, `staging`, `prod`: use Retrofit and the configured `BASE_URL`. They never read or fall back to the mock ledger. Network failures preserve form input and offer retry. The existing API client supplies bearer authentication and clears expired sessions on 401.
- Access: Home add button → transaction form; Home history icon → history; Home “Ngân sách” link → budget list. History and Budget share navigation. A picker can add a wallet/category without discarding the form.
- Integer VND only: 1–999,999,999,999 per transaction/budget; opening balances allow zero. Wallet types: `CASH`, `BANK`, `SAVINGS`. Mock permits negative derived balances; server policy for insufficient funds must be explicit.
- Calendar months use `Asia/Ho_Chi_Minh`, independent of device timezone. Transfer postings debit one wallet and credit another and are excluded from income, expense and budget spending. Edits/deletes recalculate derived balances and spending. Budgets apply to expense categories, one per category/month; warning at 80%, reached/exceeded at 100%.

## HTTP shape

All routes below require a bearer token. Responses are direct DTO bodies, without an `ApiResponse` wrapper. Never accept a client `userId` as ownership authority. Use the authenticated principal and check ownership of every referenced wallet/category/record.

| Method | Route | Request | Success |
|---|---|---|---|
| GET | `/api/v1/wallets` | — | 200, `Wallet[]` |
| POST | `/api/v1/wallets` | `WalletRequest` | 201, `Wallet` |
| GET | `/api/v1/categories` | — | 200, `Category[]` |
| POST | `/api/v1/categories` | `CategoryRequest` | 201, `Category` |
| GET | `/api/v1/transactions?page=0&size=200` | zero-based pages | 200, `TransactionPage` |
| POST | `/api/v1/transactions` | `TransactionRequest` | 201 (or 200 idempotent replay), `Transaction` |
| PUT | `/api/v1/transactions/{transactionId}` | `TransactionRequest` | 200, `Transaction` |
| DELETE | `/api/v1/transactions/{transactionId}` | — | 204 |
| GET | `/api/v1/budgets` | — | 200, `Budget[]` |
| POST | `/api/v1/budgets` | `BudgetRequest` | 201, `Budget` |
| PUT | `/api/v1/budgets/{budgetId}` | `BudgetRequest` | 200, `Budget` |
| DELETE | `/api/v1/budgets/{budgetId}` | — | 204 |

```json
{
  "walletId": "uuid", "name": "Tiền mặt", "walletType": "CASH",
  "openingBalanceMinor": 2000000
}
```

`WalletRequest` has `name`, `walletType`, `openingBalanceMinor`. Lists must include all wallets referenced by returned transactions, including archived wallets; creation pickers currently assume returned references remain usable. Backend archive filtering and picker-specific active flags require a later agreed extension.

```json
{"categoryId":"uuid","name":"Ăn uống","categoryType":"EXPENSE"}
```

`CategoryRequest` has `name`, `categoryType` (`EXPENSE` or `INCOME`). Return all categories referenced by financial history. The server must create initial default categories or permit creation when the list is empty.

```json
{
  "transactionId":"uuid", "transactionType":"EXPENSE", "amountMinor":95000,
  "categoryId":"uuid", "walletId":"uuid", "destinationWalletId":null,
  "occurredAt":"2026-10-30T08:45:00.000Z", "note":"Cà phê", "payee":"Quán Đồng"
}
```

`TransactionRequest` has the same fields except `transactionId`, plus `clientRequestId` (stable UUID from the create form). On POST, scope this key to the authenticated user: an identical retry must return the original transaction without posting again; changed content with the same key must produce 409. On PUT, the path ID is authoritative; ignore `clientRequestId` for creation purposes. Omitted nullable values mean absent: income/expense need a matching category and one wallet; transfers omit category and require distinct source/destination wallets. Notes max 2,000 characters, payee max 160. Timestamps are ISO 8601 UTC (`Z`, optional fractional seconds).

`TransactionPage` is `{ "items": [Transaction], "page": 0, "totalPages": 1 }`. Return posted transactions only, in stable descending occurrence order with ID as a tie breaker. The current client reads all pages before publishing a snapshot, filters locally, and derives balances from all opening balances + all posted transactions. A failed page must not publish a partial snapshot. For large histories, replace this with server-computed balances/aggregates and paginated screen queries before production scale. Consistent pagination during concurrent edits remains a server integration requirement.

```json
{"budgetId":"uuid","categoryId":"uuid","month":"2026-10","limitMinor":3000000}
```

`BudgetRequest` has `categoryId`, `month`, `limitMinor`. Map the month to local first-of-month inclusive / next-month exclusive dates in the existing budget schema. Reject overlapping active budgets with 409. Deleting a budget preserves transactions. Deleting/voiding a transaction removes its effect from balances and budget usage atomically.

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
