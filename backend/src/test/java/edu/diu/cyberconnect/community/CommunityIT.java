package edu.diu.cyberconnect.community;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.AffiliationType;
import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.identity.application.UserAccounts;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Community feed (M4): sanitisation, ownership, reactions, comments, visibility, blocking, reposts, moderation. */
class CommunityIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestActors actors;
    @Autowired
    private JdbcTemplate jdbc;
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
    void postLifecycleSanitisationHashtagsReactionsAndComments() throws Exception {
        TestActors.Actor alice = actors.verifiedMember(api, "cm-alice");
        TestActors.Actor bob = actors.verifiedMember(api, "cm-bob");
        ApiClient.Session a = actors.login(api, alice);
        ApiClient.Session b = actors.login(api, bob);

        Map<String, Object> create = new HashMap<>();
        create.put("intent", "TECHNICAL_DISCUSSION");
        create.put("body", "Hello\u0000 world​ #CyberSec #cybersec\r\n\n\n\nLooking for #DFIR tips <b>bold</b>  ");
        create.put("likeCount", 999);          // mass-assignment attempts are ignored
        create.put("authorId", bob.id().toString());
        create.put("status", "REMOVED");
        MvcResult created = api.post("/api/v1/posts", create, a);
        api.assertStatus(created, 201);
        JsonNode post = api.body(created);
        String postId = post.get("id").asString();
        assertThat(post.get("body").asString()).isEqualTo("Hello world #CyberSec #cybersec\n\nLooking for #DFIR tips <b>bold</b>");
        assertThat(post.get("hashtags").findValuesAsString("")).isEmpty();
        assertThat(toList(post.get("hashtags"))).containsExactly("cybersec", "dfir");
        assertThat(post.get("likeCount").asInt()).isZero();
        assertThat(post.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(post.get("own").asBoolean()).isTrue();
        assertThat(post.get("author").get("userId").asString()).isEqualTo(alice.id().toString());

        MvcResult empty = api.post("/api/v1/posts", Map.of("intent", "EVENT", "body", " ​ "), a);
        api.assertStatus(empty, 400);
        assertThat(api.code(empty)).isEqualTo("BODY_REQUIRED");
        MvcResult tooLong = api.post("/api/v1/posts", Map.of("intent", "EVENT", "body", "x".repeat(5001)), a);
        api.assertStatus(tooLong, 400);
        assertThat(api.code(tooLong)).isEqualTo("BODY_TOO_LONG");

        // Ownership: others can read but never edit or delete.
        assertThat(api.body(api.get("/api/v1/posts/" + postId, b)).get("own").asBoolean()).isFalse();
        api.assertStatus(api.put("/api/v1/posts/" + postId, Map.of("intent", "EVENT", "body", "hijack"), b), 404);
        api.assertStatus(api.delete("/api/v1/posts/" + postId, b), 404);
        JsonNode edited = api.body(api.put("/api/v1/posts/" + postId, Map.of("intent", "SEEKING_HELP", "body", "Edited #Blue"), a));
        assertThat(edited.get("editedAt").isNull()).isFalse();
        assertThat(toList(edited.get("hashtags"))).containsExactly("blue");
        assertThat(edited.get("intent").asString()).isEqualTo("SEEKING_HELP");

        // Likes are idempotent and counted server-side.
        assertThat(api.body(api.post("/api/v1/posts/" + postId + "/like", null, b)).get("likeCount").asInt()).isEqualTo(1);
        JsonNode likedTwice = api.body(api.post("/api/v1/posts/" + postId + "/like", null, b));
        assertThat(likedTwice.get("likeCount").asInt()).isEqualTo(1);
        assertThat(likedTwice.get("liked").asBoolean()).isTrue();
        assertThat(api.body(api.delete("/api/v1/posts/" + postId + "/like", b)).get("likeCount").asInt()).isZero();

        // Bookmarks.
        assertThat(api.body(api.post("/api/v1/posts/" + postId + "/bookmark", null, b)).get("bookmarked").asBoolean()).isTrue();
        assertThat(ids(api.body(api.get("/api/v1/bookmarks", b)))).containsExactly(postId);
        api.assertStatus(api.delete("/api/v1/posts/" + postId + "/bookmark", b), 200);
        assertThat(api.body(api.get("/api/v1/bookmarks", b)).get("content").size()).isZero();

        // Comments: one level of replies.
        MvcResult comment = api.post("/api/v1/posts/" + postId + "/comments", Map.of("body", "Nice <i>post</i>\u0000"), b);
        api.assertStatus(comment, 201);
        String commentId = api.body(comment).get("id").asString();
        assertThat(api.body(comment).get("body").asString()).isEqualTo("Nice <i>post</i>");
        MvcResult reply = api.post("/api/v1/posts/" + postId + "/comments", Map.of("body", "Thanks!", "parentId", commentId), a);
        api.assertStatus(reply, 201);
        String replyId = api.body(reply).get("id").asString();
        MvcResult nested = api.post("/api/v1/posts/" + postId + "/comments", Map.of("body", "deeper", "parentId", replyId), b);
        api.assertStatus(nested, 400);
        assertThat(api.code(nested)).isEqualTo("INVALID_PARENT");
        assertThat(api.body(api.get("/api/v1/posts/" + postId, a)).get("commentCount").asInt()).isEqualTo(2);

        JsonNode list = api.body(api.get("/api/v1/posts/" + postId + "/comments", a));
        assertThat(list.get("content").size()).isEqualTo(1);
        assertThat(list.get("content").get(0).get("replies").size()).isEqualTo(1);
        assertThat(list.get("content").get(0).get("replies").get(0).get("own").asBoolean()).isTrue();

        api.assertStatus(api.put("/api/v1/comments/" + commentId, Map.of("body", "edited by someone else"), a), 404);
        assertThat(api.body(api.put("/api/v1/comments/" + commentId, Map.of("body", "Nice post, edited"), b)).get("editedAt").isNull()).isFalse();
        // Deleting a top-level comment with replies keeps a placeholder so the thread stays readable.
        api.assertStatus(api.delete("/api/v1/comments/" + commentId, b), 204);
        list = api.body(api.get("/api/v1/posts/" + postId + "/comments", a));
        assertThat(list.get("content").size()).isEqualTo(1);
        assertThat(list.get("content").get(0).get("status").asString()).isEqualTo("DELETED");
        assertThat(list.get("content").get(0).get("body").isNull()).isTrue();
        assertThat(list.get("content").get(0).get("author").isNull()).isTrue();
        assertThat(list.get("content").get(0).get("replies").size()).isEqualTo(1);
        assertThat(api.body(api.get("/api/v1/posts/" + postId, a)).get("commentCount").asInt()).isEqualTo(1);

        // Feed filters.
        assertThat(ids(api.body(api.get("/api/v1/feed", b)))).contains(postId);
        assertThat(ids(api.body(api.get("/api/v1/feed?intent=EVENT", b)))).doesNotContain(postId);
        assertThat(ids(api.body(api.get("/api/v1/feed?tag=%23Blue", b)))).contains(postId);
        assertThat(ids(api.body(api.get("/api/v1/feed?q=Edited", b)))).contains(postId);
        assertThat(ids(api.body(api.get("/api/v1/feed?author=" + alice.id(), b)))).contains(postId);
        assertThat(api.body(api.get("/api/v1/hashtags/trending", b)).findValuesAsString("tag")).contains("blue");

        // Author deletion.
        api.assertStatus(api.delete("/api/v1/posts/" + postId, a), 204);
        assertThat(ids(api.body(api.get("/api/v1/feed", b)))).doesNotContain(postId);
        api.assertStatus(api.get("/api/v1/posts/" + postId, b), 404);
        assertThat(api.body(api.get("/api/v1/posts/" + postId, a)).get("status").asString()).isEqualTo("DELETED");
        api.assertStatus(api.post("/api/v1/posts/" + postId + "/comments", Map.of("body", "late"), a), 409);
    }

    @Test
    void visibilityBlockingSuspensionAndReposts() throws Exception {
        TestActors.Actor alice = actors.verifiedMember(api, "vb-alice");
        TestActors.Actor bob = actors.verifiedMember(api, "vb-bob");
        TestActors.Actor carol = actors.verifiedMember(api, "vb-carol");
        TestActors.Actor dave = actors.verifiedMember(api, "vb-dave");
        TestActors.Actor pending = actors.registerApplicant(api, "vb-pending", AffiliationType.STUDENT);
        ApiClient.Session a = actors.login(api, alice);
        ApiClient.Session b = actors.login(api, bob);
        ApiClient.Session c = actors.login(api, carol);
        ApiClient.Session d = actors.login(api, dave);
        ApiClient.Session p = actors.login(api, pending);

        String secret = api.body(api.post("/api/v1/posts", Map.of("intent", "MENTORSHIP", "body", "connections only",
                "visibility", "CONNECTIONS_ONLY"), a)).get("id").asString();
        String open = api.body(api.post("/api/v1/posts", Map.of("intent", "EVENT", "body", "open to all"), a)).get("id").asString();
        String davePost = api.body(api.post("/api/v1/posts", Map.of("intent", "ACHIEVEMENT", "body", "from dave"), d)).get("id").asString();

        assertThat(ids(api.body(api.get("/api/v1/feed", b)))).contains(open).doesNotContain(secret);
        api.assertStatus(api.get("/api/v1/posts/" + secret, b), 404);
        api.assertStatus(api.post("/api/v1/posts/" + secret + "/like", null, b), 404);
        api.assertStatus(api.post("/api/v1/posts/" + secret + "/comments", Map.of("body", "hi"), b), 404);

        String request = api.body(api.post("/api/v1/connections/requests", Map.of("userId", alice.id().toString()), b)).get("id").asString();
        api.assertStatus(api.post("/api/v1/connections/requests/" + request + "/accept", null, a), 204);
        assertThat(ids(api.body(api.get("/api/v1/feed", b)))).contains(secret, open);
        api.assertStatus(api.get("/api/v1/posts/" + secret, b), 200);
        assertThat(ids(api.body(api.get("/api/v1/feed", c)))).doesNotContain(secret);

        // Reposts: only original, all-members posts; the original is nested one level and counted.
        MvcResult repost = api.post("/api/v1/posts", Map.of("intent", "EVENT", "body", "Worth sharing", "repostOfId", open), c);
        api.assertStatus(repost, 201);
        String repostId = api.body(repost).get("id").asString();
        assertThat(api.body(repost).get("repostOf").get("id").asString()).isEqualTo(open);
        assertThat(api.body(api.get("/api/v1/posts/" + open, a)).get("repostCount").asInt()).isEqualTo(1);
        MvcResult repostOfSecret = api.post("/api/v1/posts", Map.of("intent", "EVENT", "body", "leak", "repostOfId", secret), b);
        api.assertStatus(repostOfSecret, 409);
        assertThat(api.code(repostOfSecret)).isEqualTo("REPOST_NOT_ALLOWED");
        api.assertStatus(api.post("/api/v1/posts", Map.of("intent", "EVENT", "body", "chain", "repostOfId", repostId), b), 409);
        MvcResult plainRepost = api.post("/api/v1/posts", Map.of("intent", "EVENT", "body", "", "repostOfId", open), d);
        api.assertStatus(plainRepost, 201);
        assertThat(api.body(api.get("/api/v1/posts/" + open, a)).get("repostCount").asInt()).isEqualTo(2);

        // Blocking hides both directions, including reposts of the blocked member's content.
        api.assertStatus(api.post("/api/v1/blocks", Map.of("userId", carol.id().toString()), a), 204);
        assertThat(ids(api.body(api.get("/api/v1/feed", c)))).doesNotContain(open, secret);
        api.assertStatus(api.get("/api/v1/posts/" + open, c), 404);
        assertThat(ids(api.body(api.get("/api/v1/feed", a)))).doesNotContain(repostId);
        JsonNode repostSeenByBob = api.body(api.get("/api/v1/posts/" + repostId, b));
        assertThat(repostSeenByBob.get("repostOf").get("id").asString()).isEqualTo(open);
        JsonNode repostSeenByCarol = api.body(api.get("/api/v1/posts/" + repostId, c));
        assertThat(repostSeenByCarol.get("repostUnavailable").asBoolean()).isTrue();
        assertThat(repostSeenByCarol.get("repostOf").isNull()).isTrue();

        // Suspended authors disappear from the feed; deleting a repost releases the counter.
        tx.executeWithoutResult(t -> accounts.changeStatus(dave.id(), AccountStatus.SUSPENDED, null, "test"));
        assertThat(ids(api.body(api.get("/api/v1/feed", b)))).doesNotContain(davePost);
        api.assertStatus(api.get("/api/v1/posts/" + davePost, b), 404);
        api.assertStatus(api.delete("/api/v1/posts/" + repostId, c), 204);
        assertThat(api.body(api.get("/api/v1/posts/" + open, a)).get("repostCount").asInt()).isEqualTo(1);

        // Non-verified accounts never reach the feed.
        for (String path : List.of("/api/v1/feed", "/api/v1/bookmarks", "/api/v1/posts/" + open, "/api/v1/hashtags/trending")) {
            MvcResult denied = api.get(path, p);
            api.assertStatus(denied, 403);
            assertThat(api.code(denied)).isEqualTo("ACCOUNT_NOT_VERIFIED");
        }
        api.assertStatus(api.post("/api/v1/posts", Map.of("intent", "EVENT", "body", "nope"), p), 403);
    }

    @Test
    void reportsAndModeration() throws Exception {
        TestActors.Actor moderator = actors.privileged(api, "cm-mod", Set.of(RoleName.MODERATOR));
        TestActors.Actor alice = actors.verifiedMember(api, "rp-alice");
        TestActors.Actor bob = actors.verifiedMember(api, "rp-bob");
        ApiClient.Session m = actors.login(api, moderator);
        ApiClient.Session a = actors.login(api, alice);
        ApiClient.Session b = actors.login(api, bob);

        String postId = api.body(api.post("/api/v1/posts", Map.of("intent", "JOB_OPPORTUNITY", "body", "buy followers now"), a)).get("id").asString();
        String commentId = api.body(api.post("/api/v1/posts/" + postId + "/comments", Map.of("body", "rude comment"), a)).get("id").asString();

        MvcResult report = api.post("/api/v1/reports", Map.of("targetType", "POST", "targetId", postId, "reason", "SPAM",
                "details", "Obvious spam\u0000"), b);
        api.assertStatus(report, 201);
        String reportId = api.body(report).get("id").asString();
        MvcResult duplicate = api.post("/api/v1/reports", Map.of("targetType", "POST", "targetId", postId, "reason", "SPAM"), b);
        api.assertStatus(duplicate, 409);
        assertThat(api.code(duplicate)).isEqualTo("ALREADY_REPORTED");
        MvcResult selfReport = api.post("/api/v1/reports", Map.of("targetType", "POST", "targetId", postId, "reason", "SPAM"), a);
        api.assertStatus(selfReport, 400);
        assertThat(api.code(selfReport)).isEqualTo("SELF_REPORT");
        String userReport = api.body(api.post("/api/v1/reports", Map.of("targetType", "USER", "targetId", alice.id().toString(),
                "reason", "IMPERSONATION"), b)).get("id").asString();
        String commentReport = api.body(api.post("/api/v1/reports", Map.of("targetType", "COMMENT", "targetId", commentId,
                "reason", "HARASSMENT"), b)).get("id").asString();
        api.assertStatus(api.post("/api/v1/reports", Map.of("targetType", "POST", "targetId", UUID.randomUUID().toString(), "reason", "SPAM"), b), 404);

        // Members never reach the moderation queue.
        api.assertStatus(api.get("/api/v1/moderation/reports", b), 403);
        api.assertStatus(api.post("/api/v1/moderation/posts/" + postId + "/remove", Map.of("reason", "x"), b), 403);

        JsonNode queue = api.body(api.get("/api/v1/moderation/reports?status=OPEN", m));
        assertThat(queue.get("content").findValuesAsString("id")).contains(reportId, userReport, commentReport);
        assertThat(api.body(api.get("/api/v1/moderation/reports/summary", m)).get("open").asLong()).isGreaterThanOrEqualTo(3);
        JsonNode detail = api.body(api.get("/api/v1/moderation/reports/" + reportId, m));
        assertThat(detail.get("contentSnapshot").asString()).isEqualTo("buy followers now");
        assertThat(detail.get("details").asString()).isEqualTo("Obvious spam");
        assertThat(detail.get("targetPost").get("body").asString()).isEqualTo("buy followers now");
        assertThat(detail.get("reporter").get("userId").asString()).isEqualTo(bob.id().toString());
        assertThat(detail.get("targetOwner").get("userId").asString()).isEqualTo(alice.id().toString());

        JsonNode resolved = api.body(api.post("/api/v1/moderation/reports/" + reportId + "/resolve",
                Map.of("resolution", "CONTENT_REMOVED", "note", "Spam"), m));
        assertThat(resolved.get("status").asString()).isEqualTo("RESOLVED");
        assertThat(resolved.get("targetPost").get("status").asString()).isEqualTo("REMOVED");
        api.assertStatus(api.get("/api/v1/posts/" + postId, b), 404);
        JsonNode removedForAuthor = api.body(api.get("/api/v1/posts/" + postId, a));
        assertThat(removedForAuthor.get("status").asString()).isEqualTo("REMOVED");
        assertThat(removedForAuthor.get("removedReason").asString()).isEqualTo("Spam");
        assertThat(ids(api.body(api.get("/api/v1/feed", b)))).doesNotContain(postId);
        api.assertStatus(api.put("/api/v1/posts/" + postId, Map.of("intent", "EVENT", "body", "cleaned"), a), 409);
        Integer audits = jdbc.queryForObject("select count(*) from audit_logs where action in ('COMMUNITY_POST_REMOVED', 'COMMUNITY_REPORT_RESOLVED')"
                + " and actor_id = ?::uuid", Integer.class, moderator.id());
        assertThat(audits).isEqualTo(2);
        api.assertStatus(api.post("/api/v1/moderation/reports/" + reportId + "/resolve", Map.of("resolution", "DISMISSED"), m), 409);

        MvcResult notContent = api.post("/api/v1/moderation/reports/" + userReport + "/resolve", Map.of("resolution", "CONTENT_REMOVED"), m);
        api.assertStatus(notContent, 400);
        assertThat(api.code(notContent)).isEqualTo("NOT_CONTENT");
        assertThat(api.body(api.post("/api/v1/moderation/reports/" + userReport + "/resolve", Map.of("resolution", "NO_ACTION", "note", "Checked"), m))
                .get("status").asString()).isEqualTo("RESOLVED");
        assertThat(api.body(api.post("/api/v1/moderation/reports/" + commentReport + "/resolve", Map.of("resolution", "DISMISSED"), m))
                .get("status").asString()).isEqualTo("DISMISSED");

        // Restore, then remove a comment directly.
        assertThat(api.body(api.post("/api/v1/moderation/posts/" + postId + "/restore", null, m)).get("status").asString()).isEqualTo("ACTIVE");
        api.assertStatus(api.get("/api/v1/posts/" + postId, b), 200);
        assertThat(api.body(api.post("/api/v1/moderation/comments/" + commentId + "/remove", Map.of("reason", "Harassment"), m))
                .get("status").asString()).isEqualTo("REMOVED");
        assertThat(api.body(api.get("/api/v1/posts/" + postId, b)).get("commentCount").asInt()).isZero();
        assertThat(api.body(api.get("/api/v1/posts/" + postId + "/comments", b)).get("content").size()).isZero();
        api.assertStatus(api.put("/api/v1/comments/" + commentId, Map.of("body", "sneaky edit"), a), 409);
    }

    private static List<String> ids(JsonNode page) {
        return page.get("content").findValuesAsString("id").stream().toList();
    }

    private static List<String> toList(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false).map(JsonNode::asString).toList();
    }
}
