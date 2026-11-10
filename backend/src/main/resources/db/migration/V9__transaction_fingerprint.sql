-- SHA-256 of the canonical original POST payload, keyed by the existing unique (user_id, client_request_id).
-- Written once on create; edit/void never rewrite it, so a retry after edit/void still replays.
ALTER TABLE fintrack.transactions ADD COLUMN request_fingerprint CHAR(64);
