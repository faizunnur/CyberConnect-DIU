package edu.diu.cyberconnect.common.model;

/** Platform roles describe administrative permissions. Independent of affiliation and account status. */
public enum RoleName {
    MEMBER,
    MODERATOR,
    ADMIN,
    SUPER_ADMIN;

    public boolean isPrivileged() {
        return this != MEMBER;
    }
}
