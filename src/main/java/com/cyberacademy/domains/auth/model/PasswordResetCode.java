package com.cyberacademy.domains.auth.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "password_reset_codes")
public class PasswordResetCode {
    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "code_hash", nullable = false)
    private String codeHash;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private boolean used;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
}
