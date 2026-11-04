# Finance Manager API Contract

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
| PostgreSQL columns | `wallet_id`, `wallet_type` |
| Derived balance view | `fintrack.wallet_balances` |
| UI labels | Ví, Danh sách ví, Thêm ví, Chi tiết ví, Sửa ví |
| Supported wallet types | `CASH`, `BANK`, `SAVINGS` |

An electronic-wallet type requires a separate approved contract and migration before use.
Money is integer VND. User ownership must be checked for every wallet operation and financial posting.
Balances are derived from opening balances and posted ledger entries; changing a displayed balance must
not bypass the ledger. Archive wallets while preserving their transaction history.

## Availability

Only infrastructure endpoints are implemented currently: `GET /actuator/health`, liveness/readiness,
and development OpenAPI/Swagger. Health returns its native Actuator payload, such as `{"status":"UP"}`.
The wallet routes and types above reserve the naming for future implementation; they are not implemented APIs.
Do not add compatibility routes using the previous financial-domain name.

The proposed Authentication contract, token lifecycle, email sender and implementation checklist are documented
in [AUTHENTICATION_IMPLEMENTATION.md](AUTHENTICATION_IMPLEMENTATION.md). Those endpoints remain unimplemented
until the corresponding backend code and migration are merged.

## Response conventions

Future Wallet and other business APIs return their DTO directly; an empty success uses HTTP 204.
Authentication is an explicit exception: successful login uses `ApiResponse<LoginResponse>`. Successful register
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
