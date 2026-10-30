package edu.diu.cyberconnect.networking;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.support.AbstractIntegrationTest;
import edu.diu.cyberconnect.support.ApiClient;
import edu.diu.cyberconnect.support.TestActors;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class MentorshipIT extends AbstractIntegrationTest {

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
    void mentorshipRequestsFollowAvailabilityAndOwnership() throws Exception {
        TestActors.Actor mentor = actors.verifiedMember(api, "mt-mentor");
        TestActors.Actor mentee = actors.verifiedMember(api, "mt-mentee");
        TestActors.Actor stranger = actors.verifiedMember(api, "mt-stranger");
        ApiClient.Session mentorSession = actors.login(api, mentor);
        ApiClient.Session menteeSession = actors.login(api, mentee);
        ApiClient.Session strangerSession = actors.login(api, stranger);

        Map<String, Object> request = Map.of("mentorId", mentor.id().toString(), "topic", "Breaking into DFIR",
                "message", "I would love guidance on building a forensics portfolio.");
        MvcResult unavailable = api.post("/api/v1/mentorship/requests", request, menteeSession);
        api.assertStatus(unavailable, 409);
        assertThat(api.code(unavailable)).isEqualTo("MENTOR_UNAVAILABLE");

        api.assertStatus(api.put("/api/v1/me/profile", Map.of("displayName", "Mentor M", "mentorAvailable", true,
                "mentorTopics", "DFIR careers"), mentorSession), 200);
        MvcResult self = api.post("/api/v1/mentorship/requests", Map.of("mentorId", mentor.id().toString(), "topic", "x", "message", "y"), mentorSession);
        api.assertStatus(self, 400);
        MvcResult created = api.post("/api/v1/mentorship/requests", request, menteeSession);
        api.assertStatus(created, 201);
        JsonNode view = api.body(created);
        String id = view.get("id").asString();
        assertThat(view.get("status").asString()).isEqualTo("PENDING");
        assertThat(view.get("incoming").asBoolean()).isFalse();
        assertThat(view.get("mentor").get("userId").asString()).isEqualTo(mentor.id().toString());
        MvcResult duplicate = api.post("/api/v1/mentorship/requests", request, menteeSession);
        api.assertStatus(duplicate, 409);
        assertThat(api.code(duplicate)).isEqualTo("REQUEST_PENDING");

        // Only the two parties can see it; only the mentor responds; only the mentee cancels.
        api.assertStatus(api.get("/api/v1/mentorship/requests/" + id, strangerSession), 404);
        api.assertStatus(api.post("/api/v1/mentorship/requests/" + id + "/accept", null, menteeSession), 409);
        api.assertStatus(api.post("/api/v1/mentorship/requests/" + id + "/accept", null, strangerSession), 404);
        JsonNode incoming = api.body(api.get("/api/v1/mentorship/requests?role=mentor&status=PENDING", mentorSession));
        assertThat(incoming.get("content").get(0).get("incoming").asBoolean()).isTrue();
        assertThat(incoming.get("content").get(0).get("mentee").get("userId").asString()).isEqualTo(mentee.id().toString());
        assertThat(api.body(api.get("/api/v1/mentorship/requests/summary", mentorSession)).get("pendingAsMentor").asLong()).isEqualTo(1);
        JsonNode accepted = api.body(api.post("/api/v1/mentorship/requests/" + id + "/accept", Map.of("note", "Happy to help."), mentorSession));
        assertThat(accepted.get("status").asString()).isEqualTo("ACCEPTED");
        assertThat(accepted.get("responseNote").asString()).isEqualTo("Happy to help.");
        MvcResult again = api.post("/api/v1/mentorship/requests", request, menteeSession);
        api.assertStatus(again, 409);
        assertThat(api.code(again)).isEqualTo("ALREADY_MENTORING");
        api.assertStatus(api.delete("/api/v1/mentorship/requests/" + id, menteeSession), 409);
        assertThat(api.body(api.post("/api/v1/mentorship/requests/" + id + "/complete", null, menteeSession)).get("status").asString()).isEqualTo("COMPLETED");
        assertThat(api.body(api.get("/api/v1/mentorship/requests?role=mentee", menteeSession)).get("content").findValuesAsString("status")).containsExactly("COMPLETED");

        // A new request after completion is fine; cancelling and blocking behave.
        String second = api.body(api.post("/api/v1/mentorship/requests", request, menteeSession)).get("id").asString();
        api.assertStatus(api.delete("/api/v1/mentorship/requests/" + second, menteeSession), 204);
        api.assertStatus(api.post("/api/v1/blocks", Map.of("userId", mentee.id().toString()), mentorSession), 204);
        api.assertStatus(api.post("/api/v1/mentorship/requests", request, menteeSession), 404);
    }
}
