package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.ai.service.AliyunBailianClient;
import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ChatCompletionResult;
import com.codehouse.ciciassistant.ai.service.ModelRouterService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.codehouse.ciciassistant.ontology.domain.OntologyAiProposalEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyAiProposalRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OntologyAiProposalService {

    static final int MAX_RESPONSE_BYTES = 256 * 1024;
    static final int MAX_PROMPT_BYTES = OntologyAiProposalPromptPolicy.MAX_PROMPT_BYTES;
    static final int MAX_OUTPUT_TOKENS = OntologyAiProposalPromptPolicy.MAX_OUTPUT_TOKENS;
    static final int MAX_SELECTED_FIELDS = 500;
    static final String SCENE_CODE = "ontology-modeling";
    private static final String DOMAIN_FIRST = "DOMAIN_FIRST";
    private static final String DATA_SOURCE_FIRST = "DATA_SOURCE_FIRST";
    private static final Pattern SAFE_KEY =
            Pattern.compile("^[a-z][a-z0-9]*(?:[-_][a-z0-9]+)*$");
    private static final Pattern UNSAFE_URL = Pattern.compile(
            "(?i)(?:https?|ftp|file|javascript)://|javascript:");
    private static final Pattern UNSAFE_CREDENTIAL = Pattern.compile(
            "(?i)\\b(?:api[_-]?key|access[_-]?token|secret|password|bearer)\\b\\s*[:=]");
    private static final Pattern UNSAFE_SQL = Pattern.compile(
            "(?is)\\b(?:select|insert|update|delete|drop|alter|create)\\b.{0,200}"
                    + "\\b(?:from|into|table|set|values)\\b");
    private static final Pattern UNSAFE_SCRIPT = Pattern.compile(
            "(?i)<script|</script|#!/|runtime\\.exec|processbuilder|\\bcurl\\s");
    private static final Set<String> MODEL_ERROR_SENTINELS = Set.of(
            "aliyun api key is not configured.",
            "empty response.",
            "model returned empty response.",
            "no choices in response.");

    private final OntologyWorkspaceRepository workspaces;
    private final OntologyAiProposalRepository proposals;
    private final OntologyPhysicalObjectRepository physicalObjects;
    private final OntologyPhysicalFieldRepository physicalFields;
    private final OntologyDraftService drafts;
    private final OntologyValidationService validation;
    private final ModelRouterService modelRouter;
    private final ModelProviderService modelProviders;
    private final AliyunBailianClient modelClient;
    private final OntologyTenantPersistence persistence;
    private final OntologyAiProposalStateService proposalState;
    private final ObjectMapper objectMapper;
    private final ObjectMapper strictMapper;
    private final OntologyAiProposalPromptPolicy promptPolicy;

    public OntologyAiProposalService(
            OntologyWorkspaceRepository workspaces,
            OntologyAiProposalRepository proposals,
            OntologyPhysicalObjectRepository physicalObjects,
            OntologyPhysicalFieldRepository physicalFields,
            OntologyDraftService drafts,
            OntologyValidationService validation,
            ModelRouterService modelRouter,
            ModelProviderService modelProviders,
            AliyunBailianClient modelClient,
            OntologyTenantPersistence persistence,
            OntologyAiProposalStateService proposalState,
            ObjectMapper objectMapper) {
        this.workspaces = workspaces;
        this.proposals = proposals;
        this.physicalObjects = physicalObjects;
        this.physicalFields = physicalFields;
        this.drafts = drafts;
        this.validation = validation;
        this.modelRouter = modelRouter;
        this.modelProviders = modelProviders;
        this.modelClient = modelClient;
        this.persistence = persistence;
        this.proposalState = proposalState;
        this.objectMapper = objectMapper;
        this.promptPolicy = new OntologyAiProposalPromptPolicy(objectMapper);
        this.strictMapper = objectMapper.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    }

    public ProposalView propose(
            String orgId,
            String userId,
            Long workspaceId,
            ProposalCommand command) {
        requireCurrentContext(orgId, userId);
        Objects.requireNonNull(command, "command");
        String mode = normalizeMode(command.mode());
        String instruction = requireInstruction(command.instruction());
        OntologyAiProposalStateService.BeginResult<PreparedProposal> begin =
                proposalState.begin(
                        orgId,
                        userId,
                        workspaceId,
                        mode,
                        instruction,
                        current -> {
                            AllowedSelection allowedSelection = prepareSelection(
                                    orgId, workspaceId, command, mode, current);
                            List<Map<String, Object>> messages = promptPolicy.messages(
                                    instruction,
                                    mode,
                                    current,
                                    allowedSelection.metadata());
                            return new PreparedProposal(allowedSelection, messages);
                        });
        OntologyDocument current = begin.current();
        AllowedSelection allowedSelection = begin.prepared().allowedSelection();
        List<Map<String, Object>> messages = begin.prepared().messages();
        long baseRevision = begin.baseRevision();
        OntologyAiProposalEntity proposal = begin.proposal();

        ChatCompletionResult response;
        try {
            response = invokeModel(orgId, messages);
        } catch (Exception exception) {
            return fail(
                    orgId,
                    workspaceId,
                    proposal.getId(),
                    baseRevision,
                    "AI_MODEL_UNAVAILABLE",
                    "AI_MODEL_CALL_FAILED");
        }

        try {
            OntologyDocument generated = parseResponse(response);
            validateDocumentShape(generated, false);
            validateSafeGeneratedContent(generated);
            OntologyDocument candidate = normalizeCandidate(
                    generated, current, mode, allowedSelection);
            List<OntologyValidationService.ValidationIssue> issues = validation.validate(candidate, false);
            if (issues.stream().anyMatch(issue ->
                    issue.severity() == OntologyValidationService.Severity.ERROR)) {
                throw invalid("MODEL_DOCUMENT_INVALID");
            }
            String payloadJson = writeJson(candidate);
            requirePayloadBudget(payloadJson);
            ProposalDiff diff = diff(
                    baseRevision, sanitizeServerAssets(current), candidate, sha256(payloadJson));
            OntologyAiProposalStateService.Transition transition = proposalState.finishReady(
                    orgId,
                    workspaceId,
                    proposal.getId(),
                    baseRevision,
                    payloadJson,
                    writeJson(diff),
                    writeJson(issues));
            proposal = transition.proposal();
            if (!"READY".equals(proposal.getStatus())) {
                return failedView(transition, baseRevision);
            }
            return new ProposalView(
                    proposal.getId(),
                    workspaceId,
                    mode,
                    proposal.getStatus(),
                    baseRevision,
                    candidate,
                    diff,
                    issues,
                    "",
                    "",
                    proposal.getCreatedAt(),
                    proposal.getUpdatedAt(),
                    proposal.getAppliedAt());
        } catch (ProposalFailure failure) {
            return fail(
                    orgId,
                    workspaceId,
                    proposal.getId(),
                    baseRevision,
                    failure.code(),
                    failure.diagnostic());
        } catch (Exception exception) {
            return fail(
                    orgId,
                    workspaceId,
                    proposal.getId(),
                    baseRevision,
                    "AI_PROPOSAL_INVALID",
                    "MODEL_RESPONSE_INVALID");
        }
    }

    @Transactional
    public ProposalView apply(
            String orgId,
            String userId,
            Long proposalId,
            Long expectedRevision) {
        requireCurrentContext(orgId, userId);
        Long workspaceId = proposals.findWorkspaceIdByIdAndOrgId(proposalId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("AI_PROPOSAL_INVALID"));
        OntologyWorkspaceEntity workspace = workspaces
                .findForUpdateByIdAndOrgId(workspaceId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("AI_PROPOSAL_INVALID"));
        OntologyAiProposalEntity proposal = proposals
                .findForUpdateByIdAndOrgId(proposalId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("AI_PROPOSAL_INVALID"));
        if ("ARCHIVED".equals(workspace.getStatus())
                || !Objects.equals(proposal.getWorkspaceId(), workspaceId)
                || !"READY".equals(proposal.getStatus())) {
            throw applyInvalid();
        }

        ProposalDiff diff = readStoredDiff(proposal.getDiffJson());
        if (!Objects.equals(expectedRevision, diff.baseRevision())
                || !Objects.equals(workspace.getDraftRevision(), expectedRevision)) {
            throw new ConflictException("ONTOLOGY_REVISION_CONFLICT");
        }
        if (isBlank(diff.candidateHash())
                || !Objects.equals(diff.candidateHash(), sha256(proposal.getPayloadJson()))) {
            throw applyInvalid();
        }

        OntologyDocument candidate = readStoredCandidate(proposal.getPayloadJson());
        OntologyDocument current = drafts.loadDraft(orgId, workspaceId, workspace);
        requireServerAssetsPreserved(current, candidate);
        ProposalDiff recalculatedDiff = diff(
                diff.baseRevision(),
                sanitizeServerAssets(current),
                candidate,
                sha256(proposal.getPayloadJson()));
        if (!Objects.equals(diff, recalculatedDiff)) {
            throw applyInvalid();
        }
        validateRelationCatalog(orgId, workspaceId, candidate);
        OntologyDocument hydratedCandidate = hydrateServerAssets(current, candidate);
        List<OntologyValidationService.ValidationIssue> issues =
                validation.validate(hydratedCandidate, false);
        if (issues == null || issues.stream().anyMatch(issue ->
                issue == null || issue.severity() == OntologyValidationService.Severity.ERROR)) {
            throw applyInvalid();
        }

        drafts.saveDraft(orgId, userId, workspaceId, expectedRevision, hydratedCandidate);
        proposal.markApplied(userId);
        persistence.saveForCurrentOrg(proposal);
        return new ProposalView(
                proposal.getId(),
                workspaceId,
                proposal.getProposalType(),
                proposal.getStatus(),
                diff.baseRevision(),
                candidate,
                diff,
                issues,
                "",
                "",
                proposal.getCreatedAt(),
                proposal.getUpdatedAt(),
                proposal.getAppliedAt());
    }

    private ProposalDiff readStoredDiff(String json) {
        try {
            if (json == null
                    || json.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                throw applyInvalid();
            }
            return strictMapper.readValue(json, ProposalDiff.class);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException
                    && "AI_PROPOSAL_INVALID".equals(exception.getMessage())) {
                throw exception;
            }
            throw applyInvalid();
        } catch (JsonProcessingException exception) {
            throw applyInvalid();
        }
    }

    private OntologyDocument readStoredCandidate(String json) {
        try {
            if (json == null
                    || json.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                throw applyInvalid();
            }
            OntologyDocument candidate = strictMapper.readValue(json, OntologyDocument.class);
            validateDocumentShape(candidate, false);
            validateSafeGeneratedContent(candidate);
            return candidate;
        } catch (RuntimeException exception) {
            throw applyInvalid();
        } catch (JsonProcessingException exception) {
            throw applyInvalid();
        }
    }

    private void requireServerAssetsPreserved(
            OntologyDocument current,
            OntologyDocument candidate) {
        List<OntologyDocument.DataSource> currentSources = sorted(
                sanitizeDataSources(current.dataSources()), value -> value.id() + ":" + value.key());
        List<OntologyDocument.DataSource> candidateSources = sorted(
                candidate.dataSources(), value -> value.id() + ":" + value.key());
        if (!Objects.equals(currentSources, candidateSources)
                || !safe(candidate.mappings()).containsAll(safe(current.mappings()))) {
            throw applyInvalid();
        }
    }

    private OntologyDocument hydrateServerAssets(
            OntologyDocument current,
            OntologyDocument candidate) {
        return new OntologyDocument(
                candidate.key(),
                candidate.name(),
                candidate.description(),
                candidate.concepts(),
                candidate.relations(),
                candidate.metrics(),
                candidate.actions(),
                current.dataSources(),
                candidate.mappings());
    }

    private IllegalArgumentException applyInvalid() {
        return new IllegalArgumentException("AI_PROPOSAL_INVALID");
    }

    private ChatCompletionResult invokeModel(
            String orgId,
            List<Map<String, Object>> messages) {
        Map<String, String> route = modelRouter.route(orgId, SCENE_CODE);
        String provider = requiredRouteValue(route, "provider");
        String modelName = requiredRouteValue(route, "modelName");
        Map<String, String> credentials = modelProviders.credentialsForProvider(orgId, provider);
        if (credentials == null
                || !Boolean.parseBoolean(credentials.getOrDefault("enabled", "false"))
                || isBlank(credentials.get("apiBaseUrl"))
                || (Boolean.parseBoolean(credentials.getOrDefault("apiKeyRequired", "false"))
                && isBlank(credentials.get("apiKey")))) {
            throw new IllegalStateException("Model provider is unavailable");
        }
        return modelClient.chatCompletionWithCredentials(
                modelName,
                messages,
                null,
                true,
                credentials.get("apiBaseUrl"),
                credentials.get("apiKey"),
                MAX_OUTPUT_TOKENS,
                MAX_RESPONSE_BYTES);
    }

    private ProposalView fail(
            String orgId,
            Long workspaceId,
            Long proposalId,
            long baseRevision,
            String code,
            String diagnostic) {
        OntologyAiProposalStateService.Transition transition = proposalState.fail(
                orgId,
                workspaceId,
                proposalId,
                baseRevision,
                code,
                diagnostic);
        return failedView(transition, baseRevision);
    }

    private ProposalView failedView(
            OntologyAiProposalStateService.Transition transition,
            long baseRevision) {
        OntologyAiProposalEntity proposal = transition.proposal();
        return new ProposalView(
                proposal.getId(),
                proposal.getWorkspaceId(),
                proposal.getProposalType(),
                proposal.getStatus(),
                baseRevision,
                null,
                readDiff(proposal.getDiffJson()),
                List.of(),
                transition.diagnosticCode(),
                transition.diagnosticMessage(),
                proposal.getCreatedAt(),
                proposal.getUpdatedAt(),
                proposal.getAppliedAt());
    }

    private OntologyDocument parseResponse(ChatCompletionResult response)
            throws JsonProcessingException {
        if (response == null) {
            throw unavailable("AI_RESPONSE_EMPTY");
        }
        if (response.content() == null || response.content().isBlank()) {
            if (response.content() != null
                    && response.content().getBytes(StandardCharsets.UTF_8).length
                    > MAX_RESPONSE_BYTES) {
                throw invalid("AI_RESPONSE_TOO_LARGE");
            }
            throw unavailable("AI_RESPONSE_EMPTY");
        }
        if (response.content().getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            throw invalid("AI_RESPONSE_TOO_LARGE");
        }
        if ("length".equalsIgnoreCase(response.finishReason())) {
            throw invalid("AI_RESPONSE_TRUNCATED");
        }
        String normalized = response.content().trim().toLowerCase(java.util.Locale.ROOT);
        if (MODEL_ERROR_SENTINELS.contains(normalized)
                || normalized.startsWith("model call failed:")) {
            throw unavailable("AI_MODEL_ERROR_SENTINEL");
        }
        String content = unwrapSingleCodeFence(response.content());
        return strictMapper.readValue(content, OntologyDocument.class);
    }

    private void validateDocumentShape(
            OntologyDocument document,
            boolean allowServerDataSourceConfig) {
        if (document == null) {
            throw invalid("ONTOLOGY_DOCUMENT_REQUIRED");
        }
        requireKey(document.key(), 128);
        requireName(document.name(), 160);
        requireList(document.concepts());
        requireList(document.relations());
        requireList(document.metrics());
        requireList(document.actions());
        requireList(document.dataSources());
        requireList(document.mappings());
        if (document.concepts().size() > 100
                || document.relations().size() > 1_000
                || document.metrics().size() > 1_000
                || document.actions().size() > 1_000
                || document.dataSources().size() > 100
                || document.mappings().size() > 5_000) {
            throw invalid("ONTOLOGY_DOCUMENT_LIMIT_EXCEEDED");
        }
        int totalProperties = 0;
        for (OntologyDocument.Concept concept : document.concepts()) {
            if (concept == null || concept.conceptType() == null) {
                throw invalid("ONTOLOGY_DOCUMENT_STRUCTURE_INVALID");
            }
            requireKey(concept.key(), 128);
            requireName(concept.name(), 160);
            if (concept.pluralName() != null && concept.pluralName().length() > 160) {
                throw invalid("ONTOLOGY_DOCUMENT_LENGTH_INVALID");
            }
            if (concept.displayPropertyKey() != null
                    && concept.displayPropertyKey().length() > 128) {
                throw invalid("ONTOLOGY_DOCUMENT_LENGTH_INVALID");
            }
            if (!Double.isFinite(concept.positionX()) || !Double.isFinite(concept.positionY())) {
                throw invalid("ONTOLOGY_DOCUMENT_NUMBER_INVALID");
            }
            requireList(concept.properties());
            if (concept.properties().size() > 100) {
                throw invalid("ONTOLOGY_DOCUMENT_LIMIT_EXCEEDED");
            }
            totalProperties += concept.properties().size();
            for (OntologyDocument.Property property : concept.properties()) {
                if (property == null || property.dataType() == null) {
                    throw invalid("ONTOLOGY_DOCUMENT_STRUCTURE_INVALID");
                }
                requireKey(property.key(), 128);
                requireName(property.name(), 160);
                requireList(property.enumValues());
                if (property.enumValues().size() > 100) {
                    throw invalid("ONTOLOGY_DOCUMENT_LIMIT_EXCEEDED");
                }
                if (property.dataType() == OntologyDocument.DataType.ENUM
                        && property.enumValues().isEmpty()) {
                    throw invalid("ONTOLOGY_ENUM_VALUES_INVALID");
                }
                if (property.dataType() != OntologyDocument.DataType.ENUM
                        && !property.enumValues().isEmpty()) {
                    throw invalid("ONTOLOGY_ENUM_VALUES_INVALID");
                }
                Set<String> uniqueEnumValues = new LinkedHashSet<>();
                for (String enumValue : property.enumValues()) {
                    requireName(enumValue, 160);
                    if (!uniqueEnumValues.add(enumValue.trim())) {
                        throw invalid("ONTOLOGY_ENUM_VALUES_INVALID");
                    }
                }
            }
        }
        if (totalProperties > 1_000) {
            throw invalid("ONTOLOGY_DOCUMENT_LIMIT_EXCEEDED");
        }
        for (OntologyDocument.Relation relation : document.relations()) {
            if (relation == null || relation.cardinality() == null) {
                throw invalid("ONTOLOGY_DOCUMENT_STRUCTURE_INVALID");
            }
            requireKey(relation.key(), 128);
            requireName(relation.name(), 160);
            requireKey(relation.sourceConceptKey(), 128);
            requireKey(relation.targetConceptKey(), 128);
            requireOptionalLength(relation.forwardLabel(), 160);
            requireOptionalLength(relation.reverseLabel(), 160);
        }
        for (OntologyDocument.Metric metric : document.metrics()) {
            if (metric == null || metric.aggregation() == null) {
                throw invalid("ONTOLOGY_DOCUMENT_STRUCTURE_INVALID");
            }
            requireKey(metric.key(), 128);
            requireName(metric.name(), 160);
            requireKey(metric.conceptKey(), 128);
            requireOptionalLength(metric.measurePropertyKey(), 128);
            requireOptionalLength(metric.timePropertyKey(), 128);
            requireList(metric.groupByPropertyKeys());
            requireList(metric.filters());
            if (metric.groupByPropertyKeys().size() > 100 || metric.filters().size() > 100) {
                throw invalid("ONTOLOGY_DOCUMENT_LIMIT_EXCEEDED");
            }
            metric.groupByPropertyKeys().forEach(value -> requireKey(value, 128));
            for (OntologyDocument.QueryFilter filter : metric.filters()) {
                if (filter == null || filter.operator() == null) {
                    throw invalid("ONTOLOGY_DOCUMENT_STRUCTURE_INVALID");
                }
                requireKey(filter.property(), 128);
                validateFilterValue(filter.value(), 0);
            }
        }
        for (OntologyDocument.Action action : document.actions()) {
            if (action == null) {
                throw invalid("ONTOLOGY_DOCUMENT_STRUCTURE_INVALID");
            }
            requireKey(action.key(), 128);
            requireName(action.name(), 160);
            requireKey(action.conceptKey(), 128);
            requireList(action.parameters());
            if (action.parameters().size() > 100) {
                throw invalid("ONTOLOGY_DOCUMENT_LIMIT_EXCEEDED");
            }
            for (OntologyDocument.ActionParameter parameter : action.parameters()) {
                if (parameter == null || parameter.dataType() == null) {
                    throw invalid("ONTOLOGY_DOCUMENT_STRUCTURE_INVALID");
                }
                requireKey(parameter.key(), 128);
                requireName(parameter.name(), 160);
            }
        }
        for (OntologyDocument.DataSource source : document.dataSources()) {
            if (source == null || source.type() == null) {
                throw invalid("ONTOLOGY_DOCUMENT_STRUCTURE_INVALID");
            }
            requireKey(source.key(), 128);
            requireName(source.name(), 160);
            if (!allowServerDataSourceConfig
                    && (!isBlank(source.configJson()) || !isBlank(source.sampleDataJson()))) {
                throw invalid("AI_DATA_SOURCE_CONFIG_FORBIDDEN");
            }
        }
        for (OntologyDocument.Mapping mapping : document.mappings()) {
            if (mapping == null || mapping.dataSourceId() == null) {
                throw invalid("ONTOLOGY_DOCUMENT_STRUCTURE_INVALID");
            }
            requireName(mapping.targetType(), 32);
            requireName(mapping.targetKey(), 256);
            requireName(mapping.physicalObjectKey(), 256);
            requireOptionalLength(mapping.physicalFieldKey(), 256);
            requireOptionalLength(mapping.relationTargetFieldKey(), 256);
            requireOptionalLength(mapping.transform(), 64);
            requireOptionalLength(mapping.source(), 32);
            requireOptionalLength(mapping.validationStatus(), 32);
            if (!Double.isFinite(mapping.confidence())) {
                throw invalid("ONTOLOGY_DOCUMENT_NUMBER_INVALID");
            }
        }
    }

    private void validateFilterValue(Object value, int depth) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Number) {
            return;
        }
        if (depth >= 2 || !(value instanceof List<?> list) || list.size() > 100) {
            throw invalid("ONTOLOGY_FILTER_VALUE_INVALID");
        }
        list.forEach(item -> validateFilterValue(item, depth + 1));
    }

    private void validateSafeGeneratedContent(OntologyDocument document) {
        List<String> values = new ArrayList<>();
        addText(values, document.name(), document.description());
        for (OntologyDocument.Concept concept : document.concepts()) {
            addText(values, concept.name(), concept.pluralName(), concept.description());
            for (OntologyDocument.Property property : concept.properties()) {
                addText(values, property.name(), property.description());
                values.addAll(property.enumValues());
            }
        }
        for (OntologyDocument.Relation relation : document.relations()) {
            addText(values, relation.name(), relation.description(),
                    relation.forwardLabel(), relation.reverseLabel());
        }
        for (OntologyDocument.Metric metric : document.metrics()) {
            addText(values, metric.name());
            metric.filters().forEach(filter -> addFilterText(values, filter.value()));
        }
        for (OntologyDocument.Action action : document.actions()) {
            addText(values, action.name(), action.description());
            action.parameters().forEach(parameter -> addText(values, parameter.name()));
        }
        if (values.stream().filter(Objects::nonNull).anyMatch(value -> !isStorableText(value))) {
            throw invalid("ONTOLOGY_TEXT_NOT_STORABLE");
        }
        if (values.stream().filter(Objects::nonNull).anyMatch(this::containsUnsafeContent)) {
            throw invalid("AI_GENERATED_CONTENT_UNSAFE");
        }
    }

    private void addFilterText(List<String> values, Object value) {
        if (value instanceof String text) {
            values.add(text);
        } else if (value instanceof List<?> list) {
            list.forEach(item -> addFilterText(values, item));
        }
    }

    private void addText(List<String> values, String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null) {
                values.add(candidate);
            }
        }
    }

    private boolean containsUnsafeContent(String value) {
        return UNSAFE_URL.matcher(value).find()
                || UNSAFE_CREDENTIAL.matcher(value).find()
                || UNSAFE_SQL.matcher(value).find()
                || UNSAFE_SCRIPT.matcher(value).find();
    }

    private void requireKey(String value, int maxLength) {
        if (isBlank(value)
                || !isStorableText(value)
                || value.length() > maxLength
                || !SAFE_KEY.matcher(value).matches()) {
            throw invalid("ONTOLOGY_DOCUMENT_KEY_INVALID");
        }
    }

    private void requireName(String value, int maxLength) {
        if (value != null && !isStorableText(value)) {
            throw invalid("ONTOLOGY_TEXT_NOT_STORABLE");
        }
        if (isBlank(value) || value.length() > maxLength) {
            throw invalid("ONTOLOGY_DOCUMENT_NAME_INVALID");
        }
    }

    private void requireOptionalLength(String value, int maxLength) {
        if (value != null && !isStorableText(value)) {
            throw invalid("ONTOLOGY_TEXT_NOT_STORABLE");
        }
        if (value != null && value.length() > maxLength) {
            throw invalid("ONTOLOGY_DOCUMENT_LENGTH_INVALID");
        }
    }

    private void requireList(List<?> values) {
        if (values == null) {
            throw invalid("ONTOLOGY_DOCUMENT_STRUCTURE_INVALID");
        }
    }

    private String unwrapSingleCodeFence(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLine = trimmed.indexOf('\n');
        if (firstLine < 0) {
            throw new IllegalArgumentException("Invalid JSON code fence");
        }
        String opening = trimmed.substring(0, firstLine).trim();
        if (!"```".equals(opening) && !"```json".equalsIgnoreCase(opening)) {
            throw new IllegalArgumentException("Invalid JSON code fence");
        }
        int closing = trimmed.lastIndexOf("```");
        if (closing <= firstLine || closing != trimmed.length() - 3) {
            throw new IllegalArgumentException("Invalid JSON code fence");
        }
        String body = trimmed.substring(firstLine + 1, closing).trim();
        if (body.contains("```")) {
            throw new IllegalArgumentException("Multiple JSON code fences are not allowed");
        }
        return body;
    }

    private OntologyDocument normalizeCandidate(
            OntologyDocument generated,
            OntologyDocument current,
            String mode,
            AllowedSelection allowedSelection) {
        rejectMappedRelationEndpointChanges(generated, current);
        if (DOMAIN_FIRST.equals(mode)
                && (!safe(generated.dataSources()).isEmpty()
                || !safe(generated.mappings()).isEmpty())) {
            throw new IllegalArgumentException("DOMAIN_FIRST cannot generate data assets");
        }
        if (DATA_SOURCE_FIRST.equals(mode)) {
            validateGeneratedAssetReferences(generated, current, allowedSelection);
        }
        List<OntologyDocument.DataSource> dataSources = sanitizeDataSources(current.dataSources());
        List<OntologyDocument.Mapping> mappings = DOMAIN_FIRST.equals(mode)
                ? current.mappings()
                : mergeMappings(current.mappings(), generated.mappings());
        return new OntologyDocument(
                generated.key(),
                generated.name(),
                generated.description(),
                sorted(generated.concepts(), OntologyDocument.Concept::key).stream()
                        .map(this::normalizeConcept)
                        .toList(),
                sorted(generated.relations(), OntologyDocument.Relation::key),
                sorted(generated.metrics(), OntologyDocument.Metric::key),
                sorted(generated.actions(), OntologyDocument.Action::key),
                sorted(dataSources, OntologyDocument.DataSource::key),
                sorted(mappings, mapping -> mapping.targetType() + ":" + mapping.targetKey()
                        + ":" + mapping.dataSourceId()));
    }

    private OntologyDocument sanitizeServerAssets(OntologyDocument document) {
        return new OntologyDocument(
                document.key(),
                document.name(),
                document.description(),
                document.concepts(),
                document.relations(),
                document.metrics(),
                document.actions(),
                sanitizeDataSources(document.dataSources()),
                document.mappings());
    }

    private List<OntologyDocument.DataSource> sanitizeDataSources(
            List<OntologyDocument.DataSource> sources) {
        return safe(sources).stream()
                .map(source -> new OntologyDocument.DataSource(
                        source.id(), source.key(), source.name(), source.type(), null, null))
                .toList();
    }

    private void requirePayloadBudget(String payloadJson) {
        if (payloadJson == null
                || payloadJson.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            throw invalid("AI_PROPOSAL_PAYLOAD_TOO_LARGE");
        }
    }

    private void rejectMappedRelationEndpointChanges(
            OntologyDocument generated,
            OntologyDocument current) {
        Map<String, OntologyDocument.Relation> currentRelations = new LinkedHashMap<>();
        for (OntologyDocument.Relation relation : safe(current.relations())) {
            currentRelations.put(relation.key(), relation);
        }
        Set<String> mappedRelations = safe(current.mappings()).stream()
                .filter(mapping -> "RELATION".equalsIgnoreCase(mapping.targetType()))
                .map(OntologyDocument.Mapping::targetKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (OntologyDocument.Relation generatedRelation : safe(generated.relations())) {
            OntologyDocument.Relation currentRelation =
                    currentRelations.get(generatedRelation.key());
            if (currentRelation != null
                    && mappedRelations.contains(generatedRelation.key())
                    && (!Objects.equals(
                    currentRelation.sourceConceptKey(), generatedRelation.sourceConceptKey())
                    || !Objects.equals(
                    currentRelation.targetConceptKey(), generatedRelation.targetConceptKey()))) {
                throw invalid("AI_RELATION_ENDPOINT_CHANGE_MAPPED");
            }
        }
    }

    private AllowedSelection prepareSelection(
            String orgId,
            Long workspaceId,
            ProposalCommand command,
            String mode,
            OntologyDocument current) {
        List<SourceSelection> requested = safe(command.selectedSources());
        if (DOMAIN_FIRST.equals(mode)) {
            if (!requested.isEmpty()) {
                throw new IllegalArgumentException("AI_PROPOSAL_INVALID");
            }
            return new AllowedSelection(List.of());
        }
        if (requested.isEmpty() || requested.size() > 100) {
            throw new IllegalArgumentException("AI_PROPOSAL_INVALID");
        }

        LinkedHashSet<String> uniqueSelections = new LinkedHashSet<>();
        List<SelectedMetadata> metadata = new ArrayList<>();
        int totalSelectedFields = 0;
        for (SourceSelection selection : requested) {
            if (selection == null
                    || selection.dataSourceId() == null
                    || isBlank(selection.objectKey())
                    || selection.objectKey().length() > 256
                    || selection.fieldKeys() == null
                    || selection.fieldKeys().isEmpty()
                    || selection.fieldKeys().size() > 100) {
                throw new IllegalArgumentException("AI_PROPOSAL_INVALID");
            }
            totalSelectedFields += selection.fieldKeys().size();
            if (totalSelectedFields > MAX_SELECTED_FIELDS) {
                throw new IllegalArgumentException("AI_PROPOSAL_INVALID");
            }
            String uniqueKey = selection.dataSourceId() + "\u0000" + selection.objectKey();
            if (!uniqueSelections.add(uniqueKey)) {
                throw new IllegalArgumentException("AI_PROPOSAL_INVALID");
            }
            OntologyDocument.DataSource source = safe(current.dataSources()).stream()
                    .filter(value -> Objects.equals(value.id(), selection.dataSourceId()))
                    .reduce((first, second) -> {
                        throw new IllegalArgumentException("AI_PROPOSAL_INVALID");
                    })
                    .orElseThrow(() -> new IllegalArgumentException("AI_PROPOSAL_INVALID"));
            OntologyPhysicalObjectEntity object = physicalObjects
                    .findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                            source.id(), workspaceId, orgId).stream()
                    .filter(value -> Objects.equals(value.getObjectKey(), selection.objectKey()))
                    .reduce((first, second) -> {
                        throw new IllegalArgumentException("AI_PROPOSAL_INVALID");
                    })
                    .orElseThrow(() -> new IllegalArgumentException("AI_PROPOSAL_INVALID"));
            Map<String, OntologyPhysicalFieldEntity> availableFields = new LinkedHashMap<>();
            for (OntologyPhysicalFieldEntity field : physicalFields
                    .findByPhysicalObjectIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                            object.getId(), workspaceId, orgId)) {
                if (availableFields.putIfAbsent(field.getFieldKey(), field) != null) {
                    throw new IllegalArgumentException("AI_PROPOSAL_INVALID");
                }
            }
            LinkedHashSet<String> uniqueFields = new LinkedHashSet<>();
            List<SelectedField> fields = new ArrayList<>();
            for (String fieldKey : selection.fieldKeys()) {
                if (isBlank(fieldKey) || !uniqueFields.add(fieldKey)) {
                    throw new IllegalArgumentException("AI_PROPOSAL_INVALID");
                }
                OntologyPhysicalFieldEntity field = availableFields.get(fieldKey);
                if (field == null) {
                    throw new IllegalArgumentException("AI_PROPOSAL_INVALID");
                }
                fields.add(new SelectedField(
                        field.getFieldKey(),
                        field.getName(),
                        field.getDataType(),
                        field.isNullable(),
                        field.isMultiple()));
            }
            metadata.add(new SelectedMetadata(
                    source.id(),
                    source.key(),
                    source.name(),
                    source.type().name(),
                    object.getObjectKey(),
                    object.getName(),
                    object.getObjectType(),
                    List.copyOf(fields)));
        }
        return new AllowedSelection(List.copyOf(metadata));
    }

    private void validateGeneratedAssetReferences(
            OntologyDocument generated,
            OntologyDocument current,
            AllowedSelection allowedSelection) {
        Map<Long, OntologyDocument.DataSource> serverSources = new LinkedHashMap<>();
        safe(current.dataSources()).forEach(source -> serverSources.put(source.id(), source));
        List<OntologyDocument.Mapping> effectiveMappings =
                mergeMappings(current.mappings(), generated.mappings());
        Map<String, OntologyDocument.Relation> generatedRelations = new LinkedHashMap<>();
        for (OntologyDocument.Relation relation : safe(generated.relations())) {
            if (generatedRelations.putIfAbsent(relation.key(), relation) != null) {
                throw invalid("AI_MAPPING_REFERENCE_NOT_ALLOWED");
            }
        }
        Set<Long> allowedSourceIds = allowedSelection.metadata().stream()
                .map(SelectedMetadata::dataSourceId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (OntologyDocument.DataSource generatedSource : generated.dataSources()) {
            OntologyDocument.DataSource server = serverSources.get(generatedSource.id());
            if (server == null
                    || !allowedSourceIds.contains(generatedSource.id())
                    || !Objects.equals(server.key(), generatedSource.key())
                    || !Objects.equals(server.name(), generatedSource.name())
                    || server.type() != generatedSource.type()) {
                throw invalid("AI_DATA_SOURCE_REFERENCE_NOT_ALLOWED");
            }
        }
        for (OntologyDocument.Mapping mapping : generated.mappings()) {
            List<SelectedMetadata> sourceMetadata = allowedSelection.metadata().stream()
                    .filter(item -> Objects.equals(item.dataSourceId(), mapping.dataSourceId()))
                    .toList();
            Optional<SelectedMetadata> selectedObject = sourceMetadata.stream()
                    .filter(item -> Objects.equals(item.objectKey(), mapping.physicalObjectKey()))
                    .findFirst();
            if (selectedObject.isEmpty()
                    || (mapping.physicalFieldKey() != null
                    && selectedObject.get().fields().stream().noneMatch(field ->
                            Objects.equals(field.fieldKey(), mapping.physicalFieldKey())))) {
                throw invalid("AI_MAPPING_REFERENCE_NOT_ALLOWED");
            }
            if ("RELATION".equalsIgnoreCase(mapping.targetType())) {
                OntologyDocument.Relation relation = generatedRelations.get(mapping.targetKey());
                if (relation == null || isBlank(mapping.relationTargetFieldKey())) {
                    throw invalid("AI_MAPPING_REFERENCE_NOT_ALLOWED");
                }
                List<OntologyDocument.Mapping> sourceConceptMappings = effectiveMappings.stream()
                        .filter(candidate -> "CONCEPT".equalsIgnoreCase(candidate.targetType()))
                        .filter(candidate -> Objects.equals(
                                candidate.targetKey(), relation.sourceConceptKey()))
                        .filter(candidate -> Objects.equals(
                                candidate.dataSourceId(), mapping.dataSourceId()))
                        .toList();
                List<OntologyDocument.Mapping> targetConceptMappings = effectiveMappings.stream()
                        .filter(candidate -> "CONCEPT".equalsIgnoreCase(candidate.targetType()))
                        .filter(candidate -> Objects.equals(
                                candidate.targetKey(), relation.targetConceptKey()))
                        .filter(candidate -> Objects.equals(
                                candidate.dataSourceId(), mapping.dataSourceId()))
                        .toList();
                if (sourceConceptMappings.size() != 1
                        || targetConceptMappings.size() != 1
                        || !Objects.equals(
                        mapping.physicalObjectKey(),
                        sourceConceptMappings.getFirst().physicalObjectKey())) {
                    throw invalid("AI_MAPPING_REFERENCE_NOT_ALLOWED");
                }
                String targetObjectKey = targetConceptMappings.getFirst().physicalObjectKey();
                List<SelectedMetadata> targetObjects = sourceMetadata.stream()
                        .filter(item -> Objects.equals(item.objectKey(), targetObjectKey))
                        .toList();
                if (targetObjects.size() != 1
                        || targetObjects.getFirst().fields().stream().noneMatch(field ->
                        Objects.equals(field.fieldKey(), mapping.relationTargetFieldKey()))) {
                    throw invalid("AI_MAPPING_REFERENCE_NOT_ALLOWED");
                }
            } else if (mapping.relationTargetFieldKey() != null) {
                throw invalid("AI_MAPPING_REFERENCE_NOT_ALLOWED");
            }
        }
    }

    private void validateRelationCatalog(
            String orgId,
            Long workspaceId,
            OntologyDocument candidate) {
        Map<String, OntologyDocument.Relation> relations = new LinkedHashMap<>();
        for (OntologyDocument.Relation relation : safe(candidate.relations())) {
            if (relations.putIfAbsent(relation.key(), relation) != null) {
                throw applyInvalid();
            }
        }
        Map<Long, Map<String, OntologyPhysicalObjectEntity>> objectCatalogs =
                new LinkedHashMap<>();
        Map<Long, Set<String>> fieldCatalogs = new LinkedHashMap<>();
        for (OntologyDocument.Mapping mapping : safe(candidate.mappings())) {
            if (!"RELATION".equalsIgnoreCase(mapping.targetType())) {
                continue;
            }
            OntologyDocument.Relation relation = relations.get(mapping.targetKey());
            if (relation == null || isBlank(mapping.relationTargetFieldKey())) {
                throw applyInvalid();
            }
            OntologyDocument.Mapping sourceConceptMapping = uniqueConceptMapping(
                    candidate,
                    mapping.dataSourceId(),
                    relation.sourceConceptKey());
            OntologyDocument.Mapping targetConceptMapping = uniqueConceptMapping(
                    candidate,
                    mapping.dataSourceId(),
                    relation.targetConceptKey());
            if (!Objects.equals(
                    mapping.physicalObjectKey(),
                    sourceConceptMapping.physicalObjectKey())) {
                throw applyInvalid();
            }

            Map<String, OntologyPhysicalObjectEntity> objects = objectCatalogs.computeIfAbsent(
                    mapping.dataSourceId(),
                    dataSourceId -> loadPhysicalObjects(orgId, workspaceId, dataSourceId));
            OntologyPhysicalObjectEntity sourceObject = objects.get(mapping.physicalObjectKey());
            OntologyPhysicalObjectEntity targetObject =
                    objects.get(targetConceptMapping.physicalObjectKey());
            if (sourceObject == null
                    || targetObject == null
                    || !physicalFieldsContain(
                    orgId,
                    workspaceId,
                    sourceObject,
                    mapping.physicalFieldKey(),
                    fieldCatalogs)
                    || !physicalFieldsContain(
                    orgId,
                    workspaceId,
                    targetObject,
                    mapping.relationTargetFieldKey(),
                    fieldCatalogs)) {
                throw applyInvalid();
            }
        }
    }

    private OntologyDocument.Mapping uniqueConceptMapping(
            OntologyDocument candidate,
            Long dataSourceId,
            String conceptKey) {
        List<OntologyDocument.Mapping> matches = safe(candidate.mappings()).stream()
                .filter(mapping -> "CONCEPT".equalsIgnoreCase(mapping.targetType()))
                .filter(mapping -> Objects.equals(mapping.targetKey(), conceptKey))
                .filter(mapping -> Objects.equals(mapping.dataSourceId(), dataSourceId))
                .toList();
        if (matches.size() != 1 || isBlank(matches.getFirst().physicalObjectKey())) {
            throw applyInvalid();
        }
        return matches.getFirst();
    }

    private Map<String, OntologyPhysicalObjectEntity> loadPhysicalObjects(
            String orgId,
            Long workspaceId,
            Long dataSourceId) {
        Map<String, OntologyPhysicalObjectEntity> objects = new LinkedHashMap<>();
        for (OntologyPhysicalObjectEntity object :
                physicalObjects.findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                        dataSourceId, workspaceId, orgId)) {
            if (objects.putIfAbsent(object.getObjectKey(), object) != null) {
                throw applyInvalid();
            }
        }
        return objects;
    }

    private boolean physicalFieldsContain(
            String orgId,
            Long workspaceId,
            OntologyPhysicalObjectEntity object,
            String fieldKey,
            Map<Long, Set<String>> fieldCatalogs) {
        if (isBlank(fieldKey)) {
            return false;
        }
        Set<String> fields = fieldCatalogs.computeIfAbsent(object.getId(), objectId -> {
            Set<String> loaded = new LinkedHashSet<>();
            for (OntologyPhysicalFieldEntity field :
                    physicalFields.findByPhysicalObjectIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                            objectId, workspaceId, orgId)) {
                if (!loaded.add(field.getFieldKey())) {
                    throw applyInvalid();
                }
            }
            return Set.copyOf(loaded);
        });
        return fields.contains(fieldKey);
    }

    private List<OntologyDocument.Mapping> mergeMappings(
            List<OntologyDocument.Mapping> existing,
            List<OntologyDocument.Mapping> generated) {
        Map<String, OntologyDocument.Mapping> merged = new LinkedHashMap<>();
        safe(existing).forEach(mapping -> merged.put(mappingIdentity(mapping), mapping));
        for (OntologyDocument.Mapping mapping : safe(generated)) {
            merged.putIfAbsent(mappingIdentity(mapping), new OntologyDocument.Mapping(
                    mapping.targetType(),
                    mapping.targetKey(),
                    mapping.dataSourceId(),
                    mapping.physicalObjectKey(),
                    mapping.physicalFieldKey(),
                    mapping.relationTargetFieldKey(),
                    mapping.transform(),
                    mapping.confidence(),
                    "AI",
                    "PENDING"));
        }
        return List.copyOf(merged.values());
    }

    private String mappingIdentity(OntologyDocument.Mapping mapping) {
        return mapping.targetType().trim().toUpperCase(java.util.Locale.ROOT)
                + "\u0000" + mapping.targetKey() + "\u0000" + mapping.dataSourceId();
    }

    private ProposalDiff diff(
            long baseRevision,
            OntologyDocument current,
            OntologyDocument candidate,
            String candidateHash) {
        Map<String, String> before = stableElements(current);
        Map<String, String> after = stableElements(candidate);
        List<String> added = after.keySet().stream()
                .filter(key -> !before.containsKey(key))
                .sorted()
                .toList();
        List<String> removed = before.keySet().stream()
                .filter(key -> !after.containsKey(key))
                .sorted()
                .toList();
        List<String> changed = after.keySet().stream()
                .filter(before::containsKey)
                .filter(key -> !Objects.equals(before.get(key), after.get(key)))
                .sorted()
                .toList();
        return new ProposalDiff(baseRevision, candidateHash, added, changed, removed);
    }

    private Map<String, String> stableElements(OntologyDocument document) {
        Map<String, String> elements = new LinkedHashMap<>();
        elements.put("document:" + document.key(), writeJson(new DocumentElement(
                document.key(), document.name(), document.description())));
        for (OntologyDocument.Concept concept : safe(document.concepts())) {
            elements.put("concept:" + concept.key(), writeJson(new ConceptElement(
                    concept.key(),
                    concept.name(),
                    concept.pluralName(),
                    concept.description(),
                    concept.conceptType(),
                    concept.displayPropertyKey(),
                    concept.positionX(),
                    concept.positionY(),
                    concept.queryable(),
                    concept.enabled())));
            for (OntologyDocument.Property property : safe(concept.properties())) {
                elements.put("property:" + concept.key() + "." + property.key(),
                        writeJson(property));
            }
        }
        safe(document.relations()).forEach(value ->
                elements.put("relation:" + value.key(), writeJson(value)));
        safe(document.metrics()).forEach(value ->
                elements.put("metric:" + value.key(), writeJson(value)));
        safe(document.actions()).forEach(value ->
                elements.put("action:" + value.key(), writeJson(value)));
        safe(document.dataSources()).forEach(value ->
                elements.put("dataSource:" + value.id() + ":" + value.key(), writeJson(value)));
        safe(document.mappings()).forEach(value ->
                elements.put("mapping:" + mappingIdentity(value).replace('\u0000', ':'), writeJson(value)));
        return Map.copyOf(elements);
    }

    private OntologyDocument.Concept normalizeConcept(OntologyDocument.Concept concept) {
        return new OntologyDocument.Concept(
                concept.key(),
                concept.name().trim(),
                trimNullable(concept.pluralName()),
                trimNullable(concept.description()),
                concept.conceptType(),
                trimNullable(concept.displayPropertyKey()),
                concept.positionX(),
                concept.positionY(),
                concept.queryable(),
                concept.enabled(),
                sorted(concept.properties(), OntologyDocument.Property::key).stream()
                        .map(property -> new OntologyDocument.Property(
                                property.key(),
                                property.name().trim(),
                                trimNullable(property.description()),
                                property.dataType(),
                                property.required(),
                                property.multiple(),
                                property.sensitive(),
                                property.queryable(),
                                property.enumValues().stream().map(String::trim).toList()))
                        .toList());
    }

    private String trimNullable(String value) {
        return value == null ? null : value.trim();
    }

    private String requiredRouteValue(Map<String, String> route, String key) {
        String value = route == null ? null : route.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Model route is unavailable");
        }
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ProposalFailure invalid(String diagnostic) {
        return new ProposalFailure("AI_PROPOSAL_INVALID", diagnostic);
    }

    private ProposalFailure unavailable(String diagnostic) {
        return new ProposalFailure("AI_MODEL_UNAVAILABLE", diagnostic);
    }

    private String requireInstruction(String instruction) {
        if (instruction == null
                || instruction.isBlank()
                || instruction.length() > 12_000
                || !isStorableText(instruction)
                || containsUnsafeContent(instruction)) {
            throw new IllegalArgumentException("AI_PROPOSAL_INVALID");
        }
        return instruction.trim();
    }

    private boolean isStorableText(String value) {
        if (value == null) {
            return true;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
                continue;
            }
            if (Character.isLowSurrogate(current)
                    || current == '\u0000'
                    || (Character.isISOControl(current)
                    && current != '\n'
                    && current != '\r'
                    && current != '\t')) {
                return false;
            }
        }
        return true;
    }

    private String normalizeMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toUpperCase(java.util.Locale.ROOT);
        if (!DOMAIN_FIRST.equals(normalized) && !DATA_SOURCE_FIRST.equals(normalized)) {
            throw new IllegalArgumentException("AI_PROPOSAL_INVALID");
        }
        return normalized;
    }

    private void requireCurrentContext(String orgId, String userId) {
        if (!Objects.equals(TenantContext.requireOrgId(), orgId)
                || userId == null
                || userId.isBlank()
                || TenantContext.getUserId().filter(userId::equals).isEmpty()) {
            throw new ForbiddenException("AI_PROPOSAL_INVALID");
        }
    }

    private ProposalDiff readDiff(String json) {
        try {
            return objectMapper.readValue(json, ProposalDiff.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI_PROPOSAL_INVALID", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI_PROPOSAL_INVALID", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static <T> List<T> sorted(
            List<T> values,
            java.util.function.Function<T, String> key) {
        return safe(values).stream().sorted(Comparator.comparing(key)).toList();
    }

    public record ProposalCommand(
            String instruction,
            List<SourceSelection> selectedSources,
            String mode) {
    }

    public record SourceSelection(
            Long dataSourceId,
            String objectKey,
            List<String> fieldKeys) {
    }

    public record ProposalDiff(
            long baseRevision,
            String candidateHash,
            List<String> added,
            List<String> changed,
            List<String> removed) {
    }

    public record ProposalView(
            Long id,
            Long workspaceId,
            String proposalType,
            String status,
            long baseRevision,
            OntologyDocument candidate,
            ProposalDiff diff,
            List<OntologyValidationService.ValidationIssue> validation,
            String diagnosticCode,
            String diagnosticMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant appliedAt) {
    }

    private record PreparedProposal(
            AllowedSelection allowedSelection,
            List<Map<String, Object>> messages) {
    }

    private record AllowedSelection(List<SelectedMetadata> metadata) {
    }

    private record SelectedMetadata(
            Long dataSourceId,
            String dataSourceKey,
            String dataSourceName,
            String sourceType,
            String objectKey,
            String objectName,
            String objectType,
            List<SelectedField> fields) {
    }

    private record SelectedField(
            String fieldKey,
            String fieldName,
            String dataType,
            boolean nullable,
            boolean multiple) {
    }

    private record DocumentElement(String key, String name, String description) {
    }

    private record ConceptElement(
            String key,
            String name,
            String pluralName,
            String description,
            OntologyDocument.ConceptType conceptType,
            String displayPropertyKey,
            double positionX,
            double positionY,
            boolean queryable,
            boolean enabled) {
    }

    private static final class ProposalFailure extends RuntimeException {
        private final String code;
        private final String diagnostic;

        private ProposalFailure(String code, String diagnostic) {
            super(code);
            this.code = code;
            this.diagnostic = diagnostic;
        }

        private String code() {
            return code;
        }

        private String diagnostic() {
            return diagnostic;
        }
    }
}
