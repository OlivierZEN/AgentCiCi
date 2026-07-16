package com.codehouse.ciciassistant.ontology.adapter;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class InlineSampleOntologyAdapter implements OntologyDataSourceAdapter {

    static final int MAX_PAYLOAD_BYTES = 256 * 1024;
    static final int MAX_OBJECTS = 50;
    static final int MAX_ROWS = 2_000;
    static final int MAX_FIELDS_PER_OBJECT = 128;
    private static final int MAX_QUERY_LIMIT = 200;

    private final ObjectMapper objectMapper;

    public InlineSampleOntologyAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(DataSourceConfig source) {
        return source != null && source.type() == OntologyDocument.SourceType.INLINE_SAMPLE;
    }

    @Override
    public List<PhysicalObject> discoverObjects(
            AdapterContext context,
            DataSourceConfig source) {
        JsonNode root = sampleRoot(source);
        List<PhysicalObject> result = new ArrayList<>();
        root.fields().forEachRemaining(entry -> {
            requireArray(entry.getKey(), entry.getValue());
            result.add(new PhysicalObject(
                    entry.getKey(),
                    entry.getKey(),
                    "INLINE_OBJECT",
                    "{\"rowCount\":" + entry.getValue().size() + "}"));
        });
        return List.copyOf(result);
    }

    @Override
    public List<PhysicalField> discoverFields(
            AdapterContext context,
            DataSourceConfig source,
            String objectKey) {
        JsonNode rows = requireObjectRows(sampleRoot(source), objectKey);
        Map<String, JsonNode> examples = new LinkedHashMap<>();
        for (JsonNode row : rows) {
            if (!row.isObject()) {
                throw new IllegalArgumentException("INLINE_SAMPLE_ROW_MUST_BE_OBJECT");
            }
            row.fields().forEachRemaining(entry -> examples.putIfAbsent(
                    entry.getKey(), entry.getValue()));
            if (examples.size() > MAX_FIELDS_PER_OBJECT) {
                throw new IllegalArgumentException("INLINE_SAMPLE_FIELD_LIMIT_EXCEEDED");
            }
        }
        return examples.entrySet().stream()
                .map(entry -> new PhysicalField(
                        objectKey,
                        entry.getKey(),
                        entry.getKey(),
                        dataType(entry.getValue()),
                        true,
                        entry.getValue() != null && entry.getValue().isArray(),
                        "{}"))
                .toList();
    }

    @Override
    public MappingValidation validateMapping(
            AdapterContext context,
            DataSourceConfig source,
            OntologyDocument.Mapping mapping) {
        if (mapping == null) {
            return MappingValidation.invalid("MAPPING_REQUIRED", "Mapping is required");
        }
        try {
            List<PhysicalObject> objects = discoverObjects(context, source);
            boolean objectExists = objects.stream()
                    .anyMatch(object -> Objects.equals(object.key(), mapping.physicalObjectKey()));
            if (!objectExists) {
                return MappingValidation.invalid(
                        "PHYSICAL_OBJECT_NOT_FOUND", "Mapped object was not discovered");
            }
            if (mapping.physicalFieldKey() == null || mapping.physicalFieldKey().isBlank()) {
                return "CONCEPT".equalsIgnoreCase(mapping.targetType())
                        ? MappingValidation.success()
                        : MappingValidation.invalid(
                                "PHYSICAL_FIELD_REQUIRED", "Mapped field is required");
            }
            boolean fieldExists = discoverFields(
                    context, source, mapping.physicalObjectKey()).stream()
                    .anyMatch(field -> Objects.equals(field.key(), mapping.physicalFieldKey()));
            return fieldExists
                    ? MappingValidation.success()
                    : MappingValidation.invalid(
                            "PHYSICAL_FIELD_NOT_FOUND", "Mapped field was not discovered");
        } catch (IllegalArgumentException exception) {
            return MappingValidation.invalid("INLINE_SAMPLE_INVALID", exception.getMessage());
        }
    }

    @Override
    public PhysicalResult executeRead(
            AdapterContext context,
            DataSourceConfig source,
            PhysicalQuery query) {
        requireBudget(query);
        JsonNode rows = requireObjectRows(sampleRoot(source), query.objectKey());
        Set<String> discoveredFields = new LinkedHashSet<>();
        for (JsonNode row : rows) {
            if (!row.isObject()) {
                throw new IllegalArgumentException("INLINE_SAMPLE_ROW_MUST_BE_OBJECT");
            }
            row.fieldNames().forEachRemaining(discoveredFields::add);
            if (discoveredFields.size() > MAX_FIELDS_PER_OBJECT) {
                throw new IllegalArgumentException("INLINE_SAMPLE_FIELD_LIMIT_EXCEEDED");
            }
        }
        requireAllowedFields(query, discoveredFields);

        List<Map<String, Object>> matched = new ArrayList<>();
        for (JsonNode rowNode : rows) {
            Map<String, Object> row = objectMapper.convertValue(rowNode, Map.class);
            if (query.filters().stream().allMatch(filter -> matches(row, filter))) {
                matched.add(row);
            }
        }
        sort(matched, query.orderBy());
        int total = matched.size();
        List<Map<String, Object>> projected = matched.stream()
                .limit(query.limit())
                .map(row -> project(row, query.fields()))
                .toList();
        return new PhysicalResult(projected, total);
    }

    private JsonNode sampleRoot(DataSourceConfig source) {
        if (!supports(source)) {
            throw new IllegalArgumentException("INLINE_SAMPLE_SOURCE_REQUIRED");
        }
        String json = hasText(source.sampleDataJson())
                ? source.sampleDataJson()
                : source.configJson();
        if (!hasText(json)) {
            throw new IllegalArgumentException("INLINE_SAMPLE_DATA_REQUIRED");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("INLINE_SAMPLE_PAYLOAD_LIMIT_EXCEEDED");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("INLINE_SAMPLE_OBJECT_OF_ARRAYS_REQUIRED");
            }
            if (root.size() > MAX_OBJECTS) {
                throw new IllegalArgumentException("INLINE_SAMPLE_OBJECT_LIMIT_EXCEEDED");
            }
            int rowCount = 0;
            var values = root.fields();
            while (values.hasNext()) {
                Map.Entry<String, JsonNode> entry = values.next();
                requireArray(entry.getKey(), entry.getValue());
                rowCount += entry.getValue().size();
                if (rowCount > MAX_ROWS) {
                    throw new IllegalArgumentException("INLINE_SAMPLE_ROW_LIMIT_EXCEEDED");
                }
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("INLINE_SAMPLE_JSON_INVALID", exception);
        }
    }

    private JsonNode requireObjectRows(JsonNode root, String objectKey) {
        if (!hasText(objectKey)) {
            throw new IllegalArgumentException("PHYSICAL_OBJECT_REQUIRED");
        }
        JsonNode rows = root.get(objectKey);
        if (rows == null) {
            throw new IllegalArgumentException("PHYSICAL_OBJECT_NOT_FOUND");
        }
        requireArray(objectKey, rows);
        return rows;
    }

    private void requireArray(String objectKey, JsonNode value) {
        if (!hasText(objectKey) || value == null || !value.isArray()) {
            throw new IllegalArgumentException("INLINE_SAMPLE_OBJECT_OF_ARRAYS_REQUIRED");
        }
    }

    private void requireBudget(PhysicalQuery query) {
        if (query == null || query.limit() < 1 || query.limit() > MAX_QUERY_LIMIT) {
            throw new IllegalArgumentException("QUERY_BUDGET_EXCEEDED");
        }
        if (query.fields().isEmpty()) {
            throw new IllegalArgumentException("QUERY_FIELDS_REQUIRED");
        }
    }

    private void requireAllowedFields(PhysicalQuery query, Set<String> discoveredFields) {
        Set<String> requested = new LinkedHashSet<>(query.fields());
        query.filters().forEach(filter -> requested.add(filter.field()));
        query.orderBy().forEach(order -> requested.add(order.field()));
        if (requested.stream().anyMatch(field -> !discoveredFields.contains(field))) {
            throw new IllegalArgumentException("PHYSICAL_FIELD_NOT_ALLOWED");
        }
        if (query.filters().stream().anyMatch(filter -> filter.operator() == null)) {
            throw new IllegalArgumentException("QUERY_OPERATOR_NOT_ALLOWED");
        }
    }

    private boolean matches(Map<String, Object> row, PhysicalFilter filter) {
        Object actual = row.get(filter.field());
        Object expected = filter.value();
        return switch (filter.operator()) {
            case EQ -> equivalent(actual, expected);
            case NE -> !equivalent(actual, expected);
            case IN -> asCollection(expected).stream()
                    .anyMatch(candidate -> equivalent(actual, candidate));
            case CONTAINS -> contains(actual, expected);
            case GT -> compare(actual, expected) > 0;
            case GTE -> compare(actual, expected) >= 0;
            case LT -> compare(actual, expected) < 0;
            case LTE -> compare(actual, expected) <= 0;
            case BETWEEN -> {
                List<?> bounds = asCollection(expected);
                if (bounds.size() != 2) {
                    throw new IllegalArgumentException("BETWEEN_REQUIRES_TWO_VALUES");
                }
                yield compare(actual, bounds.get(0)) >= 0
                        && compare(actual, bounds.get(1)) <= 0;
            }
            case IS_NULL -> actual == null;
        };
    }

    private boolean equivalent(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            return decimal(left).compareTo(decimal(right)) == 0;
        }
        return Objects.equals(left, right);
    }

    private boolean contains(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return false;
        }
        if (actual instanceof Collection<?> values) {
            return values.stream().anyMatch(value -> equivalent(value, expected));
        }
        return String.valueOf(actual).contains(String.valueOf(expected));
    }

    private int compare(Object left, Object right) {
        if (left == null || right == null) {
            return left == right ? 0 : left == null ? -1 : 1;
        }
        if (left instanceof Number && right instanceof Number) {
            return decimal(left).compareTo(decimal(right));
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    private List<?> asCollection(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof Collection<?> collection) {
            return List.copyOf(collection);
        }
        throw new IllegalArgumentException("QUERY_LIST_VALUE_REQUIRED");
    }

    private void sort(List<Map<String, Object>> rows, List<PhysicalOrder> orderBy) {
        Comparator<Map<String, Object>> comparator = null;
        for (PhysicalOrder order : orderBy) {
            Comparator<Map<String, Object>> fieldComparator = (left, right) ->
                    compare(left.get(order.field()), right.get(order.field()));
            if (order.direction() == Direction.DESC) {
                fieldComparator = fieldComparator.reversed();
            }
            comparator = comparator == null
                    ? fieldComparator
                    : comparator.thenComparing(fieldComparator);
        }
        if (comparator != null) {
            rows.sort(comparator);
        }
    }

    private Map<String, Object> project(Map<String, Object> row, List<String> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        fields.forEach(field -> result.put(field, row.get(field)));
        return result;
    }

    private String dataType(JsonNode value) {
        if (value == null || value.isNull()) {
            return "UNKNOWN";
        }
        if (value.isBoolean()) {
            return "BOOLEAN";
        }
        if (value.isIntegralNumber()) {
            return "INTEGER";
        }
        if (value.isFloatingPointNumber()) {
            return "DECIMAL";
        }
        if (value.isArray()) {
            return "ARRAY";
        }
        if (value.isObject()) {
            return "OBJECT";
        }
        return "TEXT";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
