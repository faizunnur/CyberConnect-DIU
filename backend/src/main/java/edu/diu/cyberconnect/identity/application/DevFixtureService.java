package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.affiliation.application.AffiliationDetails;
import edu.diu.cyberconnect.affiliation.application.AffiliationService;
import edu.diu.cyberconnect.affiliation.application.AffiliationSummary;
import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.AffiliationType;
import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.identity.domain.Role;
import edu.diu.cyberconnect.identity.domain.User;
import edu.diu.cyberconnect.identity.infrastructure.RoleRepository;
import edu.diu.cyberconnect.identity.infrastructure.UserRepository;
import edu.diu.cyberconnect.profile.application.ProfileService;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates already-verified members for end-to-end tests. Only instantiated outside production and when
 * {@code app.e2e-fixtures.enabled=true} (see {@code E2eFixturesGuard}). Passwords are random and returned once.
 */
@Service
@Profile("!prod")
@ConditionalOnProperty(name = "app.e2e-fixtures.enabled", havingValue = "true")
public class DevFixtureService {

    public record Fixture(UUID userId, String email, String password) {
    }

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AffiliationService affiliations;
    private final ProfileService profiles;
    private final SecureRandom random = new SecureRandom();

    public DevFixtureService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
            AffiliationService affiliations, ProfileService profiles) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.affiliations = affiliations;
        this.profiles = profiles;
    }

    @Transactional
    public Fixture verifiedMember(String prefix, AffiliationType type, String fullName) {
        String email = (prefix == null || prefix.isBlank() ? "e2e" : prefix.replaceAll("[^a-z0-9-]", ""))
                + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        String password = "E2e-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Role member = roles.findByName(RoleName.MEMBER).orElseThrow();
        User user = users.save(User.register(email, passwordEncoder.encode(password),
                fullName == null || fullName.isBlank() ? "E2E Member" : fullName, member));
        user.markEmailVerified();
        user.changeStatus(AccountStatus.VERIFIED);
        AffiliationType affiliationType = type == null ? AffiliationType.ALUMNI : type;
        AffiliationSummary affiliation = affiliations.createPrimary(user.getId(), affiliationType,
                details(affiliationType));
        affiliations.markVerified(affiliation.id(), null);
        profiles.ensureProfile(user.getId(), user.getFullName());
        return new Fixture(user.getId(), email, password);
    }

    private static AffiliationDetails details(AffiliationType type) {
        Map<AffiliationType, AffiliationDetails> defaults = Map.of(
                AffiliationType.STUDENT, new AffiliationDetails("000-00-0001", "60", "CSE", null, null),
                AffiliationType.ALUMNI, new AffiliationDetails("000-00-0002", null, null, 2022, null),
                AffiliationType.CSC_MEMBER, new AffiliationDetails(null, null, null, null, "CSC-0001"));
        return defaults.get(type);
    }
}
