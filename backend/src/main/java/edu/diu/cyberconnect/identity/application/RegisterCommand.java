package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.affiliation.application.AffiliationDetails;
import edu.diu.cyberconnect.common.model.AffiliationType;

public record RegisterCommand(
        String fullName,
        String email,
        String password,
        AffiliationType affiliationType,
        AffiliationDetails details,
        boolean privacyAccepted) {
}
