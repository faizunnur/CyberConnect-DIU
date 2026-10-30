package edu.diu.cyberconnect.common.model;

/** Per-field audience on a profile (email, organisation, location, mentor availability). */
public enum FieldVisibility {
    NOBODY,
    CONNECTIONS,
    ALL_MEMBERS;

    public boolean allows(boolean connected) {
        return this == ALL_MEMBERS || (this == CONNECTIONS && connected);
    }
}
