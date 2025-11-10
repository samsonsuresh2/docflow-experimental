package com.docflow.service.search;

import com.docflow.api.dto.FilterDefinition;
import com.docflow.api.dto.FilterSource;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

public class DocumentSearchFilter {

    private final String key;
    private final FilterSource source;
    private final FilterOperation operation;
    private final String rawValue;
    private final String type;

    private DocumentSearchFilter(String key, FilterSource source, FilterOperation operation, String rawValue, String type) {
        this.key = key;
        this.source = source;
        this.operation = operation;
        this.rawValue = rawValue;
        this.type = type;
    }

    public static DocumentSearchFilter fromDefinition(FilterDefinition definition, Object candidateValue) {
        if (definition == null || definition.getKey() == null || definition.getKey().isBlank() || definition.getSource() == null) {
            return null;
        }
        String raw = normaliseCandidate(candidateValue);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        FilterParseResult parsed = parseRawValue(raw);
        if (parsed == null || parsed.value().isBlank()) {
            return null;
        }
        return new DocumentSearchFilter(
            definition.getKey(),
            definition.getSource(),
            parsed.operation(),
            parsed.value(),
            definition.getType()
        );
    }

    public static DocumentSearchFilter metadataPlaceholder(String key, String value) {
        String raw = normaliseCandidate(value);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        FilterParseResult parsed = parseRawValue(raw);
        if (parsed == null || parsed.value().isBlank()) {
            return null;
        }
        return new DocumentSearchFilter(key, FilterSource.META_DATA, parsed.operation(), parsed.value(), "text");
    }

    private static String normaliseCandidate(Object candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate instanceof String str) {
            String trimmed = str.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (candidate instanceof Number || candidate instanceof Boolean) {
            return candidate.toString();
        }
        if (candidate instanceof Collection<?> collection) {
            for (Object item : collection) {
                String value = normaliseCandidate(item);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }
        if (candidate instanceof Object[] array) {
            for (Object item : array) {
                String value = normaliseCandidate(item);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }
        return candidate.toString();
    }

    private static FilterParseResult parseRawValue(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        FilterOperation operation = FilterOperation.EQUALS;
        String value = trimmed;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("like:")) {
            operation = FilterOperation.LIKE;
            value = trimmed.substring(5).trim();
        } else if (lower.startsWith("like ")) {
            operation = FilterOperation.LIKE;
            value = trimmed.substring(5).trim();
        } else if (trimmed.startsWith(">")) {
            operation = FilterOperation.GREATER_THAN;
            value = trimmed.substring(1).trim();
        } else if (trimmed.startsWith("<")) {
            operation = FilterOperation.LESS_THAN;
            value = trimmed.substring(1).trim();
        } else if (trimmed.startsWith("=")) {
            operation = FilterOperation.EQUALS;
            value = trimmed.substring(1).trim();
        } else if (trimmed.contains("%")) {
            operation = FilterOperation.LIKE;
        }

        if (value.isBlank()) {
            return null;
        }

        return new FilterParseResult(operation, value);
    }

    public String getKey() {
        return key;
    }

    public FilterSource getSource() {
        return source;
    }

    public FilterOperation getOperation() {
        return operation;
    }

    public String getRawValue() {
        return rawValue;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return "DocumentSearchFilter{" +
            "key='" + key + '\'' +
            ", source=" + source +
            ", operation=" + operation +
            ", rawValue='" + rawValue + '\'' +
            ", type='" + type + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DocumentSearchFilter that = (DocumentSearchFilter) o;
        return Objects.equals(key, that.key)
            && source == that.source
            && operation == that.operation
            && Objects.equals(rawValue, that.rawValue)
            && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, source, operation, rawValue, type);
    }

    private record FilterParseResult(FilterOperation operation, String value) {
    }
}
