# Finance Manager API Contract

## Current finance contract (2026-11-07)

[FINANCE_CRUD_CONTRACT.md](FINANCE_CRUD_CONTRACT.md) is the agreed implementation target for
Wallet, Category, Budget and income/expense Transaction APIs. It takes precedence over older
requirements, use cases, roadmap text and the historical Android handoff. These finance endpoints
are planned, not evidence of completed implementation.

- Integer VND only; UTC persistence and user-preference IANA timezone for dates/months.
- Every category belongs to one user, including initial seeded categories; no global ownerless categories.
- Wallet balances must never become negative after a financial mutation. Soft-deleting an expense
  restores its contribution once; removing income must be rejected if it would make a wallet negative.
- Archive preserves history. Derived balances use the existing ledger/view; no balance cache yet.
- Transfers are deferred until income/expense CRUD is complete.

Implementation plans: [Wallet](../development/wallet-crud-plan.md),
[Category](../development/category-crud-plan.md), [Budget](../development/budget-crud-plan.md),
[Transaction](../development/transaction-implementation-plan.md).

## Terminology

The financial domain is **Wallet** (`wallet`, plural `wallets`), displayed as **Ví** in Vietnamese.
Use Wallet consistently in backend types, Android DTOs, endpoint paths, UI copy and product specifications.
Authentication identities remain User or tài khoản đăng nhập; they are separate from wallets.

| Layer | Canonical naming |
| --- | --- |
| API collection | `/api/v1/wallets` |
| API item | `/api/v1/wallets/{walletId}` |
| JSON identifiers/types | `walletId`, `walletType` |
| Future Java types | `Wallet`, `WalletType`, `WalletController`, `WalletService`, `WalletRepository` |
| Future request/response types | `CreateWalletRequest`, `UpdateWalletRequest`, `WalletResponse` |
| PostgreSQL table | `fintrack.wallets` |
| PostgreSQL columns | `wallets.id`, `wallets.wallet_type`; references use `wallet_id` |
| Derived balance view | `fintrack.wallet_balances` |
| UI labels | Ví, Danh sách ví, Thêm ví, Chi tiết ví, Sửa ví |
| Supported wallet types | `CASH`, `BANK`, `SAVINGS` |

An electronic-wallet type requires a separate approved contract and migration before use.
Money is integer VND. User ownership must be checked for every wallet operation and financial posting.
Balances are derived from opening balances and posted ledger entries; changing a displayed balance must
not bypass the ledger. Archive wallets while preserving their transaction history.

## Availability

Infrastructure endpoints, Authentication and User/Profile/Preferences/Notification Settings have implementation
in the current source. Health returns its native Actuator payload, such as `{"status":"UP"}`.
Wallet, Category, Budget and Transaction backend APIs remain planned.
Do not add compatibility routes using the previous financial-domain name.

The Authentication contract and implemented token lifecycle are documented
in [AUTHENTICATION_IMPLEMENTATION.md](AUTHENTICATION_IMPLEMENTATION.md). Android and backend auth DTOs and routes
must follow that contract.

User/Profile response conventions are documented in
[the User Profile & Settings contract](../development/user_profile_settings_backend_guide.md).

## Response conventions

Future Wallet and other business APIs return their DTO directly; an empty success uses HTTP 204.
Authentication and User/Profile/Settings are explicit exceptions: successful login uses `ApiResponse<LoginResponse>`
and User endpoints use `ApiResponse<T>`. Successful register
returns HTTP 201 with an empty body. Use appropriate HTTP statuses and ISO 8601 UTC timestamps.
Request/response DTOs remain separate from persistence entities. `UserResponse` is reserved for
`GET /api/v1/users/me` and must not be embedded in login or register responses.

Errors use the implemented `ApiErrorResponse`: `timestamp`, `status`, `errorCode`, `message`, `path`,
and `fieldErrors` containing `field`/`message` pairs. No stack traces or submitted secrets are returned.
Validate and test the exact feature DTOs before implementing a business flow on either client or server.

## Database evolution

`backend/src/main/resources/db/migration/` is the only schema source of truth. V1/V2 are immutable history;
V3 changes the financial-domain names to wallet without dropping data. New and existing databases must
run the same Flyway chain. Historical migration names are not the current API or domain vocabulary.
The ignored root `/database/` files are reference material only and must not be executed for deployment.
