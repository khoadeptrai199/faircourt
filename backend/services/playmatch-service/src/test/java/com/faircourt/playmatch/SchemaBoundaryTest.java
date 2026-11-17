package com.faircourt.playmatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.testsupport.SchemaBoundary;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Schema boundary (AD-12): playmatch-service main sources and migrations never reference another service's schema. */
class SchemaBoundaryTest {

    @Test
    void mainSourcesAndMigrationsDoNotReferenceAnotherServicesSchema() {
        assertThat(SchemaBoundary.violations(Path.of("src/main"), "playmatch")).isEmpty();
    }

    @Test
    void checkRecognisesAnotherSchemaButNotItsOwn() {
        assertThat(SchemaBoundary.violationsIn("X.java", "String sql = \"select * from auth.app_user\";", "playmatch"))
                .isNotEmpty();
        assertThat(SchemaBoundary.violationsIn(
                        "X.java", "String sql = \"select * from playmatch.attendance\";", "playmatch"))
                .isEmpty();
    }
}
