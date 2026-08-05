package com.codehouse.ciciassistant.ontology.semattice;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Deterministically compiles an AgentCiCi ontology draft into Semattice metadata definitions. */
@Service
public class SematticeOntologyContractCompiler {

    public static final String SEMANTIC_SCHEMA = "agentcici.ontology.semantic/v1";
    private static final Pattern API_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,95}$");
    private final ObjectMapper objectMapper;

    public SematticeOntologyContractCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Contract compile(Long workspaceId, long revision, OntologyDocument document) {
        if (workspaceId == null || workspaceId < 1 || revision < 0 || document == null) {
            throw new IllegalArgumentException("SEMATTICE_ONTOLOGY_COMPILE_INPUT_INVALID");
        }
        List<OntologyDocument.Concept> concepts = safe(document.concepts()).stream()
                .filter(OntologyDocument.Concept::enabled)
                .sorted(Comparator.comparing(OntologyDocument.Concept::key))
                .toList();
        if (concepts.isEmpty()) {
            throw new IllegalArgumentException("SEMATTICE_ONTOLOGY_OBJECT_REQUIRED");
        }
        Map<String, String> objectApiNames = uniqueApiNames(
                concepts.stream().collect(java.util.stream.Collectors.toMap(
                        OntologyDocument.Concept::key,
                        concept -> apiName(concept.key()),
                        (left, right) -> left,
                        LinkedHashMap::new)),
                "SEMATTICE_ONTOLOGY_OBJECT_API_CONFLICT");
        List<ObjectDefinition> objects = new ArrayList<>();
        List<FieldDefinition> fields = new ArrayList<>();
        for (OntologyDocument.Concept concept : concepts) {
            String objectApiName = objectApiNames.get(concept.key());
            Map<String, String> fieldApiNames = uniqueApiNames(
                    safe(concept.properties()).stream().collect(java.util.stream.Collectors.toMap(
                            OntologyDocument.Property::key,
                            property -> apiName(property.key()),
                            (left, right) -> left,
                            LinkedHashMap::new)),
                    "SEMATTICE_ONTOLOGY_FIELD_API_CONFLICT");
            objects.add(new ObjectDefinition(
                    concept.key(), objectApiName, requiredLabel(concept.name()),
                    text(concept.description()), conceptSemantic(document, workspaceId, revision, concept)));
            safe(concept.properties()).stream()
                    .sorted(Comparator.comparing(OntologyDocument.Property::key))
                    .forEach(property -> fields.add(new FieldDefinition(
                            concept.key() + "." + property.key(),
                            concept.key(),
                            fieldApiNames.get(property.key()),
                            requiredLabel(property.name()),
                            text(property.description()),
                            dataType(property.dataType()),
                            property.required(),
                            property.queryable(),
                            false,
                            "active",
                            property.queryable() ? "building" : "none",
                            "on_create",
                            constraints(property),
                            propertySemantic(document, workspaceId, revision, concept, property))));
        }
        Map<String, OntologyDocument.Concept> conceptsByKey = concepts.stream()
                .collect(java.util.stream.Collectors.toMap(OntologyDocument.Concept::key, value -> value));
        Map<String, String> relationApiNames = uniqueApiNames(
                safe(document.relations()).stream()
                        .filter(OntologyDocument.Relation::enabled)
                        .collect(java.util.stream.Collectors.toMap(
                                OntologyDocument.Relation::key,
                                relation -> apiName(relation.key()),
                                (left, right) -> left,
                                LinkedHashMap::new)),
                "SEMATTICE_ONTOLOGY_RELATION_API_CONFLICT");
        List<RelationDefinition> relations = safe(document.relations()).stream()
                .filter(OntologyDocument.Relation::enabled)
                .sorted(Comparator.comparing(OntologyDocument.Relation::key))
                .map(relation -> {
                    if (!conceptsByKey.containsKey(relation.sourceConceptKey())
                            || !conceptsByKey.containsKey(relation.targetConceptKey())) {
                        throw new IllegalArgumentException("SEMATTICE_ONTOLOGY_RELATION_REFERENCE_INVALID");
                    }
                    return new RelationDefinition(
                            relation.key(),
                            relationApiNames.get(relation.key()),
                            relation.sourceConceptKey(),
                            relation.targetConceptKey(),
                            relation.cardinality() == OntologyDocument.Cardinality.MANY_TO_MANY
                                    ? "many_to_many"
                                    : "lookup",
                            "restrict",
                            text(relation.description()),
                            relationSemantic(document, workspaceId, revision, relation));
                })
                .toList();
        Contract unsigned = new Contract(
                document.key(), revision, "", List.copyOf(objects), List.copyOf(fields), relations);
        String digest = sha256(canonicalJson(unsigned));
        return new Contract(
                unsigned.ontologyKey(), unsigned.sourceRevision(), digest,
                unsigned.objects(), unsigned.fields(), unsigned.relations());
    }

    private Map<String, Object> conceptSemantic(
            OntologyDocument document,
            Long workspaceId,
            long revision,
            OntologyDocument.Concept concept) {
        Map<String, Object> semantic = baseSemantic(
                document, workspaceId, revision, "CONCEPT", concept.key(), concept.description());
        semantic.put("concept_type", concept.conceptType().name());
        semantic.put("display_property_key", text(concept.displayPropertyKey()));
        semantic.put("queryable", concept.queryable());
        semantic.put("metrics", safe(document.metrics()).stream()
                .filter(metric -> concept.key().equals(metric.conceptKey()))
                .map(metric -> Map.of(
                        "key", metric.key(),
                        "name", metric.name(),
                        "aggregation", metric.aggregation().name()))
                .toList());
        semantic.put("actions", safe(document.actions()).stream()
                .filter(action -> concept.key().equals(action.conceptKey()))
                .map(action -> Map.of(
                        "key", action.key(),
                        "name", action.name(),
                        "description", text(action.description())))
                .toList());
        return semantic;
    }

    private Map<String, Object> propertySemantic(
            OntologyDocument document,
            Long workspaceId,
            long revision,
            OntologyDocument.Concept concept,
            OntologyDocument.Property property) {
        Map<String, Object> semantic = baseSemantic(
                document,
                workspaceId,
                revision,
                "PROPERTY",
                concept.key() + "." + property.key(),
                property.description());
        semantic.put("property_key", property.key());
        semantic.put("source_data_type", property.dataType().name());
        semantic.put("multiple", property.multiple());
        semantic.put("sensitivity", property.sensitive() ? "sensitive" : "internal");
        semantic.put("queryable", property.queryable());
        return semantic;
    }

    private Map<String, Object> relationSemantic(
            OntologyDocument document,
            Long workspaceId,
            long revision,
            OntologyDocument.Relation relation) {
        Map<String, Object> semantic = baseSemantic(
                document, workspaceId, revision, "RELATION", relation.key(), relation.description());
        semantic.put("cardinality", relation.cardinality().name());
        semantic.put("forward_label", text(relation.forwardLabel()));
        semantic.put("reverse_label", text(relation.reverseLabel()));
        semantic.put("queryable", relation.queryable());
        return semantic;
    }

    private Map<String, Object> baseSemantic(
            OntologyDocument document,
            Long workspaceId,
            long revision,
            String elementType,
            String elementKey,
            String definition) {
        Map<String, Object> semantic = new LinkedHashMap<>();
        semantic.put("schema", SEMANTIC_SCHEMA);
        semantic.put("workspace_id", String.valueOf(workspaceId));
        semantic.put("ontology_key", document.key());
        semantic.put("element_type", elementType);
        semantic.put("element_key", elementKey);
        semantic.put("business_definition", text(definition));
        semantic.put("synonyms", List.of());
        semantic.put("tags", List.of(document.key()));
        semantic.put("source_revision", revision);
        return semantic;
    }

    private Map<String, Object> constraints(OntologyDocument.Property property) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (property.dataType() == OntologyDocument.DataType.ENUM
                && !safe(property.enumValues()).isEmpty()) {
            result.put("enum", List.copyOf(property.enumValues()));
        }
        return result;
    }

    private String dataType(OntologyDocument.DataType type) {
        if (type == null) {
            throw new IllegalArgumentException("SEMATTICE_ONTOLOGY_FIELD_TYPE_REQUIRED");
        }
        return switch (type) {
            case TEXT, LONG_TEXT, ENUM -> "text";
            case INTEGER, DECIMAL -> "number";
            case BOOLEAN -> "boolean";
            case DATE -> "date";
            case DATETIME -> "datetime";
            case REFERENCE -> "uuid";
        };
    }

    private Map<String, String> uniqueApiNames(Map<String, String> byKey, String error) {
        Set<String> values = new LinkedHashSet<>();
        if (byKey.values().stream().anyMatch(value -> !values.add(value))) {
            throw new IllegalArgumentException(error);
        }
        return byKey;
    }

    private String apiName(String key) {
        String normalized = text(key).trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!API_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("SEMATTICE_ONTOLOGY_API_NAME_INVALID");
        }
        return normalized;
    }

    private String requiredLabel(String value) {
        String normalized = text(value).trim();
        if (normalized.isBlank() || normalized.length() > 160) {
            throw new IllegalArgumentException("SEMATTICE_ONTOLOGY_LABEL_INVALID");
        }
        return normalized;
    }

    private String canonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("SEMATTICE_ONTOLOGY_COMPILE_FAILED", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SEMATTICE_ONTOLOGY_DIGEST_FAILED", exception);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record Contract(
            String ontologyKey,
            long sourceRevision,
            String sourceDigest,
            List<ObjectDefinition> objects,
            List<FieldDefinition> fields,
            List<RelationDefinition> relations) {
    }

    public record ObjectDefinition(
            String elementKey,
            String apiName,
            String label,
            String description,
            Map<String, Object> semantic) {
    }

    public record FieldDefinition(
            String elementKey,
            String conceptKey,
            String apiName,
            String label,
            String description,
            String dataType,
            boolean required,
            boolean indexed,
            boolean uniqueValue,
            String lifecycleState,
            String indexState,
            String defaultSemantics,
            Map<String, Object> constraints,
            Map<String, Object> semantic) {
    }

    public record RelationDefinition(
            String elementKey,
            String apiName,
            String sourceConceptKey,
            String targetConceptKey,
            String relationType,
            String deleteBehavior,
            String description,
            Map<String, Object> semantic) {
    }
}
