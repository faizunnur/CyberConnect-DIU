package edu.diu.cyberconnect.common.model;

/** Lifecycle of user-generated content: author deletion is DELETED, moderator removal is REMOVED (restorable). */
public enum ContentStatus {
    ACTIVE,
    REMOVED,
    DELETED
}
