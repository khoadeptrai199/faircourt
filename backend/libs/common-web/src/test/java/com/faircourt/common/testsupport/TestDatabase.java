package com.faircourt.common.testsupport;

import java.util.Arrays;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Guard and reset for integration tests (API foundation AC-07): refuses any database whose name does not end in
 * {@code _test}, and resets only the schema this service's Flyway owns, after checking that the connection's search
 * path holds only that schema.
 */
public final class TestDatabase {

    private TestDatabase() {}

    public static void requireTestDatabase(String databaseName) {
        if (databaseName == null || !databaseName.endsWith("_test")) {
            throw new IllegalStateException(
                    "Integration tests may only clean a *_test database, not '" + databaseName + "'");
        }
    }

    public static void requireOwnSchemaOnly(List<String> flywaySchemas, String currentSchema) {
        if (!flywaySchemas.equals(List.of(currentSchema))) {
            throw new IllegalStateException("Flyway must own exactly the service schema '" + currentSchema
                    + "', but it is configured for " + flywaySchemas);
        }
    }

    /** The connection's search path must hold only the service schema (catches connection overrides). */
    public static void requireSearchPathIsOnly(String searchPath, String schema) {
        List<String> entries = Arrays.stream(searchPath.split(","))
                .map(e -> e.strip().replace("\"", "").replace("'", ""))
                .filter(e -> !e.isEmpty())
                .toList();
        if (!entries.equals(List.of(schema))) {
            throw new IllegalStateException(
                    "search_path must be exactly '" + schema + "', but is '" + searchPath + "'");
        }
    }

    /** Drops and re-creates this service's schema after all checks pass. */
    public static void reset(JdbcClient jdbc, Flyway flyway) {
        requireTestDatabase(
                jdbc.sql("SELECT current_database()").query(String.class).single());
        requireOwnSchemaOnly(
                Arrays.asList(flyway.getConfiguration().getSchemas()),
                jdbc.sql("SELECT current_schema()").query(String.class).single());
        requireSearchPathIsOnly(
                jdbc.sql("SHOW search_path").query(String.class).single(),
                jdbc.sql("SELECT current_schema()").query(String.class).single());
        flyway.clean();
        flyway.migrate();
    }
}
