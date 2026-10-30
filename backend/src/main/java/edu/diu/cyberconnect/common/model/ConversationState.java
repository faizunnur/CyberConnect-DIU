package edu.diu.cyberconnect.common.model;

/** Per-member state of a conversation; REQUEST means the member has not accepted the first message yet. */
public enum ConversationState {
    ACTIVE,
    REQUEST,
    DECLINED,
    ARCHIVED
}
