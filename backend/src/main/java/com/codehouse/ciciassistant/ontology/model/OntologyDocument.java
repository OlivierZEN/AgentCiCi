package com.codehouse.ciciassistant.ontology.model;

import java.util.List;

public record OntologyDocument(
        String key,
        String name,
        String description,
        List<Concept> concepts,
        List<Relation> relations,
        List<Metric> metrics,
        List<Action> actions,
        List<DataSource> dataSources,
        List<Mapping> mappings) {

    public record Concept(
            String key,
            String name,
            String pluralName,
            String description,
            ConceptType conceptType,
            String displayPropertyKey,
            double positionX,
            double positionY,
            boolean queryable,
            boolean enabled,
            List<Property> properties) {
    }

    public record Property(
            String key,
            String name,
            String description,
            DataType dataType,
            boolean required,
            boolean multiple,
            boolean sensitive,
            boolean queryable,
            List<String> enumValues) {
    }

    public record Relation(
            String key,
            String name,
            String description,
            String sourceConceptKey,
            String targetConceptKey,
            Cardinality cardinality,
            String forwardLabel,
            String reverseLabel,
            boolean queryable,
            boolean enabled) {
    }

    public record Metric(
            String key,
            String name,
            String conceptKey,
            Aggregation aggregation,
            String measurePropertyKey,
            List<String> groupByPropertyKeys,
            String timePropertyKey,
            List<QueryFilter> filters) {
    }

    public record Action(
            String key,
            String name,
            String conceptKey,
            String description,
            List<ActionParameter> parameters) {
    }

    public record ActionParameter(
            String key,
            String name,
            DataType dataType,
            boolean required) {
    }

    public record DataSource(
            Long id,
            String key,
            String name,
            SourceType type,
            String configJson) {
    }

    public record Mapping(
            String targetType,
            String targetKey,
            Long dataSourceId,
            String physicalObjectKey,
            String physicalFieldKey,
            String relationTargetFieldKey,
            String transform,
            double confidence,
            String source,
            String validationStatus) {
    }

    public record QueryFilter(String property, Operator operator, Object value) {
    }

    public enum ConceptType {
        ENTITY,
        EVENT
    }

    public enum DataType {
        TEXT,
        LONG_TEXT,
        INTEGER,
        DECIMAL,
        BOOLEAN,
        DATE,
        DATETIME,
        ENUM,
        REFERENCE
    }

    public enum Cardinality {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY
    }

    public enum Aggregation {
        COUNT,
        SUM,
        AVG,
        MIN,
        MAX
    }

    public enum SourceType {
        INLINE_SAMPLE,
        CLOUDCC
    }

    public enum Operator {
        EQ,
        NE,
        IN,
        CONTAINS,
        GT,
        GTE,
        LT,
        LTE,
        BETWEEN,
        IS_NULL
    }
}
