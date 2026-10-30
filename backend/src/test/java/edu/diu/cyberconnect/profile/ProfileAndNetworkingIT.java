package edu.diu.cyberconnect.profile;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.common.config.StorageProperties;
import edu.diu.cyberconnect.common.storage.ObjectStorage;
import edu.diu.cyberconnect.support.AbstractIntegrationTest;
import edu.diu.cyberconnect.support.ApiClient;
import edu.diu.cyberconnect.support.TestActors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Profiles, privacy matrix, directory search, connections and blocking (M3). */
class ProfileAndNetworkingIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestActors actors;
    @Autowired
    private JdbcTemplate jdbc;
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
    void ownProfileEditingWithOwnershipAndMassAssignmentChecks() throws Exception {
        TestActors.Actor alice = actors.verifiedMember(api, "alice");
        TestActors.Actor bob = actors.verifiedMember(api, "bob");
        ApiClient.Session a = actors.login(api, alice);
        ApiClient.Session b = actors.login(api, bob);

        JsonNode initial = api.body(api.get("/api/v1/me/profile", a));
        assertThat(initial.get("displayName").asString()).isEqualTo("Test Student");
        assertThat(initial.get("privacy").get("profileVisibility").asString()).isEqualTo("ALL_MEMBERS");
        assertThat(initial.get("privacy").get("emailVisibility").asString()).isEqualTo("NOBODY");
        assertThat(initial.get("privacy").get("searchable").asBoolean()).isTrue();
        assertThat(initial.get("relationship").get("self").asBoolean()).isTrue();

        Map<String, Object> update = new HashMap<>();
        update.put("displayName", "Alice Rahman");
        update.put("headline", "SOC analyst");
        update.put("bio", "Blue team by day.");
        update.put("organization", "ACME Bank");
        update.put("jobTitle", "Security Analyst");
        update.put("location", "Dhaka");
        update.put("country", "Bangladesh");
        update.put("specializations", List.of("Blue team", "DFIR", "blue team"));
        update.put("mentorAvailable", true);
        update.put("mentorTopics", "SOC careers");
        update.put("accountStatus", "SUSPENDED"); // mass-assignment attempt: ignored
        update.put("roles", List.of("ADMIN"));
        JsonNode updated = api.body(api.put("/api/v1/me/profile", update, a));
        assertThat(updated.get("displayName").asString()).isEqualTo("Alice Rahman");
        assertThat(updated.get("specializations").findValuesAsString("").size()).isEqualTo(0);
        assertThat(updated.get("specializations").size()).isEqualTo(2);
        assertThat(updated.get("mentorAvailable").asBoolean()).isTrue();
        assertThat(api.body(api.get("/api/v1/auth/me", a)).get("accountStatus").asString()).isEqualTo("VERIFIED");

        MvcResult tooLong = api.put("/api/v1/me/profile", Map.of("displayName", "x".repeat(121)), a);
        api.assertStatus(tooLong, 400);
        assertThat(api.code(tooLong)).isEqualTo("VALIDATION_FAILED");

        JsonNode skills = api.body(api.put("/api/v1/me/profile/skills",
                Map.of("skills", List.of("Penetration Testing", "SIEM", "siem", "  Custom  Skill ")), a));
        assertThat(skills.findValuesAsString("slug")).containsExactly("penetration-testing", "siem", "custom-skill");
        assertThat(api.body(api.get("/api/v1/skills?q=custom", a)).findValuesAsString("name")).contains("Custom Skill");

        MvcResult created = api.post("/api/v1/me/profile/experiences", Map.of("title", "Analyst", "organization", "ACME",
                "employmentType", "FULL_TIME", "startDate", "2023-01-01", "current", true), a);
        api.assertStatus(created, 201);
        String experienceId = api.body(created).get("id").asString();
        Map<String, Object> edit = Map.of("title", "Senior Analyst", "organization", "ACME", "startDate", "2023-01-01",
                "endDate", "2024-06-30", "current", false);
        api.assertStatus(api.put("/api/v1/me/profile/experiences/" + experienceId, edit, b), 404);
        api.assertStatus(api.delete("/api/v1/me/profile/experiences/" + experienceId, b), 404);
        assertThat(api.body(api.put("/api/v1/me/profile/experiences/" + experienceId, edit, a)).get("title").asString())
                .isEqualTo("Senior Analyst");
        MvcResult badRange = api.post("/api/v1/me/profile/experiences", Map.of("title", "X", "organization", "Y",
                "startDate", "2024-01-01", "endDate", "2023-01-01", "current", false), a);
        api.assertStatus(badRange, 400);
        assertThat(api.code(badRange)).isEqualTo("INVALID_DATE_RANGE");

        MvcResult education = api.post("/api/v1/me/profile/educations", Map.of("institution", "DIU",
                "degree", "BSc", "fieldOfStudy", "Cybersecurity", "startYear", 2019, "endYear", 2023), a);
        api.assertStatus(education, 201);
        JsonNode own = api.body(api.get("/api/v1/me/profile", a));
        assertThat(own.get("experiences").size()).isEqualTo(1);
        assertThat(own.get("educations").size()).isEqualTo(1);
        assertThat(own.get("skills").size()).isEqualTo(3);
        api.assertStatus(api.delete("/api/v1/me/profile/educations/" + api.body(education).get("id").asString(), a), 204);
        assertThat(api.body(api.get("/api/v1/me/profile", a)).get("educations").size()).isEqualTo(0);
    }

    @Test
    void privacyMatrixDirectoryAndBlocking() throws Exception {
        TestActors.Actor alice = actors.verifiedMember(api, "pm-alice");
        TestActors.Actor bob = actors.verifiedMember(api, "pm-bob");
        TestActors.Actor carol = actors.verifiedMember(api, "pm-carol");
        TestActors.Actor dave = actors.registerApplicant(api, "pm-dave", edu.diu.cyberconnect.common.model.AffiliationType.STUDENT);
        markAffiliationVerified(alice.id());
        markAffiliationVerified(bob.id());
        ApiClient.Session a = actors.login(api, alice);
        ApiClient.Session b = actors.login(api, bob);
        ApiClient.Session c = actors.login(api, carol);
        ApiClient.Session d = actors.login(api, dave);

        api.assertStatus(api.put("/api/v1/me/profile", Map.of("displayName", "Alice Open", "headline", "Malware analyst",
                "organization", "OpenOrg", "location", "Dhaka"), a), 200);
        api.assertStatus(api.put("/api/v1/me/profile/skills", Map.of("skills", List.of("Malware Analysis")), a), 200);
        api.assertStatus(api.put("/api/v1/me/profile", Map.of("displayName", "Bob Private", "headline", "Red teamer",
                "bio", "secret bio", "organization", "SecureCorp", "location", "Chattogram", "mentorAvailable", true), b), 200);
        api.assertStatus(api.put("/api/v1/me/profile/privacy", Map.of("profileVisibility", "CONNECTIONS_ONLY",
                "emailVisibility", "CONNECTIONS", "organizationVisibility", "CONNECTIONS", "locationVisibility", "NOBODY",
                "mentorVisibility", "ALL_MEMBERS", "searchable", true, "messagingPolicy", "CONNECTIONS_ONLY"), b), 200);

        // Open profile: everything except email (NOBODY by default).
        JsonNode aliceSeenByCarol = api.body(api.get("/api/v1/members/" + alice.id(), c));
        assertThat(aliceSeenByCarol.get("restricted").asBoolean()).isFalse();
        assertThat(aliceSeenByCarol.get("organization").asString()).isEqualTo("OpenOrg");
        assertThat(aliceSeenByCarol.get("email").isNull()).isTrue();
        assertThat(aliceSeenByCarol.get("privacy").isNull()).isTrue();
        assertThat(aliceSeenByCarol.get("affiliations").findValuesAsString("")).isEmpty();
        assertThat(aliceSeenByCarol.get("affiliations").get(0).asString()).isEqualTo("STUDENT");

        // Connections-only profile: restricted until connected.
        JsonNode bobRestricted = api.body(api.get("/api/v1/members/" + bob.id(), c));
        assertThat(bobRestricted.get("restricted").asBoolean()).isTrue();
        assertThat(bobRestricted.get("displayName").asString()).isEqualTo("Bob Private");
        assertThat(bobRestricted.get("bio").isNull()).isTrue();
        assertThat(bobRestricted.get("organization").isNull()).isTrue();
        assertThat(bobRestricted.toString()).doesNotContain("secret bio").doesNotContain("SecureCorp");

        MvcResult request = api.post("/api/v1/connections/requests", Map.of("userId", bob.id().toString(),
                "message", "Met you at the CTF."), c);
        api.assertStatus(request, 201);
        String requestId = api.body(request).get("id").asString();
        JsonNode incoming = api.body(api.get("/api/v1/connections/requests?direction=incoming", b));
        assertThat(incoming.get("content").findValuesAsString("id")).contains(requestId);
        assertThat(incoming.get("content").get(0).get("member").get("userId").asString()).isEqualTo(carol.id().toString());
        api.assertStatus(api.post("/api/v1/connections/requests/" + requestId + "/accept", null, b), 204);

        JsonNode bobSeenByCarol = api.body(api.get("/api/v1/members/" + bob.id(), c));
        assertThat(bobSeenByCarol.get("restricted").asBoolean()).isFalse();
        assertThat(bobSeenByCarol.get("email").asString()).isEqualTo(bob.email());
        assertThat(bobSeenByCarol.get("organization").asString()).isEqualTo("SecureCorp");
        assertThat(bobSeenByCarol.get("location").isNull()).as("location NOBODY").isTrue();
        assertThat(bobSeenByCarol.get("relationship").get("connected").asBoolean()).isTrue();
        // Alice is not connected to Bob: still restricted, and the directory card hides his organisation.
        assertThat(api.body(api.get("/api/v1/members/" + bob.id(), a)).get("restricted").asBoolean()).isTrue();

        // Directory: free text, affiliation, skill, mentor filters; organisation filter honours visibility.
        assertThat(ids(api.body(api.get("/api/v1/members?q=Malware", c)))).contains(alice.id().toString());
        assertThat(ids(api.body(api.get("/api/v1/members?skill=malware-analysis", c)))).contains(alice.id().toString());
        assertThat(ids(api.body(api.get("/api/v1/members?affiliation=STUDENT", c)))).contains(alice.id().toString());
        assertThat(ids(api.body(api.get("/api/v1/members?mentorAvailable=true", c)))).contains(bob.id().toString())
                .doesNotContain(alice.id().toString());
        assertThat(ids(api.body(api.get("/api/v1/members?organization=SecureCorp", c)))).contains(bob.id().toString());
        assertThat(ids(api.body(api.get("/api/v1/members?organization=SecureCorp", a)))).doesNotContain(bob.id().toString());
        JsonNode directoryForAlice = api.body(api.get("/api/v1/members?q=Bob", a));
        assertThat(directoryForAlice.get("content").get(0).get("organization").isNull()).isTrue();
        assertThat(ids(api.body(api.get("/api/v1/members", c)))).doesNotContain(carol.id().toString());

        // Opting out of search hides the card but not the profile.
        api.assertStatus(api.put("/api/v1/me/profile/privacy", Map.of("profileVisibility", "ALL_MEMBERS",
                "emailVisibility", "NOBODY", "organizationVisibility", "ALL_MEMBERS", "locationVisibility", "ALL_MEMBERS",
                "mentorVisibility", "ALL_MEMBERS", "searchable", false, "messagingPolicy", "EVERYONE"), a), 200);
        assertThat(ids(api.body(api.get("/api/v1/members?q=Alice", c)))).doesNotContain(alice.id().toString());
        api.assertStatus(api.get("/api/v1/members/" + alice.id(), c), 200);

        // Blocking: uniform 404 both ways, removed from the directory, requests impossible, connections dropped.
        api.assertStatus(api.post("/api/v1/blocks", Map.of("userId", carol.id().toString()), a), 204);
        MvcResult blocked = api.get("/api/v1/members/" + alice.id(), c);
        api.assertStatus(blocked, 404);
        assertThat(api.code(blocked)).isEqualTo("MEMBER_NOT_FOUND");
        api.assertStatus(api.get("/api/v1/members/" + carol.id(), a), 404);
        api.assertStatus(api.get("/api/v1/members/" + alice.id() + "/photo", c), 404);
        api.assertStatus(api.post("/api/v1/connections/requests", Map.of("userId", alice.id().toString()), c), 404);
        assertThat(ids(api.body(api.get("/api/v1/members", a)))).doesNotContain(carol.id().toString());
        assertThat(api.body(api.get("/api/v1/blocks", a)).findValuesAsString("userId")).containsExactly(carol.id().toString());

        api.assertStatus(api.post("/api/v1/blocks", Map.of("userId", carol.id().toString()), b), 204);
        assertThat(ids(api.body(api.get("/api/v1/connections", c)))).doesNotContain(bob.id().toString());
        assertThat(api.body(api.get("/api/v1/connections/summary", c)).get("connections").asLong()).isZero();

        api.assertStatus(api.delete("/api/v1/blocks/" + carol.id(), a), 204);
        api.assertStatus(api.get("/api/v1/members/" + alice.id(), c), 200);

        // Non-verified accounts never reach the community tier.
        for (String path : List.of("/api/v1/members", "/api/v1/me/profile", "/api/v1/connections",
                "/api/v1/members/" + alice.id())) {
            MvcResult denied = api.get(path, d);
            api.assertStatus(denied, 403);
            assertThat(api.code(denied)).isEqualTo("ACCOUNT_NOT_VERIFIED");
        }
        api.assertStatus(api.get("/api/v1/members/" + dave.id(), c), 404);
    }

    @Test
    void connectionLifecycle() throws Exception {
        TestActors.Actor ann = actors.verifiedMember(api, "cl-ann");
        TestActors.Actor ben = actors.verifiedMember(api, "cl-ben");
        TestActors.Actor cat = actors.verifiedMember(api, "cl-cat");
        ApiClient.Session a = actors.login(api, ann);
        ApiClient.Session b = actors.login(api, ben);
        ApiClient.Session c = actors.login(api, cat);

        MvcResult self = api.post("/api/v1/connections/requests", Map.of("userId", ann.id().toString()), a);
        api.assertStatus(self, 400);
        assertThat(api.code(self)).isEqualTo("SELF_CONNECTION");
        api.assertStatus(api.post("/api/v1/connections/requests", Map.of("userId", UUID.randomUUID().toString()), a), 404);

        MvcResult first = api.post("/api/v1/connections/requests", Map.of("userId", ben.id().toString()), a);
        api.assertStatus(first, 201);
        assertThat(api.body(first).get("status").asString()).isEqualTo("PENDING");
        assertThat(api.body(first).get("incoming").asBoolean()).isFalse();
        MvcResult duplicate = api.post("/api/v1/connections/requests", Map.of("userId", ben.id().toString()), a);
        api.assertStatus(duplicate, 409);
        assertThat(api.code(duplicate)).isEqualTo("REQUEST_PENDING");

        JsonNode annProfileForBen = api.body(api.get("/api/v1/members/" + ann.id(), b));
        assertThat(annProfileForBen.get("relationship").get("pendingIncoming").asBoolean()).isTrue();
        assertThat(annProfileForBen.get("relationship").get("requestId").asString()).isEqualTo(api.body(first).get("id").asString());

        // Reverse request from Ben acts as acceptance (no reversed duplicate row).
        MvcResult reverse = api.post("/api/v1/connections/requests", Map.of("userId", ann.id().toString()), b);
        api.assertStatus(reverse, 201);
        assertThat(api.body(reverse).get("status").asString()).isEqualTo("ACCEPTED");
        Integer rows = jdbc.queryForObject("select count(*) from connections where (requester_id = ?::uuid and addressee_id = ?::uuid)"
                + " or (requester_id = ?::uuid and addressee_id = ?::uuid)", Integer.class, ann.id(), ben.id(), ben.id(), ann.id());
        assertThat(rows).isEqualTo(1);
        MvcResult again = api.post("/api/v1/connections/requests", Map.of("userId", ben.id().toString()), a);
        api.assertStatus(again, 409);
        assertThat(api.code(again)).isEqualTo("ALREADY_CONNECTED");
        assertThat(ids(api.body(api.get("/api/v1/connections", a)))).containsExactly(ben.id().toString());
        assertThat(api.body(api.get("/api/v1/connections/summary", a)).get("connections").asLong()).isEqualTo(1);

        api.assertStatus(api.delete("/api/v1/connections/" + ben.id(), a), 204);
        assertThat(api.body(api.get("/api/v1/connections", a)).get("content").size()).isZero();
        api.assertStatus(api.delete("/api/v1/connections/" + ben.id(), a), 404);

        // Decline, re-request after decline, cancel, and stale ids.
        String toCat = api.body(api.post("/api/v1/connections/requests", Map.of("userId", cat.id().toString()), a)).get("id").asString();
        api.assertStatus(api.post("/api/v1/connections/requests/" + toCat + "/accept", null, b), 404); // not the addressee
        api.assertStatus(api.post("/api/v1/connections/requests/" + toCat + "/decline", null, c), 204);
        assertThat(api.body(api.get("/api/v1/connections/requests?direction=outgoing", a)).get("content").size()).isZero();
        String toCatAgain = api.body(api.post("/api/v1/connections/requests", Map.of("userId", cat.id().toString()), a)).get("id").asString();
        assertThat(api.body(api.get("/api/v1/connections/requests?direction=outgoing", a)).get("content").findValuesAsString("id"))
                .containsExactly(toCatAgain);
        api.assertStatus(api.delete("/api/v1/connections/requests/" + toCatAgain, c), 404); // only the requester cancels
        api.assertStatus(api.delete("/api/v1/connections/requests/" + toCatAgain, a), 204);
        api.assertStatus(api.post("/api/v1/connections/requests/" + toCatAgain + "/accept", null, c), 404);
        assertThat(api.body(api.get("/api/v1/connections/summary", c)).get("pendingIncoming").asLong()).isZero();
    }

    @Test
    void photoUploadServesSanitizedJpegAndRespectsBlocks() throws Exception {
        TestActors.Actor alice = actors.verifiedMember(api, "ph-alice");
        TestActors.Actor bob = actors.verifiedMember(api, "ph-bob");
        ApiClient.Session a = actors.login(api, alice);
        ApiClient.Session b = actors.login(api, bob);

        api.assertStatus(api.get("/api/v1/members/" + alice.id() + "/photo", b), 404);
        MvcResult uploaded = api.upload("/api/v1/me/profile/photo", ApiClient.fictionalIdCardPng(), "me.png", "image/png", null, a);
        api.assertStatus(uploaded, 200);
        String photoUrl = api.body(uploaded).get("photoUrl").asString();
        assertThat(photoUrl).isEqualTo("/api/v1/members/" + alice.id() + "/photo?v=1");
        String firstKey = jdbc.queryForObject("select photo_key from user_profiles where user_id = ?::uuid", String.class, alice.id());
        assertThat(storage.exists(storageProperties.buckets().media(), firstKey)).isTrue();

        MvcResult photo = api.get(photoUrl, b);
        api.assertStatus(photo, 200);
        assertThat(photo.getResponse().getContentType()).startsWith("image/jpeg");
        assertThat(photo.getResponse().getHeader("Cache-Control")).contains("private");
        byte[] bytes = photo.getResponse().getContentAsByteArray();
        assertThat(bytes[0] & 0xFF).isEqualTo(0xFF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xD8);

        MvcResult replaced = api.upload("/api/v1/me/profile/photo", ApiClient.fictionalIdCardPng(), "me2.png", "image/png", null, a);
        assertThat(api.body(replaced).get("photoUrl").asString()).endsWith("?v=2");
        assertThat(storage.exists(storageProperties.buckets().media(), firstKey)).as("old object removed").isFalse();

        MvcResult notImage = api.upload("/api/v1/me/profile/photo", "%PDF-1.4\n%%EOF".getBytes(), "x.pdf", "application/pdf", null, a);
        api.assertStatus(notImage, 400);
        assertThat(api.code(notImage)).isEqualTo("UNSUPPORTED_FILE_TYPE");

        api.assertStatus(api.post("/api/v1/blocks", Map.of("userId", bob.id().toString()), a), 204);
        api.assertStatus(api.get("/api/v1/members/" + alice.id() + "/photo", b), 404);

        api.assertStatus(api.delete("/api/v1/me/profile/photo", a), 204);
        assertThat(api.body(api.get("/api/v1/me/profile", a)).get("photoUrl").isNull()).isTrue();
        api.assertStatus(api.get("/api/v1/members/" + alice.id() + "/photo", a), 404);
    }

    private void markAffiliationVerified(UUID userId) {
        jdbc.update("update user_affiliations set status = 'VERIFIED', verified_at = now() where user_id = ?::uuid", userId);
    }

    private static List<String> ids(JsonNode page) {
        return page.get("content").findValuesAsString("userId");
    }
}
