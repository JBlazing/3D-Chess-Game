package com.jblazing.auth;

import com.jblazing.support.TestDatabases;

import java.util.HashMap;
import java.util.Map;

class SessionPersistencePostgresTest extends SessionPersistenceTests {

    @Override
    Map<String, Object> database() {
        Map<String, Object> properties = new HashMap<>(TestDatabases.redisProperties());
        properties.put("DB_URL", TestDatabases.postgresUrl());
        properties.put("DB_USERNAME", "postgres");
        return properties;
    }
}
