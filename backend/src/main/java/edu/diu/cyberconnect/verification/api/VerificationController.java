package edu.diu.cyberconnect.verification.api;

import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.verification.application.AppealService;
import edu.diu.cyberconnect.verification.application.SubmissionService;
import edu.diu.cyberconnect.verification.application.VerificationViews.AppealView;
import edu.diu.cyberconnect.verification.application.VerificationViews.DocumentView;
import edu.diu.cyberconnect.verification.application.VerificationViews.StatusView;
import edu.diu.cyberconnect.verification.application.VerificationViews.SubmissionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Applicant-facing verification endpoints. Reachable by any authenticated account (onboarding allowlist). */
@RestController
@RequestMapping("/api/v1/verification")
public class VerificationController {

    private final SubmissionService submissions;
    private final AppealService appeals;

    public VerificationController(SubmissionService submissions, AppealService appeals) {
        this.submissions = submissions;
        this.appeals = appeals;
    }

    @GetMapping("/status")
    public StatusView status() {
        return submissions.status(CurrentUser.require().getId());
    }

    @PostMapping("/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionView start(@Valid @RequestBody StartRequest request) {
        return submissions.startSubmission(CurrentUser.require().getId(), request.affiliationId());
    }

    @PostMapping(value = "/submissions/{submissionId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView upload(@PathVariable UUID submissionId, @RequestPart("file") MultipartFile file,
            @RequestParam(value = "documentType", required = false) String documentType) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("FILE_UNREADABLE", "The uploaded file could not be read.");
        }
        return submissions.addDocument(CurrentUser.require().getId(), submissionId, documentType, content,
                file.getOriginalFilename(), file.getContentType());
    }

    @DeleteMapping("/submissions/{submissionId}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDocument(@PathVariable UUID submissionId, @PathVariable UUID documentId) {
        submissions.removeDocument(CurrentUser.require().getId(), submissionId, documentId);
    }

    @PostMapping("/submissions/{submissionId}/submit")
    public SubmissionView submit(@PathVariable UUID submissionId) {
        return submissions.submit(CurrentUser.require().getId(), submissionId);
    }

    @PostMapping("/appeals")
    @ResponseStatus(HttpStatus.CREATED)
    public AppealView appeal(@Valid @RequestBody AppealRequest request) {
        return appeals.submit(CurrentUser.require().getId(), request.submissionId(), request.statement());
    }

    @GetMapping("/appeals/{appealId}")
    public AppealView getAppeal(@PathVariable UUID appealId) {
        return appeals.getForUser(CurrentUser.require().getId(), appealId);
    }

    public record StartRequest(@NotNull UUID affiliationId) {
    }

    public record AppealRequest(@NotNull UUID submissionId, @NotBlank @Size(min = 20, max = 4000) String statement) {
    }
}
