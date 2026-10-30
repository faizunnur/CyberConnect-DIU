package edu.diu.cyberconnect.support;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.AffiliationType;
import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.identity.application.MfaService;
import edu.diu.cyberconnect.identity.application.TotpGenerator;
import edu.diu.cyberconnect.identity.application.UserAccounts;
import edu.diu.cyberconnect.identity.application.UserSummary;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/** Creates applicants, verified members and MFA-enrolled moderators/admins through the real flows. */
@Component
public class TestActors {

    public static final String PASSWORD = "Correct-Horse-Battery-Staple-42";

    public record Actor(UUID id, String email, byte[] totpSecret) {
        /** Uses the next time step: the current one may already be consumed by enrolment or a previous login. */
        public String totpNow() {
            return TotpGenerator.code(totpSecret, TotpGenerator.currentStep() + 1);
        }
    }

    private final UserAccounts accounts;
    private final MfaService mfa;
    private final CapturingMailSender mail;
    private final TransactionTemplate tx;

    public TestActors(UserAccounts accounts, MfaService mfa, CapturingMailSender mail, TransactionTemplate tx) {
        this.accounts = accounts;
        this.mfa = mfa;
        this.mail = mail;
        this.tx = tx;
    }

    /** Registers through the API and verifies the email from the captured link. Returns the (PENDING) actor. */
    public Actor registerApplicant(ApiClient api, String emailPrefix, AffiliationType type) throws Exception {
        String email = ApiClient.uniqueEmail(emailPrefix);
        ApiClient.Session anonymous = api.newSession();
        Map<String, Object> body = switch (type) {
            case STUDENT -> Map.of("fullName", "Test Student", "email", email, "password", PASSWORD,
                    "affiliationType", "STUDENT", "studentId", "000-00-0001", "batch", "60", "department", "CSE",
                    "privacyAccepted", true);
            case ALUMNI -> Map.of("fullName", "Test Alumni", "email", email, "password", PASSWORD,
                    "affiliationType", "ALUMNI", "studentId", "000-00-0002", "graduationYear", 2022,
                    "privacyAccepted", true);
            case CSC_MEMBER -> Map.of("fullName", "Test Member", "email", email, "password", PASSWORD,
                    "affiliationType", "CSC_MEMBER", "cscMemberId", "CSC-0001", "privacyAccepted", true);
        };
        api.assertStatus(api.post("/api/v1/auth/register", body, anonymous), 202);
        String token = mail.extractParam(mail.lastTo(email).orElseThrow(), "token");
        api.assertStatus(api.post("/api/v1/auth/verify-email", Map.of("token", token), anonymous), 204);
        UserSummary summary = accounts.findByEmail(email).orElseThrow();
        return new Actor(summary.id(), email, null);
    }

    /** Applicant whose account was moved straight to the given status (service-level; audited as system). */
    public Actor applicantWithStatus(ApiClient api, String prefix, AccountStatus status) throws Exception {
        Actor actor = registerApplicant(api, prefix, AffiliationType.STUDENT);
        if (status == AccountStatus.SUSPENDED) {
            tx.executeWithoutResult(t -> accounts.changeStatus(actor.id(), AccountStatus.VERIFIED, null, "test setup"));
        }
        if (status != AccountStatus.PENDING) {
            tx.executeWithoutResult(t -> accounts.changeStatus(actor.id(), status, null, "test setup"));
        }
        return actor;
    }

    /** Verified member with the given privileged roles and MFA enrolled; returns the TOTP secret for logins. */
    public Actor privileged(ApiClient api, String prefix, Set<RoleName> roles) throws Exception {
        Actor base = registerApplicant(api, prefix, AffiliationType.ALUMNI);
        tx.executeWithoutResult(t -> {
            accounts.changeStatus(base.id(), AccountStatus.VERIFIED, null, "test setup");
            accounts.replaceRoles(base.id(), roles, null);
        });
        HttpSession session = new MockHttpSession();
        MfaService.Enrolment enrolment = tx.execute(t -> mfa.beginEnrolment(base.id(), session));
        byte[] secret = TotpGenerator.base32Decode(enrolment.secret());
        tx.executeWithoutResult(t -> mfa.confirmEnrolment(base.id(), TotpGenerator.code(secret,
                TotpGenerator.currentStep()), session));
        return new Actor(base.id(), base.email(), secret);
    }

    public Actor verifiedMember(ApiClient api, String prefix) throws Exception {
        return applicantWithStatus(api, prefix, AccountStatus.VERIFIED);
    }

    /** Signs in and completes the MFA step when required. */
    public ApiClient.Session login(ApiClient api, Actor actor) throws Exception {
        ApiClient.Session session = api.newSession();
        var loginResult = api.post("/api/v1/auth/login", Map.of("email", actor.email(), "password", PASSWORD), session);
        api.assertStatus(loginResult, 200);
        JsonNode result = api.body(loginResult);
        if (result.path("mfaRequired").asBoolean(false)) {
            assertThat(actor.totpSecret()).as("MFA required but actor has no secret").isNotNull();
            var mfaResult = api.post("/api/v1/auth/login/mfa", Map.of("code", actor.totpNow()), session);
            api.assertStatus(mfaResult, 200);
        }
        assertThat(session.hasSession())
                .as("session cookie after login; set-cookie=%s; requestSession=%s", loginResult.getResponse().getHeaders("Set-Cookie"),
                        loginResult.getRequest().getSession(false) == null ? "none" : loginResult.getRequest().getSession(false).getClass().getName())
                .isTrue();
        return session;
    }
}
