package edu.diu.cyberconnect.common.security;

import edu.diu.cyberconnect.common.model.RoleName;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Fine-grained authorities derived from roles. ADMIN deliberately does not receive DOCUMENT_VIEW or
 * VERIFICATION_REVIEW: reviewing identity documents is a moderator duty, and an admin who must review holds both roles.
 */
public final class Authorities {

    public static final String VERIFICATION_REVIEW = "VERIFICATION_REVIEW";
    public static final String DOCUMENT_VIEW = "DOCUMENT_VIEW";
    public static final String APPEAL_DECIDE = "APPEAL_DECIDE";
    public static final String MODERATION_ACTION = "MODERATION_ACTION";
    public static final String ROLE_ASSIGN = "ROLE_ASSIGN";
    public static final String SUPER_ADMIN_MANAGE = "SUPER_ADMIN_MANAGE";
    public static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";
    public static final String AUDIT_READ = "AUDIT_READ";
    public static final String VERIFICATION_REOPEN = "VERIFICATION_REOPEN";
    public static final String MEMBER_MANAGE = "MEMBER_MANAGE";

    public static final String ROLE_PREFIX = "ROLE_";

    private Authorities() {
    }

    public static Set<String> forRoles(Collection<RoleName> roles) {
        Set<String> authorities = new LinkedHashSet<>();
        for (RoleName role : roles) {
            authorities.add(ROLE_PREFIX + role.name());
        }
        Set<RoleName> set = roles.isEmpty() ? EnumSet.noneOf(RoleName.class) : EnumSet.copyOf(roles);
        if (set.contains(RoleName.MODERATOR)) {
            authorities.add(VERIFICATION_REVIEW);
            authorities.add(DOCUMENT_VIEW);
            authorities.add(APPEAL_DECIDE);
            authorities.add(MODERATION_ACTION);
            authorities.add(AUDIT_READ);
        }
        if (set.contains(RoleName.ADMIN) || set.contains(RoleName.SUPER_ADMIN)) {
            authorities.add(ROLE_ASSIGN);
            authorities.add(SETTINGS_MANAGE);
            authorities.add(MODERATION_ACTION);
            authorities.add(AUDIT_READ);
            authorities.add(VERIFICATION_REOPEN);
            authorities.add(MEMBER_MANAGE);
        }
        if (set.contains(RoleName.SUPER_ADMIN)) {
            authorities.add(SUPER_ADMIN_MANAGE);
        }
        return authorities;
    }
}
