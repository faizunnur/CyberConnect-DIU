package edu.diu.cyberconnect.profile.infrastructure;

import edu.diu.cyberconnect.common.model.AffiliationType;
import edu.diu.cyberconnect.common.model.FieldVisibility;
import edu.diu.cyberconnect.profile.application.DirectoryQuery;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
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
 * Read-only directory query. It joins the profile tables with {@code users}, {@code user_affiliations} and the
 * networking tables ({@code connections}, {@code blocks}) for filtering and relationship flags; it never writes
 * them. Relationship-dependent field visibility is applied by the service on top of the {@code connected} flag.
 */
@Repository
public class DirectorySearchRepository {

    /** One directory row; organisation/location/mentor fields still carry their visibility for the service. */
    public record Row(UUID userId, String displayName, String headline, String organization,
            FieldVisibility organizationVisibility, String location, FieldVisibility locationVisibility,
            boolean mentorAvailable, FieldVisibility mentorVisibility, String photoKey, int photoVersion,
            boolean connected, List<AffiliationType> affiliations, List<String> skills) {
    }

    private static final String SELECT = """
            SELECT p.user_id, p.display_name, p.headline, p.organization, p.organization_visibility,
                   p.location, p.location_visibility, p.mentor_available, p.mentor_visibility,
                   p.photo_key, p.photo_version,
                   EXISTS (SELECT 1 FROM connections c WHERE c.status = 'ACCEPTED'
                           AND ((c.requester_id = :viewer AND c.addressee_id = p.user_id)
                             OR (c.addressee_id = :viewer AND c.requester_id = p.user_id))) AS connected,
                   (SELECT array_agg(a.affiliation_type ORDER BY a.affiliation_type) FROM user_affiliations a
                     WHERE a.user_id = p.user_id AND a.status = 'VERIFIED') AS affiliations,
                   (SELECT array_agg(s.name ORDER BY us.position) FROM user_skills us
                      JOIN skills s ON s.id = us.skill_id WHERE us.user_id = p.user_id) AS skills
            FROM user_profiles p
            JOIN users u ON u.id = p.user_id
            """;

    private static final String CONNECTED = """
            EXISTS (SELECT 1 FROM connections c WHERE c.status = 'ACCEPTED'
                    AND ((c.requester_id = :viewer AND c.addressee_id = p.user_id)
                      OR (c.addressee_id = :viewer AND c.requester_id = p.user_id)))""";

    private final JdbcClient jdbc;

    public DirectorySearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Page<Row> search(UUID viewerId, DirectoryQuery query, Pageable pageable) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("viewer", viewerId);
        where.add("u.account_status = 'VERIFIED'");
        where.add("p.searchable = true");
        where.add("p.user_id <> :viewer");
        where.add("""
                NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = :viewer AND b.blocked_id = p.user_id)
                                                      OR (b.blocker_id = p.user_id AND b.blocked_id = :viewer))""");

        String q = trimmed(query.q());
        if (q != null) {
            params.put("q", q);
            params.put("qLike", like(q));
            where.add("""
                    (p.search_vector @@ websearch_to_tsquery('simple', :q)
                     OR p.display_name ILIKE :qLike OR p.headline ILIKE :qLike
                     OR EXISTS (SELECT 1 FROM user_skills us JOIN skills s ON s.id = us.skill_id
                                WHERE us.user_id = p.user_id AND s.name ILIKE :qLike))""");
        }
        if (query.affiliation() != null) {
            params.put("affiliation", query.affiliation().name());
            where.add("""
                    EXISTS (SELECT 1 FROM user_affiliations a WHERE a.user_id = p.user_id
                            AND a.status = 'VERIFIED' AND a.affiliation_type = :affiliation)""");
        }
        String skill = trimmed(query.skill());
        if (skill != null) {
            params.put("skill", skill.toLowerCase());
            where.add("""
                    EXISTS (SELECT 1 FROM user_skills us JOIN skills s ON s.id = us.skill_id
                            WHERE us.user_id = p.user_id AND (s.slug = :skill OR lower(s.name) = :skill))""");
        }
        String organization = trimmed(query.organization());
        if (organization != null) {
            params.put("orgLike", like(organization));
            where.add("(p.organization ILIKE :orgLike AND (p.organization_visibility = 'ALL_MEMBERS'"
                    + " OR (p.organization_visibility = 'CONNECTIONS' AND " + CONNECTED + ")))");
        }
        String experience = trimmed(query.experience());
        if (experience != null) {
            params.put("expLike", like(experience));
            where.add("""
                    (p.job_title ILIKE :expLike OR EXISTS (SELECT 1 FROM profile_experiences e
                        WHERE e.user_id = p.user_id AND (e.title ILIKE :expLike OR e.organization ILIKE :expLike)))""");
        }
        if (query.mentorAvailable() != null) {
            params.put("mentor", query.mentorAvailable());
            where.add("(p.mentor_available = :mentor AND (p.mentor_visibility = 'ALL_MEMBERS'"
                    + " OR (p.mentor_visibility = 'CONNECTIONS' AND " + CONNECTED + ")))");
        }

        String whereClause = " WHERE " + String.join(" AND ", where);
        String order = q != null
                ? " ORDER BY ts_rank(p.search_vector, websearch_to_tsquery('simple', :q)) DESC, p.display_name ASC"
                : " ORDER BY p.display_name ASC, p.user_id ASC";

        JdbcClient.StatementSpec count = jdbc.sql("SELECT count(*) FROM user_profiles p JOIN users u ON u.id = p.user_id"
                + whereClause);
        params.forEach(count::param);
        long total = count.query(Long.class).single();

        JdbcClient.StatementSpec select = jdbc.sql(SELECT + whereClause + order + " LIMIT :limit OFFSET :offset");
        params.forEach(select::param);
        select.param("limit", pageable.getPageSize()).param("offset", pageable.getOffset());
        List<Row> rows = select.query(DirectorySearchRepository::map).list();
        return new PageImpl<>(rows, pageable, total);
    }

    /** Cards for an explicit id list (connections, requests, blocks); no searchability or block filtering. */
    public List<Row> byIds(UUID viewerId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + " WHERE p.user_id IN (:ids)")
                .param("viewer", viewerId)
                .param("ids", new ArrayList<>(ids))
                .query(DirectorySearchRepository::map)
                .list();
    }

    private static Row map(ResultSet rs, int rowNum) throws SQLException {
        return new Row(
                rs.getObject("user_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("headline"),
                rs.getString("organization"),
                FieldVisibility.valueOf(rs.getString("organization_visibility")),
                rs.getString("location"),
                FieldVisibility.valueOf(rs.getString("location_visibility")),
                rs.getBoolean("mentor_available"),
                FieldVisibility.valueOf(rs.getString("mentor_visibility")),
                rs.getString("photo_key"),
                rs.getInt("photo_version"),
                rs.getBoolean("connected"),
                strings(rs.getArray("affiliations")).stream().map(AffiliationType::valueOf).toList(),
                strings(rs.getArray("skills")));
    }

    private static List<String> strings(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        List<String> result = new ArrayList<>(values.length);
        for (Object value : values) {
            if (value != null) {
                result.add(value.toString());
            }
        }
        return result;
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String like(String value) {
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
