ALTER TABLE fintrack.notification_settings ADD COLUMN email_finance_enabled BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE fintrack.notifications (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES fintrack.users(id) ON DELETE CASCADE,
 type TEXT NOT NULL CHECK(type IN ('DAILY_REMINDER','BUDGET_WARNING','BUDGET_EXCEEDED')),
 event_key TEXT NOT NULL, title TEXT NOT NULL CHECK(length(title)<=300), body TEXT NOT NULL CHECK(length(body)<=2000),
 payload JSONB NOT NULL, amount_minor BIGINT CHECK(amount_minor>=0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp(), read_at TIMESTAMPTZ, deleted_at TIMESTAMPTZ,
 UNIQUE(user_id,event_key), UNIQUE(user_id,id)
);
CREATE INDEX ix_notifications_feed ON fintrack.notifications(user_id,created_at DESC,id DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_notifications_unread ON fintrack.notifications(user_id) WHERE deleted_at IS NULL AND read_at IS NULL;
CREATE TABLE fintrack.notification_outbox (
 delivery_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL, notification_id UUID NOT NULL,
 channel TEXT NOT NULL DEFAULT 'EMAIL' CHECK(channel='EMAIL'), template_name TEXT NOT NULL,
 template_version INTEGER NOT NULL DEFAULT 1 CHECK(template_version=1), variables JSONB NOT NULL,
 recipient TEXT, subject TEXT NOT NULL, rendered_html TEXT,
 state TEXT NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','IN_FLIGHT','RETRY','SENT','CANCELLED','DEAD')),
 attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp(),
 lease_until TIMESTAMPTZ, claim_token UUID, first_attempt_at TIMESTAMPTZ, sent_at TIMESTAMPTZ,
 expires_at TIMESTAMPTZ NOT NULL, reason TEXT,
 FOREIGN KEY(user_id,notification_id) REFERENCES fintrack.notifications(user_id,id) ON DELETE CASCADE,
 UNIQUE(notification_id,channel)
);
CREATE INDEX ix_notification_outbox_due ON fintrack.notification_outbox(next_attempt_at) WHERE state IN ('PENDING','RETRY','IN_FLIGHT');
CREATE TABLE fintrack.budget_notification_state (
 user_id UUID NOT NULL, budget_id UUID NOT NULL, context TEXT NOT NULL,
 observed_severity INTEGER NOT NULL DEFAULT 0, cycle_number BIGINT NOT NULL DEFAULT 1,
 highest_emitted_severity INTEGER NOT NULL DEFAULT 0, last_evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(user_id,budget_id), FOREIGN KEY(user_id,budget_id) REFERENCES fintrack.budgets(user_id,id) ON DELETE CASCADE
);
CREATE TABLE fintrack.notification_finance_events (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, user_id UUID NOT NULL REFERENCES fintrack.users(id) ON DELETE CASCADE,
 baseline_reset BOOLEAN NOT NULL DEFAULT false, budget_id UUID, occurred_at TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp(), processed_at TIMESTAMPTZ
);
CREATE INDEX ix_notification_events_pending ON fintrack.notification_finance_events(user_id,id) WHERE processed_at IS NULL;
CREATE TABLE fintrack.daily_notification_schedule (
 user_id UUID PRIMARY KEY REFERENCES fintrack.users(id) ON DELETE CASCADE,
 schedule_version TEXT NOT NULL, next_due_at TIMESTAMPTZ NOT NULL, last_processed_local_date DATE
);

-- Writers only append events. They never lock evaluator/inbox/outbox rows or call a provider.
CREATE FUNCTION fintrack.append_notification_finance_event() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE reset BOOLEAN := false; bid UUID := NULL;
BEGIN
 IF TG_TABLE_NAME='transactions' THEN
   IF TG_OP='INSERT' THEN
     IF NEW.transaction_type<>'EXPENSE' OR NEW.status<>'POSTED' THEN RETURN NEW; END IF;
   ELSE
     IF (NEW.transaction_type<>'EXPENSE' AND OLD.transaction_type<>'EXPENSE') OR
        (NEW.transaction_type,NEW.category_id,NEW.amount_minor,NEW.occurred_at,NEW.status)
        IS NOT DISTINCT FROM (OLD.transaction_type,OLD.category_id,OLD.amount_minor,OLD.occurred_at,OLD.status) THEN RETURN NEW; END IF;
   END IF;
 ELSIF TG_TABLE_NAME='budgets' THEN
   bid := NEW.id;
   IF TG_OP='UPDATE' AND (NEW.category_id,NEW.period_start,NEW.limit_minor,NEW.archived_at) IS NOT DISTINCT FROM
      (OLD.category_id,OLD.period_start,OLD.limit_minor,OLD.archived_at) THEN RETURN NEW; END IF;
 ELSIF TG_TABLE_NAME='notification_settings' THEN
   IF TG_OP='UPDATE' THEN
     IF (NEW.notifications_enabled,NEW.daily_reminder_enabled,NEW.daily_reminder_time,NEW.budget_alert_enabled,
         NEW.budget_alert_threshold_percent,NEW.budget_exceed_alert_enabled,NEW.email_finance_enabled)
         IS NOT DISTINCT FROM (OLD.notifications_enabled,OLD.daily_reminder_enabled,OLD.daily_reminder_time,OLD.budget_alert_enabled,
         OLD.budget_alert_threshold_percent,OLD.budget_exceed_alert_enabled,OLD.email_finance_enabled) THEN RETURN NEW; END IF;
     reset := (NEW.notifications_enabled AND NOT OLD.notifications_enabled) OR
        (NEW.budget_alert_enabled AND NOT OLD.budget_alert_enabled) OR
        (NEW.budget_exceed_alert_enabled AND NOT OLD.budget_exceed_alert_enabled) OR
        (NEW.email_finance_enabled AND NOT OLD.email_finance_enabled);
   ELSE reset := true; END IF;
 ELSIF TG_TABLE_NAME='user_preferences' THEN
   IF TG_OP='UPDATE' AND NEW.time_zone IS NOT DISTINCT FROM OLD.time_zone THEN RETURN NEW; END IF;
 ELSIF TG_TABLE_NAME='monthly_budgets' THEN
   IF TG_OP='INSERT' OR NEW.archived_at IS NOT DISTINCT FROM OLD.archived_at THEN RETURN NEW; END IF;
 END IF;
 INSERT INTO fintrack.notification_finance_events(user_id,baseline_reset,budget_id) VALUES(NEW.user_id,reset,bid);
 RETURN NEW;
END $$;
CREATE TRIGGER notification_transaction_events AFTER INSERT OR UPDATE ON fintrack.transactions FOR EACH ROW EXECUTE FUNCTION fintrack.append_notification_finance_event();
CREATE TRIGGER notification_budget_events AFTER INSERT OR UPDATE ON fintrack.budgets FOR EACH ROW EXECUTE FUNCTION fintrack.append_notification_finance_event();
CREATE TRIGGER notification_parent_events AFTER INSERT OR UPDATE ON fintrack.monthly_budgets FOR EACH ROW EXECUTE FUNCTION fintrack.append_notification_finance_event();
CREATE TRIGGER notification_settings_events AFTER INSERT OR UPDATE ON fintrack.notification_settings FOR EACH ROW EXECUTE FUNCTION fintrack.append_notification_finance_event();
CREATE TRIGGER notification_timezone_events AFTER INSERT OR UPDATE ON fintrack.user_preferences FOR EACH ROW EXECUTE FUNCTION fintrack.append_notification_finance_event();
-- Deployment baseline prevents catch-up of pre-existing risk; new budgets still have their own creation event.
INSERT INTO fintrack.notification_finance_events(user_id,baseline_reset) SELECT id,true FROM fintrack.users;
