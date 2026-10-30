package edu.diu.cyberconnect.affiliation.application;

import edu.diu.cyberconnect.common.model.AffiliationType;
import java.util.List;
import java.util.UUID;

/** Public API of the affiliation module. */
public interface AffiliationService {

    /** Creates the primary affiliation at registration. Details are validated against the eligibility rules. */
    AffiliationSummary createPrimary(UUID userId, AffiliationType type, AffiliationDetails details);

    /** Adds a further affiliation for an already verified member; it starts PENDING and needs its own submission. */
    AffiliationSummary requestAdditional(UUID userId, AffiliationType type, AffiliationDetails details);

    /** Lets the applicant correct details before (re)submitting evidence. */
    AffiliationSummary updateDetails(UUID userId, UUID affiliationId, AffiliationDetails details);

    List<AffiliationSummary> listForUser(UUID userId);

    AffiliationSummary get(UUID affiliationId);

    void markVerified(UUID affiliationId, UUID submissionId);

    boolean hasVerifiedAffiliation(UUID userId);
}
