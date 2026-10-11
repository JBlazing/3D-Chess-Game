package com.jblazing.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The database the tests run against: a real PostgreSQL server (embedded-postgres, started once
 * for the whole test run). The app picks it up through DB_URL, the same way it is pointed at a
 * database in production.
 */
public final class TestDatabases {

    private static EmbeddedPostgres postgres;

    private TestDatabases() {
    }

    public static synchronized String postgresUrl() {
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

    public static void usePostgres(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", TestDatabases::postgresUrl);
        registry.add("DB_USERNAME", () -> "postgres");
    }
}
