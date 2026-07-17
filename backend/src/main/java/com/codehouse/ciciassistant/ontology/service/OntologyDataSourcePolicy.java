package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.common.security.SecretKeyMatcher;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.DataSourceConfig;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class OntologyDataSourcePolicy {

    public static final int MAX_SAMPLE_BYTES = 256 * 1024;
    public static final int MAX_OBJECTS = 50;
    public static final int MAX_ROWS = 2_000;
    public static final int MAX_FIELDS_PER_OBJECT = 128;
    private static final int MAX_CONFIG_BYTES = 16 * 1024;
    private static final int MAX_CONFIG_DEPTH = 4;
    private static final Pattern URL_VALUE = Pattern.compile("(?i)^\\s*(https?|wss?)://.*");

    private final ObjectMapper objectMapper;
    private final List<OntologyDataSourceAdapter> adapters;

    public OntologyDataSourcePolicy(
            ObjectMapper objectMapper,
            List<OntologyDataSourceAdapter> adapters) {
        this.objectMapper = objectMapper;
        this.adapters = List.copyOf(adapters);
    }

    public void validate(OntologyDocument.DataSource source) {
        if (source == null || source.type() == null) {
            throw new IllegalArgumentException("ONTOLOGY_DATA_SOURCE_INVALID");
        }
        JsonNode config = parseConfig(source.configJson());
        if (source.type() == OntologyDocument.SourceType.INLINE_SAMPLE) {
            if (config.size() != 0) {
                throw new IllegalArgumentException("DATA_SOURCE_CONFIG_INVALID");
            }
            validateSample(source.sampleDataJson());
            return;
        }
        if (hasText(source.sampleDataJson())) {
            throw new IllegalArgumentException("CONNECTOR_SAMPLE_DATA_FORBIDDEN");
        }
        validatePublicConfig(config, 0);
        String adapterKey = config.path("adapterKey").asText("").trim();
        if (adapterKey.isEmpty()) {
            throw new IllegalArgumentException("DATA_SOURCE_ADAPTER_REQUIRED");
        }
        DataSourceConfig candidate = new DataSourceConfig(
                source.id(), null, source.key(), source.name(), source.type(),
                adapterKey, source.configJson(), null);
        List<OntologyDataSourceAdapter> matchingAdapters = adapters.stream()
                .filter(adapter -> supports(adapter, candidate))
                .limit(2)
                .toList();
        if (matchingAdapters.size() != 1) {
            throw new IllegalArgumentException("DATA_SOURCE_ADAPTER_NOT_ALLOWED");
        }
        OntologyDataSourceAdapter adapter = matchingAdapters.getFirst();
        requireAllowedRootConfig(config, adapter);
        try {
            adapter.validatePublicConfig(config);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("DATA_SOURCE_CONFIG_INVALID");
        }
    }

    public String adapterKey(String configJson) {
        JsonNode config = parseConfig(configJson);
        String value = config.path("adapterKey").asText("").trim();
        return value.isEmpty() ? null : value;
    }

    public SampleSummary sampleSummary(String sampleDataJson) {
        if (!hasText(sampleDataJson)) {
            return new SampleSummary(0, 0, 0);
        }
        JsonNode root = validateSample(sampleDataJson);
        int rows = 0;
        int fields = 0;
        for (JsonNode objectRows : root) {
            rows += objectRows.size();
            for (JsonNode row : objectRows) {
                fields += row.size();
            }
        }
        return new SampleSummary(root.size(), rows, fields);
    }

    private JsonNode parseConfig(String configJson) {
        if (!hasText(configJson)) {
            return objectMapper.createObjectNode();
        }
        if (configJson.getBytes(StandardCharsets.UTF_8).length > MAX_CONFIG_BYTES) {
            throw new IllegalArgumentException("DATA_SOURCE_CONFIG_INVALID");
        }
        try {
            JsonNode config = objectMapper.readTree(configJson);
            if (config == null || !config.isObject()) {
                throw new IllegalArgumentException("DATA_SOURCE_CONFIG_INVALID");
            }
            return config;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("DATA_SOURCE_CONFIG_INVALID", exception);
        }
    }

    private JsonNode validateSample(String sampleDataJson) {
        if (!hasText(sampleDataJson)) {
            throw new IllegalArgumentException("INLINE_SAMPLE_DATA_REQUIRED");
        }
        if (sampleDataJson.getBytes(StandardCharsets.UTF_8).length > MAX_SAMPLE_BYTES) {
            throw new IllegalArgumentException("INLINE_SAMPLE_PAYLOAD_LIMIT_EXCEEDED");
        }
        try {
            JsonNode root = objectMapper.readTree(sampleDataJson);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("INLINE_SAMPLE_OBJECT_OF_ARRAYS_REQUIRED");
            }
            if (root.size() > MAX_OBJECTS) {
                throw new IllegalArgumentException("INLINE_SAMPLE_OBJECT_LIMIT_EXCEEDED");
            }
            int rows = 0;
            var objects = root.fields();
            while (objects.hasNext()) {
                Map.Entry<String, JsonNode> entry = objects.next();
                if (!boundedKey(entry.getKey()) || !entry.getValue().isArray()) {
                    throw new IllegalArgumentException("INLINE_SAMPLE_OBJECT_OF_ARRAYS_REQUIRED");
                }
                rows += entry.getValue().size();
                if (rows > MAX_ROWS) {
                    throw new IllegalArgumentException("INLINE_SAMPLE_ROW_LIMIT_EXCEEDED");
                }
                for (JsonNode row : entry.getValue()) {
                    if (!row.isObject()) {
                        throw new IllegalArgumentException("INLINE_SAMPLE_ROW_MUST_BE_OBJECT");
                    }
                    if (row.size() > MAX_FIELDS_PER_OBJECT) {
                        throw new IllegalArgumentException("INLINE_SAMPLE_FIELD_LIMIT_EXCEEDED");
                    }
                    var fields = row.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        if (!boundedKey(field.getKey()) || !isBoundedSampleValue(field.getValue())) {
                            throw new IllegalArgumentException("INLINE_SAMPLE_FIELD_INVALID");
                        }
                    }
                }
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("INLINE_SAMPLE_JSON_INVALID", exception);
        }
    }

    private boolean isBoundedSampleValue(JsonNode value) {
        if (value == null || value.isNull() || value.isValueNode()) {
            return value == null || !value.isTextual() || value.textValue().length() <= 8_192;
        }
        if (!value.isArray() || value.size() > 100) {
            return false;
        }
        for (JsonNode item : value) {
            if (item == null || (!item.isNull() && !item.isValueNode())) {
                return false;
            }
        }
        return true;
    }

    private void validatePublicConfig(JsonNode value, int depth) {
        if (depth > MAX_CONFIG_DEPTH) {
            throw new IllegalArgumentException("DATA_SOURCE_CONFIG_INVALID");
        }
        if (value.isObject()) {
            var fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!boundedKey(field.getKey())) {
                    throw new IllegalArgumentException("DATA_SOURCE_CONFIG_INVALID");
                }
                if (SecretKeyMatcher.matches(field.getKey())) {
                    throw new IllegalArgumentException("DATA_SOURCE_CONFIG_SECRET_FORBIDDEN");
                }
                validatePublicConfig(field.getValue(), depth + 1);
            }
            return;
        }
        if (value.isArray()) {
            if (value.size() > 100) {
                throw new IllegalArgumentException("DATA_SOURCE_CONFIG_INVALID");
            }
            for (JsonNode item : value) {
                validatePublicConfig(item, depth + 1);
            }
            return;
        }
        if (!value.isValueNode()) {
            throw new IllegalArgumentException("DATA_SOURCE_CONFIG_INVALID");
        }
        if (value.isTextual() && URL_VALUE.matcher(value.textValue()).matches()) {
            throw new IllegalArgumentException("DATA_SOURCE_CONFIG_URL_FORBIDDEN");
        }
    }

    private boolean supports(OntologyDataSourceAdapter adapter, DataSourceConfig source) {
        try {
            return adapter.supports(source);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void requireAllowedRootConfig(
            JsonNode config,
            OntologyDataSourceAdapter adapter) {
        Set<String> allowed;
        try {
            allowed = adapter.publicConfigKeys();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("DATA_SOURCE_ADAPTER_NOT_ALLOWED");
        }
        if (allowed == null
                || allowed.isEmpty()
                || allowed.stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("DATA_SOURCE_ADAPTER_NOT_ALLOWED");
        }
        var fields = config.fieldNames();
        while (fields.hasNext()) {
            if (!allowed.contains(fields.next())) {
                throw new IllegalArgumentException("DATA_SOURCE_CONFIG_FIELD_NOT_ALLOWED");
            }
        }
    }

    private boolean boundedKey(String value) {
        return value != null && !value.isBlank() && value.length() <= 256;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record SampleSummary(int objectCount, int rowCount, int fieldValueCount) {
    }
}
