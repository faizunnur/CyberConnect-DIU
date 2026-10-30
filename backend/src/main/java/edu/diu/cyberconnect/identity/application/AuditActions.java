package edu.diu.cyberconnect.identity.application;

/** Audit action names emitted by the identity module. */
public final class AuditActions {

    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGIN_MFA_FAILED = "LOGIN_MFA_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String MFA_ENABLED = "MFA_ENABLED";
    public static final String MFA_DISABLED = "MFA_DISABLED";
    public static final String MFA_STEP_UP = "MFA_STEP_UP";
    public static final String MFA_RECOVERY_CODE_USED = "MFA_RECOVERY_CODE_USED";
    public static final String MFA_RECOVERY_CODES_REGENERATED = "MFA_RECOVERY_CODES_REGENERATED";
    public static final String ACCOUNT_STATUS_CHANGED = "ACCOUNT_STATUS_CHANGED";
    public static final String VERIFICATION_LOCKED = "VERIFICATION_LOCKED";
    public static final String VERIFICATION_UNLOCKED = "VERIFICATION_UNLOCKED";
    public static final String ROLES_CHANGED = "ROLES_CHANGED";
    public static final String SESSIONS_REVOKED = "SESSIONS_REVOKED";
    public static final String SUPER_ADMIN_BOOTSTRAPPED = "SUPER_ADMIN_BOOTSTRAPPED";

    public static final String TARGET_USER = "USER";

    private AuditActions() {
    }
}
