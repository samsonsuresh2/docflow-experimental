package com.docflow.service.form;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class UploadFieldConfigParser {

    private final ObjectMapper objectMapper;

    public UploadFieldConfigParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<UploadFieldDefinition> parse(String rawConfig) {
        if (rawConfig == null || rawConfig.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(rawConfig);
            JsonNode fieldsNode;
            if (root.isArray()) {
                fieldsNode = root;
            } else if (root.has("fields") && root.get("fields").isArray()) {
                fieldsNode = root.get("fields");
            } else {
                return List.of();
            }

            List<UploadFieldDefinition> results = new ArrayList<>();
            for (JsonNode node : fieldsNode) {
                UploadFieldDefinition definition = parseField(node);
                if (definition != null) {
                    results.add(definition);
                }
            }
            return results;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private UploadFieldDefinition parseField(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String name = text(node, "name");
        if (name == null || name.isBlank()) {
            return null;
        }

        UploadFieldDefinition definition = new UploadFieldDefinition();
        definition.setName(name);
        definition.setLabel(text(node, "label", name));
        definition.setType(text(node, "type"));
        definition.setPlaceholder(text(node, "placeholder"));
        definition.setRequired(node.path("required").asBoolean(false));
        definition.setReadOnly(node.path("readOnly").asBoolean(false));
        definition.setLockAfterFilled(node.path("lockAfterFilled").asBoolean(false));
        definition.setNotificationTeamRouting(node.path("notificationTeamRouting").asBoolean(false));
        definition.setVisibleToRoles(stringSet(node, "visibleToRoles", stringSet(node, "roles", Set.of())));
        definition.setEditableByRoles(stringSet(node, "editableByRoles", Set.of()));
        definition.setRequiredAtStatuses(stringSet(node, "requiredAtStatuses", Set.of()));

        if (node.has("visibleIf") && node.get("visibleIf").isObject()) {
            JsonNode visibleIfNode = node.get("visibleIf");
            String field = text(visibleIfNode, "field");
            if (field != null && !field.isBlank()) {
                VisibleIfCondition condition = new VisibleIfCondition();
                condition.setField(field);
                condition.setNotIn(stringSet(visibleIfNode, "notIn", Set.of(), false));
                definition.setVisibleIf(condition);
            }
        }

        return definition;
    }

    private String text(JsonNode node, String field) {
        return text(node, field, null);
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode valueNode = node.get(field);
        if (valueNode == null || valueNode.isNull()) {
            return defaultValue;
        }
        String text = valueNode.asText();
        return text != null ? text : defaultValue;
    }

    private Set<String> stringSet(JsonNode node, String field, Set<String> defaultValue) {
        return stringSet(node, field, defaultValue, true);
    }

    private Set<String> stringSet(JsonNode node, String field, Set<String> defaultValue, boolean upperCase) {
        JsonNode valueNode = node.get(field);
        if (valueNode == null) {
            return defaultValue;
        }
        Set<String> result = new LinkedHashSet<>();
        if (valueNode.isArray()) {
            for (JsonNode item : valueNode) {
                if (item != null && !item.isNull()) {
                    String text = item.asText();
                    if (text != null && !text.isBlank()) {
                        result.add(text.toUpperCase(Locale.ROOT));
                    }
                }
            }
        } else if (valueNode.isTextual() && !valueNode.asText().isBlank()) {
            result.add(normalize(valueNode.asText(), upperCase));
        }

        return result.isEmpty() ? defaultValue : result;
    }

    private String normalize(String value, boolean upperCase) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (upperCase) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        return trimmed;
    }
}
