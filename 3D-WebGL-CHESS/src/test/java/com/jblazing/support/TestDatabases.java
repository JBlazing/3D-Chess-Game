package com.jblazing.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * The data stores the tests run against, each started once for the whole test run: a real
 * PostgreSQL server (embedded-postgres) and a real Redis server (a Testcontainers container, the
 * same image as the development server). The app picks them up through DB_URL and REDIS_*, the
 * same way it is pointed at them in production.
 * <p>
 * In the CI pipeline both are job services instead, given by environment variables:
 * <ul>
 *   <li>TEST_DB_URL: a JDBC URL to a PostgreSQL server that lets the user postgres in without a password.
 *       (embedded-postgres can't run there: its arm64 binaries don't load on the pi's 16K-page kernel.)</li>
 *   <li>TEST_REDIS_HOST: a Redis server on port 6379 with {@link #REDIS_PASSWORD}.</li>
 * </ul>
 */
public final class TestDatabases {

    public static final String REDIS_PASSWORD = "test-password";

    private static EmbeddedPostgres postgres;
    private static GenericContainer<?> redis;

    private TestDatabases() {
    }

    public static synchronized String postgresUrl() {
        String external = System.getenv("TEST_DB_URL");
        if (external != null) {
            return external;
        }
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not start the embedded PostgreSQL server", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // the JVM is exiting anyway
                }
            }));
        }
        return postgres.getJdbcUrl("postgres", "postgres");
    }

    /** The running Redis container; Testcontainers' Ryuk removes it when the test run ends. */
    @SuppressWarnings("resource")
    public static synchronized GenericContainer<?> redis() {
        if (redis == null) {
            redis = new GenericContainer<>("redis:8.10.2")
                    .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
                    .withExposedPorts(6379);
            redis.start();
        }
        return redis;
    }

    public static String redisHost() {
        String external = System.getenv("TEST_REDIS_HOST");
        return external != null ? external : redis().getHost();
    }

    public static int redisPort() {
        return System.getenv("TEST_REDIS_HOST") != null ? 6379 : redis().getMappedPort(6379);
    }

    /** Properties pointing the app at the test Redis server. */
    public static Map<String, Object> redisProperties() {
        return Map.of("REDIS_HOST", redisHost(), "REDIS_PORT", redisPort(), "REDIS_PASSWORD", REDIS_PASSWORD);
    }

    public static void useRedis(DynamicPropertyRegistry registry) {
        redisProperties().forEach((name, value) -> registry.add(name, () -> value));
    }

    public static void usePostgres(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", TestDatabases::postgresUrl);
        registry.add("DB_USERNAME", () -> "postgres");
    }
}
