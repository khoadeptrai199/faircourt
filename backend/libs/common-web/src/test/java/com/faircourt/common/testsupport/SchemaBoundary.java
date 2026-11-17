package com.faircourt.common.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Schema boundary check (AD-12). All services share one DB user, so the database does not stop a query on another
 * service's schema; this check fails the build instead. It flags, for every schema except the service's own (including
 * the legacy {@code public} schema):
 *
 * <ul>
 *   <li>a schema-qualified name inside SQL text: in migrations and resource files, and in Java string literals and
 *       text blocks ({@code auth.app_user}, {@code "auth"."app_user"}, {@code "auth." + table});
 *   <li>any setting or statement that points a connection or entity at that schema ({@code schema = "auth"},
 *       {@code default_schema=auth}, {@code currentSchema=auth}, {@code hikari.schema=auth}, {@code search_path},
 *       {@code SET/CREATE/ALTER/DROP SCHEMA}).
 * </ul>
 *
 * Plain Java code is not scanned for qualified names, so a lambda such as {@code auth -> auth.requestMatchers(...)} is
 * not a false positive. Connection overrides set outside the module (e.g. Compose env) are covered at runtime by
 * {@link TestDatabase#reset}, which checks the search path.
 */
public final class SchemaBoundary {

    public static final List<String> ALL_SCHEMAS = List.of("auth", "player", "playmatch", "rating", "public");

    private static final Pattern TEXT_BLOCK = Pattern.compile("\"\"\"(.*?)\"\"\"", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:\\\\.|[^\"\\\\\\n])*)\"");

    private SchemaBoundary() {}

    /** Every violation in the files under {@code root} (normally {@code src/main}); empty when clean. */
    public static List<String> violations(Path root, String ownSchema) {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .forEach(file -> hits.addAll(violationsIn(file.toString(), read(file), ownSchema)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return hits;
    }

    /** Violations in one file's content; Java files are checked in their string literals only for qualified names. */
    public static List<String> violationsIn(String fileName, String content, String ownSchema) {
        String others = String.join(
                "|", ALL_SCHEMAS.stream().filter(s -> !s.equals(ownSchema)).toList());
        Pattern qualified = Pattern.compile("(?i)(?<![\\w.$])\"?(?:" + others + ")\"?\\s*\\.");
        Pattern setting = Pattern.compile("(?i)(?:schema[\\w.-]*|search_path)\\s*[=:]\\s*[\"']?(?:" + others
                + ")\\b"
                + "|\\b(?:set|create|alter|drop)\\s+(?:schema|search_path)\\s+(?:if\\s+(?:not\\s+)?exists\\s+|to\\s+)?"
                + "[\"']?(?:" + others + ")\\b");

        List<String> hits = new ArrayList<>();
        String sqlText = fileName.endsWith(".java") ? stringLiterals(content) : content;
        find(qualified, sqlText, fileName, hits);
        find(setting, content, fileName, hits);
        return hits;
    }

    private static String stringLiterals(String java) {
        StringBuilder out = new StringBuilder();
        Matcher blocks = TEXT_BLOCK.matcher(java);
        while (blocks.find()) {
            out.append(unescape(blocks.group(1))).append('\n');
        }
        Matcher literals = STRING_LITERAL.matcher(TEXT_BLOCK.matcher(java).replaceAll(""));
        while (literals.find()) {
            out.append(unescape(literals.group(1))).append('\n');
        }
        return out.toString();
    }

    /** Java escapes that matter for SQL text: {@code \\"} → {@code "}, {@code \\\\} → {@code \\}. */
    private static String unescape(String literal) {
        return literal.replace("\\\\", "\u0000").replace("\\\"", "\"").replace("\u0000", "\\");
    }

    private static void find(Pattern pattern, String text, String fileName, List<String> hits) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            hits.add(fileName + ": " + m.group().strip());
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
