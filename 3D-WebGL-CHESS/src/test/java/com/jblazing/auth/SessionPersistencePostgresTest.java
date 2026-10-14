package com.jblazing.auth;

import com.jblazing.support.TestDatabases;
import com.jblazing.support.TestEngine;

import java.util.HashMap;
import java.util.Map;

class SessionPersistencePostgresTest extends SessionPersistenceTests {

    @Override
    Map<String, Object> database() {
        Map<String, Object> properties = new HashMap<>(TestDatabases.redisProperties());
        properties.put("DB_URL", TestDatabases.postgresUrl());
        properties.put("DB_USERNAME", "postgres");
        properties.putAll(TestEngine.properties());
        return properties;
    }
}
