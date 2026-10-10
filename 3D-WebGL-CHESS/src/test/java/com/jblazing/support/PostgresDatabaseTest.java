package com.jblazing.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base for tests on a real (embedded) PostgreSQL server. */
public abstract class PostgresDatabaseTest extends DatabaseTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabases.usePostgres(registry);
    }
}
