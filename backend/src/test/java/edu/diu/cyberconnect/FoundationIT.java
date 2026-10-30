package edu.diu.cyberconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.diu.cyberconnect.admin.application.SettingKeys;
import edu.diu.cyberconnect.admin.application.SystemSettings;
import edu.diu.cyberconnect.audit.application.AuditEntry;
import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** M1 acceptance: migrations, public health, CSRF bootstrap, deny-by-default, append-only audit, Redis sessions. */
class FoundationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditService auditService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private SystemSettings systemSettings;

    @Autowired
    @SuppressWarnings("rawtypes")
    private SessionRepository sessionRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void flywayBaselineIsAppliedByMigratorRole() {
        Integer migrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(migrations).isGreaterThanOrEqualTo(1);
        Integer roles = jdbcTemplate.queryForObject("select count(*) from roles", Integer.class);
        assertThat(roles).isEqualTo(4);
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.environment").value("test"));
    }

    @Test
    void csrfEndpointSetsReadableCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.cookieName").value("XSRF-TOKEN"));
    }

    @Test
    void protectedEndpointsRequireAuthenticationWithProblemJson() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void mutatingRequestsWithoutCsrfTokenAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/health"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void auditLogsAreAppendOnlyForTheRuntimeRole() {
        transactionTemplate.executeWithoutResult(tx -> auditService.record(
                AuditEntry.system("TEST_EVENT", "test", null, Map.of("probe", "value"))));

        Integer written = jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where action = 'TEST_EVENT'", Integer.class);
        assertThat(written).isEqualTo(1);

        assertThatThrownBy(() -> jdbcTemplate.update("update audit_logs set action = 'TAMPERED' where action = 'TEST_EVENT'"))
                .hasStackTraceContaining("permission denied");
        assertThatThrownBy(() -> jdbcTemplate.update("delete from audit_logs where action = 'TEST_EVENT'"))
                .hasStackTraceContaining("permission denied");
        assertThatThrownBy(() -> jdbcTemplate.update("delete from flyway_schema_history"))
                .hasStackTraceContaining("permission denied");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sessionsAreStoredInRedis() {
        Session session = sessionRepository.createSession();
        session.setAttribute("probe", "value");
        sessionRepository.save(session);

        Session loaded = sessionRepository.findById(session.getId());
        assertThat(loaded).isNotNull();
        assertThat((String) loaded.getAttribute("probe")).isEqualTo("value");
        assertThat(redisTemplate.keys("cyberconnect:session:*")).isNotEmpty();

        sessionRepository.deleteById(session.getId());
        assertThat(sessionRepository.findById(session.getId())).isNull();
    }

    @Test
    void platformSettingsAreSeeded() {
        assertThat(systemSettings.getBoolean(SettingKeys.REAL_DOCUMENTS_ENABLED)).isFalse();
        assertThat(systemSettings.getInt(SettingKeys.MAX_SUBMISSIONS_PER_AFFILIATION)).isEqualTo(3);
        assertThat(systemSettings.getInt(SettingKeys.DOCUMENT_RETENTION_DAYS)).isEqualTo(30);
        assertThat(systemSettings.getString(SettingKeys.STUDENT_ID_PATTERN)).isEqualTo("^[A-Za-z0-9-]{5,20}$");
    }
}
