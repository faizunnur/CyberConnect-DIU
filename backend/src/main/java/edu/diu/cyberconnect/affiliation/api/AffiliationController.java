package edu.diu.cyberconnect.affiliation.api;

import edu.diu.cyberconnect.affiliation.application.AffiliationDetails;
import edu.diu.cyberconnect.affiliation.application.AffiliationService;
import edu.diu.cyberconnect.affiliation.application.AffiliationSummary;
import edu.diu.cyberconnect.common.exception.ForbiddenException;
import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.AffiliationType;
import edu.diu.cyberconnect.common.security.AccountAccessFilter;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.common.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/affiliations")
public class AffiliationController {

    private final AffiliationService affiliations;

    public AffiliationController(AffiliationService affiliations) {
        this.affiliations = affiliations;
    }

    @GetMapping
    public List<AffiliationSummary> list() {
        return affiliations.listForUser(CurrentUser.require().getId());
    }

    /** Additional affiliations can only be requested by verified members. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AffiliationSummary requestAdditional(@Valid @RequestBody AffiliationRequest request) {
        AccountStatus status = CurrentUser.snapshot().map(UserPrincipal.AccountSnapshot::status).orElse(AccountStatus.PENDING);
        if (status != AccountStatus.VERIFIED) {
            throw new ForbiddenException(AccountAccessFilter.statusCode(status),
                    "Additional affiliations can be requested after your account is verified.");
        }
        return affiliations.requestAdditional(CurrentUser.require().getId(), request.type(), request.details());
    }

    @PutMapping("/{affiliationId}")
    public AffiliationSummary updateDetails(@PathVariable UUID affiliationId,
            @Valid @RequestBody AffiliationDetailsRequest request) {
        return affiliations.updateDetails(CurrentUser.require().getId(), affiliationId, request.details());
    }

    public record AffiliationRequest(
            @NotNull AffiliationType type,
            @Size(max = 32) String studentId,
            @Size(max = 16) String batch,
            @Size(max = 120) String department,
            @Min(1990) @Max(2100) Integer graduationYear,
            @Size(max = 32) String cscMemberId) {

        AffiliationDetails details() {
            return new AffiliationDetails(studentId, batch, department, graduationYear, cscMemberId);
        }
    }

    public record AffiliationDetailsRequest(
            @Size(max = 32) String studentId,
            @Size(max = 16) String batch,
            @Size(max = 120) String department,
            @Min(1990) @Max(2100) Integer graduationYear,
            @Size(max = 32) String cscMemberId) {

        AffiliationDetails details() {
            return new AffiliationDetails(studentId, batch, department, graduationYear, cscMemberId);
        }
    }
}
