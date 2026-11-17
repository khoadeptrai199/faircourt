package com.faircourt.common.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Privacy check for every non-host response (AD-09): the JSON may contain only the listed field paths. Nested fields
 * are written as {@code parent.child}; fields inside array elements as {@code list[].field}.
 */
public final class JsonAllowlist {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private JsonAllowlist() {}

    public static void assertOnlyFields(String json, String... allowedPaths) {
        Set<String> actual = fieldPaths(json);

        assertThat(actual).as("JSON fields must be a subset of the allowlist").isSubsetOf(Set.of(allowedPaths));
    }

    public static Set<String> fieldPaths(String json) {
        Set<String> paths = new TreeSet<>();
        collect(JSON.readTree(json), "", paths);
        return paths;
    }

    private static void collect(JsonNode node, String prefix, Set<String> paths) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String path = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
                paths.add(path);
                collect(field.getValue(), path, paths);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                collect(element, prefix + "[]", paths);
            }
        }
    }
}
