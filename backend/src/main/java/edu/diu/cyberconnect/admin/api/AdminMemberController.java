package edu.diu.cyberconnect.admin.api;

import edu.diu.cyberconnect.affiliation.application.AffiliationService;
import edu.diu.cyberconnect.affiliation.application.AffiliationSummary;
import edu.diu.cyberconnect.common.exception.ForbiddenException;
import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.common.security.Authorities;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.common.security.StepUpService;
import edu.diu.cyberconnect.common.security.UserPrincipal;
import edu.diu.cyberconnect.identity.application.UserAccounts;
import edu.diu.cyberconnect.identity.application.UserSummary;
import edu.diu.cyberconnect.moderation.application.MemberModerationService;
import edu.diu.cyberconnect.moderation.application.ModerationActionView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/members")
public class AdminMemberController {

    private final UserAccounts accounts;
    private final AffiliationService affiliations;
    private final MemberModerationService moderation;
    private final StepUpService stepUp;

    public AdminMemberController(UserAccounts accounts, AffiliationService affiliations,
            MemberModerationService moderation, StepUpService stepUp) {
        this.accounts = accounts;
        this.affiliations = affiliations;
        this.moderation = moderation;
        this.stepUp = stepUp;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    public Page<UserSummary> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) RoleName role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return accounts.search(q, status, role, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    public MemberDetail get(@PathVariable UUID userId) {
        return new MemberDetail(accounts.get(userId), affiliations.listForUser(userId), moderation.history(userId));
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    public UserSummary replaceRoles(@PathVariable UUID userId, @Valid @RequestBody RolesRequest request,
            HttpServletRequest http) {
        stepUp.requireFresh(http.getSession(false));
        UserPrincipal actor = CurrentUser.require();
        boolean touchesTopRoles = request.roles().contains(RoleName.SUPER_ADMIN) || request.roles().contains(RoleName.ADMIN)
                || accounts.get(userId).roles().stream().anyMatch(r -> r == RoleName.ADMIN || r == RoleName.SUPER_ADMIN);
        if (touchesTopRoles && !hasAuthority(Authorities.SUPER_ADMIN_MANAGE)) {
            throw new ForbiddenException("SUPER_ADMIN_REQUIRED", "Only a SUPER_ADMIN can grant or revoke ADMIN roles.");
        }
        accounts.replaceRoles(userId, request.roles(), actor.getId());
        return accounts.get(userId);
    }

    @PostMapping("/{userId}/deactivate")
    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID userId, @Valid @RequestBody ReasonRequest request, HttpServletRequest http) {
        stepUp.requireFresh(http.getSession(false));
        moderation.deactivate(userId, request.reason(), CurrentUser.require().getId());
    }

    @PostMapping("/{userId}/reactivate")
    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivate(@PathVariable UUID userId, @Valid @RequestBody ReasonRequest request, HttpServletRequest http) {
        stepUp.requireFresh(http.getSession(false));
        moderation.reactivate(userId, request.reason(), CurrentUser.require().getId());
    }

    @PostMapping("/{userId}/reopen-verification")
    @PreAuthorize("hasAuthority('VERIFICATION_REOPEN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reopenVerification(@PathVariable UUID userId, @Valid @RequestBody ReasonRequest request,
            HttpServletRequest http) {
        stepUp.requireFresh(http.getSession(false));
        moderation.reopenVerification(userId, request.reason(), CurrentUser.require().getId());
    }

    private static boolean hasAuthority(String authority) {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
    }

    public record MemberDetail(UserSummary user, List<AffiliationSummary> affiliations,
            List<ModerationActionView> moderationHistory) {
    }

    public record RolesRequest(@NotNull Set<RoleName> roles) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {
    }
}
