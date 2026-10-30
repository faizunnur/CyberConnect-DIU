package edu.diu.cyberconnect.security;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.AffiliationType;
import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.support.AbstractIntegrationTest;
import edu.diu.cyberconnect.support.ApiClient;
import edu.diu.cyberconnect.support.TestActors;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Account-state × endpoint matrix. Every protected area must answer with the documented problem code for each
 * status, and never leak data through ownership gaps.
 */
class AuthorizationMatrixIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestActors actors;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, objectMapper);
    }

    @Test
    void anonymousRequestsGet401OnEverythingProtected() throws Exception {
        ApiClient.Session anonymous = api.newSession();
        for (String path : new String[] {"/api/v1/auth/me", "/api/v1/verification/status", "/api/v1/me/affiliations",
                "/api/v1/moderation/verifications", "/api/v1/admin/members", "/api/v1/admin/audit"}) {
            MvcResult result = api.get(path, anonymous);
            api.assertStatus(result, 401);
            assertThat(api.code(result)).isEqualTo("AUTHENTICATION_REQUIRED");
        }
    }

    @Test
    void nonVerifiedStatusesReachOnlyOnboardingEndpoints() throws Exception {
        Map<AccountStatus, String> expectedCode = Map.of(
                AccountStatus.PENDING, "ACCOUNT_NOT_VERIFIED",
                AccountStatus.REJECTED, "ACCOUNT_REJECTED",
                AccountStatus.SUSPENDED, "ACCOUNT_SUSPENDED",
                AccountStatus.DEACTIVATED, "ACCOUNT_DEACTIVATED");

        for (var entry : expectedCode.entrySet()) {
            TestActors.Actor actor = actors.applicantWithStatus(api, "mx-" + entry.getKey().name().toLowerCase(), entry.getKey());
            ApiClient.Session session = actors.login(api, actor);

            api.assertStatus(api.get("/api/v1/auth/me", session), 200);
            api.assertStatus(api.get("/api/v1/verification/status", session), 200);
            api.assertStatus(api.get("/api/v1/me/affiliations", session), 200);

            for (String path : new String[] {"/api/v1/moderation/verifications", "/api/v1/admin/members",
                    "/api/v1/admin/settings", "/api/v1/admin/audit"}) {
                MvcResult result = api.get(path, session);
                api.assertStatus(result, 403);
                assertThat(api.code(result)).as(entry.getKey() + " " + path).isEqualTo(entry.getValue());
            }
            MvcResult mutation = api.post("/api/v1/me/affiliations", Map.of("type", "CSC_MEMBER", "cscMemberId", "X-1"), session);
            api.assertStatus(mutation, 403);
            assertThat(api.code(mutation)).isEqualTo(entry.getValue());
        }
    }

    @Test
    void verifiedMembersAreBlockedFromPrivilegedAreasByRoleAndMfa() throws Exception {
        TestActors.Actor member = actors.verifiedMember(api, "mx-verified");
        ApiClient.Session session = actors.login(api, member);
        for (String path : new String[] {"/api/v1/moderation/verifications", "/api/v1/moderation/appeals",
                "/api/v1/admin/members", "/api/v1/admin/settings", "/api/v1/admin/audit"}) {
            MvcResult result = api.get(path, session);
            api.assertStatus(result, 403);
            assertThat(api.code(result)).as(path).isEqualTo("MFA_ENROLLMENT_REQUIRED");
        }

        // A moderator without ADMIN authority is denied admin endpoints with a plain access-denied code.
        TestActors.Actor moderator = actors.privileged(api, "mx-mod", Set.of(RoleName.MODERATOR));
        ApiClient.Session modSession = actors.login(api, moderator);
        api.assertStatus(api.get("/api/v1/moderation/verifications", modSession), 200);
        MvcResult denied = api.get("/api/v1/admin/settings", modSession);
        api.assertStatus(denied, 403);
        assertThat(api.code(denied)).isEqualTo("ACCESS_DENIED");

        // An admin without MODERATOR is denied document review.
        TestActors.Actor admin = actors.privileged(api, "mx-adm", Set.of(RoleName.ADMIN));
        ApiClient.Session adminSession = actors.login(api, admin);
        api.assertStatus(api.get("/api/v1/admin/settings", adminSession), 200);
        api.assertStatus(api.get("/api/v1/moderation/verifications", adminSession), 403);
    }

    @Test
    void ownershipIsEnforcedBetweenApplicants() throws Exception {
        TestActors.Actor alice = actors.registerApplicant(api, "alice", AffiliationType.STUDENT);
        TestActors.Actor bob = actors.registerApplicant(api, "bob", AffiliationType.STUDENT);
        ApiClient.Session aliceSession = actors.login(api, alice);
        ApiClient.Session bobSession = actors.login(api, bob);

        String aliceAffiliation = api.body(api.get("/api/v1/verification/status", aliceSession))
                .get("affiliations").get(0).get("id").asString();
        MvcResult started = api.post("/api/v1/verification/submissions", Map.of("affiliationId", aliceAffiliation), aliceSession);
        String submissionId = api.body(started).get("id").asString();

        MvcResult bobStarts = api.post("/api/v1/verification/submissions", Map.of("affiliationId", aliceAffiliation), bobSession);
        api.assertStatus(bobStarts, 403);
        assertThat(api.code(bobStarts)).isEqualTo("NOT_OWNER");

        MvcResult bobUploads = api.upload("/api/v1/verification/submissions/" + submissionId + "/documents",
                ApiClient.fictionalIdCardPng(), "x.png", "image/png", "EVIDENCE", bobSession);
        api.assertStatus(bobUploads, 403);
        api.assertStatus(api.post("/api/v1/verification/submissions/" + submissionId + "/submit", null, bobSession), 403);
        api.assertStatus(api.put("/api/v1/me/affiliations/" + aliceAffiliation, Map.of("studentId", "999-99-9999", "batch", "1", "department", "X"), bobSession), 403);
        api.assertStatus(api.get("/api/v1/verification/appeals/" + UUID.randomUUID(), bobSession), 404);
    }

    @Test
    void roleAndStatusFieldsCannotBeMassAssigned() throws Exception {
        ApiClient.Session anonymous = api.newSession();
        String email = ApiClient.uniqueEmail("mass");
        api.assertStatus(api.post("/api/v1/auth/register", Map.of(
                "fullName", "Mass Assign", "email", email, "password", TestActors.PASSWORD,
                "affiliationType", "STUDENT", "studentId", "000-00-0100", "batch", "60", "department", "CSE",
                "privacyAccepted", true, "roles", new String[] {"SUPER_ADMIN"}, "accountStatus", "VERIFIED"), anonymous), 202);
        ApiClient.Session session = api.newSession();
        MvcResult login = api.post("/api/v1/auth/login", Map.of("email", email, "password", TestActors.PASSWORD), session);
        assertThat(api.body(login).get("user").get("accountStatus").asString()).isEqualTo("PENDING");
        assertThat(api.body(login).get("user").get("roles").findValuesAsString("")).isEmpty();
        assertThat(api.body(login).get("user").get("roles").toString()).isEqualTo("[\"MEMBER\"]");
    }
}
