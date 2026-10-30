package edu.diu.cyberconnect.identity.domain;

/** Platform roles describe administrative permissions. They are independent of affiliations and account status. */
public enum RoleName {
    MEMBER,
    MODERATOR,
    ADMIN,
    SUPER_ADMIN;

    public boolean isPrivileged() {
        return this != MEMBER;
    }
}
