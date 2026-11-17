package com.faircourt.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.testsupport.SchemaBoundary;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Schema boundary (AD-12): auth-service main sources and migrations never reference another service's schema. */
class SchemaBoundaryTest {

    @Test
    void mainSourcesAndMigrationsDoNotReferenceAnotherServicesSchema() {
        assertThat(SchemaBoundary.violations(Path.of("src/main"), "auth")).isEmpty();
    }

    @Test
    void checkRecognisesAnotherSchemaButNotItsOwn() {
        assertThat(SchemaBoundary.violationsIn("X.java", "String sql = \"select * from player.app_user\";", "auth"))
                .isNotEmpty();
        assertThat(SchemaBoundary.violationsIn("X.java", "String sql = \"select * from auth.attendance\";", "auth"))
                .isEmpty();
    }
}
