package com.faircourt.common.testsupport;

import java.util.Arrays;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Guard and reset for integration tests (API foundation AC-07): refuses any database whose name does not end in
 * {@code _test}, and resets only the schema this service's Flyway owns.
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

    /** Drops and re-creates this service's schema after both checks pass. */
    public static void reset(JdbcClient jdbc, Flyway flyway) {
        requireTestDatabase(
                jdbc.sql("SELECT current_database()").query(String.class).single());
        requireOwnSchemaOnly(
                Arrays.asList(flyway.getConfiguration().getSchemas()),
                jdbc.sql("SELECT current_schema()").query(String.class).single());
        flyway.clean();
        flyway.migrate();
    }
}
