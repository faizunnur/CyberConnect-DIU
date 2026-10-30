package edu.diu.cyberconnect.support;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;

/**
 * Shared infrastructure for integration tests: PostgreSQL (with the migrator/app role split), Redis and an
 * S3-compatible store, started once per JVM. Requires Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
@SuppressWarnings({"rawtypes", "unchecked", "resource"})
public abstract class AbstractIntegrationTest {

    protected static final String APP_ROLE = "cyberconnect_app";
    protected static final String APP_PASSWORD = "app-test-password";
    protected static final String STORAGE_ACCESS_KEY = "testaccesskey";
    protected static final String STORAGE_SECRET_KEY = "testsecretkey123456";

    protected static final PostgreSQLContainer POSTGRES = postgres();

    protected static final GenericContainer REDIS = new GenericContainer("redis:7.4.12-alpine")
            .withExposedPorts(6379);

    protected static final GenericContainer STORAGE = new GenericContainer("rustfs/rustfs:1.0.4")
            .withEnv("RUSTFS_ACCESS_KEY", STORAGE_ACCESS_KEY)
            .withEnv("RUSTFS_SECRET_KEY", STORAGE_SECRET_KEY)
            .withEnv("RUSTFS_VOLUMES", "/data")
            .withEnv("RUSTFS_ADDRESS", ":9000")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/health").forPort(9000).forStatusCode(200));

    static {
        Startables.deepStart(List.of(POSTGRES, REDIS, STORAGE)).join();
    }

    private static PostgreSQLContainer postgres() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17.11-alpine");
        container.withDatabaseName("cyberconnect");
        container.withUsername("cyberconnect_migrator");
        container.withPassword("migrator-test");
        container.withInitScript("db/testcontainers/init-roles.sql");
        return container;
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String jdbcUrl = POSTGRES.getJdbcUrl();
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.url", () -> jdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.app_role", () -> APP_ROLE);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("storage.endpoint", () -> "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(9000));
        registry.add("storage.access-key", () -> STORAGE_ACCESS_KEY);
        registry.add("storage.secret-key", () -> STORAGE_SECRET_KEY);
    }

    protected static String storageEndpoint() {
        return "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(9000);
    }
}
