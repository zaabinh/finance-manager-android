# Flyway migrations

This directory is the only source of truth for the database schema.

- `V1__init_schema.sql`: application schema `fintrack`.
- `V2__create_core_tables.sql`: immutable core-schema history, required extensions, indexes, constraints, posting-validation triggers and balance view.
- `V3__rename_accounts_to_wallets.sql`: data-preserving terminology migration to `wallets`, `wallet_id`, `wallet_type`, `wallet_balances`, and wallet-named constraints/indexes. Earlier names remain only in historical migrations and upgrade verification.
- `V4__add_authentication_support.sql`: non-null `email_verified`/`onboarding_completed` booleans, hashed one-time challenges and refresh rotation links/client metadata. Existing users remain unverified until they complete email verification.
- `V5__refresh_token_families.sql`: refresh token families (`family_id`, backfilled per existing rotation chain), `revoke_reason`, and the sealed successor used for the concurrent-refresh grace period.

Docker Compose creates only the PostgreSQL database and role. Spring Boot runs Flyway before JPA starts.
Application tables and functions must use schema `fintrack`; extension objects live in `public`.
Flyway manages transactions: do not wrap migrations in explicit `BEGIN`/`COMMIT` statements.

Never edit an applied migration. Add the next `V<version>__<description>.sql` instead.
Do not maintain parallel schema scripts under `/database/` or Docker init hooks.
Test fixtures are under `src/test/resources/db/` and roll back their changes.
