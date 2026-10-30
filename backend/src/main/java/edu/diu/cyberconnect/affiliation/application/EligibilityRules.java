package edu.diu.cyberconnect.affiliation.application;

import edu.diu.cyberconnect.admin.application.SettingKeys;
import edu.diu.cyberconnect.admin.application.SystemSettings;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.model.AffiliationType;
import java.time.Year;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Configurable eligibility requirements (product requirements, D-09). The student-id pattern comes from
 * {@code system_settings} so the official DIU format can be applied without a release.
 */
@Component
public class EligibilityRules {

    private final SystemSettings settings;

    public EligibilityRules(SystemSettings settings) {
        this.settings = settings;
    }

    public void validate(AffiliationType type, AffiliationDetails details) {
        switch (type) {
            case STUDENT -> {
                requireStudentId(details.studentId());
                require(details.batch(), "batch", "Batch is required for students.");
                require(details.department(), "department", "Department is required for students.");
            }
            case ALUMNI -> {
                requireStudentId(details.studentId());
                if (details.graduationYear() == null) {
                    throw new BadRequestException("GRADUATION_YEAR_REQUIRED", "Graduation year is required for alumni.");
                }
                int year = details.graduationYear();
                if (year < 2000 || year > Year.now().getValue()) {
                    throw new BadRequestException("GRADUATION_YEAR_INVALID", "Graduation year is out of range.");
                }
            }
            case CSC_MEMBER -> require(details.cscMemberId(), "cscMemberId", "CSC member ID is required.");
        }
    }

    public String studentIdPattern() {
        return settings.getString(SettingKeys.STUDENT_ID_PATTERN);
    }

    private void requireStudentId(String studentId) {
        require(studentId, "studentId", "Student ID is required.");
        if (!Pattern.compile(studentIdPattern()).matcher(studentId.trim()).matches()) {
            throw new BadRequestException("STUDENT_ID_INVALID", "Student ID format is not recognised.");
        }
    }

    private static void require(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("FIELD_REQUIRED", message + " (" + field + ")");
        }
    }
}
