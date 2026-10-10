package com.jblazing.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base for tests on SQLite, the default database. */
public abstract class SqliteDatabaseTest extends DatabaseTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabases.useSqlite(registry);
    }
}
