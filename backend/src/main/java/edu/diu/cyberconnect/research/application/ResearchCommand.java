package edu.diu.cyberconnect.research.application;

import java.time.LocalDate;
import java.util.List;

public record ResearchCommand(String title, String summary, boolean lookingForMembers, LocalDate startDate,
        LocalDate endDate, List<String> skills) {
}
