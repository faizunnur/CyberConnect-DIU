package edu.diu.cyberconnect.profile.application;

public record EducationCommand(String institution, String degree, String fieldOfStudy, Integer startYear,
        Integer endYear, String description) {
}
