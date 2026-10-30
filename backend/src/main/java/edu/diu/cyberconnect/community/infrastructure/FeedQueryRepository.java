package edu.diu.cyberconnect.community.infrastructure;

import edu.diu.cyberconnect.community.application.FeedQuery;
import edu.diu.cyberconnect.community.application.TrendingTag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read-only feed query. Visibility is decided in SQL so paging stays correct: the author must be a verified account,
 * neither side may have blocked the other, and CONNECTIONS_ONLY posts need an accepted connection (or the viewer is
 * the author). Joins {@code users}, {@code blocks} and {@code connections} read-only.
 */
@Repository
public class FeedQueryRepository {

    private static final String VISIBLE = """
            p.status = 'ACTIVE' AND u.account_status = 'VERIFIED'
            AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = :viewer AND b.blocked_id = p.author_id)
                                                     OR (b.blocker_id = p.author_id AND b.blocked_id = :viewer))
            AND (p.visibility = 'ALL_MEMBERS' OR p.author_id = :viewer
                 OR EXISTS (SELECT 1 FROM connections c WHERE c.status = 'ACCEPTED'
                            AND ((c.requester_id = :viewer AND c.addressee_id = p.author_id)
                              OR (c.addressee_id = :viewer AND c.requester_id = p.author_id))))""";

    private final JdbcClient jdbc;

    public FeedQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Ids of visible posts matching the query, newest first. */
    public Page<UUID> feed(UUID viewerId, FeedQuery query, Pageable pageable) {
        List<String> where = new ArrayList<>(List.of(VISIBLE));
        Map<String, Object> params = new HashMap<>();
        params.put("viewer", viewerId);
        if (query.intent() != null) {
            params.put("intent", query.intent().name());
            where.add("p.intent = :intent");
        }
        if (query.authorId() != null) {
            params.put("author", query.authorId());
            where.add("p.author_id = :author");
        }
        if (query.tag() != null && !query.tag().isBlank()) {
            params.put("tag", query.tag());
            where.add("EXISTS (SELECT 1 FROM post_hashtags ph JOIN hashtags h ON h.id = ph.hashtag_id"
                    + " WHERE ph.post_id = p.id AND h.tag = :tag)");
        }
        if (query.q() != null && !query.q().isBlank()) {
            params.put("q", query.q().trim());
            where.add("p.search_vector @@ websearch_to_tsquery('simple', :q)");
        }
        if (query.bookmarkedOnly()) {
            where.add("EXISTS (SELECT 1 FROM bookmarks bm WHERE bm.user_id = :viewer AND bm.post_id = p.id)");
        }
        String from = " FROM posts p JOIN users u ON u.id = p.author_id WHERE " + String.join(" AND ", where);
        String order = query.bookmarkedOnly()
                ? " ORDER BY (SELECT bm.created_at FROM bookmarks bm WHERE bm.user_id = :viewer AND bm.post_id = p.id) DESC"
                : " ORDER BY p.created_at DESC, p.id DESC";

        JdbcClient.StatementSpec count = jdbc.sql("SELECT count(*)" + from);
        params.forEach(count::param);
        long total = count.query(Long.class).single();

        JdbcClient.StatementSpec select = jdbc.sql("SELECT p.id" + from + order + " LIMIT :limit OFFSET :offset");
        params.forEach(select::param);
        select.param("limit", pageable.getPageSize()).param("offset", pageable.getOffset());
        List<UUID> ids = select.query(UUID.class).list();
        return new PageImpl<>(ids, pageable, total);
    }

    /** Whether one post is visible to the viewer under the feed rules (ACTIVE, author verified, no block, visibility). */
    public boolean isVisible(UUID viewerId, UUID postId) {
        return jdbc.sql("SELECT count(*) FROM posts p JOIN users u ON u.id = p.author_id WHERE p.id = :post AND " + VISIBLE)
                .param("viewer", viewerId).param("post", postId)
                .query(Long.class).single() > 0;
    }

    public List<TrendingTag> trending(int days, int limit) {
        return jdbc.sql("""
                        SELECT h.tag, count(*) AS posts FROM post_hashtags ph
                          JOIN hashtags h ON h.id = ph.hashtag_id
                          JOIN posts p ON p.id = ph.post_id
                         WHERE p.status = 'ACTIVE' AND p.visibility = 'ALL_MEMBERS'
                           AND p.created_at > now() - make_interval(days => :days)
                         GROUP BY h.tag ORDER BY posts DESC, h.tag ASC LIMIT :limit""")
                .param("days", days).param("limit", limit)
                .query((rs, i) -> new TrendingTag(rs.getString("tag"), rs.getLong("posts")))
                .list();
    }
}
