package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OntologyDraftSafetyPolicy {

    public static final int MAX_DOCUMENT_BYTES = 1024 * 1024;
    public static final int MAX_CONCEPTS = 100;
    public static final int MAX_PROPERTIES_PER_CONCEPT = 100;
    public static final int MAX_PROPERTIES = 1_000;
    public static final int MAX_RELATIONS = 500;
    public static final int MAX_METRICS = 200;
    public static final int MAX_ACTIONS = 200;
    public static final int MAX_DATA_SOURCES = 50;
    public static final int MAX_MAPPINGS = 5_000;
    private static final int MAX_ENUM_VALUES = 200;
    private static final int MAX_GROUP_BY_PROPERTIES = 100;
    private static final int MAX_FILTERS_PER_METRIC = 100;
    private static final int MAX_ACTION_PARAMETERS = 100;
    private static final int MAX_FILTER_COLLECTION_SIZE = 100;
    private static final int MAX_FILTER_DEPTH = 8;
    private static final int MAX_FILTER_NODES = 2_000;
    private static final int MAX_KEY_LENGTH = 128;
    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_MAPPING_KEY_LENGTH = 256;
    private static final int MAX_DESCRIPTION_LENGTH = 65_536;
    private static final int MAX_TRANSFORM_LENGTH = 4_096;
    private static final int MAX_FILTER_STRING_LENGTH = 8_192;

    private final ObjectMapper objectMapper;

    public OntologyDraftSafetyPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validateDocument(OntologyDocument document) {
        guarded(() -> {
            require(document != null);
            requireText(document.key(), MAX_KEY_LENGTH);
            requireText(document.name(), MAX_NAME_LENGTH);
            optionalText(document.description(), MAX_DESCRIPTION_LENGTH);
            requireList(document.concepts(), MAX_CONCEPTS);
            requireList(document.relations(), MAX_RELATIONS);
            requireList(document.metrics(), MAX_METRICS);
            requireList(document.actions(), MAX_ACTIONS);
            requireList(document.dataSources(), MAX_DATA_SOURCES);
            requireList(document.mappings(), MAX_MAPPINGS);

            int propertyCount = 0;
            for (OntologyDocument.Concept concept : document.concepts()) {
                require(concept != null && concept.conceptType() != null);
                requireText(concept.key(), MAX_KEY_LENGTH);
                requireText(concept.name(), MAX_NAME_LENGTH);
                optionalText(concept.pluralName(), MAX_NAME_LENGTH);
                optionalText(concept.description(), MAX_DESCRIPTION_LENGTH);
                optionalText(concept.displayPropertyKey(), MAX_KEY_LENGTH);
                require(Double.isFinite(concept.positionX()) && Double.isFinite(concept.positionY()));
                requireList(concept.properties(), MAX_PROPERTIES_PER_CONCEPT);
                propertyCount += concept.properties().size();
                require(propertyCount <= MAX_PROPERTIES);
                for (OntologyDocument.Property property : concept.properties()) {
                    validateProperty(property);
                }
            }

            for (OntologyDocument.Relation relation : document.relations()) {
                require(relation != null && relation.cardinality() != null);
                requireText(relation.key(), MAX_KEY_LENGTH);
                requireText(relation.name(), MAX_NAME_LENGTH);
                optionalText(relation.description(), MAX_DESCRIPTION_LENGTH);
                requireText(relation.sourceConceptKey(), MAX_KEY_LENGTH);
                requireText(relation.targetConceptKey(), MAX_KEY_LENGTH);
                optionalText(relation.forwardLabel(), MAX_NAME_LENGTH);
                optionalText(relation.reverseLabel(), MAX_NAME_LENGTH);
            }

            int[] filterNodes = {0};
            for (OntologyDocument.Metric metric : document.metrics()) {
                require(metric != null && metric.aggregation() != null);
                requireText(metric.key(), MAX_KEY_LENGTH);
                requireText(metric.name(), MAX_NAME_LENGTH);
                requireText(metric.conceptKey(), MAX_KEY_LENGTH);
                optionalText(metric.measurePropertyKey(), MAX_KEY_LENGTH);
                optionalText(metric.timePropertyKey(), MAX_KEY_LENGTH);
                requireList(metric.groupByPropertyKeys(), MAX_GROUP_BY_PROPERTIES);
                for (String propertyKey : metric.groupByPropertyKeys()) {
                    requireText(propertyKey, MAX_KEY_LENGTH);
                }
                requireList(metric.filters(), MAX_FILTERS_PER_METRIC);
                for (OntologyDocument.QueryFilter filter : metric.filters()) {
                    require(filter != null && filter.operator() != null);
                    requireText(filter.property(), MAX_KEY_LENGTH);
                    validateFilterValue(filter.value(), 0, filterNodes);
                }
            }

            for (OntologyDocument.Action action : document.actions()) {
                require(action != null);
                requireText(action.key(), MAX_KEY_LENGTH);
                requireText(action.name(), MAX_NAME_LENGTH);
                requireText(action.conceptKey(), MAX_KEY_LENGTH);
                optionalText(action.description(), MAX_DESCRIPTION_LENGTH);
                requireList(action.parameters(), MAX_ACTION_PARAMETERS);
                for (OntologyDocument.ActionParameter parameter : action.parameters()) {
                    require(parameter != null && parameter.dataType() != null);
                    requireText(parameter.key(), MAX_KEY_LENGTH);
                    requireText(parameter.name(), MAX_NAME_LENGTH);
                }
            }

            for (OntologyDocument.DataSource source : document.dataSources()) {
                require(source != null && source.type() != null);
                requireText(source.key(), MAX_KEY_LENGTH);
                requireText(source.name(), MAX_NAME_LENGTH);
                optionalBytes(source.configJson(), MAX_DOCUMENT_BYTES);
                optionalBytes(source.sampleDataJson(), MAX_DOCUMENT_BYTES);
            }

            validateMappingsInternal(document.mappings());
            require(serializedBytes(document) <= MAX_DOCUMENT_BYTES);
        });
    }

    public void validateMappings(List<OntologyDocument.Mapping> mappings) {
        guarded(() -> {
            requireList(mappings, MAX_MAPPINGS);
            validateMappingsInternal(mappings);
            require(serializedBytes(mappings) <= MAX_DOCUMENT_BYTES);
        });
    }

    private void validateProperty(OntologyDocument.Property property) {
        require(property != null && property.dataType() != null);
        requireText(property.key(), MAX_KEY_LENGTH);
        requireText(property.name(), MAX_NAME_LENGTH);
        optionalText(property.description(), MAX_DESCRIPTION_LENGTH);
        requireList(property.enumValues(), MAX_ENUM_VALUES);
        Set<String> enumValues = new HashSet<>();
        for (String enumValue : property.enumValues()) {
            requireText(enumValue, MAX_MAPPING_KEY_LENGTH);
            require(enumValues.add(enumValue.trim()));
        }
        if (property.dataType() == OntologyDocument.DataType.ENUM) {
            require(!property.enumValues().isEmpty());
        } else {
            require(property.enumValues().isEmpty());
        }
    }

    private void validateMappingsInternal(List<OntologyDocument.Mapping> mappings) {
        for (OntologyDocument.Mapping mapping : mappings) {
            require(mapping != null && mapping.dataSourceId() != null && mapping.dataSourceId() != 0);
            requireText(mapping.targetType(), 32);
            requireText(mapping.targetKey(), MAX_MAPPING_KEY_LENGTH);
            requireText(mapping.physicalObjectKey(), MAX_MAPPING_KEY_LENGTH);
            optionalText(mapping.physicalFieldKey(), MAX_MAPPING_KEY_LENGTH);
            optionalText(mapping.relationTargetFieldKey(), MAX_MAPPING_KEY_LENGTH);
            optionalText(mapping.transform(), MAX_TRANSFORM_LENGTH);
            require(Double.isFinite(mapping.confidence()));
            require(mapping.confidence() >= 0 && mapping.confidence() <= 1);
            requireText(mapping.source(), 32);
            requireText(mapping.validationStatus(), 32);
        }
    }

    private void validateFilterValue(Object value, int depth, int[] nodes) {
        require(depth <= MAX_FILTER_DEPTH);
        require(++nodes[0] <= MAX_FILTER_NODES);
        if (value == null || value instanceof Boolean || value instanceof Number) {
            if (value instanceof Double doubleValue) {
                require(Double.isFinite(doubleValue));
            }
            if (value instanceof Float floatValue) {
                require(Float.isFinite(floatValue));
            }
            return;
        }
        if (value instanceof String stringValue) {
            optionalText(stringValue, MAX_FILTER_STRING_LENGTH);
            return;
        }
        if (value instanceof List<?> listValue) {
            require(listValue.size() <= MAX_FILTER_COLLECTION_SIZE);
            for (Object item : listValue) {
                validateFilterValue(item, depth + 1, nodes);
            }
            return;
        }
        if (value instanceof Map<?, ?> mapValue) {
            require(mapValue.size() <= MAX_FILTER_COLLECTION_SIZE);
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                require(entry.getKey() instanceof String);
                requireText((String) entry.getKey(), MAX_KEY_LENGTH);
                validateFilterValue(entry.getValue(), depth + 1, nodes);
            }
            return;
        }
        throw new SafetyViolation();
    }

    private int serializedBytes(Object value) {
        try {
            return objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8).length;
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new SafetyViolation();
        }
    }

    private void optionalBytes(String value, int maxBytes) {
        if (value != null) {
            require(value.getBytes(StandardCharsets.UTF_8).length <= maxBytes);
        }
    }

    private void optionalText(String value, int maxLength) {
        if (value != null) {
            require(value.length() <= maxLength);
        }
    }

    private void requireText(String value, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength);
    }

    private void requireList(List<?> values, int maxSize) {
        require(values != null && values.size() <= maxSize);
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new SafetyViolation();
        }
    }

    private void guarded(Runnable validation) {
        try {
            validation.run();
        } catch (SafetyViolation | NullPointerException exception) {
            throw new IllegalArgumentException("ONTOLOGY_VALIDATION_FAILED");
        }
    }

    private static final class SafetyViolation extends RuntimeException {
    }
}
