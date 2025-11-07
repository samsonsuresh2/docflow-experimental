package com.docflow.domain.repository;

import com.docflow.domain.DocumentMetadata;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.service.search.DocumentSearchFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class DocumentRepositoryImpl implements DocumentRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<DocumentParent> searchDocuments(String documentNumber,
                                                DocumentStatus status,
                                                List<DocumentSearchFilter> dynamicFilters,
                                                Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<DocumentParent> query = cb.createQuery(DocumentParent.class);
        Root<DocumentParent> root = query.from(DocumentParent.class);
        query.select(root).distinct(true);

        List<Predicate> predicates = buildPredicates(cb, query, root, documentNumber, status, dynamicFilters);
        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(Predicate[]::new));
        }
        applySorting(cb, query, root, pageable.getSort());

        TypedQuery<DocumentParent> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<DocumentParent> content = typedQuery.getResultList();

        long total = count(documentNumber, status, dynamicFilters);

        return new PageImpl<>(content, pageable, total);
    }

    private long count(String documentNumber, DocumentStatus status, List<DocumentSearchFilter> dynamicFilters) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<DocumentParent> root = countQuery.from(DocumentParent.class);
        countQuery.select(cb.countDistinct(root));

        List<Predicate> predicates = buildPredicates(cb, countQuery, root, documentNumber, status, dynamicFilters);
        if (!predicates.isEmpty()) {
            countQuery.where(predicates.toArray(Predicate[]::new));
        }

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<Predicate> buildPredicates(CriteriaBuilder cb,
                                            CriteriaQuery<?> query,
                                            Root<DocumentParent> root,
                                            String documentNumber,
                                            DocumentStatus status,
                                            List<DocumentSearchFilter> dynamicFilters) {
        List<Predicate> predicates = new ArrayList<>();
        if (documentNumber != null && !documentNumber.isBlank()) {
            String likePattern = "%" + documentNumber.toLowerCase(Locale.ROOT) + "%";
            predicates.add(cb.like(cb.lower(root.get("documentNumber")), likePattern));
        }
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }

        if (dynamicFilters != null) {
            for (DocumentSearchFilter filter : dynamicFilters) {
                if (filter == null || filter.getRawValue() == null) {
                    continue;
                }
                switch (filter.getSource()) {
                    case DOCUMENT_PARENT -> buildDocumentPredicate(cb, root, filter).ifPresent(predicates::add);
                    case META_DATA -> buildMetadataPredicate(cb, query, root, filter).ifPresent(predicates::add);
                    default -> {
                    }
                }
            }
        }
        return predicates;
    }

    private Optional<Predicate> buildDocumentPredicate(CriteriaBuilder cb,
                                                       Root<DocumentParent> root,
                                                       DocumentSearchFilter filter) {
        javax.persistence.criteria.Path<?> path;
        try {
            path = root.get(filter.getKey());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        Class<?> javaType = path.getJavaType();
        String rawValue = filter.getRawValue();
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }

        return switch (filter.getOperation()) {
            case LIKE -> buildLikePredicate(cb, path.as(String.class), rawValue);
            case GREATER_THAN -> buildComparisonPredicate(cb, path, javaType, rawValue, true);
            case LESS_THAN -> buildComparisonPredicate(cb, path, javaType, rawValue, false);
            case EQUALS -> buildEqualsPredicate(cb, path, javaType, rawValue);
        };
    }

    private Optional<Predicate> buildEqualsPredicate(CriteriaBuilder cb,
                                                     javax.persistence.criteria.Path<?> path,
                                                     Class<?> javaType,
                                                     String rawValue) {
        Object converted = convertValue(rawValue, javaType, false);
        if (converted == null) {
            return Optional.empty();
        }
        if (converted instanceof String stringValue) {
            Expression<String> expression = cb.lower(path.as(String.class));
            return Optional.of(cb.equal(expression, stringValue.toLowerCase(Locale.ROOT)));
        }
        return Optional.of(cb.equal(path, converted));
    }

    private Optional<Predicate> buildLikePredicate(CriteriaBuilder cb,
                                                   Expression<String> expression,
                                                   String rawValue) {
        String lowered = rawValue.toLowerCase(Locale.ROOT);
        String pattern = lowered.contains("%") ? lowered : "%" + lowered + "%";
        return Optional.of(cb.like(cb.lower(expression), pattern));
    }

    private Optional<Predicate> buildComparisonPredicate(CriteriaBuilder cb,
                                                         javax.persistence.criteria.Path<?> path,
                                                         Class<?> javaType,
                                                         String rawValue,
                                                         boolean greaterThan) {
        Object converted = convertValue(rawValue, javaType, true);
        if (!(converted instanceof Comparable<?> comparable)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        javax.persistence.criteria.Path<? extends Comparable<Object>> comparablePath =
            (javax.persistence.criteria.Path<? extends Comparable<Object>>) path;
        if (greaterThan) {
            return Optional.of(cb.greaterThan(comparablePath, comparable));
        }
        return Optional.of(cb.lessThan(comparablePath, comparable));
    }

    private Optional<Predicate> buildMetadataPredicate(CriteriaBuilder cb,
                                                       CriteriaQuery<?> query,
                                                       Root<DocumentParent> root,
                                                       DocumentSearchFilter filter) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<DocumentMetadata> metadataRoot = subquery.from(DocumentMetadata.class);
        subquery.select(metadataRoot.get("document").get("id"));

        Predicate matchDocument = cb.equal(metadataRoot.get("document").get("id"), root.get("id"));
        Predicate matchKey = cb.equal(cb.lower(metadataRoot.get("fieldKey")), filter.getKey().toLowerCase(Locale.ROOT));

        Predicate valuePredicate = buildMetadataValuePredicate(cb, metadataRoot.get("fieldValue"), filter);
        if (valuePredicate == null) {
            return Optional.empty();
        }

        subquery.where(matchDocument, matchKey, valuePredicate);
        return Optional.of(cb.exists(subquery));
    }

    private Predicate buildMetadataValuePredicate(CriteriaBuilder cb,
                                                  javax.persistence.criteria.Path<String> valuePath,
                                                  DocumentSearchFilter filter) {
        String rawValue = filter.getRawValue();
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String lowered = rawValue.toLowerCase(Locale.ROOT);
        Expression<String> expression = cb.lower(valuePath);
        return switch (filter.getOperation()) {
            case LIKE -> {
                String pattern = lowered.contains("%") ? lowered : "%" + lowered + "%";
                yield cb.like(expression, pattern);
            }
            case GREATER_THAN -> cb.greaterThan(expression, lowered);
            case LESS_THAN -> cb.lessThan(expression, lowered);
            case EQUALS -> cb.equal(expression, lowered);
        };
    }

    private Object convertValue(String rawValue, Class<?> javaType, boolean truncateToStartOfDay) {
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (OffsetDateTime.class.isAssignableFrom(javaType)) {
            try {
                OffsetDateTime parsed = OffsetDateTime.parse(trimmed);
                return truncateToStartOfDay ? parsed.truncatedTo(ChronoUnit.SECONDS) : parsed;
            } catch (DateTimeParseException ex) {
                try {
                    OffsetDateTime midnight = OffsetDateTime.parse(trimmed + "T00:00:00Z");
                    return truncateToStartOfDay ? midnight.truncatedTo(ChronoUnit.SECONDS) : midnight;
                } catch (DateTimeParseException ignored) {
                    return null;
                }
            }
        }
        if (Enum.class.isAssignableFrom(javaType) && DocumentStatus.class.isAssignableFrom(javaType)) {
            try {
                return DocumentStatus.valueOf(trimmed.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        if (Long.class.isAssignableFrom(javaType) || long.class.isAssignableFrom(javaType)) {
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (Integer.class.isAssignableFrom(javaType) || int.class.isAssignableFrom(javaType)) {
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (Boolean.class.isAssignableFrom(javaType) || boolean.class.isAssignableFrom(javaType)) {
            return Boolean.parseBoolean(trimmed);
        }
        return trimmed;
    }

    private void applySorting(CriteriaBuilder cb,
                              CriteriaQuery<DocumentParent> query,
                              Root<DocumentParent> root,
                              Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return;
        }
        List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            if (!order.isIgnoreCase()) {
                try {
                    jakarta.persistence.criteria.Path<?> path = root.get(order.getProperty());
                    orders.add(order.isAscending() ? cb.asc(path) : cb.desc(path));
                } catch (IllegalArgumentException ignored) {
                }
            } else {
                try {
                    Expression<String> expression = cb.lower(root.get(order.getProperty()));
                    orders.add(order.isAscending() ? cb.asc(expression) : cb.desc(expression));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (!orders.isEmpty()) {
            query.orderBy(orders);
        }
    }
}
