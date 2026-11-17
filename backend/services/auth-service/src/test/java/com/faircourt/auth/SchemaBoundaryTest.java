package com.faircourt.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Schema boundary (AD-12, F18 AC-05): the services share one DB user, so the database does not stop a query on
 * another service's schema. This test fails the build when this service's main sources or migrations reference
 * another service's schema (player|playmatch|rating) by a schema-qualified table name, a schema statement or a search_path.
 */
class SchemaBoundaryTest {

    private static final String OTHER_SCHEMAS = "player|playmatch|rating";
    private static final String TABLES = "app_user|guest_access|player_profile|skill_assessment|play_session"
            + "|attendance|play_match|match_player|match_feedback|flyway_schema_history";

    static final Pattern FORBIDDEN =
            Pattern.compile("(?i)\\b(?:" + OTHER_SCHEMAS + ")\\s*\\.\\s*\"?(?:" + TABLES + ")\\b"
                    + "|\\b(?:create|drop|alter|set)\\s+schema\\s+(?:if\\s+(?:not\\s+)?exists\\s+)?\"?(?:"
                    + OTHER_SCHEMAS + ")\\b"
                    + "|search_path\\s*(?:=|to)\\s*[\"']?(?:" + OTHER_SCHEMAS + ")\\b"
                    + "|\\bschemas?\\s*=\\s*(?:" + OTHER_SCHEMAS + ")\\b");

    @Test
    void mainSourcesAndMigrationsDoNotReferenceAnotherServicesSchema() throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main"))) {
            files.filter(Files::isRegularFile).forEach(file -> {
                Matcher m = FORBIDDEN.matcher(read(file));
                while (m.find()) {
                    hits.add(file + ": " + m.group());
                }
            });
        }

        assertThat(hits).isEmpty();
    }

    @Test
    void checkRecognisesAQueryOnAnotherServicesSchemaButNotOnItsOwn() {
        assertThat(FORBIDDEN.matcher("select * from player.app_user").find()).isTrue();
        assertThat(FORBIDDEN.matcher("SET search_path TO player").find()).isTrue();

        assertThat(FORBIDDEN.matcher("select * from auth.attendance").find()).isFalse();
        assertThat(FORBIDDEN
                        .matcher("auth -> auth.requestMatchers(\"/actuator/health\")")
                        .find())
                .isFalse();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
