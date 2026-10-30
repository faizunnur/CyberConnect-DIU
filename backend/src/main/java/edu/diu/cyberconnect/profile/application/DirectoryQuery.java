package edu.diu.cyberconnect.profile.application;

import edu.diu.cyberconnect.common.model.AffiliationType;

public record DirectoryQuery(String q, AffiliationType affiliation, String skill, String organization,
        String experience, Boolean mentorAvailable) {
}
