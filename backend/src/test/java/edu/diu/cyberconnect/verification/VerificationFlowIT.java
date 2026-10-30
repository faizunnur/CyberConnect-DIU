package edu.diu.cyberconnect.verification;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.common.config.StorageProperties;
import edu.diu.cyberconnect.common.model.AffiliationType;
import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.common.storage.ObjectStorage;
import edu.diu.cyberconnect.support.AbstractIntegrationTest;
import edu.diu.cyberconnect.support.ApiClient;
import edu.diu.cyberconnect.support.CapturingMailSender;
import edu.diu.cyberconnect.support.TestActors;
import edu.diu.cyberconnect.verification.application.RetentionPurgeJob;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Scenarios 1–5 and 8 of the proposal's critical end-to-end list, at the API level. */
class VerificationFlowIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestActors actors;
    @Autowired
    private CapturingMailSender mail;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RetentionPurgeJob purgeJob;
    @Autowired
    private ObjectStorage storage;
    @Autowired
    private StorageProperties storageProperties;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, objectMapper);
    }

    @Test
    void applicantIsApprovedByModeratorAndGainsAccess() throws Exception {
        TestActors.Actor moderator = actors.privileged(api, "mod", Set.of(RoleName.MODERATOR));
        TestActors.Actor applicant = actors.registerApplicant(api, "app", AffiliationType.STUDENT);
        ApiClient.Session applicantSession = actors.login(api, applicant);

        // Pending: own status is visible, community is not.
        JsonNode status = api.body(api.get("/api/v1/verification/status", applicantSession));
        assertThat(status.get("accountStatus").asString()).isEqualTo("PENDING");
        assertThat(status.get("canStartSubmission").asBoolean()).isTrue();
        MvcResult blocked = api.post("/api/v1/me/affiliations", Map.of("type", "CSC_MEMBER", "cscMemberId", "CSC-9"), applicantSession);
        api.assertStatus(blocked, 403);
        assertThat(api.code(blocked)).isEqualTo("ACCOUNT_NOT_VERIFIED");

        String affiliationId = status.get("affiliations").get(0).get("id").asString();
        MvcResult started = api.post("/api/v1/verification/submissions", Map.of("affiliationId", affiliationId), applicantSession);
        api.assertStatus(started, 201);
        String submissionId = api.body(started).get("id").asString();

        MvcResult noDocs = api.post("/api/v1/verification/submissions/" + submissionId + "/submit", null, applicantSession);
        api.assertStatus(noDocs, 400);

        MvcResult badFile = api.upload("/api/v1/verification/submissions/" + submissionId + "/documents",
                "MZ not an image".getBytes(), "id.png", "image/png", "STUDENT_ID_CARD", applicantSession);
        api.assertStatus(badFile, 400);
        assertThat(api.code(badFile)).isEqualTo("UNSUPPORTED_FILE_TYPE");

        MvcResult uploaded = api.upload("/api/v1/verification/submissions/" + submissionId + "/documents",
                ApiClient.fictionalIdCardPng(), "card.png", "image/png", "STUDENT_ID_CARD", applicantSession);
        api.assertStatus(uploaded, 201);
        String documentId = api.body(uploaded).get("id").asString();
        assertThat(api.body(uploaded).has("storageKey")).isFalse();

        api.assertStatus(api.post("/api/v1/verification/submissions/" + submissionId + "/submit", null, applicantSession), 200);
        api.assertStatus(api.upload("/api/v1/verification/submissions/" + submissionId + "/documents",
                ApiClient.fictionalIdCardPng(), "late.png", "image/png", "OTHER", applicantSession), 409);

        // Moderator sees it in the queue and can view the document only with step-up MFA (login MFA counts).
        ApiClient.Session modSession = actors.login(api, moderator);
        JsonNode queue = api.body(api.get("/api/v1/moderation/verifications", modSession));
        assertThat(queue.get("content").findValuesAsString("id")).contains(submissionId);
        api.assertStatus(api.post("/api/v1/moderation/verifications/" + submissionId + "/assign", null, modSession), 204);

        MvcResult document = api.get("/api/v1/moderation/verifications/" + submissionId + "/documents/" + documentId + "/content", modSession);
        api.assertStatus(document, 200);
        assertThat(document.getResponse().getContentType()).startsWith("image/png");
        assertThat(document.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(document.getResponse().getHeader("Content-Disposition")).startsWith("inline");
        Integer views = jdbc.queryForObject("select count(*) from document_access_log where document_id = ?::uuid", Integer.class, documentId);
        assertThat(views).isEqualTo(1);

        // The applicant can never fetch the document, even their own.
        api.assertStatus(api.get("/api/v1/moderation/verifications/" + submissionId + "/documents/" + documentId + "/content", applicantSession), 403);

        api.assertStatus(api.post("/api/v1/moderation/verifications/" + submissionId + "/approve", Map.of("internalNotes", "Looks fine"), modSession), 204);

        JsonNode me = api.body(api.get("/api/v1/auth/me", applicantSession));
        assertThat(me.get("accountStatus").asString()).isEqualTo("VERIFIED");
        assertThat(me.get("affiliations").get(0).get("status").asString()).isEqualTo("VERIFIED");
        api.assertStatus(api.post("/api/v1/me/affiliations", Map.of("type", "CSC_MEMBER", "cscMemberId", "CSC-9"), applicantSession), 201);
        assertThat(mail.lastTo(applicant.email()).orElseThrow().subject()).contains("Welcome");

        Integer audits = jdbc.queryForObject(
                "select count(*) from audit_logs where target_id = ?::uuid and action in ('VERIFICATION_SUBMISSION_APPROVED','VERIFICATION_SUBMISSION_ASSIGNED')",
                Integer.class, submissionId);
        assertThat(audits).isEqualTo(2);

        // Retention: after the window the object is gone but the metadata row remains.
        String storageKey = jdbc.queryForObject("select storage_key from verification_documents where id = ?::uuid", String.class, documentId);
        assertThat(storage.exists(storageProperties.buckets().documents(), storageKey)).isTrue();
        int purged = purgeJob.purgeExpired(Instant.now().plus(31, ChronoUnit.DAYS));
        assertThat(purged).isGreaterThanOrEqualTo(1);
        assertThat(storage.exists(storageProperties.buckets().documents(), storageKey)).isFalse();
        assertThat(jdbc.queryForObject("select purged_at is not null from verification_documents where id = ?::uuid", Boolean.class, documentId)).isTrue();
        api.assertStatus(api.get("/api/v1/moderation/verifications/" + submissionId + "/documents/" + documentId + "/content", modSession), 404);
    }

    @Test
    void rejectionResubmissionAndAppealWithSeparationOfDuties() throws Exception {
        TestActors.Actor firstModerator = actors.privileged(api, "mod1", Set.of(RoleName.MODERATOR));
        TestActors.Actor secondModerator = actors.privileged(api, "mod2", Set.of(RoleName.MODERATOR));
        TestActors.Actor applicant = actors.registerApplicant(api, "rej", AffiliationType.ALUMNI);
        ApiClient.Session applicantSession = actors.login(api, applicant);
        ApiClient.Session mod1 = actors.login(api, firstModerator);
        ApiClient.Session mod2 = actors.login(api, secondModerator);

        String affiliationId = api.body(api.get("/api/v1/verification/status", applicantSession))
                .get("affiliations").get(0).get("id").asString();
        String first = submitWithDocument(applicantSession, affiliationId);

        // Moderators cannot review themselves; first moderator rejects with a resubmittable reason.
        api.assertStatus(api.post("/api/v1/moderation/verifications/" + first + "/reject",
                Map.of("reasonCode", "DOCUMENT_UNREADABLE"), mod1), 204);
        // Rejection revokes the applicant's sessions; they must sign in again.
        api.assertStatus(api.get("/api/v1/auth/me", applicantSession), 401);
        applicantSession = actors.login(api, applicant);
        JsonNode me = api.body(api.get("/api/v1/auth/me", applicantSession));
        assertThat(me.get("accountStatus").asString()).isEqualTo("REJECTED");
        JsonNode status = api.body(api.get("/api/v1/verification/status", applicantSession));
        assertThat(status.get("currentSubmission").get("decision").get("reasonCode").asString()).isEqualTo("DOCUMENT_UNREADABLE");
        assertThat(status.get("currentSubmission").get("decision").get("allowsResubmission").asBoolean()).isTrue();
        assertThat(status.get("canStartSubmission").asBoolean()).isTrue();
        assertThat(status.get("attemptsRemaining").asInt()).isEqualTo(2);
        assertThat(mail.lastTo(applicant.email()).orElseThrow().textBody()).contains("not approved").doesNotContain("internal");

        // Resubmission moves the account back to PENDING; second rejection is not resubmittable.
        String second = submitWithDocument(applicantSession, affiliationId);
        assertThat(api.body(api.get("/api/v1/auth/me", applicantSession)).get("accountStatus").asString()).isEqualTo("PENDING");
        api.assertStatus(api.post("/api/v1/moderation/verifications/" + second + "/reject",
                Map.of("reasonCode", "NOT_ELIGIBLE", "internalNotes", "internal-only text"), mod1), 204);
        api.assertStatus(api.get("/api/v1/auth/me", applicantSession), 401);
        applicantSession = actors.login(api, applicant);
        status = api.body(api.get("/api/v1/verification/status", applicantSession));
        assertThat(status.get("canStartSubmission").asBoolean()).isFalse();
        assertThat(status.get("canAppeal").asBoolean()).isTrue();
        assertThat(status.toString()).doesNotContain("internal-only text");
        MvcResult blockedResubmit = api.post("/api/v1/verification/submissions", Map.of("affiliationId", affiliationId), applicantSession);
        api.assertStatus(blockedResubmit, 409);
        assertThat(api.code(blockedResubmit)).isEqualTo("RESUBMISSION_NOT_ALLOWED");

        // Appeal: the rejecting moderator may not decide it; another moderator approves.
        MvcResult appeal = api.post("/api/v1/verification/appeals", Map.of("submissionId", second,
                "statement", "I graduated in 2022; the transcript shows my degree and student id."), applicantSession);
        api.assertStatus(appeal, 201);
        String appealId = api.body(appeal).get("id").asString();
        api.assertStatus(api.post("/api/v1/verification/appeals", Map.of("submissionId", second,
                "statement", "duplicate appeal attempt with enough characters"), applicantSession), 409);

        MvcResult sameReviewer = api.post("/api/v1/moderation/appeals/" + appealId + "/decide",
                Map.of("approved", true, "textForApplicant", "ok"), mod1);
        api.assertStatus(sameReviewer, 403);
        assertThat(api.code(sameReviewer)).isEqualTo("SAME_REVIEWER");

        JsonNode appealQueue = api.body(api.get("/api/v1/moderation/appeals", mod2));
        assertThat(appealQueue.get("content").findValuesAsString("id")).contains(appealId);
        api.assertStatus(api.post("/api/v1/moderation/appeals/" + appealId + "/decide",
                Map.of("approved", true, "textForApplicant", "Evidence verified on appeal."), mod2), 204);

        assertThat(api.body(api.get("/api/v1/auth/me", applicantSession)).get("accountStatus").asString()).isEqualTo("VERIFIED");
        assertThat(api.body(api.get("/api/v1/verification/appeals/" + appealId, applicantSession)).get("status").asString()).isEqualTo("APPROVED");
    }

    @Test
    void deniedAppealLocksVerificationUntilAdminReopens() throws Exception {
        TestActors.Actor moderator = actors.privileged(api, "modA", Set.of(RoleName.MODERATOR));
        TestActors.Actor judge = actors.privileged(api, "modB", Set.of(RoleName.MODERATOR));
        TestActors.Actor admin = actors.privileged(api, "adm", Set.of(RoleName.ADMIN));
        TestActors.Actor applicant = actors.registerApplicant(api, "deny", AffiliationType.CSC_MEMBER);
        ApiClient.Session applicantSession = actors.login(api, applicant);
        ApiClient.Session modSession = actors.login(api, moderator);
        ApiClient.Session judgeSession = actors.login(api, judge);
        ApiClient.Session adminSession = actors.login(api, admin);

        String affiliationId = api.body(api.get("/api/v1/verification/status", applicantSession))
                .get("affiliations").get(0).get("id").asString();
        String submissionId = submitWithDocument(applicantSession, affiliationId);
        api.assertStatus(api.post("/api/v1/moderation/verifications/" + submissionId + "/reject",
                Map.of("reasonCode", "SUSPECTED_FRAUD", "internalNotes", "matches known pattern"), modSession), 204);
        api.assertStatus(api.get("/api/v1/auth/me", applicantSession), 401);
        applicantSession = actors.login(api, applicant);
        MvcResult appeal = api.post("/api/v1/verification/appeals", Map.of("submissionId", submissionId,
                "statement", "I believe this was a mistake, here is my explanation in detail."), applicantSession);
        String appealId = api.body(appeal).get("id").asString();
        api.assertStatus(api.post("/api/v1/moderation/appeals/" + appealId + "/decide",
                Map.of("approved", false, "textForApplicant", "The decision stands."), judgeSession), 204);

        JsonNode status = api.body(api.get("/api/v1/verification/status", applicantSession));
        assertThat(status.get("verificationLocked").asBoolean()).isTrue();
        assertThat(status.get("canStartSubmission").asBoolean()).isFalse();
        assertThat(status.get("canAppeal").asBoolean()).isFalse();

        // Admin (not a moderator) cannot read documents, but can reopen the case.
        api.assertStatus(api.get("/api/v1/moderation/verifications", adminSession), 403);
        api.assertStatus(api.post("/api/v1/admin/members/" + applicant.id() + "/reopen-verification",
                Map.of("reason", "New evidence provided by the department"), adminSession), 204);
        status = api.body(api.get("/api/v1/verification/status", applicantSession));
        assertThat(status.get("verificationLocked").asBoolean()).isFalse();
        assertThat(status.get("canStartSubmission").asBoolean()).isFalse();
    }

    private String submitWithDocument(ApiClient.Session session, String affiliationId) throws Exception {
        MvcResult started = api.post("/api/v1/verification/submissions", Map.of("affiliationId", affiliationId), session);
        api.assertStatus(started, 201);
        String submissionId = api.body(started).get("id").asString();
        api.assertStatus(api.upload("/api/v1/verification/submissions/" + submissionId + "/documents",
                ApiClient.fictionalIdCardPng(), "evidence.png", "image/png", "EVIDENCE", session), 201);
        api.assertStatus(api.post("/api/v1/verification/submissions/" + submissionId + "/submit", null, session), 200);
        return submissionId;
    }
}
