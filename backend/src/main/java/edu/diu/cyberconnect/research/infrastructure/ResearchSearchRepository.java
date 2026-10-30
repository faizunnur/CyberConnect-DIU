package edu.diu.cyberconnect.research.infrastructure;

import edu.diu.cyberconnect.research.application.ResearchQuery;
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

/** Read-only research listing: lead must be a verified account and not blocked either way. */
@Repository
public class ResearchSearchRepository {

    private static final String VISIBLE = """
            p.status <> 'REMOVED' AND u.account_status = 'VERIFIED'
            AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = :viewer AND b.blocked_id = p.lead_id)
                                                     OR (b.blocker_id = p.lead_id AND b.blocked_id = :viewer))""";

    private final JdbcClient jdbc;

    public ResearchSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Page<UUID> search(UUID viewerId, ResearchQuery query, Pageable pageable) {
        List<String> where = new ArrayList<>(List.of(VISIBLE));
        Map<String, Object> params = new HashMap<>();
        params.put("viewer", viewerId);
        if (query.status() != null) {
            params.put("status", query.status().name());
            where.add("p.status = :status");
        } else {
            where.add("p.status IN ('PROPOSED', 'ACTIVE')");
        }
        if (query.q() != null && !query.q().isBlank()) {
            params.put("q", query.q().trim());
            where.add("p.search_vector @@ websearch_to_tsquery('simple', :q)");
        }
        if (query.skill() != null && !query.skill().isBlank()) {
            params.put("skill", query.skill().trim().toLowerCase());
            where.add("EXISTS (SELECT 1 FROM research_project_skills ps JOIN skills s ON s.id = ps.skill_id"
                    + " WHERE ps.project_id = p.id AND (s.slug = :skill OR lower(s.name) = :skill))");
        }
        if (query.lookingForMembers() != null) {
            params.put("looking", query.lookingForMembers());
            where.add("p.looking_for_members = :looking");
        }
        if (query.memberId() != null) {
            params.put("member", query.memberId());
            where.add("EXISTS (SELECT 1 FROM research_members m WHERE m.project_id = p.id AND m.user_id = :member)");
        }
        String from = " FROM research_projects p JOIN users u ON u.id = p.lead_id WHERE " + String.join(" AND ", where);
        String order = query.q() != null && !query.q().isBlank()
                ? " ORDER BY ts_rank(p.search_vector, websearch_to_tsquery('simple', :q)) DESC, p.created_at DESC"
                : " ORDER BY p.created_at DESC, p.id DESC";
        JdbcClient.StatementSpec count = jdbc.sql("SELECT count(*)" + from);
        params.forEach(count::param);
        long total = count.query(Long.class).single();
        JdbcClient.StatementSpec select = jdbc.sql("SELECT p.id" + from + order + " LIMIT :limit OFFSET :offset");
        params.forEach(select::param);
        select.param("limit", pageable.getPageSize()).param("offset", pageable.getOffset());
        return new PageImpl<>(select.query(UUID.class).list(), pageable, total);
    }

    public boolean leadVisible(UUID viewerId, UUID projectId) {
        return jdbc.sql("""
                        SELECT count(*) FROM research_projects p JOIN users u ON u.id = p.lead_id
                         WHERE p.id = :id AND u.account_status = 'VERIFIED'
                           AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = :viewer AND b.blocked_id = p.lead_id)
                                                                    OR (b.blocker_id = p.lead_id AND b.blocked_id = :viewer))""")
                .param("id", projectId).param("viewer", viewerId)
                .query(Long.class).single() > 0;
    }
}
