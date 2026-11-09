# Notifications backend implementation report

Branch: `feature/notification`, base `575ae54`. Worktree: `.worktrees/notification`.
Implementation/review: 2026-11-09–10 (Asia/Saigon). Retained for review without merge/push.
Source plan: `docs/development/notifications-backend-plan.md`; final contract:
`docs/api/NOTIFICATIONS.md`; rollout: `docs/deployment/NOTIFICATIONS.md`.

## Implemented and final architecture

Authenticated owner-scoped inbox list/detail/unread count, mark-read, snapshot-cutoff
read-all and idempotent soft delete. Count/page/unread use a database repeatable-read
snapshot; per-user insert/read-all locking and statement-time createdAt preserve cutoff
behavior. Unknown/duplicate list parameters, invalid booleans/bodies and future cutoff
are rejected. Deleted items remain tombstones for semantic event deduplication.

V2 notification settings add explicit finance email consent, default false. Unverified
enable is 409. V1 and V2 partial mutations lock the settings row and preserve concurrent
changes/consent. Existing auth OTP/reset emails and their sender/renderer remain unchanged.

V11 migration adds inbox/outbox, budget cycle state, finance events and daily schedules.
Append-only database AFTER triggers keep finance events atomic with actual expense,
budget/parent lifecycle, settings and timezone writes. Rollback/no-op/client replay do not
create an extra committed financial event; transfers/pure income do not affect category
expense usage. No financial writer calls a provider or takes evaluator-state locks.

`FinanceNotificationProducer` uses existing JDBC/BudgetRepository/BudgetResponse, injectable
Clock and bounded per-user polling/reconciliation. Budget evaluation is serialized per
user and REPEATABLE_READ to avoid mixing old settings/timezone with new usage. Only
active current-local-month children under active monthly parents emit. Severity cycles,
enable/deployment baselines, superseded warning cancellation and immutable event keys
prevent repeat alerts. Archived categories retain financial history. Polling may coalesce
short-lived changes and changes arriving before a baseline reset is processed.

Daily scheduling persists UTC due times from each user's IANA zone/local time, with DST
gap/overlap rules, two-hour same-date grace, canonical schedule versions, row claims and
user/date dedup across restarts/instances/settings changes. Per-user poll failures are
isolated and counted without logging personal payloads.

`NotificationOutboxWorker` commits claims before network, uses a 90-second lease and
claim-token fencing, freezes rendered HTML/recipient/subject/delivery ID, checks live
consent/status/recipient/deletion/expiry/budget or daily context, and cancels stale work.
Network/429/5xx/concurrent-idempotent 409 retry with bounded jitter/Retry-After; permanent
failures terminate. Five attempts maximum, within event expiry and 23-hour horizon.
The existing Resend sender's stable idempotency key and timeouts are reused. SENT only
means accepted by the provider; it never marks an inbox item read or proves delivery.
No exactly-once end-to-end guarantee is claimed. MAIL_FROM must stay stable during retries.

Three templates reuse the existing escaped renderer/shared layout/logo/footer:
daily-reminder, budget-warning and budget-exceeded. Variables are exact allowlists,
links use configured HTTPS origin/server paths, subject CRLF is sanitized, money is
integer VND, and exactly-at-limit copy has zero excess. There is no public create/mail
test API. Producers and email default disabled; no real mail or deployment was performed.

## Main changed files

- `backend/src/main/resources/db/migration/V11__notifications.sql`
- `controller/NotificationController.java`, `NotificationSettingsV2Controller.java`
- `repository/NotificationRepository.java`, `NotificationSettingsRepository.java`
- `service/notification/NotificationService.java`, `NotificationSettingsV2Service.java`
- `service/notification/FinanceNotificationProducer.java`, `NotificationOutboxWorker.java`
- `service/notification/FinanceEmailContent.java`, `NotificationJson.java`
- `config/NotificationSchedulingConfig.java`, `application.yml`, `exception/ErrorCode.java`
- `service/user/UserService.java` (V1 partial-update locking only)
- `dto/response/notification/NotificationResponse.java`, three financial email templates
- `FinanceNotificationsTest`, `NotificationsPostgresIT`, extended `FinanceApiIT`,
  `ApplicationSmokeTest`, `UserServiceTest`, `PostgresMigrationIT`; API/rollout/source-plan documents.

Java paths above are relative to `backend/src/main/java/com/fintrack/api` where omitted.
No Android source, auth flow, financial CRUD contract, dependency version or historical
migration was changed. Dashboard code is deliberately absent from this independent branch.

## Verification

Environment: Java 21.0.11, Maven, PostgreSQL 18.4 on loopback, isolated disposable databases.
Existing local Maven cache was used offline. Flyway fresh migration V1–V11 passed.
Flyway warns PG18 is newer than its tested PG17 baseline; actual migration/tests use PG18.

Initial focused run: 4 unit/template/DST/provider-classification tests plus 19 real
PostgreSQL tests passed, zero failures/errors/skips; Maven verify and executable jar build
passed. Final full unit regression passed **159/159**, zero failures/errors/skips.
The complete integration run covered **83 tests**: Authentication 8, Budget 9,
Category 5, Finance API 9, migration/restart 2, Redis rate limiting 5, Notifications 20,
Transaction 20 and Wallet 5. Initially 82 passed and one migration assertion failed
because its exact table list still described V10. That expectation was updated for
the five V11 tables, and both migration tests were rerun: **2/2 pass**, Maven BUILD SUCCESS.
All nine suites' latest reports now total **83/83 pass**, zero failures/errors/skips.
No production logic changed after the full run; only the migration-test expectation.
The full suite was not needlessly repeated after that focused correction.

Unit/complete integration command: `mvn -Ppostgres-it verify` (initial integration result
described above). Correction verification: `mvn -Ppostgres-it -Dit.test=PostgresMigrationIT
test-compile failsafe:integration-test failsafe:verify`. The executable backend JAR was
successfully packaged during the complete run. Authentication/Redis/migration tests also
ran real Docker Testcontainers (PostgreSQL 17.7 / Redis 7.4); finance/notifications tests
used the isolated loopback PostgreSQL 18.4 database. No real provider mail was sent.

SQL upgrade smoke in a separate empty database applied V1–V10, inserted a verified user,
existing notification settings (threshold85) and wallet balance123456, then applied V11.
Assertions passed: consent false, threshold unchanged, balance unchanged, one deployment
baseline event, zero notification/outbox rows. This is a SQL upgrade smoke, separate
from the fresh Flyway history validation.

PostgreSQL cases cover exact risk thresholds/cycles/void/archive/parent/context, replay
and rollback, concurrent evaluators/daily schedulers, read-all/insert locks, snapshot cutoff
between SELECTs, owner isolation, stable readAt/soft-delete tombstones, settings V1/V2
concurrency, unverified consent, grace/date/version handling, frozen retries, provider
outside transaction, lease recovery/fencing and stale-recipient/disable/void/delete/expiry.
Unit tests cover HTML escaping/shared layout/exact variables, unsafe links/subjects,
DST gap/overlap/leap day and provider status/Retry-After classification. Real JWT HTTP
tests verify ownership, direct response shape, read/delete/cutoff and settings serialization.

Reproduction from backend: `mvn -Ppostgres-it verify`. Supply only a dedicated disposable
FINANCE_TEST_JDBC_URL/user/password, or use Testcontainers. NotificationsPostgresIT clears
its database's user fixtures; never run it against data to preserve. The local loopback
run uses sslmode=disable and bounded connect/socket timeouts to avoid per-connection SSL
negotiation delays; production TLS configuration is unchanged.

## Remaining work and practical limits

- Android: Retrofit/Repository/ViewModel, paginated inbox/read/delete/cutoff refresh,
  V2 consent UI, session-switch guards, user-timezone grouping, typed target navigation,
  archived/404 handling and device/backend tests. OS permission remains a separate control.
- Home badge integration is deferred until both independent branches are reviewed and
  combined by the user. Use this inbox unread predicate inside the dashboard snapshot;
  sending email does not affect unread. Do not fabricate zero on database errors.
- Deployment must supply actual HTTPS App Links/landing pages and verified provider/sender
  configuration before enabling email. No Android build, device/network test, real email
  delivery, email-client visual test, staging deploy or production load test was performed.
- Retention/purge, backlog/oldest-age dashboards, alert wiring and delivery/bounce webhooks
  remain operational work. Persisted outbox payloads require controlled DB/backup access.
- Per-user failures retain durable events for retry and raise counters; a permanently
  broken user needs investigation. Batches and polling are bounded, not a guarantee of
  real-time delivery or preservation of every intermediate financial state.
- Settings can change after the final provider check; already accepted mail cannot be
  recalled. A crash after acceptance may repeat a provider request with its original key.
  Never change delivery keys to resend an ambiguous result outside the retry window.
