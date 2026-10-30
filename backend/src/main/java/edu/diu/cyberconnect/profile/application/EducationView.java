package edu.diu.cyberconnect.profile.application;

import java.util.UUID;

public record EducationView(UUID id, String institution, String degree, String fieldOfStudy, Integer startYear,
        Integer endYear, String description) {
}
