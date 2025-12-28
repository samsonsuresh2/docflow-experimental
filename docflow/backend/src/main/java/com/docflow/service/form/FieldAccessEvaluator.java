package com.docflow.service.form;

import com.docflow.domain.DocumentStatus;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class FieldAccessEvaluator {

    private static final EnumSet<DocumentStatus> EDITABLE_STATUSES =
        EnumSet.of(DocumentStatus.DRAFT, DocumentStatus.REWORK);

    private FieldAccessEvaluator() {
    }

    public static FieldAccessDecision evaluate(UploadFieldDefinition field,
                                               String activeRole,
                                               DocumentStatus currentStatus,
                                               Object currentValue,
                                               boolean visibleIfPasses) {
        boolean visibleByRole = field.getVisibleToRoles().isEmpty() ||
            (activeRole != null && containsIgnoreCase(field.getVisibleToRoles(), activeRole));
        boolean visible = visibleIfPasses && visibleByRole;

        boolean locked = field.isLockAfterFilled() && isValueFilled(currentValue);

        boolean editableByStatus = currentStatus == null || EDITABLE_STATUSES.contains(currentStatus);
        boolean editable = editableByStatus;
        if (!field.getEditableByRoles().isEmpty()) {
            editable = editable && activeRole != null && containsIgnoreCase(field.getEditableByRoles(), activeRole);
        }
        if (locked) {
            editable = false;
        }

        boolean requiredNow;
        if (!field.getRequiredAtStatuses().isEmpty()) {
            requiredNow = visible && matchesStatus(field.getRequiredAtStatuses(), currentStatus);
        } else {
            requiredNow = visible && field.isRequired();
        }

        return new FieldAccessDecision(visible, editable, requiredNow, locked);
    }

    public static boolean isValueFilled(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String str) {
            return !str.trim().isEmpty();
        }
        if (value instanceof Boolean) {
            return true;
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext();
        }
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) > 0;
        }
        return true;
    }

    private static boolean containsIgnoreCase(Set<String> candidates, String value) {
        for (String candidate : candidates) {
            if (candidate != null && candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    public static boolean evaluateVisibleIf(UploadFieldDefinition field, java.util.Map<String, Object> values) {
        VisibleIfCondition condition = field.getVisibleIf();
        if (condition == null || condition.getField() == null || condition.getField().isBlank()) {
            return true;
        }
        Object candidate = values != null ? values.get(condition.getField()) : null;
        if (candidate == null) {
            return true;
        }
        if (candidate instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && condition.getNotIn().contains(String.valueOf(item))) {
                    return false;
                }
            }
            return true;
        }
        return !condition.getNotIn().contains(String.valueOf(candidate));
    }

    private static boolean matchesStatus(Set<String> configuredStatuses, DocumentStatus currentStatus) {
        if (currentStatus == null) {
            return false;
        }
        String normalizedCurrent = normalizeStatus(currentStatus.name());
        for (String configured : configuredStatuses) {
            if (normalizedCurrent.equals(normalizeStatus(configured))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeStatus(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim()
            .replace('-', '_')
            .replace(' ', '_')
            .toUpperCase(Locale.ROOT);
        if ("SUBMITTED".equals(normalized)) {
            return "OPEN";
        }
        return normalized;
    }
}
