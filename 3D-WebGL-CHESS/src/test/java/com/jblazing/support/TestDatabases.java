package com.jblazing.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The databases the tests run against: a real PostgreSQL server (embedded-postgres, started once
 * for the whole test run) and a temporary SQLite file. The app picks one up through DB_URL, the
 * same way it is pointed at a database in production.
 */
public final class TestDatabases {

    private static EmbeddedPostgres postgres;
    private static Path sqliteFile;

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

    /** Same connection options as the app's default SQLite URL. */
    public static String sqliteUrl(Path file) {
        return "jdbc:sqlite:" + file.toAbsolutePath() + "?journal_mode=WAL&busy_timeout=5000&transaction_mode=IMMEDIATE&foreign_keys=true";
    }

    public static synchronized Path sharedSqliteFile() {
        if (sqliteFile == null) {
            try {
                Path dir = Files.createTempDirectory("chess-test-db");
                sqliteFile = dir.resolve("chess.db");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return sqliteFile;
    }

    public static void usePostgres(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", TestDatabases::postgresUrl);
        registry.add("DB_USERNAME", () -> "postgres");
    }

    public static void useSqlite(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> sqliteUrl(sharedSqliteFile()));
    }
}
