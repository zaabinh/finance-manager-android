# Flyway migrations

Naming: `V<version>__<description>.sql`, for example:

- `V1__init_schema.sql`
- `V2__seed_default_categories.sql`

Never edit a migration that has already been applied. Add a new one instead.
