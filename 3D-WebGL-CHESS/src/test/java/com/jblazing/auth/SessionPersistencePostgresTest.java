package com.jblazing.auth;

import com.jblazing.support.TestDatabases;

import java.util.Map;

class SessionPersistencePostgresTest extends SessionPersistenceTests {

    @Override
    Map<String, Object> database() {
        return Map.of("DB_URL", TestDatabases.postgresUrl(), "DB_USERNAME", "postgres");
    }
}
