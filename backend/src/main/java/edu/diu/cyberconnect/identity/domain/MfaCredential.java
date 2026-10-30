package edu.diu.cyberconnect.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** TOTP credential. The secret is stored AES-GCM encrypted; the key id allows rotation. */
@Entity
@Table(name = "mfa_credentials")
public class MfaCredential {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "totp_secret_enc", nullable = false)
    private String totpSecretEnc;

    @Column(name = "encryption_key_id", nullable = false)
    private String encryptionKeyId;

    @Column(name = "enabled_at", nullable = false)
    private Instant enabledAt;

    @Column(name = "last_used_step")
    private Long lastUsedStep;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MfaCredential() {
    }

    public MfaCredential(UUID userId, String totpSecretEnc, String encryptionKeyId) {
        this.userId = userId;
        this.totpSecretEnc = totpSecretEnc;
        this.encryptionKeyId = encryptionKeyId;
        this.enabledAt = Instant.now();
        this.createdAt = enabledAt;
        this.updatedAt = enabledAt;
    }

    /** Prevents replay of a code within its time step. Returns false if the step was already consumed. */
    public boolean consumeStep(long step) {
        if (lastUsedStep != null && step <= lastUsedStep) {
            return false;
        }
        lastUsedStep = step;
        updatedAt = Instant.now();
        return true;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTotpSecretEnc() {
        return totpSecretEnc;
    }

    public String getEncryptionKeyId() {
        return encryptionKeyId;
    }

    public Instant getEnabledAt() {
        return enabledAt;
    }
}
