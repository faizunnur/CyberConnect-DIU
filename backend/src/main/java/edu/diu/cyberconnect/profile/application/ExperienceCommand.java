package edu.diu.cyberconnect.profile.application;

import edu.diu.cyberconnect.common.model.EmploymentType;
import java.time.LocalDate;

public record ExperienceCommand(String title, String organization, EmploymentType employmentType, String location,
        LocalDate startDate, LocalDate endDate, boolean current, String description) {
}
