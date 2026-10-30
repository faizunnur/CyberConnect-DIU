package edu.diu.cyberconnect.verification.api;

import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.common.security.StepUpService;
import edu.diu.cyberconnect.verification.application.DocumentAccessService;
import edu.diu.cyberconnect.verification.application.ReviewService;
import edu.diu.cyberconnect.verification.application.VerificationViews.QueueItem;
import edu.diu.cyberconnect.verification.application.VerificationViews.ReasonCodeView;
import edu.diu.cyberconnect.verification.application.VerificationViews.SubmissionDetail;
import edu.diu.cyberconnect.verification.domain.SubmissionStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/moderation/verifications")
@PreAuthorize("hasAuthority('VERIFICATION_REVIEW')")
public class ModerationVerificationController {

    private final ReviewService reviews;
    private final DocumentAccessService documentAccess;
    private final StepUpService stepUp;

    public ModerationVerificationController(ReviewService reviews, DocumentAccessService documentAccess,
            StepUpService stepUp) {
        this.reviews = reviews;
        this.documentAccess = documentAccess;
        this.stepUp = stepUp;
    }

    @GetMapping
    public Page<QueueItem> queue(@RequestParam(required = false) Set<SubmissionStatus> status,
            @RequestParam(required = false) UUID assignee,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviews.queue(status, assignee, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/reason-codes")
    public List<ReasonCodeView> reasonCodes() {
        return reviews.reasonCodes();
    }

    @GetMapping("/{submissionId}")
    public SubmissionDetail detail(@PathVariable UUID submissionId) {
        return reviews.detail(submissionId);
    }

    @PostMapping("/{submissionId}/assign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@PathVariable UUID submissionId) {
        reviews.assign(submissionId, CurrentUser.require().getId());
    }

    @PostMapping("/{submissionId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable UUID submissionId, @RequestBody(required = false) NotesRequest request,
            HttpServletRequest http) {
        stepUp.requireFresh(http.getSession(false));
        reviews.approve(submissionId, CurrentUser.require().getId(), request == null ? null : request.internalNotes());
    }

    @PostMapping("/{submissionId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID submissionId, @Valid @RequestBody RejectRequest request,
            HttpServletRequest http) {
        stepUp.requireFresh(http.getSession(false));
        reviews.reject(submissionId, CurrentUser.require().getId(), request.reasonCode(), request.reasonText(),
                request.internalNotes());
    }

    /** Streams the decrypted document inline. Requires DOCUMENT_VIEW, fresh MFA, and logs every view. */
    @GetMapping("/{submissionId}/documents/{documentId}/content")
    @PreAuthorize("hasAuthority('DOCUMENT_VIEW')")
    public ResponseEntity<byte[]> document(@PathVariable UUID submissionId, @PathVariable UUID documentId,
            HttpServletRequest http) {
        stepUp.requireFresh(http.getSession(false));
        DocumentAccessService.OpenedDocument opened = documentAccess.open(submissionId, documentId,
                CurrentUser.require().getId(), "VERIFICATION_REVIEW", http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(opened.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(opened.filename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                // The reviewer UI embeds PDFs in a same-origin frame; everything else keeps the global DENY.
                .header("X-Frame-Options", "SAMEORIGIN")
                .header("Content-Security-Policy", "frame-ancestors 'self'; default-src 'none'")
                .body(opened.content());
    }

    public record NotesRequest(@Size(max = 4000) String internalNotes) {
    }

    public record RejectRequest(@NotBlank @Size(max = 64) String reasonCode, @Size(max = 2000) String reasonText,
            @Size(max = 4000) String internalNotes) {
    }
}
