package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class OntologyAiProposalPromptPolicy {

    static final int MAX_PROMPT_BYTES = 256 * 1024;
    static final int MAX_OUTPUT_TOKENS = 16_384;

    private static final String SYSTEM_PROMPT = """
            You generate one complete, domain-neutral OntologyDocument JSON object for business ontology modeling.
            Return JSON only, optionally wrapped by one outer ```json code fence. Do not include any text outside it.
            Never include credentials, secrets, SQL, scripts, executable code, remote URLs, publishing instructions,
            archive operations, system writes, or external write-back actions. Actions are contracts only.
            Use only the fields and enum values in the supplied OntologyDocument contract.
            DOMAIN_FIRST must return empty dataSources and mappings arrays.
            DATA_SOURCE_FIRST may reference only the explicitly supplied source, object, and field whitelist,
            must leave every data-source configJson null, and must not invent a source, object, or field.
            """;
    private static final String DOCUMENT_CONTRACT = """
            OntologyDocument contract (all fields are required; use JSON null only where marked nullable):
            OntologyDocument{key:string,name:string,description:string|null,concepts:Concept[],relations:Relation[],metrics:Metric[],actions:Action[],dataSources:DataSource[],mappings:Mapping[]}
            Concept{key:string,name:string,pluralName:string|null,description:string|null,conceptType:ConceptType,displayPropertyKey:string|null,positionX:number,positionY:number,queryable:boolean,enabled:boolean,properties:Property[]}
            Property{key:string,name:string,description:string|null,dataType:DataType,required:boolean,multiple:boolean,sensitive:boolean,queryable:boolean,enumValues:string[]}
            Relation{key:string,name:string,description:string|null,sourceConceptKey:string,targetConceptKey:string,cardinality:Cardinality,forwardLabel:string|null,reverseLabel:string|null,queryable:boolean,enabled:boolean}
            Metric{key:string,name:string,conceptKey:string,aggregation:Aggregation,measurePropertyKey:string|null,groupByPropertyKeys:string[],timePropertyKey:string|null,filters:QueryFilter[]}
            QueryFilter{property:string,operator:Operator,value:null|string|number|boolean|array(max depth 2)}
            Action{key:string,name:string,conceptKey:string,description:string|null,parameters:ActionParameter[]}
            ActionParameter{key:string,name:string,dataType:DataType,required:boolean}
            DataSource{id:integer,key:string,name:string,type:SourceType,configJson:null}; configJson must be null.
            Mapping{targetType:string,targetKey:string,dataSourceId:integer,physicalObjectKey:string,physicalFieldKey:string|null,relationTargetFieldKey:string|null,transform:string|null,confidence:number,source:string|null,validationStatus:string|null}
            ConceptType=[ENTITY,EVENT]
            DataType=[TEXT,LONG_TEXT,INTEGER,DECIMAL,BOOLEAN,DATE,DATETIME,ENUM,REFERENCE]
            Cardinality=[ONE_TO_ONE,ONE_TO_MANY,MANY_TO_ONE,MANY_TO_MANY]
            Aggregation=[COUNT,SUM,AVG,MIN,MAX]
            SourceType=[INLINE_SAMPLE,CONNECTOR]
            Operator=[EQ,NE,IN,CONTAINS,GT,GTE,LT,LTE,BETWEEN,IS_NULL]
            Mapping targetType=[CONCEPT,PROPERTY,RELATION,METRIC,ACTION]; transform=[DIRECT,TRIM,LOWERCASE,UPPERCASE,NUMBER,DATE,DATETIME,BOOLEAN_MAP,ENUM_MAP,REFERENCE].
            Constraints: every array is non-null; keys match ^[a-z][a-z0-9]*(?:[-_][a-z0-9]+)*$;
            max concepts=100, max properties per concept=100, max total properties=1000, max each nested list=100;
            names are non-blank and max 160 characters; numbers are finite; references must resolve;
            ENUM requires 1..100 unique enumValues and every other DataType requires empty enumValues;
            sensitive properties cannot be queryable; actions are contracts only; AI mappings use source=AI and validationStatus=PENDING.
            """;

    private final ObjectMapper objectMapper;

    OntologyAiProposalPromptPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<Map<String, Object>> messages(
            String instruction,
            String mode,
            OntologyDocument current,
            Object selectedMetadata) {
        OntologyDocument safeCurrent = new OntologyDocument(
                current.key(),
                current.name(),
                current.description(),
                current.concepts(),
                current.relations(),
                current.metrics(),
                current.actions(),
                List.of(),
                List.of());
        String userPrompt = "mode=" + mode
                + "\ninstruction=" + instruction
                + "\ncurrentSemanticDraft=" + writeJson(safeCurrent)
                + "\nselectedMetadata=" + writeJson(selectedMetadata);
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT + "\n" + DOCUMENT_CONTRACT),
                Map.of("role", "user", "content", userPrompt));
        if (writeJson(messages).getBytes(StandardCharsets.UTF_8).length > MAX_PROMPT_BYTES) {
            throw new IllegalArgumentException("AI_PROPOSAL_INVALID");
        }
        return messages;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("AI_PROPOSAL_INVALID", exception);
        }
    }
}
