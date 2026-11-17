package com.faircourt.player;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.testsupport.SchemaBoundary;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Schema boundary (AD-12): player-service main sources and migrations never reference another service's schema. */
class SchemaBoundaryTest {

    @Test
    void mainSourcesAndMigrationsDoNotReferenceAnotherServicesSchema() {
        assertThat(SchemaBoundary.violations(Path.of("src/main"), "player")).isEmpty();
    }

    @Test
    void checkRecognisesAnotherSchemaButNotItsOwn() {
        assertThat(SchemaBoundary.violationsIn("X.java", "String sql = \"select * from auth.app_user\";", "player"))
                .isNotEmpty();
        assertThat(SchemaBoundary.violationsIn("X.java", "String sql = \"select * from player.attendance\";", "player"))
                .isEmpty();
    }
}
