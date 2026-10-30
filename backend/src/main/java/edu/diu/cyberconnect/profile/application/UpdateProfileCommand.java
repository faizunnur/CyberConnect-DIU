package edu.diu.cyberconnect.profile.application;

import java.util.List;

public record UpdateProfileCommand(String displayName, String headline, String bio, String organization,
        String jobTitle, String location, String country, List<String> specializations, boolean mentorAvailable,
        String mentorTopics) {
}
