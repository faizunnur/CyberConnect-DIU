package edu.diu.cyberconnect.opportunity.infrastructure;

import edu.diu.cyberconnect.opportunity.application.OpportunityQuery;
import java.time.LocalDate;
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
 * Read-only listing of open opportunities: author must be a verified account, no block either way, deadline not past.
 * Joins {@code users} and {@code blocks} read-only.
 */
@Repository
public class OpportunitySearchRepository {

    private static final String VISIBLE = """
            o.status = 'OPEN' AND u.account_status = 'VERIFIED' AND (o.deadline IS NULL OR o.deadline >= :today)
            AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = :viewer AND b.blocked_id = o.author_id)
                                                     OR (b.blocker_id = o.author_id AND b.blocked_id = :viewer))""";

    private final JdbcClient jdbc;

    public OpportunitySearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Page<UUID> search(UUID viewerId, OpportunityQuery query, LocalDate today, Pageable pageable) {
        List<String> where = new ArrayList<>(List.of(VISIBLE));
        Map<String, Object> params = new HashMap<>();
        params.put("viewer", viewerId);
        params.put("today", today);
        if (query.q() != null && !query.q().isBlank()) {
            params.put("q", query.q().trim());
            where.add("o.search_vector @@ websearch_to_tsquery('simple', :q)");
        }
        if (query.type() != null) {
            params.put("type", query.type().name());
            where.add("o.type = :type");
        }
        if (query.locationType() != null) {
            params.put("locationType", query.locationType().name());
            where.add("o.location_type = :locationType");
        }
        if (query.skill() != null && !query.skill().isBlank()) {
            params.put("skill", query.skill().trim().toLowerCase());
            where.add("EXISTS (SELECT 1 FROM opportunity_skills os JOIN skills s ON s.id = os.skill_id"
                    + " WHERE os.opportunity_id = o.id AND (s.slug = :skill OR lower(s.name) = :skill))");
        }
        if (query.authorId() != null) {
            params.put("author", query.authorId());
            where.add("o.author_id = :author");
        }
        String from = " FROM opportunities o JOIN users u ON u.id = o.author_id WHERE " + String.join(" AND ", where);
        String order = query.q() != null && !query.q().isBlank()
                ? " ORDER BY ts_rank(o.search_vector, websearch_to_tsquery('simple', :q)) DESC, o.published_at DESC"
                : " ORDER BY o.published_at DESC, o.id DESC";

        JdbcClient.StatementSpec count = jdbc.sql("SELECT count(*)" + from);
        params.forEach(count::param);
        long total = count.query(Long.class).single();
        JdbcClient.StatementSpec select = jdbc.sql("SELECT o.id" + from + order + " LIMIT :limit OFFSET :offset");
        params.forEach(select::param);
        select.param("limit", pageable.getPageSize()).param("offset", pageable.getOffset());
        return new PageImpl<>(select.query(UUID.class).list(), pageable, total);
    }

    /** Whether the viewer may read a non-draft opportunity (author verified, no block). Status is checked by the service. */
    public boolean authorVisible(UUID viewerId, UUID opportunityId) {
        return jdbc.sql("""
                        SELECT count(*) FROM opportunities o JOIN users u ON u.id = o.author_id
                         WHERE o.id = :id AND u.account_status = 'VERIFIED'
                           AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = :viewer AND b.blocked_id = o.author_id)
                                                                    OR (b.blocker_id = o.author_id AND b.blocked_id = :viewer))""")
                .param("id", opportunityId).param("viewer", viewerId)
                .query(Long.class).single() > 0;
    }
}
