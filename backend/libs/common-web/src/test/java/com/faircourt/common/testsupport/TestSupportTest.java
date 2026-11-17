package com.faircourt.common.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TestSupportTest {

    @Test
    void testDatabaseGuardRefusesADatabaseNameWithoutTestSuffix() {
        assertThatThrownBy(() -> TestDatabase.requireTestDatabase("faircourt"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TestDatabase.requireTestDatabase(null)).isInstanceOf(IllegalStateException.class);

        assertThatCode(() -> TestDatabase.requireTestDatabase("faircourt_test")).doesNotThrowAnyException();
    }

    @Test
    void testDatabaseResetsOnlyWhenFlywayOwnsExactlyTheServiceSchema() {
        assertThatThrownBy(() -> TestDatabase.requireOwnSchemaOnly(List.of("auth", "player"), "auth"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TestDatabase.requireOwnSchemaOnly(List.of("public"), "auth"))
                .isInstanceOf(IllegalStateException.class);

        assertThatCode(() -> TestDatabase.requireOwnSchemaOnly(List.of("auth"), "auth"))
                .doesNotThrowAnyException();
    }

    @Test
    void jsonAllowlistListsNestedAndArrayFieldPaths() {
        String json = """
				{"title":"T","courts":[{"courtNo":1}],"price":{"min":1}}""";

        assertThat(JsonAllowlist.fieldPaths(json))
                .containsExactly("courts", "courts[].courtNo", "price", "price.min", "title");
    }

    @Test
    void jsonAllowlistFailsWhenAFieldIsNotAllowed() {
        String json = """
				{"title":"T","hostUserId":"x"}""";

        assertThatThrownBy(() -> JsonAllowlist.assertOnlyFields(json, "title")).isInstanceOf(AssertionError.class);
        assertThatCode(() -> JsonAllowlist.assertOnlyFields(json, "title", "hostUserId"))
                .doesNotThrowAnyException();
    }

    @Test
    void searchPathMustHoldOnlyTheServiceSchema() {
        assertThatCode(() -> TestDatabase.requireSearchPathIsOnly("playmatch", "playmatch"))
                .doesNotThrowAnyException();
        assertThatCode(() -> TestDatabase.requireSearchPathIsOnly("\"playmatch\"", "playmatch"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> TestDatabase.requireSearchPathIsOnly("playmatch, auth", "playmatch"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TestDatabase.requireSearchPathIsOnly("\"$user\", public", "playmatch"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void schemaBoundaryCatchesEveryWayToReachAnotherSchema() {
        String[][] javaSamples = {
            {"Q.java", "String sql = \"select * from auth.app_user\";"},
            {"Q.java", "String sql = \"select * from \\\"auth\\\".\\\"app_user\\\"\";"},
            {"Q.java", "String sql = \"select * from auth.refresh_token\";"},
            {"Q.java", "String sql = \"select * from \" + \"auth.\" + table;"},
            {"Q.java", "String sql = \"\"\"\n    SELECT 1 FROM public.app_user\n    \"\"\";"},
            {"E.java", "@Table(name = \"app_user\", schema = \"auth\")"},
        };
        for (String[] sample : javaSamples) {
            assertThat(SchemaBoundary.violationsIn(sample[0], sample[1], "playmatch"))
                    .as(sample[1])
                    .isNotEmpty();
        }
        String[] configSamples = {
            "spring.jpa.properties.hibernate.default_schema=auth",
            "spring.datasource.url=jdbc:postgresql://db/x?currentSchema=auth",
            "spring.datasource.hikari.schema=auth",
            "SET search_path TO auth;",
            "CREATE SCHEMA IF NOT EXISTS auth;",
        };
        for (String sample : configSamples) {
            assertThat(SchemaBoundary.violationsIn("a.properties", sample, "playmatch"))
                    .as(sample)
                    .isNotEmpty();
        }
    }

    @Test
    void schemaBoundaryIgnoresOwnSchemaAndPlainJavaCode() {
        assertThat(SchemaBoundary.violationsIn(
                        "Q.java", "String sql = \"select * from playmatch.attendance\";", "playmatch"))
                .isEmpty();
        assertThat(SchemaBoundary.violationsIn(
                        "S.java", "http.authorizeHttpRequests(auth -> auth.requestMatchers(\"/x\"));", "playmatch"))
                .isEmpty();
        assertThat(SchemaBoundary.violationsIn("P.java", "String pkg = \"com.faircourt.auth.seed\";", "playmatch"))
                .isEmpty();
        assertThat(SchemaBoundary.violationsIn("a.properties", "spring.flyway.schemas=playmatch", "playmatch"))
                .isEmpty();
    }
}
