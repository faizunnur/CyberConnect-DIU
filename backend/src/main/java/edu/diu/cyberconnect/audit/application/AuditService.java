package edu.diu.cyberconnect.audit.application;

/** Public API of the audit module. Other modules depend on this interface only. */
public interface AuditService {

    /** Records within the caller's transaction, so the audit row commits atomically with the audited change. */
    void record(AuditEntry entry);

    /** Records in a new transaction; use for failures that roll back the surrounding work. */
    void recordDetached(AuditEntry entry);
}
