package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.affiliation.application.AffiliationService;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.common.security.Authorities;
import edu.diu.cyberconnect.common.security.StepUpService;
import edu.diu.cyberconnect.identity.domain.User;
import edu.diu.cyberconnect.identity.infrastructure.MfaCredentialRepository;
import edu.diu.cyberconnect.identity.infrastructure.UserRepository;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MeAssembler {

    private final UserRepository users;
    private final MfaCredentialRepository mfaCredentials;
    private final AffiliationService affiliations;
    private final StepUpService stepUp;

    public MeAssembler(UserRepository users, MfaCredentialRepository mfaCredentials, AffiliationService affiliations,
            StepUpService stepUp) {
        this.users = users;
        this.mfaCredentials = mfaCredentials;
        this.affiliations = affiliations;
        this.stepUp = stepUp;
    }

    @Transactional(readOnly = true)
    public MeResponse forUser(UUID userId, HttpSession session) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found."));
        return assemble(user, session);
    }

    public MeResponse assemble(User user, HttpSession session) {
        boolean mfaEnabled = mfaCredentials.existsById(user.getId());
        boolean privileged = user.roleNames().stream().anyMatch(RoleName::isPrivileged);
        return new MeResponse(user.getId(), user.getEmail(), user.getFullName(), user.isEmailVerified(),
                user.getAccountStatus(), user.roleNames(), Authorities.forRoles(user.roleNames()), mfaEnabled,
                privileged && !mfaEnabled, stepUp.validUntil(session).orElse(null), user.isVerificationLocked(),
                affiliations.listForUser(user.getId()), user.getCreatedAt());
    }
}
