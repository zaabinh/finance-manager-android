# Finance Manager Backend

Infrastructure for the FinTrack REST API. Core database tables are defined in Flyway;
business APIs and authentication are not implemented yet.

## Stack and prerequisites

- Java **21**; set `JAVA_HOME` to JDK 21.
- Spring Boot **3.5.16**, Web, Security, Validation, Data JPA and Actuator.
- Springdoc OpenAPI **2.8.17**, compatible with Boot 3.5.x.
- Deployment profiles: development `dev`, staging `staging`, production `prod`.
- PostgreSQL **17** (Compose uses `17-alpine`; integration tests use 17.7), Flyway Core and PostgreSQL support.
- Lombok, JUnit 5, MockMvc, Spring Security Test; Testcontainers PostgreSQL for integration tests.
- Maven **3.9.16** via Maven Wrapper; no global Maven installation required.
- Maven Central access on first build; Docker Engine/Compose and image registry access for container tests.

Spring Boot manages dependency/plugin versions except Springdoc, which has its own release cycle.
References: [Boot requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
and [Springdoc compatibility](https://springdoc.org/v2/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot).

Run commands from `backend/`. Windows uses `.\mvnw.cmd` instead of `./mvnw`.
If a Unix checkout does not retain executable permissions, run `chmod +x mvnw` first.

## Packages

```text
com.fintrack.api/
  FinanceManagerApplication.java
  controller/
  service/
  repository/
  entity/
  dto/request/
  dto/response/       ApiErrorResponse
  mapper/
  security/          SecurityErrorWriter
  config/            SecurityConfig, OpenApiConfig
  exception/         ErrorCode, GlobalExceptionHandler
```

Component scanning starts at this root. Existing scaffold directories remain in place.
Empty layers use `package-info.java`; implementations are added with their features.
Controllers validate and delegate; services own business rules; repositories own persistence.
Never expose entities directly through the API.

## Environment variables

Copy the repository-root `.env.example` to the root `.env`, set `POSTGRES_PASSWORD`, and configure
`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PORT` (default development port: **5433**).
Docker Compose uses this root configuration. Development imports `../.env` and then the optional
backend `.env` as Java properties files, relative to the backend working directory:
use `KEY=value` without shell quotes, `export`, or inline comments. Real environment variables take precedence.
Backend `DB_*` values, when provided, override corresponding root `POSTGRES_*` settings. Keep them aligned
with Compose, or omit backend overrides to share the root configuration.
Production does not import `.env`; inject configuration through the deployment environment or a secret manager.
The `.env` file is ignored by Git.

| Variable | Purpose/default |
| --- | --- |
| `DB_HOST` | PostgreSQL hostname; defaults to `localhost` |
| `DB_PORT` | Overrides `POSTGRES_PORT`; defaults to `5433` |
| `DB_NAME` | Overrides `POSTGRES_DB` |
| `DB_USERNAME` | Overrides `POSTGRES_USER` |
| `DB_PASSWORD` | Overrides `POSTGRES_PASSWORD`; supply your own |
| `CORS_ALLOWED_ORIGINS` | Comma-separated exact HTTP(S) origins; empty by default |
| `SERVER_PORT` | HTTP port; defaults to `8080` |
| `SPRING_PROFILES_ACTIVE` | Set as a **process environment variable**; defaults to profile `dev` |

`SPRING_PROFILES_ACTIVE` in `.env` is informational for tooling; switching profiles requires setting the process variable.
Do not use personal/shared production databases for development or tests.

## PostgreSQL

Check local availability with `pg_isready -h localhost -p 5432`.
In an administrative `psql` session, create a separate role/database if they do not already exist:

```sql
CREATE ROLE fintrack_dev LOGIN;
\password fintrack_dev
CREATE DATABASE fintrack_dev OWNER fintrack_dev;
```

The password is entered interactively. Set matching values in `.env` or environment variables.
Verify authentication with `psql -h localhost -U fintrack_dev -d fintrack_dev -W`.
The development role needs permission to create its `fintrack` schema.

Alternatively, start PostgreSQL with Docker Compose:

```sh
docker compose -f compose.yaml up -d --wait postgres
docker compose -f compose.yaml ps
```

The container uses `127.0.0.1:5433` by default to coexist with local PostgreSQL on 5432.
Set `POSTGRES_PORT` in the root `.env` to change it; update any backend `DB_PORT` override as well.
The container exposes only a loopback port and uses the `finance-manager-android_postgres_data` volume.
`DB_HOST=localhost` applies when the application runs on the host.
Changing initialization credentials does not change users in an existing volume.
Do not run `down -v` against data you need. The backend Compose file includes the canonical root
`docker-compose.yml` (Compose 2.20+ required). Both entrypoints use the same `finance-manager-android`
project and `finance-manager-postgres` container. Compose initializes only the database/user;
it does not mount or execute schema scripts.

## Run and profiles

See [basic CI](../.github/workflows/README.md).
Local Compose defaults to `dev`. Set the process environment `SPRING_PROFILES_ACTIVE=staging`
for staging or `prod` for production. Only development imports local `.env` files; staging and production
require deployment-injected database settings. Staging disables Swagger and SQL logging like production.
Production CD is outside the current phase; admin CI is deferred until admin source exists.

```sh
./mvnw spring-boot:run
```

Development is the default. Select profiles explicitly as follows:

```sh
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
SPRING_PROFILES_ACTIVE=prod java -jar target/finance-manager-backend-0.0.1-SNAPSHOT.jar
```

PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:SPRING_PROFILES_ACTIVE = 'dev'
.\mvnw.cmd spring-boot:run
```

For production, export all database settings before starting. Activate one environment profile at a time.
Datasource, Flyway and JPA remain enabled in normal application runs. Startup fails on database connection
or schema validation failures. Open Session in View is disabled; Hibernate uses `validate`.
The application entrypoint and PostgreSQL integration-test JVM use UTC, avoiding reliance on host timezone
aliases such as `Asia/Saigon`. User display preferences remain `Asia/Ho_Chi_Minh` by default.

## Flyway

Application startup automatically runs `src/main/resources/db/migration/` before JPA starts.
`backend/src/main/resources/db/migration/` is the **only source of truth for schema definitions**.
`V1__init_schema.sql` initializes `fintrack`; Flyway may create the schema before V1 to store its history.
V2 initializes the core tables; V3 standardizes the financial domain as **Wallet / Ví** while preserving data.
The resulting tables are `users`, `user_preferences`, `refresh_sessions`, `wallets`, `categories`,
`transactions`, `transaction_entries`, and `budgets`, plus indexes, foreign keys, deferred posting-validation
triggers and the `wallet_balances` view. Use `wallet_id` / `wallet_type` in SQL and `walletId` / `walletType`
in API contracts. Monetary amounts use integer VND. See [the API contract](../docs/api/CONTRACT.md).
V1/V2 are applied migration history and must not be edited; the old financial-domain names occur only
in immutable migrations and explicit upgrade tests. Both new and existing databases run V3.
`citext` and `btree_gist` extensions are installed in `public`; application tables live in `fintrack`.
The migration role needs permission to install these trusted extensions (or have an administrator provision them).
Flyway and Hibernate share the default application schema.
The root `/database/` directory is untracked and ignored, and is never used by Compose, Maven or tests.
No Maven Flyway plugin or separate credentials are needed. Never edit an applied migration;
add `V2__...sql` for later changes. Flyway clean is disabled.

After startup, verify in `psql`:

```sql
SELECT installed_rank, version, description, checksum, success
FROM fintrack.flyway_schema_history
ORDER BY installed_rank;
```

Expect one successful row for each of versions `1`, `2` and `3` (a schema creation history row may also exist).
Stop and restart, then repeat the query: each version must still appear exactly once.
Startup logs should report the schema is up to date. Do not automatically baseline or repair shared databases.

## Tests and CI

```sh
./mvnw clean verify
```

This packages the executable jar and runs context/health, dev/prod security, CORS, OpenAPI and exception tests.
The **test-only** profile excludes datasource/JPA/Flyway auto-configuration and removes the DB readiness contributor.
These tests need Java 21 and Maven Central access; they use neither personal secrets nor H2.
They verify web infrastructure, **not database connectivity or production migrations**.

To additionally verify actual migrations on isolated PostgreSQL:

```sh
./mvnw -Ppostgres-it verify
```

`PostgresMigrationIT` uses Testcontainers PostgreSQL 17.7. It starts the complete application, checks health,
readiness, Swagger and OpenAPI over HTTP, verifies V1/V2/V3 history and all eight core tables, runs
`src/test/resources/db/core-schema-smoke.sql` to check postings and derived balances with rollback,
verifies a second migration executes zero migrations, and restarts against the same database.
A separate populated V2 database verifies that V3 preserves wallet IDs, ledger references and balances,
and renames the corresponding constraints without dropping data.
Docker must be running and accessible, with permission
to pull `postgres:17.7` and Testcontainers support images. It fails instead of silently skipping when Docker
is unavailable. No local DB credentials are used; container data is temporary.
CI should use JDK 21 and run both commands on a Docker-enabled runner for database acceptance.

Reports: `target/surefire-reports/` and `target/failsafe-reports/`.

### Verification on 2026-10-27 (Windows)

- JDK 21.0.11: Maven unit/package verification succeeded with 13 smoke/unit tests;
  two PostgreSQL integration tests also passed, with no failures/errors/skips.
- Testcontainers PostgreSQL **17.7** verified V1/V2/V3, all eight tables, posting constraints, the wallet balance view,
  rollback of test data, real health/Swagger/OpenAPI endpoints, and restart without repeat migration.
  A populated V2 database also verified preserved wallet IDs, entry links and balances after upgrading to V3.
- The wallet terminology update rebuilt the Docker backend and applied V3 to the existing PostgreSQL volume.
  No database reset or data deletion was performed for this upgrade. Normalized fingerprints of wallet rows,
  ledger entries and derived balances were identical before and after migration.
- The new Compose project is `finance-manager-android`; PostgreSQL **17.10** (`17-alpine`) is healthy on
  `127.0.0.1:5433`. Port 5432 could not be bound, so root and backend local port settings were aligned to 5433.
- The rebuilt Compose backend reports health `UP`. Flyway history has one successful row per version
  `1`, `2` and `3`. Integration-test restart reported no migration necessary.
- PostgreSQL queries verified the eight core tables, `wallet_balances`, `wallet_id` / `wallet_type`, and
  the absence of the previous financial table/view names and constraint/index names. Compose backend and
  PostgreSQL remain running.
- `/database/` is ignored and removed from the Git index, with its local files retained.

## Endpoints and security

| Endpoint | Development | Production |
| --- | --- | --- |
| `http://localhost:8080/actuator/health` | Public | Public |
| `/actuator/health/liveness` | Public | Public |
| `/actuator/health/readiness` | Public; includes DB | Public; includes DB |
| `http://localhost:8080/swagger-ui/index.html` | Public | Disabled and protected |
| `http://localhost:8080/v3/api-docs` | Public | Disabled and protected |

Only Actuator health is exposed; component details are hidden. Liveness does not depend on the DB.
Security is stateless; form login, HTTP Basic and generated users are disabled. All other routes require
authentication; until authentication is implemented, anonymous calls receive JSON 401.
CORS allows only configured exact origins, never wildcard origins or credentialed requests.
CSRF is disabled for this stateless foundation; reassess if cookie authentication is introduced.

Errors contain `timestamp`, `status`, `errorCode`, `message`, `path`, `fieldErrors`.
Validation identifies invalid fields without echoing submitted values. Parser details and stack traces are
not returned. Unexpected failures are logged server-side; protect/redact operational logs as needed.

## Production

- Activate `prod`; Swagger/OpenAPI and SQL/bind/extract logging are disabled.
- Supply secrets externally; use HTTP TLS and database TLS (deployment datasource URL with `sslmode=verify-full`).
- Configure exact frontend origins; no origins are allowed unless supplied.
- Use a least-privilege application role and a separate migration role when needed.
- Restrict DB/management networks; never expose all Actuator endpoints or public health details.
- Set connection limits, backups, trusted proxy settings and log retention for the deployment.
- Implement/test authentication and authorization before enabling business APIs.

## Next modules

Implement Authentication and token lifecycle, then User, Wallet, Transaction and Budget.
Add versioned migrations and service ownership checks as each module is designed.
