package com.jblazing.db;

import com.jblazing.support.TestSupport;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Liquibase builds the same schema on every database, from the changesets in db/changelog. */
interface MigrationTests extends TestSupport {

    private Map<String, String> applied() throws SQLException {
        Map<String, String> rows = new LinkedHashMap<>();
        try (Connection c = dataSource().getConnection();
             ResultSet rs = c.createStatement().executeQuery("select id, filename from databasechangelog order by orderexecuted")) {
            while (rs.next()) rows.put(rs.getString("id"), rs.getString("filename"));
        }
        return rows;
    }

    private Map<String, String> columns(String table) throws SQLException {
        Map<String, String> types = new LinkedHashMap<>();
        try (Connection c = dataSource().getConnection(); ResultSet rs = c.getMetaData().getColumns(null, null, table, null)) {
            while (rs.next()) types.put(rs.getString("COLUMN_NAME").toLowerCase(), rs.getString("TYPE_NAME").toLowerCase());
        }
        return types;
    }

    @Test
    default void everyChangesetIsApplied() throws SQLException {
        // the ratings change has one variant for SQLite (plain ALTER TABLE) and one for everything else
        String ratings = isPostgres() ? "3-elo-ratings" : "3-elo-ratings-sqlite";
        assertThat(applied().keySet()).containsExactly("1-create-user-account", "2-create-game", ratings, "4-spring-session");
    }

    @Test
    default void changesetsMovedOutOfTheMasterKeepTheirOriginalPath() throws SQLException {
        // 1-3 were first applied from db.changelog-master.yaml; databases that ran them must not run them again
        Map<String, String> applied = applied();
        List<String> moved = new ArrayList<>(applied.keySet()).subList(0, 3);
        moved.forEach(id -> assertThat(applied.get(id)).as(id).isEqualTo("db/changelog/db.changelog-master.yaml"));
        assertThat(applied.get("4-spring-session")).isEqualTo("db/changelog/changes/004-spring-session.yaml");
    }

    @Test
    default void userAccountTable() throws SQLException {
        assertThat(columns("user_account")).containsOnlyKeys("id", "provider", "provider_subject", "email", "name",
                "picture_url", "created_at", "last_login_at", "rating", "rated_games");
    }

    @Test
    default void gameTable() throws SQLException {
        assertThat(columns("game")).containsOnlyKeys("id", "white_player", "black_player", "white_account_id",
                "black_account_id", "status", "winner", "fen", "moves", "created_at", "updated_at", "rated",
                "white_rating", "black_rating", "white_rating_change", "black_rating_change");
    }

    @Test
    default void typesThatDifferByDatabase() throws SQLException {
        Map<String, String> account = columns("user_account");
        Map<String, String> session = columns("spring_session_attributes");
        if (isPostgres()) {
            assertThat(account).containsEntry("id", "int8").containsEntry("created_at", "timestamptz");
            // BYTEA, not the OID large-object reference Liquibase would make of BLOB
            assertThat(session).containsEntry("attribute_bytes", "bytea");
        } else {
            // SQLite: AUTOINCREMENT needs exactly INTEGER; timestamps are epoch millis
            assertThat(account).containsEntry("id", "integer").containsEntry("created_at", "integer");
            assertThat(session).containsEntry("attribute_bytes", "blob");
        }
    }

    @Test
    default void sessionTables() throws SQLException {
        assertThat(columns("spring_session")).containsOnlyKeys("primary_id", "session_id", "creation_time",
                "last_access_time", "max_inactive_interval", "expiry_time", "principal_name");
        assertThat(columns("spring_session_attributes")).containsOnlyKeys("session_primary_id", "attribute_name", "attribute_bytes");
    }
}
