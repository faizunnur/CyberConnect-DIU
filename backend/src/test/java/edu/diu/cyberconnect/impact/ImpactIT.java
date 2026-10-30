package edu.diu.cyberconnect.impact;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.support.AbstractIntegrationTest;
import edu.diu.cyberconnect.support.ApiClient;
import edu.diu.cyberconnect.support.TestActors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Impact records (D-12): on-platform sources, dual confirmation, disputes, moderator resolution, CONFIRMED-only stats. */
class ImpactIT extends AbstractIntegrationTest {

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
    void proposeConfirmDisputeResolveAndCount() throws Exception {
        TestActors.Actor moderator = actors.privileged(api, "im-mod", Set.of(RoleName.MODERATOR));
        TestActors.Actor author = actors.verifiedMember(api, "im-author");
        TestActors.Actor applicant = actors.verifiedMember(api, "im-applicant");
        TestActors.Actor member = actors.verifiedMember(api, "im-member");
        TestActors.Actor stranger = actors.verifiedMember(api, "im-stranger");
        ApiClient.Session m = actors.login(api, moderator);
        ApiClient.Session a = actors.login(api, author);
        ApiClient.Session b = actors.login(api, applicant);
        ApiClient.Session c = actors.login(api, member);
        ApiClient.Session s = actors.login(api, stranger);
        long baseline = api.body(api.get("/api/v1/impact/stats", s)).get("confirmed").asLong();

        // Sources: an accepted application, an active research project with a member, a completed mentorship.
        Map<String, Object> opportunity = new HashMap<>(Map.of("type", "INTERNSHIP", "title", "SOC intern", "organization", "ACME",
                "locationType", "REMOTE", "description", "Triage alerts with the blue team for three months.", "applicationMethod", "INTERNAL", "publish", true));
        String opportunityId = api.body(api.post("/api/v1/opportunities", opportunity, a)).get("id").asString();
        api.assertStatus(api.post("/api/v1/opportunities/" + opportunityId + "/apply", Map.of("coverNote", "please"), b), 201);
        String applicationId = api.body(api.get("/api/v1/opportunities/" + opportunityId + "/applicants", a)).get("content").get(0).get("id").asString();
        api.assertStatus(api.post("/api/v1/opportunities/" + opportunityId + "/applicants/" + applicationId + "/decide", Map.of("status", "ACCEPTED"), a), 200);
        api.assertStatus(api.post("/api/v1/opportunities/" + opportunityId + "/apply", Map.of(), s), 201); // stays SUBMITTED
        String strangerApplication = api.body(api.get("/api/v1/opportunities/" + opportunityId + "/applicants", a)).get("content").get(1).get("id").asString();

        String projectId = api.body(api.post("/api/v1/research", Map.of("title", "Honeypot telemetry", "summary", "Collecting and clustering honeypot sessions for a paper."), a)).get("id").asString();
        api.assertStatus(api.post("/api/v1/research/" + projectId + "/apply", Map.of(), c), 201);
        String researchApplication = api.body(api.get("/api/v1/research/" + projectId, a)).get("pendingApplications").get(0).get("id").asString();
        api.assertStatus(api.post("/api/v1/research/" + projectId + "/applications/" + researchApplication + "/accept", null, a), 200);
        api.assertStatus(api.post("/api/v1/research/" + projectId + "/status", Map.of("status", "ACTIVE"), a), 200);

        api.assertStatus(api.put("/api/v1/me/profile", Map.of("displayName", "Author Mentor", "mentorAvailable", true), a), 200);
        String mentorship = api.body(api.post("/api/v1/mentorship/requests", Map.of("mentorId", author.id().toString(), "topic", "SOC careers",
                "message", "How do I move from help desk to SOC?"), b)).get("id").asString();
        api.assertStatus(api.post("/api/v1/mentorship/requests/" + mentorship + "/accept", null, a), 200);
        String pendingMentorship = api.body(api.post("/api/v1/mentorship/requests", Map.of("mentorId", author.id().toString(), "topic", "Later",
                "message", "Can we talk next month about certifications?"), c)).get("id").asString();

        // Sources are computed server-side per member.
        JsonNode applicantSources = api.body(api.get("/api/v1/impact/sources", b));
        assertThat(applicantSources.findValuesAsString("type")).contains("JOB_REFERRAL").doesNotContain("MENTORSHIP");
        assertThat(applicantSources.get(0).get("myRole").asString()).isEqualTo("BENEFICIARY");
        assertThat(applicantSources.get(0).get("counterpart").get("userId").asString()).isEqualTo(author.id().toString());
        assertThat(api.body(api.get("/api/v1/impact/sources", s)).size()).as("submitted application is not eligible").isZero();

        // Job referral proposed by the beneficiary; only the counterpart can confirm; stats count CONFIRMED only.
        Map<String, Object> proposal = new HashMap<>(Map.of("type", "JOB_REFERRAL", "sourceId", applicationId,
                "title", "Got the ACME SOC internship", "description", "Referred and\u0000 hired."));
        proposal.put("status", "CONFIRMED"); // mass assignment attempt
        MvcResult proposed = api.post("/api/v1/impact", proposal, b);
        api.assertStatus(proposed, 201);
        JsonNode record = api.body(proposed);
        String jobImpact = record.get("id").asString();
        assertThat(record.get("status").asString()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(record.get("description").asString()).isEqualTo("Referred and hired.");
        assertThat(record.get("beneficiaryConfirmedAt").isNull()).isFalse();
        assertThat(record.get("contributorConfirmedAt").isNull()).isTrue();
        assertThat(record.get("awaitingMe").asBoolean()).isFalse();
        assertThat(record.get("sourceTitle").asString()).isEqualTo("SOC intern");
        assertThat(record.get("trail").get(0).get("decision").asString()).isEqualTo("PROPOSE");
        MvcResult duplicate = api.post("/api/v1/impact", proposal, b);
        api.assertStatus(duplicate, 409);
        assertThat(api.code(duplicate)).isEqualTo("IMPACT_EXISTS");
        api.assertStatus(api.get("/api/v1/impact/" + jobImpact, s), 404);
        api.assertStatus(api.post("/api/v1/impact/" + jobImpact + "/confirm", null, s), 404);
        MvcResult selfConfirm = api.post("/api/v1/impact/" + jobImpact + "/confirm", null, b);
        api.assertStatus(selfConfirm, 409);
        assertThat(api.code(selfConfirm)).isEqualTo("NOT_AWAITING_YOU");
        assertThat(api.body(api.get("/api/v1/notifications", a)).get("content").findValuesAsString("type")).contains("IMPACT_PROPOSED");
        JsonNode forAuthor = api.body(api.get("/api/v1/impact", a)).get("content").get(0);
        assertThat(forAuthor.get("awaitingMe").asBoolean()).isTrue();
        assertThat(forAuthor.get("myRole").asString()).isEqualTo("CONTRIBUTOR");
        assertThat(api.body(api.get("/api/v1/impact/stats", s)).get("confirmed").asLong()).isEqualTo(baseline);
        JsonNode confirmed = api.body(api.post("/api/v1/impact/" + jobImpact + "/confirm", null, a));
        assertThat(confirmed.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(confirmed.get("confirmedAt").isNull()).isFalse();
        assertThat(api.body(api.get("/api/v1/impact/stats", s)).get("confirmed").asLong()).isEqualTo(baseline + 1);
        JsonNode authorImpact = api.body(api.get("/api/v1/members/" + author.id() + "/impact", b));
        assertThat(authorImpact.get("asContributor").asLong()).isEqualTo(1);
        assertThat(authorImpact.get("contributedByType").get("JOB_REFERRAL").asLong()).isEqualTo(1);
        assertThat(authorImpact.get("recent").get(0).get("title").asString()).isEqualTo("Got the ACME SOC internship");

        // Ineligible sources: not a party, not accepted, mentorship not completed.
        MvcResult notParty = api.post("/api/v1/impact", Map.of("type", "JOB_REFERRAL", "sourceId", applicationId, "title", "x"), s);
        api.assertStatus(notParty, 404);
        MvcResult notAccepted = api.post("/api/v1/impact", Map.of("type", "JOB_REFERRAL", "sourceId", strangerApplication, "title", "x"), s);
        api.assertStatus(notAccepted, 409);
        assertThat(api.code(notAccepted)).isEqualTo("SOURCE_NOT_ELIGIBLE");
        api.assertStatus(api.post("/api/v1/impact", Map.of("type", "MENTORSHIP", "sourceId", mentorship, "title", "x"), b), 409);
        api.assertStatus(api.post("/api/v1/impact", Map.of("type", "MENTORSHIP", "sourceId", pendingMentorship, "title", "x"), c), 409);

        // Research: the lead proposes for a member, the member disputes, a moderator rejects, a new proposal is confirmed.
        MvcResult noCounterpart = api.post("/api/v1/impact", Map.of("type", "RESEARCH", "sourceId", projectId, "title", "Co-authored the paper"), a);
        api.assertStatus(noCounterpart, 400);
        assertThat(api.code(noCounterpart)).isEqualTo("COUNTERPART_REQUIRED");
        String researchImpact = api.body(api.post("/api/v1/impact", Map.of("type", "RESEARCH", "sourceId", projectId,
                "counterpartId", member.id().toString(), "title", "Co-authored the honeypot paper"), a)).get("id").asString();
        api.assertStatus(api.post("/api/v1/impact/" + researchImpact + "/dispute", Map.of("reason", ""), c), 400);
        JsonNode disputed = api.body(api.post("/api/v1/impact/" + researchImpact + "/dispute", Map.of("reason", "I only attended one meeting."), c));
        assertThat(disputed.get("status").asString()).isEqualTo("DISPUTED");
        api.assertStatus(api.post("/api/v1/impact/" + researchImpact + "/withdraw", null, a), 409);
        api.assertStatus(api.get("/api/v1/moderation/impact/disputes", a), 403);
        assertThat(api.body(api.get("/api/v1/moderation/impact/disputes", m)).get("content").findValuesAsString("id")).contains(researchImpact);
        JsonNode resolved = api.body(api.post("/api/v1/moderation/impact/" + researchImpact + "/resolve", Map.of("confirmed", false, "note", "Not enough evidence."), m));
        assertThat(resolved.get("status").asString()).isEqualTo("REJECTED");
        assertThat(resolved.get("resolutionNote").asString()).isEqualTo("Not enough evidence.");
        assertThat(resolved.get("trail").findValuesAsString("decision")).containsExactly("PROPOSE", "DISPUTE", "RESOLVE_REJECTED");
        api.assertStatus(api.post("/api/v1/moderation/impact/" + researchImpact + "/resolve", Map.of("confirmed", true), m), 409);
        assertThat(api.body(api.get("/api/v1/impact/stats", s)).get("confirmed").asLong()).isEqualTo(baseline + 1);
        String again = api.body(api.post("/api/v1/impact", Map.of("type", "RESEARCH", "sourceId", projectId,
                "title", "Collected the honeypot dataset"), c)).get("id").asString();
        assertThat(api.body(api.post("/api/v1/impact/" + again + "/confirm", null, a)).get("status").asString()).isEqualTo("CONFIRMED");
        JsonNode stats = api.body(api.get("/api/v1/impact/stats", s));
        assertThat(stats.get("confirmed").asLong()).isEqualTo(baseline + 2);
        assertThat(stats.get("byType").get("RESEARCH").asLong()).isGreaterThanOrEqualTo(1);

        // Mentorship: completed first, then the mentor proposes and withdraws; the mentee can propose anew.
        api.assertStatus(api.post("/api/v1/mentorship/requests/" + mentorship + "/complete", null, b), 200);
        String mentorshipImpact = api.body(api.post("/api/v1/impact", Map.of("type", "MENTORSHIP", "sourceId", mentorship,
                "title", "Guided the move into SOC work"), a)).get("id").asString();
        assertThat(api.body(api.post("/api/v1/impact/" + mentorshipImpact + "/withdraw", null, a)).get("status").asString()).isEqualTo("REJECTED");
        api.assertStatus(api.post("/api/v1/impact", Map.of("type", "MENTORSHIP", "sourceId", mentorship, "title", "Landed a SOC role after mentorship"), b), 201);
        List<String> mine = api.body(api.get("/api/v1/impact?status=PENDING_CONFIRMATION", b)).get("content").findValuesAsString("type");
        assertThat(mine).containsExactly("MENTORSHIP");
        assertThat(api.body(api.get("/api/v1/impact/sources", b)).findValues("alreadyRecorded").stream().map(JsonNode::asBoolean).toList())
                .contains(true);
        api.assertStatus(api.get("/api/v1/members/" + UUID.randomUUID() + "/impact", b), 404);
    }
}
