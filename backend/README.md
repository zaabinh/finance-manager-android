# Backend

Spring Boot REST API (Java 21) with PostgreSQL.

**Stack:** Spring Web · Spring Security · JWT (access + refresh token) · Spring Data JPA / Hibernate · Bean Validation · Flyway · Swagger/OpenAPI

## Layered architecture

```
Controller → Service → Repository → JPA/Hibernate → PostgreSQL
```

| Package | Purpose |
|---------|---------|
| `config/` | Security, CORS, OpenAPI, Jackson, scheduling configuration |
| `security/` | JWT provider, authentication filter, `UserDetails`, entry points |
| `controller/` | REST controllers. Thin: validation + delegation only. `admin/` holds `/api/admin/**` |
| `service/` | Business rules, ownership checks, financial calculations (`impl/` for implementations) |
| `repository/` | Spring Data JPA repositories |
| `entity/` | JPA entities; `enums/` for `WalletType`, `CategoryType`, `TransactionType`, `InputMethod`, ... |
| `dto/` | `request/` and `response/` DTOs. Entities are never exposed directly. |
| `mapper/` | Entity ↔ DTO mapping |
| `exception/` | Custom exceptions + `GlobalExceptionHandler` |
| `scheduler/` | Daily reminders, budget threshold checks |
| `util/` | Helpers |

Resources: `application*.yml` (profiles `dev`, `prod`) and Flyway migrations in `db/migration/`.

## Rules

- Every query on user data must be scoped to the authenticated user.
- Never log passwords or tokens.
- Use soft deletion where historical consistency matters (wallets, categories).
