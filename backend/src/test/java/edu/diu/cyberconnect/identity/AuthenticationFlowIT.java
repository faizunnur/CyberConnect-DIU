package edu.diu.cyberconnect.identity;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.identity.application.UserAccounts;
import edu.diu.cyberconnect.support.AbstractIntegrationTest;
import edu.diu.cyberconnect.support.ApiClient;
import edu.diu.cyberconnect.support.CapturingMailSender;
import edu.diu.cyberconnect.support.TestActors;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AuthenticationFlowIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CapturingMailSender mail;
    @Autowired
    private TestActors actors;
    @Autowired
    private UserAccounts accounts;
    @Autowired
    private TransactionTemplate tx;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, objectMapper);
    }

    @Test
    void registrationEmailVerificationAndLoginWork() throws Exception {
        String email = ApiClient.uniqueEmail("auth");
        ApiClient.Session browser = api.newSession();
        MvcResult registered = api.post("/api/v1/auth/register", Map.of(
                "fullName", "Ayesha Example", "email", email, "password", TestActors.PASSWORD,
                "affiliationType", "STUDENT", "studentId", "000-00-0009", "batch", "61", "department", "CSE",
                "privacyAccepted", true), browser);
        api.assertStatus(registered, 202);

        // Uniform response for duplicate registration; existing account gets an informational mail instead.
        mail.clear();
        api.assertStatus(api.post("/api/v1/auth/register", Map.of(
                "fullName", "Someone Else", "email", email, "password", TestActors.PASSWORD,
                "affiliationType", "STUDENT", "studentId", "000-00-0009", "batch", "61", "department", "CSE",
                "privacyAccepted", true), browser), 202);
        assertThat(mail.lastTo(email).orElseThrow().subject()).contains("account");
        assertThat(accounts.findByEmail(email).orElseThrow().emailVerified()).isFalse();

        // Sign-in is allowed before email verification; the flag is reported.
        ApiClient.Session session = api.newSession();
        MvcResult login = api.post("/api/v1/auth/login", Map.of("email", email, "password", TestActors.PASSWORD), session);
        api.assertStatus(login, 200);
        JsonNode me = api.body(login).get("user");
        assertThat(me.get("accountStatus").asString()).isEqualTo("PENDING");
        assertThat(me.get("emailVerified").asBoolean()).isFalse();
        assertThat(me.get("affiliations").get(0).get("type").asString()).isEqualTo("STUDENT");
        assertThat(me.has("passwordHash")).isFalse();

        // Resend + verify via the captured link.
        api.assertStatus(api.post("/api/v1/auth/resend-verification", null, session), 202);
        String token = mail.extractParam(mail.lastTo(email).orElseThrow(), "token");
        api.assertStatus(api.post("/api/v1/auth/verify-email", Map.of("token", token), api.newSession()), 204);
        api.assertStatus(api.post("/api/v1/auth/verify-email", Map.of("token", token), api.newSession()), 400);
        assertThat(api.body(api.get("/api/v1/auth/me", session)).get("emailVerified").asBoolean()).isTrue();

        // Wrong password is a uniform 401, and the session survives.
        MvcResult bad = api.post("/api/v1/auth/login", Map.of("email", email, "password", "nope-nope-nope-1"), api.newSession());
        api.assertStatus(bad, 401);
        assertThat(api.code(bad)).isEqualTo("INVALID_CREDENTIALS");
        MvcResult unknown = api.post("/api/v1/auth/login", Map.of("email", "nobody@example.test", "password", "nope-nope-nope-1"), api.newSession());
        api.assertStatus(unknown, 401);
        assertThat(api.code(unknown)).isEqualTo("INVALID_CREDENTIALS");

        // Logout kills the session.
        api.assertStatus(api.post("/api/v1/auth/logout", null, session), 204);
        api.assertStatus(api.get("/api/v1/auth/me", session), 401);
    }

    @Test
    void passwordResetRevokesOtherSessions() throws Exception {
        TestActors.Actor actor = actors.registerApplicant(api, "reset", edu.diu.cyberconnect.common.model.AffiliationType.STUDENT);
        ApiClient.Session oldSession = actors.login(api, actor);
        api.assertStatus(api.get("/api/v1/auth/me", oldSession), 200);

        api.assertStatus(api.post("/api/v1/auth/forgot-password", Map.of("email", actor.email()), api.newSession()), 202);
        api.assertStatus(api.post("/api/v1/auth/forgot-password", Map.of("email", "ghost@example.test"), api.newSession()), 202);
        String token = mail.extractParam(mail.lastTo(actor.email()).orElseThrow(), "token");
        String newPassword = "Another-Strong-Passphrase-77";
        api.assertStatus(api.post("/api/v1/auth/reset-password", Map.of("token", token, "newPassword", newPassword), api.newSession()), 204);

        api.assertStatus(api.get("/api/v1/auth/me", oldSession), 401);
        ApiClient.Session fresh = api.newSession();
        api.assertStatus(api.post("/api/v1/auth/login", Map.of("email", actor.email(), "password", newPassword), fresh), 200);
        api.assertStatus(api.post("/api/v1/auth/login", Map.of("email", actor.email(), "password", TestActors.PASSWORD), api.newSession()), 401);
    }

    @Test
    void mfaEnrolmentLoginStepUpAndRecoveryCodes() throws Exception {
        TestActors.Actor member = actors.verifiedMember(api, "mfa");
        ApiClient.Session session = actors.login(api, member);

        MvcResult enrol = api.post("/api/v1/mfa/enrol", null, session);
        api.assertStatus(enrol, 200);
        String secretBase32 = api.body(enrol).get("secret").asString();
        assertThat(api.body(enrol).get("otpauthUri").asString()).startsWith("otpauth://totp/");
        byte[] secret = edu.diu.cyberconnect.identity.application.TotpGenerator.base32Decode(secretBase32);

        api.assertStatus(api.post("/api/v1/mfa/enrol/confirm", Map.of("code", "000000"), session), 400);
        MvcResult confirm = api.post("/api/v1/mfa/enrol/confirm",
                Map.of("code", edu.diu.cyberconnect.identity.application.TotpGenerator.code(secret,
                        edu.diu.cyberconnect.identity.application.TotpGenerator.currentStep())), session);
        api.assertStatus(confirm, 200);
        JsonNode codes = api.body(confirm).get("recoveryCodes");
        assertThat(codes).hasSize(10);
        String recovery = codes.get(0).asString();
        assertThat(recovery).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");

        // Next login requires MFA; a recovery code works once.
        ApiClient.Session second = api.newSession();
        MvcResult first = api.post("/api/v1/auth/login", Map.of("email", member.email(), "password", TestActors.PASSWORD), second);
        assertThat(api.body(first).get("mfaRequired").asBoolean()).isTrue();
        api.assertStatus(api.get("/api/v1/auth/me", second), 401);
        api.assertStatus(api.post("/api/v1/auth/login/mfa", Map.of("code", recovery), second), 200);
        assertThat(api.body(api.get("/api/v1/auth/me", second)).get("mfaEnabled").asBoolean()).isTrue();

        ApiClient.Session third = api.newSession();
        api.post("/api/v1/auth/login", Map.of("email", member.email(), "password", TestActors.PASSWORD), third);
        MvcResult reused = api.post("/api/v1/auth/login/mfa", Map.of("code", recovery), third);
        api.assertStatus(reused, 401);

        // Step-up: a TOTP code refreshes the window reported on /me.
        MvcResult stepUp = api.post("/api/v1/mfa/step-up", Map.of("code",
                edu.diu.cyberconnect.identity.application.TotpGenerator.code(secret,
                        edu.diu.cyberconnect.identity.application.TotpGenerator.currentStep() + 1)), second);
        api.assertStatus(stepUp, 200);
        assertThat(api.body(api.get("/api/v1/auth/me", second)).get("stepUpValidUntil").isNull()).isFalse();
    }

    @Test
    void privilegedRolesCannotDisableMfaAndRoleChangesRevokeSessions() throws Exception {
        TestActors.Actor moderator = actors.privileged(api, "mod", Set.of(RoleName.MODERATOR));
        ApiClient.Session session = actors.login(api, moderator);
        MvcResult disable = api.post("/api/v1/mfa/disable", Map.of("code", moderator.totpNow()), session);
        api.assertStatus(disable, 403);
        assertThat(api.code(disable)).isEqualTo("MFA_REQUIRED_FOR_ROLE");

        tx.executeWithoutResult(t -> accounts.replaceRoles(moderator.id(), Set.of(), null));
        api.assertStatus(api.get("/api/v1/auth/me", session), 401);
        assertThat(accounts.get(moderator.id()).roles()).containsExactly(RoleName.MEMBER);
    }

    @Test
    void suspensionTerminatesLiveSessions() throws Exception {
        TestActors.Actor member = actors.verifiedMember(api, "susp");
        ApiClient.Session session = actors.login(api, member);
        api.assertStatus(api.get("/api/v1/auth/me", session), 200);
        tx.executeWithoutResult(t -> accounts.changeStatus(member.id(), AccountStatus.SUSPENDED, null, "test"));
        api.assertStatus(api.get("/api/v1/auth/me", session), 401);
    }
}
