package edu.diu.cyberconnect.opportunity;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.opportunity.application.OpportunityService;
import edu.diu.cyberconnect.support.AbstractIntegrationTest;
import edu.diu.cyberconnect.support.ApiClient;
import edu.diu.cyberconnect.support.TestActors;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OpportunityIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestActors actors;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OpportunityService opportunityService;
    @Autowired
    private TransactionTemplate tx;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, objectMapper);
    }

    private static Map<String, Object> opportunity(String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "INTERNSHIP");
        body.put("title", title);
        body.put("organization", "ACME Security");
        body.put("locationType", "HYBRID");
        body.put("location", "Dhaka");
        body.put("description", "Join the SOC team.\u0000 <b>Plain</b> text only.");
        body.put("applicationMethod", "INTERNAL");
        body.put("skills", List.of("SIEM", "Incident Response"));
        body.put("publish", true);
        return body;
    }

    @Test
    void publishApplyDecideAndClose() throws Exception {
        TestActors.Actor author = actors.verifiedMember(api, "op-author");
        TestActors.Actor applicant = actors.verifiedMember(api, "op-applicant");
        TestActors.Actor other = actors.verifiedMember(api, "op-other");
        ApiClient.Session a = actors.login(api, author);
        ApiClient.Session b = actors.login(api, applicant);
        ApiClient.Session c = actors.login(api, other);

        Map<String, Object> body = opportunity("SOC intern");
        body.put("applicationCount", 99); // ignored
        body.put("authorId", other.id().toString());
        MvcResult created = api.post("/api/v1/opportunities", body, a);
        api.assertStatus(created, 201);
        JsonNode view = api.body(created);
        String id = view.get("id").asString();
        assertThat(view.get("status").asString()).isEqualTo("OPEN");
        assertThat(view.get("description").asString()).isEqualTo("Join the SOC team. <b>Plain</b> text only.");
        assertThat(view.get("skills").findValuesAsString("slug")).containsExactly("siem", "incident-response");
        assertThat(view.get("applicationCount").asInt()).isZero();
        assertThat(view.get("author").get("userId").asString()).isEqualTo(author.id().toString());
        assertThat(view.get("own").asBoolean()).isTrue();
        assertThat(view.get("canApply").asBoolean()).isFalse();

        MvcResult badUrl = api.post("/api/v1/opportunities", withMethod(opportunity("Ext"), "EXTERNAL_URL", "javascript:alert(1)", null), a);
        api.assertStatus(badUrl, 400);
        assertThat(api.code(badUrl)).isEqualTo("APPLICATION_URL_REQUIRED");

        // Listing and search, filters.
        assertThat(ids(api.body(api.get("/api/v1/opportunities?q=SOC", b)))).contains(id);
        assertThat(ids(api.body(api.get("/api/v1/opportunities?type=SCHOLARSHIP", b)))).doesNotContain(id);
        assertThat(ids(api.body(api.get("/api/v1/opportunities?skill=siem", b)))).contains(id);
        assertThat(ids(api.body(api.get("/api/v1/opportunities?locationType=REMOTE", b)))).doesNotContain(id);

        // Applicant flow.
        JsonNode seen = api.body(api.get("/api/v1/opportunities/" + id, b));
        assertThat(seen.get("canApply").asBoolean()).isTrue();
        MvcResult own = api.post("/api/v1/opportunities/" + id + "/apply", Map.of("coverNote", "me"), a);
        api.assertStatus(own, 400);
        assertThat(api.code(own)).isEqualTo("OWN_OPPORTUNITY");
        MvcResult applied = api.post("/api/v1/opportunities/" + id + "/apply", Map.of("coverNote", "I run a home SOC lab."), b);
        api.assertStatus(applied, 201);
        assertThat(api.body(applied).get("status").asString()).isEqualTo("SUBMITTED");
        MvcResult twice = api.post("/api/v1/opportunities/" + id + "/apply", Map.of("coverNote", "again"), b);
        api.assertStatus(twice, 409);
        assertThat(api.code(twice)).isEqualTo("ALREADY_APPLIED");
        assertThat(api.body(api.get("/api/v1/opportunities/" + id, b)).get("myApplication").get("status").asString()).isEqualTo("SUBMITTED");
        assertThat(api.body(api.get("/api/v1/opportunities/" + id, a)).get("applicationCount").asInt()).isEqualTo(1);
        assertThat(api.body(api.get("/api/v1/opportunities/applications", b)).get("content").get(0).get("opportunity").get("id").asString()).isEqualTo(id);

        // Only the author sees applicants and decides.
        api.assertStatus(api.get("/api/v1/opportunities/" + id + "/applicants", b), 404);
        api.assertStatus(api.get("/api/v1/opportunities/" + id + "/applicants", c), 404);
        JsonNode applicants = api.body(api.get("/api/v1/opportunities/" + id + "/applicants", a));
        assertThat(applicants.get("content").size()).isEqualTo(1);
        String applicationId = applicants.get("content").get(0).get("id").asString();
        assertThat(applicants.get("content").get(0).get("applicant").get("userId").asString()).isEqualTo(applicant.id().toString());
        assertThat(applicants.get("content").get(0).get("coverNote").asString()).isEqualTo("I run a home SOC lab.");
        api.assertStatus(api.post("/api/v1/opportunities/" + id + "/applicants/" + applicationId + "/decide", Map.of("status", "WITHDRAWN"), a), 400);
        assertThat(api.body(api.post("/api/v1/opportunities/" + id + "/applicants/" + applicationId + "/decide",
                Map.of("status", "SHORTLISTED"), a)).get("status").asString()).isEqualTo("SHORTLISTED");
        api.assertStatus(api.post("/api/v1/opportunities/" + id + "/applicants/" + applicationId + "/decide", Map.of("status", "ACCEPTED"), c), 404);

        // Withdraw and re-apply keep the counter honest.
        api.assertStatus(api.delete("/api/v1/opportunities/" + id + "/apply", b), 204);
        assertThat(api.body(api.get("/api/v1/opportunities/" + id, a)).get("applicationCount").asInt()).isZero();
        api.assertStatus(api.post("/api/v1/opportunities/" + id + "/apply", Map.of(), b), 201);
        assertThat(api.body(api.get("/api/v1/opportunities/" + id, a)).get("applicationCount").asInt()).isEqualTo(1);

        // Close: visible but no longer open; others cannot close.
        api.assertStatus(api.post("/api/v1/opportunities/" + id + "/close", null, c), 404);
        assertThat(api.body(api.post("/api/v1/opportunities/" + id + "/close", null, a)).get("status").asString()).isEqualTo("CLOSED");
        assertThat(ids(api.body(api.get("/api/v1/opportunities", c)))).doesNotContain(id);
        assertThat(api.body(api.get("/api/v1/opportunities/" + id, c)).get("canApply").asBoolean()).isFalse();
        api.assertStatus(api.post("/api/v1/opportunities/" + id + "/apply", Map.of(), c), 409);
        api.assertStatus(api.put("/api/v1/opportunities/" + id, opportunity("edit after close"), a), 409);
        assertThat(api.body(api.post("/api/v1/opportunities/" + id + "/publish", null, a)).get("status").asString()).isEqualTo("OPEN");
        assertThat(ids(api.body(api.get("/api/v1/opportunities/mine", a)))).contains(id);
    }

    @Test
    void externalMethodsDraftsExpiryBlockingAndModeration() throws Exception {
        TestActors.Actor moderator = actors.privileged(api, "op-mod", Set.of(RoleName.MODERATOR));
        TestActors.Actor author = actors.verifiedMember(api, "op2-author");
        TestActors.Actor viewer = actors.verifiedMember(api, "op2-viewer");
        ApiClient.Session m = actors.login(api, moderator);
        ApiClient.Session a = actors.login(api, author);
        ApiClient.Session v = actors.login(api, viewer);

        MvcResult external = api.post("/api/v1/opportunities", withMethod(opportunity("External role"), "EXTERNAL_URL",
                "https://careers.example.test/job/1", null), a);
        api.assertStatus(external, 201);
        String externalId = api.body(external).get("id").asString();
        assertThat(api.body(api.get("/api/v1/opportunities/" + externalId, v)).get("applicationUrl").asString()).startsWith("https://");
        MvcResult applyExternal = api.post("/api/v1/opportunities/" + externalId + "/apply", Map.of(), v);
        api.assertStatus(applyExternal, 409);
        assertThat(api.code(applyExternal)).isEqualTo("EXTERNAL_APPLICATION");

        MvcResult email = api.post("/api/v1/opportunities", withMethod(opportunity("Email role"), "EMAIL", null, "jobs@example.test"), a);
        api.assertStatus(email, 201);
        assertThat(api.body(email).get("applicationEmail").asString()).isEqualTo("jobs@example.test");

        // Drafts are invisible to others and deletable; publishing with a past deadline is refused.
        Map<String, Object> draft = opportunity("Draft role");
        draft.put("publish", false);
        String draftId = api.body(api.post("/api/v1/opportunities", draft, a)).get("id").asString();
        api.assertStatus(api.get("/api/v1/opportunities/" + draftId, v), 404);
        assertThat(ids(api.body(api.get("/api/v1/opportunities", v)))).doesNotContain(draftId);
        Map<String, Object> past = opportunity("Past");
        past.put("deadline", LocalDate.now(java.time.ZoneId.of("Asia/Dhaka")).minusDays(1).toString());
        MvcResult pastDeadline = api.post("/api/v1/opportunities", past, a);
        api.assertStatus(pastDeadline, 400);
        assertThat(api.code(pastDeadline)).isEqualTo("DEADLINE_PASSED");
        api.assertStatus(api.delete("/api/v1/opportunities/" + draftId, a), 204);
        api.assertStatus(api.get("/api/v1/opportunities/" + draftId, a), 404);

        // Expiry: a published opportunity whose deadline passes expires via the job.
        Map<String, Object> expiring = opportunity("Expiring");
        expiring.put("deadline", LocalDate.now(java.time.ZoneId.of("Asia/Dhaka")).plusDays(1).toString());
        String expiringId = api.body(api.post("/api/v1/opportunities", expiring, a)).get("id").asString();
        jdbc.update("update opportunities set deadline = current_date - 1 where id = ?::uuid", java.util.UUID.fromString(expiringId));
        assertThat(ids(api.body(api.get("/api/v1/opportunities", v)))).as("deadline filter hides overdue listings").doesNotContain(expiringId);
        int expired = tx.execute(t -> opportunityService.expireOverdue());
        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(api.body(api.get("/api/v1/opportunities/" + expiringId, v)).get("status").asString()).isEqualTo("EXPIRED");

        // Blocking hides the author's opportunities; moderation removes and restores with audit.
        api.assertStatus(api.post("/api/v1/blocks", Map.of("userId", viewer.id().toString()), a), 204);
        assertThat(ids(api.body(api.get("/api/v1/opportunities", v)))).doesNotContain(externalId);
        api.assertStatus(api.get("/api/v1/opportunities/" + externalId, v), 404);
        api.assertStatus(api.delete("/api/v1/blocks/" + viewer.id(), a), 204);

        api.assertStatus(api.post("/api/v1/moderation/opportunities/" + externalId + "/remove", Map.of("reason", "Scam link"), v), 403);
        JsonNode removed = api.body(api.post("/api/v1/moderation/opportunities/" + externalId + "/remove", Map.of("reason", "Scam link"), m));
        assertThat(removed.get("status").asString()).isEqualTo("REMOVED");
        api.assertStatus(api.get("/api/v1/opportunities/" + externalId, v), 404);
        assertThat(api.body(api.get("/api/v1/opportunities/" + externalId, a)).get("removedReason").asString()).isEqualTo("Scam link");
        Integer audits = jdbc.queryForObject("select count(*) from audit_logs where action = 'OPPORTUNITY_REMOVED' and target_id = ?::uuid",
                Integer.class, java.util.UUID.fromString(externalId));
        assertThat(audits).isEqualTo(1);
        assertThat(api.body(api.post("/api/v1/moderation/opportunities/" + externalId + "/restore", null, m)).get("status").asString()).isEqualTo("OPEN");
        api.assertStatus(api.get("/api/v1/opportunities/" + externalId, v), 200);
    }

    private static Map<String, Object> withMethod(Map<String, Object> body, String method, String url, String email) {
        body.put("applicationMethod", method);
        if (url != null) {
            body.put("applicationUrl", url);
        }
        if (email != null) {
            body.put("applicationEmail", email);
        }
        return body;
    }

    private static List<String> ids(JsonNode page) {
        return page.get("content").findValuesAsString("id").stream().toList();
    }
}
