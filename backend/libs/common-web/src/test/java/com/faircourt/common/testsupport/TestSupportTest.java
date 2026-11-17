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
}
