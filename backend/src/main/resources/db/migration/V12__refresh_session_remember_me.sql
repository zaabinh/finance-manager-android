-- "Ghi nhớ tôi": a login without it gets a short-lived refresh family. The flag is copied on every
-- rotation so the short lifetime cannot be extended by refreshing. Existing sessions keep the long lifetime.
ALTER TABLE fintrack.refresh_sessions ADD COLUMN remember_me BOOLEAN NOT NULL DEFAULT TRUE;
