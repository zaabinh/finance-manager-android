# Notifications backend contract

Implemented on `feature/notification`, based on `575ae54`. Source requirements:
[notifications-backend-plan.md](../development/notifications-backend-plan.md).
This branch is independent of `feature/home-dashboard` and does not change Android UI.

## Authenticated inbox API

Every owner is taken from the Bearer JWT. A missing/invalid JWT returns 401;
another owner's ID returns the same 404 as an unknown ID. Responses are direct JSON,
except settings which retain the existing User API envelope.

| Method and path | Request / response |
| --- | --- |
| GET `/api/v1/notifications` | Optional `page=0`, `size=20` (1–100), `readStatus=ALL` (`ALL/READ/UNREAD`). Returns `items,page,totalPages,unreadCount,snapshotAt`. Negative pages, unknown/duplicate parameters are 400. |
| GET `/api/v1/notifications/{id}` | One undeleted item, or 404. |
| GET `/api/v1/notifications/unread-count` | `unreadCount,snapshotAt`. Read and deleted items excluded. |
| PATCH `/api/v1/notifications/{id}/read` | Exactly `{"read":true}`; returns item. Repeated read preserves the first readAt. False, coercion and unknown fields rejected. |
| POST `/api/v1/notifications/read-all` | `{"through":"2026-11-09T12:00:00Z"}` from a prior snapshot. Returns `affectedCount,unreadCount,snapshotAt`. Invalid/non-UTC/future cutoff rejected. |
| DELETE `/api/v1/notifications/{id}` | Soft delete; own repeat delete returns 204. Detail/read thereafter 404; associated pending delivery cancelled. |

An item contains `notificationId,type,title,body,createdAt,readAt,amountMinor,target`.
Types are `DAILY_REMINDER`, `BUDGET_WARNING`, `BUDGET_EXCEEDED`. Amounts are integer VND,
nullable for reminders. Targets are typed maps: `TRANSACTION_CREATE` with localDate,
or `BUDGET_DETAIL` with budgetId/month. No email recipient, delivery payload or financial
note is exposed. Opening a target must recheck authentication/ownership and archive status.

Ordering is createdAt DESC, UUID DESC. List count/items/unread share a read-only
REPEATABLE_READ snapshot. snapshotAt is the first statement's database timestamp,
captured before subsequent reads. It is a created-at cutoff, not a durable change cursor
or a claim that every item before it was displayed on the current page. A transaction
which inserted an older item but committed after that snapshot can still be covered.
Inbox insert and read-all serialize through the same per-user advisory transaction lock;
insert timestamps are assigned in a new statement after acquiring that lock. Items
created after the supplied cutoff stay unread. Email SENT never changes readAt.

## Versioned email consent

GET/PATCH `/api/v2/users/me/notification-settings` use the existing
`{success,message,data,code}` User envelope. Settings include all V1 fields plus
`emailFinanceEnabled`, default false, and updatedAt. PATCH accepts partial boolean
fields, minute-precision dailyReminderTime and integer threshold 1–100. Omitted/null
fields preserve existing values; an entirely empty patch is 400. Unknown fields and
string/number boolean coercion are rejected. Explicit enable requires a verified account
email; otherwise 409 `NOTIFICATION_EMAIL_UNVERIFIED`.

Both V1 and V2 serialize partial updates on the settings row. V1 never clears V2
email consent. Master/type flags govern inbox and financial email. Financial email
additionally requires an ACTIVE user, verified current recipient and explicit opt-in.
Android OS notification permission does not govern email consent. Auth verification and
forgot-password mail remain independent and unchanged.

## Durable production and delivery

Migration V11 adds inbox, immutable event keys, outbox, budget cycle state, durable finance
events, daily schedules and email consent. Append-only AFTER triggers record actual expense
create/edit/void, relevant budget/monthly-parent/settings/timezone mutations in the same
financial DB transaction. Replay/no-op/rollback do not publish a new committed event.
Pure income, transfers, wallet opening/archive do not change category-budget expense usage.
Writers do not call email or lock evaluator state. The poller acknowledges only observed
event IDs, including out-of-order commits.

Budget evaluation serializes per user and uses REPEATABLE_READ for one settings/timezone/
ledger snapshot. Existing BudgetRepository/BudgetResponse determine exact thresholds.
Only active children under active monthly parents for the current local month produce
alerts. Archived category history still counts. SAFE→WARNING emits once, direct EXCEEDED
emits only exceeded; a higher severity supersedes unsent warning. Returning SAFE starts
a new cycle. Context category/month changes start a new cycle. Disabled levels still
consume the observed severity. Deployment and re-enable baseline existing risk without
historical catch-up. Mutations before an enable-reset is processed can coalesce into that
baseline; polling does not preserve every intermediate risk transition.

Daily scheduling persists next_due_at and a canonical timezone/time/settings version.
DST gaps choose the first valid time, overlaps the earlier offset. Catch-up is at most
two hours and within the same local date. Schedule row locks with SKIP LOCKED and unique
user/date event keys prevent duplicates across instances, restarts, deletes and schedule
changes. Per-user failures are isolated and counted without logging personal payloads.

Delivery claims commit before network. A 90-second lease plus claim token fences stale
workers; the existing sender uses a 5-second connect and 10-second read timeout. Before
sending, recheck owner status/verification/consent/recipient, item deletion, budget cycle,
live severity/context or daily schedule, expiry and claim ownership. HTML, recipient,
subject and delivery ID remain unchanged on retry. No provider call runs in a finance
transaction. Disabled/archived/voided/deleted/stale deliveries are cancelled.

Transient network/429/5xx and concurrent-idempotent-request 409 retry with jitter,
Retry-After and bounded 1m/5m/15m/1h backoff, at most five attempts, within event expiry
and 23 hours from first claim. Other 4xx/template/config errors terminate. Stable
`email/{deliveryId}` keys use Resend's [24-hour idempotency window](https://resend.com/docs/dashboard/emails/idempotency-keys);
[provider error semantics](https://resend.com/docs/api-reference/errors) distinguish
concurrent requests from a changed payload. Keep MAIL_FROM unchanged while deliveries
remain outstanding. A crash after acceptance can repeat a provider request with the same
key; there is no end-to-end exactly-once or delivery-receipt guarantee. SENT means the
provider call was accepted. A settings change after the final check cannot recall mail.

## Templates, deployment and Android handoff

`daily-reminder`, `budget-warning`, `budget-exceeded` reuse the existing escaped renderer,
layout/logo/footer and sender. Exact variable sets, server-owned HTTPS origin/path
allowlists, subject sanitization and VND formatting apply; no OTP variables. At exactly
the limit, copy says the limit is reached and overAmount is zero.

See [NOTIFICATIONS.md deployment guide](../deployment/NOTIFICATIONS.md) for feature flags
and activation. No public create/test-mail endpoint is added. Android still needs Retrofit,
Repository/ViewModel, real pagination/read/delete, snapshot-based read-all, V2 consent,
session guards, grouping by user timezone and authenticated typed-target navigation.
App Links/landing pages must exist before email rollout. Home unread integration is
deferred until the user reviews and combines both independent feature branches; it must
use this same unread predicate within the dashboard's existing database snapshot.
