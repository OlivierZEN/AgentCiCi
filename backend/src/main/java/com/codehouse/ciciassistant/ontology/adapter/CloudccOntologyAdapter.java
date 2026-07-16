package com.codehouse.ciciassistant.ontology.adapter;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CloudccOntologyAdapter implements OntologyDataSourceAdapter {

    private static final String ADAPTER_KEY = "cloudcc";
    private static final int MAX_QUERY_LIMIT = 200;
    private static final int MAX_QUERY_FIELDS = 100;
    private static final int MAX_LIST_VALUES = 100;
    private static final int MAX_LITERAL_LENGTH = 2_048;
    private static final Pattern SAFE_IDENTIFIER =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");
    private static final Pattern OBJECT_LINE = Pattern.compile(
            "^\\s*(.*?)\\s+API:\\s*(\\S+)\\s+前缀:\\s*(\\S+)\\s*$");
    private static final Pattern FIELD_LINE = Pattern.compile(
            "^\\s*(.*?)\\s+API:\\s*(\\S+)\\s+类型:\\s*(\\S+)\\s*$");
    private static final Pattern DECLARED_OBJECT_COUNT = Pattern.compile("共\\s*(\\d+)\\s*个");
    private static final Pattern DECLARED_FIELD_COUNT = Pattern.compile(
            "标准字段\\s*(\\d+)\\s*个\\s*\\|\\s*自定义字段\\s*(\\d+)\\s*个");

    private final CloudccOpenApiService cloudcc;
    private final ObjectMapper objectMapper;
    private final Map<DiscoveryCacheKey, Map<String, String>> discoveredPrefixes =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<DiscoveryCacheKey, Map<String, String>> eldest) {
                    return size() > 256;
                }
            });

    public CloudccOntologyAdapter(
            CloudccOpenApiService cloudcc,
            ObjectMapper objectMapper) {
        this.cloudcc = cloudcc;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(DataSourceConfig source) {
        return source != null
                && source.type() == OntologyDocument.SourceType.CONNECTOR
                && ADAPTER_KEY.equalsIgnoreCase(trimmed(source.adapterKey()));
    }

    @Override
    public List<PhysicalObject> discoverObjects(
            AdapterContext context,
            DataSourceConfig source) {
        requireSupported(source);
        List<PhysicalObject> objects = new ArrayList<>();
        parseObjects(
                cloudcc.getStandardObjects(context.orgId(), context.userId()),
                "STANDARD",
                objects);
        parseObjects(
                cloudcc.getCustomObjects(context.orgId(), context.userId()),
                "CUSTOM",
                objects);
        Set<String> uniqueKeys = new LinkedHashSet<>();
        if (objects.stream().anyMatch(object -> !uniqueKeys.add(object.key()))) {
            throw new IllegalStateException("CONNECTOR_DISCOVERY_DUPLICATE_OBJECT");
        }
        Map<String, String> prefixes = new LinkedHashMap<>();
        for (PhysicalObject object : objects) {
            prefixes.put(
                    object.key(),
                    readMetadata(object.metadataJson()).path("prefix").asText(""));
        }
        discoveredPrefixes.put(
                new DiscoveryCacheKey(context.orgId(), context.userId(), source.id(), source.key()),
                Map.copyOf(prefixes));
        return List.copyOf(objects);
    }

    @Override
    public List<PhysicalField> discoverFields(
            AdapterContext context,
            DataSourceConfig source,
            String objectKey) {
        requireSupported(source);
        requireIdentifier(objectKey, "PHYSICAL_OBJECT_NOT_ALLOWED");
        String prefix = configuredPrefix(source, objectKey);
        DiscoveryCacheKey cacheKey = new DiscoveryCacheKey(
                context.orgId(), context.userId(), source.id(), source.key());
        if (prefix == null) {
            prefix = discoveredPrefixes.getOrDefault(cacheKey, Map.of()).get(objectKey);
        }
        if (prefix == null) {
            PhysicalObject object = discoverObjects(context, source).stream()
                    .filter(candidate -> Objects.equals(candidate.key(), objectKey))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "PHYSICAL_OBJECT_NOT_FOUND"));
            prefix = readMetadata(object.metadataJson()).path("prefix").asText("");
        }
        if (prefix.isBlank()) {
            throw new IllegalStateException("CONNECTOR_DISCOVERY_PREFIX_MISSING");
        }
        String response = cloudcc.getObjectFields(context.orgId(), context.userId(), prefix);
        return parseFields(response, objectKey);
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
            boolean objectExists = objects.stream().anyMatch(object ->
                    Objects.equals(object.key(), mapping.physicalObjectKey()));
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
        } catch (RuntimeException exception) {
            return MappingValidation.invalid(
                    "CONNECTOR_MAPPING_VALIDATION_FAILED", exception.getMessage());
        }
    }

    @Override
    public void validateQuery(DataSourceConfig source, PhysicalQuery query) {
        requireSupported(source);
        requireBudget(query);
        if (!query.orderBy().isEmpty()) {
            throw new IllegalArgumentException("QUERY_ORDER_NOT_SUPPORTED");
        }
        requireIdentifier(query.objectKey(), "PHYSICAL_OBJECT_NOT_ALLOWED");
        List<String> fields = List.copyOf(new LinkedHashSet<>(query.fields()));
        if (fields.isEmpty() || fields.size() > MAX_QUERY_FIELDS) {
            throw new IllegalArgumentException("QUERY_FIELDS_INVALID");
        }
        fields.forEach(field -> requireIdentifier(field, "PHYSICAL_FIELD_NOT_ALLOWED"));
        query.filters().forEach(filter -> {
            if (filter == null || filter.operator() == null) {
                throw new IllegalArgumentException("QUERY_OPERATOR_NOT_ALLOWED");
            }
            requireIdentifier(filter.field(), "PHYSICAL_FIELD_NOT_ALLOWED");
        });
        query.filters().forEach(this::compileFilter);
    }

    @Override
    public PhysicalResult executeRead(
            AdapterContext context,
            DataSourceConfig source,
            PhysicalQuery query) {
        validateQuery(source, query);
        List<String> fields = List.copyOf(new LinkedHashSet<>(query.fields()));
        String expressions = query.filters().stream()
                .map(this::compileFilter)
                .reduce((left, right) -> left + " and " + right)
                .orElse("");
        CloudccOpenApiService.PageRecords result = cloudcc.pageQueryRecords(
                context.orgId(),
                context.userId(),
                query.objectKey(),
                String.join(",", fields),
                expressions,
                1,
                query.limit());
        return new PhysicalResult(
                result.records().stream().map(row -> project(row, fields)).toList(),
                result.totalCount(),
                result.pageCount() > result.pageNum()
                        || result.totalCount() > result.records().size());
    }

    private void parseObjects(
            String response,
            String objectType,
            List<PhysicalObject> target) {
        requireDiscoveryResponse(response);
        int before = target.size();
        for (String line : response.lines().toList()) {
            Matcher matcher = OBJECT_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String name = matcher.group(1).trim();
            String apiName = matcher.group(2).trim();
            String prefix = matcher.group(3).trim();
            requireIdentifier(apiName, "CONNECTOR_DISCOVERY_OBJECT_IDENTIFIER_INVALID");
            if (name.isBlank() || prefix.isBlank()) {
                throw new IllegalStateException("CONNECTOR_DISCOVERY_FORMAT_UNRECOGNIZED");
            }
            target.add(new PhysicalObject(
                    apiName,
                    name,
                    objectType,
                    writeMetadata(Map.of(
                            "prefix", prefix,
                            "objectType", objectType))));
        }
        int declaredCount = declaredCount(response, DECLARED_OBJECT_COUNT);
        int parsedCount = target.size() - before;
        if (declaredCount >= 0 && declaredCount != parsedCount) {
            throw new IllegalStateException("CONNECTOR_DISCOVERY_FORMAT_UNRECOGNIZED");
        }
    }

    private List<PhysicalField> parseFields(String response, String objectKey) {
        requireDiscoveryResponse(response);
        List<PhysicalField> fields = new ArrayList<>();
        for (String line : response.lines().toList()) {
            Matcher matcher = FIELD_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String name = matcher.group(1).trim();
            String apiName = matcher.group(2).trim();
            String dataType = matcher.group(3).trim();
            requireIdentifier(apiName, "CONNECTOR_DISCOVERY_FIELD_IDENTIFIER_INVALID");
            if (name.isBlank() || dataType.isBlank()) {
                throw new IllegalStateException("CONNECTOR_DISCOVERY_FORMAT_UNRECOGNIZED");
            }
            fields.add(new PhysicalField(
                    objectKey,
                    apiName,
                    name,
                    dataType,
                    true,
                    false,
                    "{}"));
        }
        Matcher counts = DECLARED_FIELD_COUNT.matcher(response);
        if (counts.find()) {
            int declared = Integer.parseInt(counts.group(1))
                    + Integer.parseInt(counts.group(2));
            if (declared != fields.size()) {
                throw new IllegalStateException("CONNECTOR_DISCOVERY_FORMAT_UNRECOGNIZED");
            }
        } else if (fields.isEmpty()) {
            throw new IllegalStateException("CONNECTOR_DISCOVERY_FORMAT_UNRECOGNIZED");
        }
        return List.copyOf(fields);
    }

    private String compileFilter(PhysicalFilter filter) {
        String field = filter.field();
        return switch (filter.operator()) {
            case EQ -> filter.value() == null
                    ? field + " is null"
                    : field + " = " + literal(filter.value());
            case NE -> filter.value() == null
                    ? field + " is not null"
                    : field + " != " + literal(filter.value());
            case IN -> field + " in (" + listValues(filter.value()).stream()
                    .map(this::literal)
                    .reduce((left, right) -> left + "," + right)
                    .orElseThrow(() -> new IllegalArgumentException("QUERY_LIST_VALUE_REQUIRED"))
                    + ")";
            case CONTAINS -> field + " like " + literal(
                    "%" + safeTextValue(filter.value()) + "%");
            case GT -> field + " > " + literal(filter.value());
            case GTE -> field + " >= " + literal(filter.value());
            case LT -> field + " < " + literal(filter.value());
            case LTE -> field + " <= " + literal(filter.value());
            case BETWEEN -> {
                List<?> bounds = listValues(filter.value());
                if (bounds.size() != 2) {
                    throw new IllegalArgumentException("BETWEEN_REQUIRES_TWO_VALUES");
                }
                yield field + " between " + literal(bounds.get(0))
                        + " and " + literal(bounds.get(1));
            }
            case IS_NULL -> field + " is null";
        };
    }

    private String literal(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("QUERY_FILTER_VALUE_REQUIRED");
        }
        if (value instanceof Number number) {
            BigDecimal decimal;
            try {
                decimal = new BigDecimal(String.valueOf(number));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("QUERY_FILTER_VALUE_NOT_ALLOWED");
            }
            return decimal.toPlainString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "true" : "false";
        }
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            throw new IllegalArgumentException("QUERY_FILTER_VALUE_NOT_ALLOWED");
        }
        String text = String.valueOf(value);
        if (text.length() > MAX_LITERAL_LENGTH
                || text.indexOf('\\') >= 0
                || containsControlCharacter(text)) {
            throw new IllegalArgumentException("QUERY_FILTER_VALUE_UNSAFE");
        }
        return "'" + text.replace("'", "''") + "'";
    }

    private String safeTextValue(Object value) {
        if (value == null || value instanceof Collection<?> || value instanceof Map<?, ?>) {
            throw new IllegalArgumentException("QUERY_FILTER_VALUE_NOT_ALLOWED");
        }
        String text = String.valueOf(value);
        if (text.length() > MAX_LITERAL_LENGTH
                || text.indexOf('\\') >= 0
                || text.indexOf('%') >= 0
                || text.indexOf('_') >= 0
                || containsControlCharacter(text)) {
            throw new IllegalArgumentException("QUERY_FILTER_VALUE_UNSAFE");
        }
        return text;
    }

    private List<?> listValues(Object value) {
        if (!(value instanceof List<?> values)
                || values.isEmpty()
                || values.size() > MAX_LIST_VALUES) {
            throw new IllegalArgumentException("QUERY_LIST_VALUE_REQUIRED");
        }
        return values;
    }

    private Map<String, Object> project(Map<String, Object> row, List<String> fields) {
        Map<String, Object> projected = new LinkedHashMap<>();
        fields.forEach(field -> projected.put(field, row.get(field)));
        return projected;
    }

    private String configuredPrefix(DataSourceConfig source, String objectKey) {
        if (source.configJson() == null || source.configJson().isBlank()) {
            return null;
        }
        try {
            String prefix = objectMapper.readTree(source.configJson())
                    .path("objectPrefixes")
                    .path(objectKey)
                    .asText("");
            return prefix.isBlank() ? null : prefix;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("DATA_SOURCE_CONFIG_INVALID", exception);
        }
    }

    private JsonNode readMetadata(String metadataJson) {
        try {
            return objectMapper.readTree(metadataJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("CONNECTOR_DISCOVERY_METADATA_INVALID", exception);
        }
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("CONNECTOR_DISCOVERY_METADATA_INVALID", exception);
        }
    }

    private int declaredCount(String response, Pattern pattern) {
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return response.contains("列表为空") ? 0 : -1;
    }

    private void requireDiscoveryResponse(String response) {
        if (response == null || response.isBlank() || response.stripLeading().startsWith("❌")) {
            throw new IllegalStateException("CONNECTOR_DISCOVERY_FAILED");
        }
    }

    private void requireSupported(DataSourceConfig source) {
        if (!supports(source)) {
            throw new IllegalArgumentException("CONNECTOR_ADAPTER_MISMATCH");
        }
    }

    private void requireBudget(PhysicalQuery query) {
        if (query == null || query.limit() < 1 || query.limit() > MAX_QUERY_LIMIT) {
            throw new IllegalArgumentException("QUERY_BUDGET_EXCEEDED");
        }
    }

    private void requireIdentifier(String value, String errorCode) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(errorCode);
        }
    }

    private boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private String trimmed(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record DiscoveryCacheKey(
            String orgId,
            String userId,
            Long sourceId,
            String sourceKey) {
    }
}
