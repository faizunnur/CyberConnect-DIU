package edu.diu.cyberconnect.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rejection_reason_codes")
public class RejectionReasonCode {

    @Id
    private String code;

    @Column(name = "applicant_text", nullable = false)
    private String applicantText;

    @Column(name = "allows_resubmission", nullable = false)
    private boolean allowsResubmission;

    @Column(name = "visible_to_applicant", nullable = false)
    private boolean visibleToApplicant;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected RejectionReasonCode() {
    }

    public String getCode() {
        return code;
    }

    public String getApplicantText() {
        return applicantText;
    }

    public boolean isAllowsResubmission() {
        return allowsResubmission;
    }

    public boolean isVisibleToApplicant() {
        return visibleToApplicant;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
