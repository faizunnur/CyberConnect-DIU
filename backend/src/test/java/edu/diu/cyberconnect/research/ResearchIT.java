package edu.diu.cyberconnect.research;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.support.AbstractIntegrationTest;
import edu.diu.cyberconnect.support.ApiClient;
import edu.diu.cyberconnect.support.TestActors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ResearchIT extends AbstractIntegrationTest {

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
    void projectLifecycleApplicationsMembersAndModeration() throws Exception {
        TestActors.Actor moderator = actors.privileged(api, "rs-mod", Set.of(RoleName.MODERATOR));
        TestActors.Actor lead = actors.verifiedMember(api, "rs-lead");
        TestActors.Actor ann = actors.verifiedMember(api, "rs-ann");
        TestActors.Actor ben = actors.verifiedMember(api, "rs-ben");
        ApiClient.Session m = actors.login(api, moderator);
        ApiClient.Session l = actors.login(api, lead);
        ApiClient.Session a = actors.login(api, ann);
        ApiClient.Session b = actors.login(api, ben);

        Map<String, Object> body = new HashMap<>();
        body.put("title", "Malware clustering with ML");
        body.put("summary", "We cluster malware samples\u0000 by behaviour.");
        body.put("skills", List.of("Malware Analysis", "Python"));
        body.put("startDate", "2026-11-01");
        body.put("memberCount", 50); // ignored
        MvcResult created = api.post("/api/v1/research", body, l);
        api.assertStatus(created, 201);
        JsonNode project = api.body(created);
        String id = project.get("id").asString();
        assertThat(project.get("status").asString()).isEqualTo("PROPOSED");
        assertThat(project.get("summary").asString()).isEqualTo("We cluster malware samples by behaviour.");
        assertThat(project.get("memberCount").asInt()).isEqualTo(1);
        assertThat(project.get("members").get(0).get("role").asString()).isEqualTo("LEAD");
        assertThat(project.get("skills").findValuesAsString("slug")).containsExactly("malware-analysis", "python");
        assertThat(project.get("own").asBoolean()).isTrue();

        assertThat(ids(api.body(api.get("/api/v1/research?q=malware", a)))).contains(id);
        assertThat(ids(api.body(api.get("/api/v1/research?skill=python", a)))).contains(id);
        assertThat(ids(api.body(api.get("/api/v1/research?lookingForMembers=false", a)))).doesNotContain(id);

        // Apply, lead decides, member joins.
        JsonNode seen = api.body(api.get("/api/v1/research/" + id, a));
        assertThat(seen.get("canApply").asBoolean()).isTrue();
        assertThat(seen.get("pendingApplications").size()).isZero();
        api.assertStatus(api.post("/api/v1/research/" + id + "/apply", Map.of("message", "I know YARA."), a), 201);
        api.assertStatus(api.post("/api/v1/research/" + id + "/apply", Map.of("message", "again"), a), 409);
        api.assertStatus(api.post("/api/v1/research/" + id + "/apply", Map.of("message", "me too"), b), 201);
        api.assertStatus(api.post("/api/v1/research/" + id + "/apply", Map.of(), l), 409);
        JsonNode forLead = api.body(api.get("/api/v1/research/" + id, l));
        assertThat(forLead.get("pendingApplications").size()).isEqualTo(2);
        String annApplication = forLead.get("pendingApplications").get(0).get("id").asString();
        String benApplication = forLead.get("pendingApplications").get(1).get("id").asString();
        assertThat(api.body(api.get("/api/v1/research/" + id, b)).get("pendingApplications").size()).as("applicants never see the queue").isZero();
        api.assertStatus(api.post("/api/v1/research/" + id + "/applications/" + annApplication + "/accept", null, b), 404);
        JsonNode accepted = api.body(api.post("/api/v1/research/" + id + "/applications/" + annApplication + "/accept", null, l));
        assertThat(accepted.get("memberCount").asInt()).isEqualTo(2);
        assertThat(accepted.get("members").findValuesAsString("role")).containsExactly("LEAD", "MEMBER");
        assertThat(api.body(api.post("/api/v1/research/" + id + "/applications/" + benApplication + "/decline", null, l))
                .get("pendingApplications").size()).isZero();
        assertThat(api.body(api.get("/api/v1/research/applications", b)).get("content").get(0).get("status").asString()).isEqualTo("DECLINED");
        assertThat(api.body(api.get("/api/v1/research/" + id, a)).get("member").asBoolean()).isTrue();
        assertThat(ids(api.body(api.get("/api/v1/research?mine=true", a)))).contains(id);
        api.assertStatus(api.post("/api/v1/research/" + id + "/apply", Map.of("message", "retry"), b), 201);

        // Status changes and membership management belong to the lead only.
        api.assertStatus(api.post("/api/v1/research/" + id + "/status", Map.of("status", "ACTIVE"), a), 404);
        assertThat(api.body(api.post("/api/v1/research/" + id + "/status", Map.of("status", "ACTIVE"), l)).get("status").asString()).isEqualTo("ACTIVE");
        api.assertStatus(api.post("/api/v1/research/" + id + "/status", Map.of("status", "REMOVED"), l), 409);
        api.assertStatus(api.post("/api/v1/research/" + id + "/leave", null, l), 400);
        api.assertStatus(api.post("/api/v1/research/" + id + "/leave", null, a), 204);
        assertThat(api.body(api.get("/api/v1/research/" + id, l)).get("memberCount").asInt()).isEqualTo(1);
        api.assertStatus(api.post("/api/v1/research/" + id + "/apply", Map.of(), a), 201);
        String annAgain = api.body(api.get("/api/v1/research/" + id, l)).get("pendingApplications").get(1).get("id").asString();
        api.assertStatus(api.post("/api/v1/research/" + id + "/applications/" + annAgain + "/accept", null, l), 200);
        assertThat(api.body(api.delete("/api/v1/research/" + id + "/members/" + ann.id(), l)).get("memberCount").asInt()).isEqualTo(1);

        // Closing recruitment and moderation.
        body.put("lookingForMembers", false);
        assertThat(api.body(api.put("/api/v1/research/" + id, body, l)).get("lookingForMembers").asBoolean()).isFalse();
        api.assertStatus(api.post("/api/v1/research/" + id + "/apply", Map.of(), a), 409);
        api.assertStatus(api.post("/api/v1/moderation/research/" + id + "/remove", Map.of("reason", "Plagiarism"), a), 403);
        assertThat(api.body(api.post("/api/v1/moderation/research/" + id + "/remove", Map.of("reason", "Plagiarism"), m)).get("status").asString()).isEqualTo("REMOVED");
        api.assertStatus(api.get("/api/v1/research/" + id, a), 404);
        assertThat(api.body(api.get("/api/v1/research/" + id, l)).get("removedReason").asString()).isEqualTo("Plagiarism");
        assertThat(ids(api.body(api.get("/api/v1/research", a)))).doesNotContain(id);
        assertThat(api.body(api.post("/api/v1/moderation/research/" + id + "/restore", null, m)).get("status").asString()).isEqualTo("ACTIVE");
        api.assertStatus(api.get("/api/v1/research/" + id, a), 200);
    }

    private static List<String> ids(JsonNode page) {
        return page.get("content").findValuesAsString("id").stream().toList();
    }
}
