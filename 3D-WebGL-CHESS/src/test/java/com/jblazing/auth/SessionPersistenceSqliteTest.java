package com.jblazing.auth;

import com.jblazing.support.TestDatabases;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

class SessionPersistenceSqliteTest extends SessionPersistenceTests {

    @TempDir
    Path dir;

    @Override
    Map<String, Object> database() {
        return Map.of("DB_URL", TestDatabases.sqliteUrl(dir.resolve("sessions.db")));
    }
}
