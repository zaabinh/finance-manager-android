ALTER TABLE fintrack.users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE fintrack.verification_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES fintrack.users(id) ON DELETE CASCADE,
    email public.CITEXT NOT NULL,
    purpose TEXT NOT NULL CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    code_hash CHAR(64) NOT NULL,
    consumed_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > created_at),
    CHECK (attempt_count BETWEEN 0 and 5)
);

CREATE INDEX ix_verification_challenges_lookup
        ON fintrack.verification_challenges(email, purpose, expires_at)
        WHERE consumed_at IS NULL;

ALTER TABLE fintrack.refresh_sessions
    ADD COLUMN replaced_by_session_id UUID REFERENCES fintrack.refresh_sessions(id),
    ADD COLUMN user_agent VARCHAR(300),
    ADD COLUMN ip_address INET;
