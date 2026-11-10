package com.fintrack.api.entity;

import jakarta.persistence.*;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "refresh_sessions", schema = "fintrack")
@Getter
@NoArgsConstructor
public class RefreshSession {
    @Id @GeneratedValue(strategy = GenerationType.UUID) 
    private UUID id;
    
    @Column(name = "user_id", nullable = false) 
    private UUID userId;

    @Column(name = "family_id", nullable = false) 
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, columnDefinition = "char(64)") @JdbcTypeCode(SqlTypes.CHAR) 
    private String tokenHash;

    @Column(name = "expires_at", nullable = false) 
    private Instant expiresAt;

    @Column(name = "issued_at", nullable = false) 
    private Instant issuedAt;

    @Column(name = "last_used_at") 
    private Instant lastUsedAt;

    @Column(name = "revoked_at") private Instant revokedAt;

    @Column(name = "revoke_reason", columnDefinition = "text") 
    private String revokeReason;

    @Column(name = "replaced_by_session_id") 
    private UUID replacedBySessionId;

    @Column(name = "successor_token_ciphertext") 
    private byte[] successorTokenCiphertext;

    @Column(name = "user_agent", length = 300) 
    private String userAgent;

    @JdbcTypeCode(SqlTypes.INET) @Column(name = "ip_address", columnDefinition = "inet") 
    private InetAddress ipAddress;
}
