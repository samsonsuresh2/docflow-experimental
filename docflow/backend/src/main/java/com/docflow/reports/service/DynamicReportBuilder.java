package com.docflow.reports.service;

import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportFilter;
import com.docflow.reports.dto.ReportJoin;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class DynamicReportBuilder {

    private static final Set<String> ALLOWED_OPERATORS = Set.of("=", "<", ">", "<=", ">=", "like", "between");

    public BuiltReport build(DynamicReportRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request required");
        }
        String baseEntity = requireEntity(request.getBaseEntity(), "baseEntity is required");

        Map<String, EntityPlan> plans = new LinkedHashMap<>();
        EntityPlan basePlan = new EntityPlan(baseEntity, "b");
        plans.put(baseEntity, basePlan);

        List<JoinPlan> joinPlans = buildJoinPlans(request.getJoins(), basePlan, plans);
        List<ColumnProjection> projections = collectColumns(request.getColumns(), basePlan, plans);
        collectFilters(request.getFilters(), basePlan, plans);

        ParameterCollector parameters = new ParameterCollector();
        Map<String, EntityQuery> queries = new LinkedHashMap<>();
        for (EntityPlan plan : plans.values()) {
            queries.put(plan.entity(), buildEntityQuery(plan, parameters));
        }

        List<ColumnSelection> selections = buildSelections(projections);
        String sql = assembleSql(queries, basePlan, joinPlans, selections);

        return new BuiltReport(sql, parameters.asMap(), selections);
    }

    private List<JoinPlan> buildJoinPlans(List<ReportJoin> joins,
                                          EntityPlan basePlan,
                                          Map<String, EntityPlan> plans) {
        List<JoinPlan> joinPlans = new ArrayList<>();
        if (joins == null) {
            return joinPlans;
        }
        int index = 0;
        for (ReportJoin join : joins) {
            String rightEntity = requireEntity(join.getRightEntity(), "Join entity required");
            if (plans.containsKey(rightEntity)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate join entity: " + rightEntity);
            }
            EntityPlan rightPlan = new EntityPlan(rightEntity, "j" + index++);
            plans.put(rightEntity, rightPlan);

            String on = Objects.requireNonNullElse(join.getOn(), "").trim();
            if (on.isEmpty() || !on.contains("=")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Join condition must be left=right");
            }
            String[] parts = on.split("=", 2);
            Reference left = resolveReference(parts[0].trim(), basePlan.entity(), plans);
            Reference right = resolveReference(parts[1].trim(), rightPlan.entity(), plans);
            if (!right.plan().equals(rightPlan)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Join right side must reference " + rightPlan.entity());
            }

            left.plan().ensureKey(left.key());
            right.plan().ensureKey(right.key());
            joinPlans.add(new JoinPlan(left, right));
        }
        return joinPlans;
    }

    private List<ColumnProjection> collectColumns(List<String> columns,
                                                  EntityPlan basePlan,
                                                  Map<String, EntityPlan> plans) {
        List<ColumnProjection> projections = new ArrayList<>();
        projections.add(new ColumnProjection(basePlan, "entity_id", "entity_id"));
        if (columns == null) {
            return projections;
        }
        Set<String> seen = new LinkedHashSet<>();
        seen.add(identifier(basePlan, "entity_id"));
        for (String raw : columns) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Reference ref = resolveReference(raw.trim(), basePlan.entity(), plans);
            ref.plan().ensureKey(ref.key());
            String display = raw.contains(".") ? raw.trim() :
                    (ref.plan().equals(basePlan) ? ref.key() : ref.plan().entity() + "." + ref.key());
            if (seen.add(identifier(ref.plan(), ref.key()))) {
                projections.add(new ColumnProjection(ref.plan(), ref.key(), display));
            }
        }
        return projections;
    }

    private void collectFilters(List<ReportFilter> filters,
                                EntityPlan basePlan,
                                Map<String, EntityPlan> plans) {
        if (filters == null) {
            return;
        }
        for (ReportFilter filter : filters) {
            Reference ref = resolveReference(filter.getKey(), basePlan.entity(), plans);
            String op = normalizeOperator(filter.getOp());
            if (!ALLOWED_OPERATORS.contains(op)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator not allowed: " + filter.getOp());
            }
            ref.plan().ensureKey(ref.key());
            ref.plan().filters().add(new FilterSpec(ref.key(), op, Objects.toString(filter.getValue(), null)));
        }
    }

    private EntityQuery buildEntityQuery(EntityPlan plan, ParameterCollector parameters) {
        String tableAlias = plan.alias() + "_src";
        StringBuilder select = new StringBuilder("SELECT ")
                .append(tableAlias).append(".entity_id AS entity_id");
        Map<String, String> aliasMap = new LinkedHashMap<>();
        int idx = 0;
        for (String key : plan.keys()) {
            String alias = plan.alias() + "_c" + idx++;
            String keyParam = parameters.add(key);
            select.append(", MAX(CASE WHEN ")
                    .append(tableAlias).append(".column_key = :").append(keyParam)
                    .append(" THEN ").append(tableAlias).append(".column_value END) AS ")
                    .append(alias);
            aliasMap.put(key, alias);
        }
        select.append(" FROM ").append(plan.entity()).append(" ").append(tableAlias);
        select.append(" GROUP BY ").append(tableAlias).append(".entity_id");

        List<String> having = new ArrayList<>();
        for (FilterSpec filter : plan.filters()) {
            if ("entity_id".equals(filter.key())) {
                String valueParam = parameters.add(filter.value());
                having.add(tableAlias + ".entity_id " + toSqlOperator(filter, valueParam));
                continue;
            }
            String alias = aliasMap.get(filter.key());
            if (alias == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown key for filtering: " + filter.key());
            }
            having.add(alias + " " + toSqlOperator(filter, parameters));
        }
        if (!having.isEmpty()) {
            select.append(" HAVING ").append(String.join(" AND ", having));
        }
        plan.aliases(aliasMap);
        return new EntityQuery(plan.alias(), select.toString(), aliasMap);
    }

    private List<ColumnSelection> buildSelections(List<ColumnProjection> projections) {
        List<ColumnSelection> selections = new ArrayList<>();
        int idx = 0;
        for (ColumnProjection projection : projections) {
            EntityPlan plan = projection.plan();
            String label;
            if (projection.key().equals("entity_id") && selections.isEmpty()) {
                label = "entity_id";
            } else {
                label = "col" + idx++;
            }
            selections.add(new ColumnSelection(plan.alias(), plan.entity(), projection.key(), label, projection.display()));
        }
        return selections;
    }

    private String assembleSql(Map<String, EntityQuery> queries,
                               EntityPlan basePlan,
                               List<JoinPlan> joinPlans,
                               List<ColumnSelection> selections) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> selectParts = new ArrayList<>();
        for (ColumnSelection selection : selections) {
            String expression = buildColumnExpression(selection, queries);
            selectParts.add(expression + " AS " + selection.label());
        }
        sql.append(String.join(", ", selectParts));
        sql.append(" FROM (").append(queries.get(basePlan.entity()).sql()).append(") ")
                .append(basePlan.alias());

        for (JoinPlan join : joinPlans) {
            EntityQuery rightQuery = queries.get(join.right().plan().entity());
            sql.append(" LEFT JOIN (").append(rightQuery.sql()).append(") ")
                    .append(join.right().plan().alias())
                    .append(" ON ")
                    .append(buildJoinExpression(join.left(), queries))
                    .append(" = ")
                    .append(buildJoinExpression(join.right(), queries));
        }
        return sql.toString();
    }

    private String buildColumnExpression(ColumnSelection selection, Map<String, EntityQuery> queries) {
        EntityQuery query = queries.get(selection.entityName());
        if (selection.key().equals("entity_id")) {
            return selection.entityAlias() + ".entity_id";
        }
        String alias = query.aliases().get(selection.key());
        if (alias == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Column not available: " + selection.key());
        }
        return selection.entityAlias() + "." + alias;
    }

    private String buildJoinExpression(Reference ref, Map<String, EntityQuery> queries) {
        if ("entity_id".equals(ref.key())) {
            return ref.plan().alias() + ".entity_id";
        }
        EntityQuery query = queries.get(ref.plan().entity());
        String alias = query.aliases().get(ref.key());
        if (alias == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Join column not available: " + ref.key());
        }
        return ref.plan().alias() + "." + alias;
    }

    private Reference resolveReference(String value, String defaultEntity, Map<String, EntityPlan> plans) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reference required");
        }
        String entity = defaultEntity;
        String key = normalized;
        if (normalized.contains(".")) {
            int idx = normalized.indexOf('.');
            entity = normalized.substring(0, idx);
            key = normalized.substring(idx + 1);
        }
        EntityPlan plan = plans.get(entity);
        if (plan == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown entity: " + entity);
        }
        return new Reference(plan, key);
    }

    private String toSqlOperator(FilterSpec filter, ParameterCollector collector) {
        return switch (filter.operator()) {
            case "like" -> "LIKE :" + collector.add(filter.value());
            case "between" -> {
                String[] parts = splitBetweenValues(filter.value());
                String first = collector.add(parts[0]);
                String second = collector.add(parts[1]);
                yield "BETWEEN :" + first + " AND :" + second;
            }
            default -> filter.operator().toUpperCase(Locale.ROOT) + " :" + collector.add(filter.value());
        };
    }

    private String toSqlOperator(FilterSpec filter, String parameter) {
        if ("between".equals(filter.operator())) {
            throw new IllegalStateException("Between operator requires collector");
        }
        if ("like".equals(filter.operator())) {
            return "LIKE :" + parameter;
        }
        return filter.operator().toUpperCase(Locale.ROOT) + " :" + parameter;
    }

    private String[] splitBetweenValues(String value) {
        if (value == null || !value.contains(",")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Between requires two comma-separated values");
        }
        String[] parts = value.split(",", 2);
        return new String[]{parts[0].trim(), parts[1].trim()};
    }

    private String normalizeOperator(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String requireEntity(String value, String message) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return trimmed;
    }

    public record BuiltReport(String sql, Map<String, Object> parameters, List<ColumnSelection> columns) {
    }

    public record ColumnSelection(String entityAlias, String entityName, String key, String label, String displayName) {
    }

    private record EntityQuery(String alias, String sql, Map<String, String> aliases) {
    }

    private record JoinPlan(Reference left, Reference right) {
    }

    private record ColumnProjection(EntityPlan plan, String key, String display) {
    }

    private record FilterSpec(String key, String operator, String value) {
    }

    private record Reference(EntityPlan plan, String key) {
    }

    private static class EntityPlan {
        private final String entity;
        private final String alias;
        private final Set<String> keys = new LinkedHashSet<>();
        private final List<FilterSpec> filters = new ArrayList<>();
        private Map<String, String> aliases = Map.of();

        EntityPlan(String entity, String alias) {
            this.entity = entity;
            this.alias = alias;
        }

        String entity() {
            return entity;
        }

        String alias() {
            return alias;
        }

        Set<String> keys() {
            return keys;
        }

        List<FilterSpec> filters() {
            return filters;
        }

        void ensureKey(String key) {
            if (key != null && !key.isBlank() && !"entity_id".equals(key)) {
                keys.add(key);
            }
        }

        void aliases(Map<String, String> aliases) {
            this.aliases = aliases;
        }

        Map<String, String> aliases() {
            return aliases;
        }
    }

    private static class ParameterCollector {
        private final Map<String, Object> values = new LinkedHashMap<>();

        String add(Object value) {
            String name = "p" + values.size();
            values.put(name, value);
            return name;
        }

        Map<String, Object> asMap() {
            return values;
        }
    }

    private String identifier(EntityPlan plan, String key) {
        return plan.alias() + ":" + key;
    }
}
