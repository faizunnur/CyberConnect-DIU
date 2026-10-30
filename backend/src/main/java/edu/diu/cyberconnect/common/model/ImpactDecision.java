package edu.diu.cyberconnect.common.model;

/** Entries of the append-only impact decision trail. */
public enum ImpactDecision {
    PROPOSE,
    CONFIRM,
    DISPUTE,
    WITHDRAW,
    RESOLVE_CONFIRMED,
    RESOLVE_REJECTED
}
