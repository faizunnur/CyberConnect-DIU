package edu.diu.cyberconnect.verification.domain;

import java.util.EnumSet;
import java.util.Set;

public enum SubmissionStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    WITHDRAWN;

    public static final Set<SubmissionStatus> ACTIVE = EnumSet.of(DRAFT, SUBMITTED, UNDER_REVIEW);
    public static final Set<SubmissionStatus> REVIEWABLE = EnumSet.of(SUBMITTED, UNDER_REVIEW);
    public static final Set<SubmissionStatus> FINAL = EnumSet.of(APPROVED, REJECTED, WITHDRAWN);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isFinal() {
        return FINAL.contains(this);
    }
}
