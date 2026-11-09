# Notifications rollout

Apply V11 through Flyway after V1–V10; never edit historical migrations. Inbox/settings
APIs can be reviewed with producers and email disabled (both default false):

```dotenv
NOTIFICATION_PRODUCERS_ENABLED=false
NOTIFICATION_EMAIL_ENABLED=false
NOTIFICATION_ACTION_ORIGIN=
```

Only set NOTIFICATION_ACTION_ORIGIN to an existing HTTPS origin owned by the deployment.
It must serve authenticated landing/App Link routes `/app/transactions/new`,
`/app/budgets/{id}`, `/app/settings/notifications`. Do not use placeholders in production.
GET links must not auto-login or change data. Existing MAIL_FROM, RESEND_API_KEY and
public HTTPS MAIL_LOGO_URL remain the mail configuration; never commit credentials.

Review fresh and upgrade migration, authenticated ownership/read-state, financial
regression, V2 verified-email consent, timezone/DST, baseline and scheduler tests first.
Enable producers in a test environment with controlled accounts. Inspect inbox and
outbox with a test sender. Only enable email after real routes, verified sender and
explicit recipient consent have been confirmed. This implementation run sends no real
mail and performs no deployment.

Poll delays in milliseconds can be supplied as Spring properties:
`app.notifications.poll-delay-ms=60000`, `reconcile-delay-ms=900000`,
`delivery-delay-ms=10000`. Batches are bounded to 100 users / 20 deliveries. Multiple
instances use PostgreSQL advisory/row locks and SKIP LOCKED, without another queue.
Do not run scheduling inside a financial caller transaction.

Metrics: `fintrack.notification.poll.failure` by operation,
`fintrack.notification.user.failure` by operation, `fintrack.notification.delivery` by
state/allowlisted reason. Configure operational alerts for failures and DEAD rows; raw
provider exceptions, recipients and payloads are not logged. Backlog/oldest-age dashboards,
retention/purge jobs and delivery/bounce webhooks remain operational follow-up work.
Sensitive outbox bodies are persisted for durable retries: restrict DB access and apply
the deployment's backup/encryption/retention policies. No retention job is enabled.

Disabling producers stops new inbox production; committed finance events remain pending.
Disabling email pauses delivery; outbox stays durable and expiry is checked on recovery.
Re-enable baselines may coalesce changes until evaluated; do not promise every transient
risk alert. Keep sender configuration (particularly MAIL_FROM) stable during outstanding
retries. Retry horizon is 23 hours / at most five claims and event expiry; never change
delivery keys to resend uncertain outcomes. SENT is provider acceptance, not confirmed
delivery. Turning a setting off cannot recall a previously accepted email.

Use an isolated disposable database for `NotificationsPostgresIT`, which truncates its
user fixtures. Never point FINANCE_TEST_JDBC_URL at a development, staging or production
database with data to preserve. Local command from backend (JDK 21 + Maven):

```text
mvn -Ppostgres-it verify
```

Optionally supply FINANCE_TEST_JDBC_URL, FINANCE_TEST_DB_USER and
FINANCE_TEST_DB_PASSWORD for that disposable database; otherwise Testcontainers starts
PostgreSQL. Keep runtime database migration separate from fixture tests.
