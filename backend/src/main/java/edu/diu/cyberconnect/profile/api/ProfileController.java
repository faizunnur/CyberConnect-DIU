package edu.diu.cyberconnect.profile.api;

import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.model.EmploymentType;
import edu.diu.cyberconnect.common.model.FieldVisibility;
import edu.diu.cyberconnect.common.model.MessagingPolicy;
import edu.diu.cyberconnect.common.model.ProfileVisibility;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.profile.application.EducationCommand;
import edu.diu.cyberconnect.profile.application.EducationView;
import edu.diu.cyberconnect.profile.application.ExperienceCommand;
import edu.diu.cyberconnect.profile.application.ExperienceView;
import edu.diu.cyberconnect.profile.application.PrivacySettings;
import edu.diu.cyberconnect.profile.application.ProfileService;
import edu.diu.cyberconnect.profile.application.ProfileView;
import edu.diu.cyberconnect.profile.application.SkillView;
import edu.diu.cyberconnect.profile.application.UpdateProfileCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The signed-in member's own profile. Verified members only (community tier). */
@RestController
@RequestMapping("/api/v1/me/profile")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ProfileView get() {
        return profiles.getOwn(CurrentUser.require().getId());
    }

    @PutMapping
    public ProfileView update(@Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(CurrentUser.require().getId(), new UpdateProfileCommand(request.displayName(),
                request.headline(), request.bio(), request.organization(), request.jobTitle(), request.location(),
                request.country(), request.specializations(), Boolean.TRUE.equals(request.mentorAvailable()),
                request.mentorTopics()));
    }

    @GetMapping("/privacy")
    public PrivacySettings privacy() {
        return profiles.privacy(CurrentUser.require().getId());
    }

    @PutMapping("/privacy")
    public PrivacySettings updatePrivacy(@Valid @RequestBody PrivacyRequest request) {
        return profiles.updatePrivacy(CurrentUser.require().getId(), new PrivacySettings(request.profileVisibility(),
                request.emailVisibility(), request.organizationVisibility(), request.locationVisibility(),
                request.mentorVisibility(), Boolean.TRUE.equals(request.searchable()), request.messagingPolicy()));
    }

    @PutMapping("/skills")
    public List<SkillView> replaceSkills(@Valid @RequestBody SkillsRequest request) {
        return profiles.replaceSkills(CurrentUser.require().getId(), request.skills());
    }

    @PostMapping("/experiences")
    @ResponseStatus(HttpStatus.CREATED)
    public ExperienceView addExperience(@Valid @RequestBody ExperienceRequest request) {
        return profiles.addExperience(CurrentUser.require().getId(), request.command());
    }

    @PutMapping("/experiences/{experienceId}")
    public ExperienceView updateExperience(@PathVariable UUID experienceId, @Valid @RequestBody ExperienceRequest request) {
        return profiles.updateExperience(CurrentUser.require().getId(), experienceId, request.command());
    }

    @DeleteMapping("/experiences/{experienceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExperience(@PathVariable UUID experienceId) {
        profiles.deleteExperience(CurrentUser.require().getId(), experienceId);
    }

    @PostMapping("/educations")
    @ResponseStatus(HttpStatus.CREATED)
    public EducationView addEducation(@Valid @RequestBody EducationRequest request) {
        return profiles.addEducation(CurrentUser.require().getId(), request.command());
    }

    @PutMapping("/educations/{educationId}")
    public EducationView updateEducation(@PathVariable UUID educationId, @Valid @RequestBody EducationRequest request) {
        return profiles.updateEducation(CurrentUser.require().getId(), educationId, request.command());
    }

    @DeleteMapping("/educations/{educationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEducation(@PathVariable UUID educationId) {
        profiles.deleteEducation(CurrentUser.require().getId(), educationId);
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileView uploadPhoto(@RequestPart("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("FILE_UNREADABLE", "The uploaded file could not be read.");
        }
        return profiles.updatePhoto(CurrentUser.require().getId(), content);
    }

    @DeleteMapping("/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePhoto() {
        profiles.removePhoto(CurrentUser.require().getId());
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 120) String displayName,
            @Size(max = 160) String headline,
            @Size(max = 4000) String bio,
            @Size(max = 160) String organization,
            @Size(max = 120) String jobTitle,
            @Size(max = 120) String location,
            @Size(max = 80) String country,
            @Size(max = 10) List<@Size(max = 60) String> specializations,
            Boolean mentorAvailable,
            @Size(max = 500) String mentorTopics) {
    }

    public record PrivacyRequest(
            @NotNull ProfileVisibility profileVisibility,
            @NotNull FieldVisibility emailVisibility,
            @NotNull FieldVisibility organizationVisibility,
            @NotNull FieldVisibility locationVisibility,
            @NotNull FieldVisibility mentorVisibility,
            @NotNull Boolean searchable,
            @NotNull MessagingPolicy messagingPolicy) {
    }

    public record SkillsRequest(@NotNull @Size(max = 30) List<@NotBlank @Size(max = 60) String> skills) {
    }

    public record ExperienceRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 160) String organization,
            EmploymentType employmentType,
            @Size(max = 120) String location,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            Boolean current,
            @Size(max = 2000) String description) {

        ExperienceCommand command() {
            return new ExperienceCommand(title, organization, employmentType, location, startDate, endDate,
                    Boolean.TRUE.equals(current), description);
        }
    }

    public record EducationRequest(
            @NotBlank @Size(max = 160) String institution,
            @Size(max = 120) String degree,
            @Size(max = 120) String fieldOfStudy,
            @Min(1950) @Max(2100) Integer startYear,
            @Min(1950) @Max(2100) Integer endYear,
            @Size(max = 2000) String description) {

        EducationCommand command() {
            return new EducationCommand(institution, degree, fieldOfStudy, startYear, endYear, description);
        }
    }
}
