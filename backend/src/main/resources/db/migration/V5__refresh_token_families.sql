-- Refresh Token Rotation: token families, reuse detection and a short grace period for concurrent refreshes.
--
-- family_id                   One login (device chain). Every rotation stays in the family; reuse revokes it whole.
-- revoke_reason               Why a token stopped being usable. Only ROTATED tokens can trigger reuse detection.
-- successor_token_ciphertext  AES-GCM(successor refresh token), keyed from the *presented* raw token, so only a
--                             holder of the old token can read it. Lets a concurrent refresh inside the grace
--                             period receive the already issued successor instead of tripping reuse detection.

ALTER TABLE fintrack.refresh_sessions
    ADD COLUMN family_id UUID,
    ADD COLUMN revoke_reason TEXT
        CHECK (revoke_reason IN ('ROTATED', 'LOGOUT', 'REUSE_DETECTED', 'PASSWORD_CHANGED', 'PASSWORD_RESET')),
    ADD COLUMN successor_token_ciphertext BYTEA;

-- Backfill: walk existing rotation chains from their roots so each chain becomes one family.
WITH RECURSIVE chain(id, family_id) AS (
    SELECT s.id, s.id
      FROM fintrack.refresh_sessions s
     WHERE NOT EXISTS (SELECT 1 FROM fintrack.refresh_sessions p WHERE p.replaced_by_session_id = s.id)
    UNION ALL
    SELECT s.replaced_by_session_id, chain.family_id
      FROM fintrack.refresh_sessions s
      JOIN chain ON s.id = chain.id
     WHERE s.replaced_by_session_id IS NOT NULL
)
UPDATE fintrack.refresh_sessions s SET family_id = chain.family_id FROM chain WHERE s.id = chain.id;

UPDATE fintrack.refresh_sessions SET family_id = id WHERE family_id IS NULL;
UPDATE fintrack.refresh_sessions SET revoke_reason = 'ROTATED'
 WHERE replaced_by_session_id IS NOT NULL AND revoked_at IS NOT NULL;

ALTER TABLE fintrack.refresh_sessions
    ALTER COLUMN family_id SET NOT NULL,
    ADD CONSTRAINT ck_refresh_sessions_reason_requires_revocation
        CHECK (revoke_reason IS NULL OR revoked_at IS NOT NULL);

CREATE INDEX ix_refresh_sessions_family_active ON fintrack.refresh_sessions(family_id) WHERE revoked_at IS NULL;
-- Supports the self-referencing FK and clearing a predecessor's sealed successor on rotation.
CREATE INDEX ix_refresh_sessions_replaced_by ON fintrack.refresh_sessions(replaced_by_session_id)
    WHERE replaced_by_session_id IS NOT NULL;
