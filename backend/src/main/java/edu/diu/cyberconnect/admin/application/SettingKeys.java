package edu.diu.cyberconnect.admin.application;

/** Keys seeded by the baseline migration. */
public final class SettingKeys {

    public static final String REAL_DOCUMENTS_ENABLED = "verification.real_documents_enabled";
    public static final String MAX_SUBMISSIONS_PER_AFFILIATION = "verification.max_submissions_per_affiliation";
    public static final String DOCUMENT_RETENTION_DAYS = "verification.documents.retention_days";
    public static final String DOCUMENT_MAX_FILE_BYTES = "verification.documents.max_file_bytes";
    public static final String DOCUMENT_MAX_FILES_PER_SUBMISSION = "verification.documents.max_files_per_submission";
    public static final String STUDENT_ID_PATTERN = "verification.student_id_pattern";
    public static final String DEACTIVATION_RESTORE_DAYS = "accounts.deactivation_restore_days";

    private SettingKeys() {
    }
}
