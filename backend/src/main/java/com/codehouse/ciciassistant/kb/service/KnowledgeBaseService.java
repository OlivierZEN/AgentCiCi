package com.codehouse.ciciassistant.kb.service;

import com.codehouse.ciciassistant.agent.domain.AgentKnowledgeBindingRepository;
import com.codehouse.ciciassistant.ai.service.RagService;
import com.codehouse.ciciassistant.billing.service.BillingUsageMeteringService;
import com.codehouse.ciciassistant.kb.domain.KbChunkEntity;
import com.codehouse.ciciassistant.kb.domain.KbChunkRepository;
import com.codehouse.ciciassistant.kb.domain.KbDocumentMetadataEntity;
import com.codehouse.ciciassistant.kb.domain.KbDocumentMetadataRepository;
import com.codehouse.ciciassistant.kb.domain.KbDocumentEntity;
import com.codehouse.ciciassistant.kb.domain.KbDocumentRepository;
import com.codehouse.ciciassistant.kb.domain.KbEvalCaseEntity;
import com.codehouse.ciciassistant.kb.domain.KbEvalCaseRepository;
import com.codehouse.ciciassistant.kb.domain.KbEvalCaseResultEntity;
import com.codehouse.ciciassistant.kb.domain.KbEvalCaseResultRepository;
import com.codehouse.ciciassistant.kb.domain.KbEvalRunEntity;
import com.codehouse.ciciassistant.kb.domain.KbEvalRunRepository;
import com.codehouse.ciciassistant.kb.domain.KbEvalSuiteEntity;
import com.codehouse.ciciassistant.kb.domain.KbEvalSuiteRepository;
import com.codehouse.ciciassistant.kb.domain.KbMetadataFieldEntity;
import com.codehouse.ciciassistant.kb.domain.KbMetadataFieldRepository;
import com.codehouse.ciciassistant.kb.domain.KbRetrievalLogEntity;
import com.codehouse.ciciassistant.kb.domain.KbRetrievalLogRepository;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseEntity;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseRepository;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.codehouse.ciciassistant.kb.config.KbAsyncConfig;

@Service
public class KnowledgeBaseService {

    private static final int DEFAULT_CHUNK_SIZE = 280;
    private static final int DEFAULT_CHUNK_OVERLAP = 40;
    private static final int DEFAULT_TOP_K = 5;

    private final KnowledgeBaseRepository kbRepository;
    private final KbDocumentRepository documentRepository;
    private final KbChunkRepository chunkRepository;
    private final KbMetadataFieldRepository metadataFieldRepository;
    private final KbDocumentMetadataRepository documentMetadataRepository;
    private final KbRetrievalLogRepository retrievalLogRepository;
    private final KbEvalSuiteRepository evalSuiteRepository;
    private final KbEvalCaseRepository evalCaseRepository;
    private final KbEvalRunRepository evalRunRepository;
    private final KbEvalCaseResultRepository evalCaseResultRepository;
    private final AgentKnowledgeBindingRepository agentKnowledgeBindingRepository;
    private final ModelProviderService modelProviderService;
    private final BillingUsageMeteringService billingUsageMeteringService;
    private final VectorStoreClient vectorStoreClient;
    private final EmbeddingService embeddingService;
    private final KbAccessControlService kbAccessControlService;
    private final RagService ragService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String indexingMode;
    private final String defaultEmbeddingProvider;
    private final String defaultEmbeddingModel;
    private final Integer defaultEmbeddingDimension;
    private final long maxUploadFileSizeBytes;
    private final Set<String> allowedUploadExtensions;
    private final Set<String> allowedUploadContentTypes;
    private final Path storageRoot;

    public KnowledgeBaseService(KnowledgeBaseRepository kbRepository,
                                KbDocumentRepository documentRepository,
                                KbChunkRepository chunkRepository,
                                KbMetadataFieldRepository metadataFieldRepository,
                                KbDocumentMetadataRepository documentMetadataRepository,
                                KbRetrievalLogRepository retrievalLogRepository,
                                KbEvalSuiteRepository evalSuiteRepository,
                                KbEvalCaseRepository evalCaseRepository,
                                KbEvalRunRepository evalRunRepository,
                                KbEvalCaseResultRepository evalCaseResultRepository,
                                AgentKnowledgeBindingRepository agentKnowledgeBindingRepository,
                                ModelProviderService modelProviderService,
                                BillingUsageMeteringService billingUsageMeteringService,
                                VectorStoreClient vectorStoreClient,
                                EmbeddingService embeddingService,
                                KbAccessControlService kbAccessControlService,
                                RagService ragService,
                                RabbitTemplate rabbitTemplate,
                                ObjectMapper objectMapper,
                                @Value("${app.kb.storage-dir:./data/kb-files}") String storageDir,
                                @Value("${app.kb.indexing.mode:local}") String indexingMode,
                                @Value("${app.kb.embedding.provider:local}") String defaultEmbeddingProvider,
                                @Value("${app.kb.embedding.model:local-hash}") String defaultEmbeddingModel,
                                @Value("${app.kb.embedding.dimension:1024}") Integer defaultEmbeddingDimension,
                                @Value("${app.kb.upload.max-file-size-bytes:26214400}") Long maxUploadFileSizeBytes,
                                @Value("${app.kb.upload.allowed-extensions:txt,md,markdown,csv,json,docx,pdf}") String allowedUploadExtensions,
                                @Value("${app.kb.upload.allowed-content-types:text/plain,text/markdown,text/csv,application/csv,application/json,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/pdf}") String allowedUploadContentTypes) {
        this.kbRepository = kbRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.metadataFieldRepository = metadataFieldRepository;
        this.documentMetadataRepository = documentMetadataRepository;
        this.retrievalLogRepository = retrievalLogRepository;
        this.evalSuiteRepository = evalSuiteRepository;
        this.evalCaseRepository = evalCaseRepository;
        this.evalRunRepository = evalRunRepository;
        this.evalCaseResultRepository = evalCaseResultRepository;
        this.agentKnowledgeBindingRepository = agentKnowledgeBindingRepository;
        this.modelProviderService = modelProviderService;
        this.billingUsageMeteringService = billingUsageMeteringService;
        this.vectorStoreClient = vectorStoreClient;
        this.embeddingService = embeddingService;
        this.kbAccessControlService = kbAccessControlService;
        this.ragService = ragService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.indexingMode = indexingMode;
        this.defaultEmbeddingProvider = normalizeEmbeddingProvider(defaultEmbeddingProvider);
        this.defaultEmbeddingModel = normalizeEmbeddingModel(defaultEmbeddingModel);
        this.defaultEmbeddingDimension = sanitizeEmbeddingDimension(defaultEmbeddingDimension);
        this.maxUploadFileSizeBytes = Math.max(1L, maxUploadFileSizeBytes == null ? 26_214_400L : maxUploadFileSizeBytes);
        this.allowedUploadExtensions = normalizeCsvSet(allowedUploadExtensions);
        this.allowedUploadContentTypes = normalizeCsvSet(allowedUploadContentTypes);
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public Map<String, Object> createKnowledgeBase(String orgId, String name, String description) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity(orgId, name, description);
        entity.updateKnowledgeSettings(
                entity.getChunkSize(),
                entity.getChunkOverlap(),
                entity.getChunkDelimiter(),
                entity.getRetrievalStrategy(),
                entity.getTopK(),
                entity.getScoreThreshold(),
                defaultEmbeddingProvider,
                defaultEmbeddingModel,
                defaultEmbeddingDimension);
        entity = kbRepository.save(entity);
        return kbPayload(entity);
    }

    @Transactional
    public Map<String, Object> updateKnowledgeBase(String orgId, Long id, String name, String description) {
        KnowledgeBaseEntity kb = kbRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
        kb.update(name, description, kb.getStatus());
        kbRepository.save(kb);
        return kbPayload(kb);
    }

    @Transactional
    public Map<String, Object> getKnowledgeBaseSettings(String orgId, Long kbId) {
        KnowledgeBaseEntity kb = kbRepository.findByIdAndOrgId(kbId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
        return kbSettingsPayload(kb);
    }

    @Transactional
    public Map<String, Object> updateKnowledgeBaseSettings(String orgId, Long kbId, KbSettingsCommand command) {
        KnowledgeBaseEntity kb = kbRepository.findByIdAndOrgId(kbId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
        int chunkSize = sanitizeChunkSize(command.chunkSize());
        int chunkOverlap = sanitizeChunkOverlap(chunkSize, command.chunkOverlap());
        String chunkDelimiter = normalizeDelimiter(command.chunkDelimiter());
        int topK = sanitizeTopK(command.topK());
        double scoreThreshold = sanitizeScoreThreshold(command.scoreThreshold());
        String retrievalStrategy = normalizeRetrievalStrategy(command.retrievalStrategy());
        String embeddingProvider = normalizeEmbeddingProvider(command.embeddingProvider());
        String embeddingModel = normalizeEmbeddingModel(command.embeddingModel());
        int embeddingDimension = sanitizeEmbeddingDimension(command.embeddingDimension());
        kb.updateKnowledgeSettings(
                chunkSize,
                chunkOverlap,
                chunkDelimiter,
                retrievalStrategy,
                topK,
                scoreThreshold,
                embeddingProvider,
                embeddingModel,
                embeddingDimension);
        kbRepository.save(kb);
        return kbSettingsPayload(kb);
    }

    @Transactional
    public List<Map<String, Object>> listEmbeddingModelOptions(String orgId) {
        return modelProviderService.embeddingModelOptions(orgId);
    }

    public Map<String, Object> uploadPolicy(String orgId) {
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("maxFileSizeBytes", maxUploadFileSizeBytes);
        payload.put("maxFilesPerUpload", 1);
        payload.put("allowedExtensions", allowedUploadExtensions.stream().sorted().toList());
        payload.put("allowedContentTypes", allowedUploadContentTypes.stream().sorted().toList());
        payload.put("supportedParserLabels", List.of("TXT", "Markdown", "CSV", "JSON", "DOCX", "PDF"));
        payload.put("unsupportedParserLabels", List.of());
        payload.put("pdfPolicy", "Text-based PDF parsing is enabled. Encrypted, scanned, malformed, or empty-text PDFs fail with a clear parser error.");
        payload.put("sourceTypes", List.of(
                Map.of("code", "LOCAL_FILE", "status", "available"),
                Map.of("code", "EMPTY", "status", "available_via_manual_chunks"),
                Map.of("code", "WEB", "status", "planned"),
                Map.of("code", "NOTION", "status", "planned"),
                Map.of("code", "EXTERNAL_API", "status", "planned")
        ));
        payload.put("serviceApi", Map.of(
                "status", "planned",
                "apiAccessEnabled", false,
                "message", "Knowledge Service API keys are not issued in this build; use authenticated admin APIs."
        ));
        return payload;
    }

    public List<Map<String, Object>> listKnowledgeBases(String orgId) {
        ArrayList<Map<String, Object>> out = new ArrayList<>();
        for (KnowledgeBaseEntity item : kbRepository.findByOrgIdAndStatusNotOrderByIdDesc(orgId, "DELETED")) {
            HashMap<String, Object> row = new HashMap<>(kbPayload(item));
            row.put("documentCount", documentRepository.countByOrgIdAndKnowledgeBaseIdAndStatusNot(
                    orgId, item.getId(), "DELETED"));
            row.put("publishedDocumentCount", documentRepository.countByOrgIdAndKnowledgeBaseIdAndStatus(
                    orgId, item.getId(), "PUBLISHED"));
            row.put("chunkCount", chunkRepository.countByOrgIdAndKnowledgeBaseIdAndStatusAndEnabledTrue(
                    orgId, String.valueOf(item.getId()), "ACTIVE"));
            out.add(row);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> uploadDocument(String orgId, Long kbId, MultipartFile file) {
        KnowledgeBaseEntity kb = kbRepository.findByIdAndOrgId(kbId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
        UploadAdmission admission = validateUploadAdmission(file);
        try {
            Files.createDirectories(storageRoot.resolve(orgId).resolve(String.valueOf(kbId)));
            String safeName = UUID.randomUUID() + "-" + admission.safeFilename();
            Path path = storageRoot.resolve(orgId).resolve(String.valueOf(kbId)).resolve(safeName);
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

            KbDocumentEntity doc = documentRepository.save(new KbDocumentEntity(
                    orgId,
                    kb.getId(),
                    admission.originalFilename(),
                    admission.contentType(),
                    path.toString(),
                    file.getSize()
            ));
            return documentPayload(doc);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to store file: " + ex.getMessage());
        }
    }

    public List<Map<String, Object>> listDocuments(String orgId, Long kbId) {
        return documentRepository.findByOrgIdAndKnowledgeBaseIdAndStatusNotOrderByIdDesc(orgId, kbId, "DELETED")
                .stream()
                .map(this::documentPayload)
                .toList();
    }

    @Transactional
    public Map<String, Object> renameDocument(String orgId, Long documentId, String name) {
        KbDocumentEntity doc = documentRepository.findByIdAndOrgId(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        doc.rename(name);
        documentRepository.save(doc);
        return documentPayload(doc);
    }

    @Transactional
    public Map<String, Object> setDocumentEnabled(String orgId, Long documentId, boolean enabled) {
        KbDocumentEntity doc = documentRepository.findByIdAndOrgId(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        doc.setEnabled(enabled);
        documentRepository.save(doc);
        return documentPayload(doc);
    }

    @Transactional
    public Map<String, Object> setDocumentArchived(String orgId, Long documentId, boolean archived) {
        KbDocumentEntity doc = documentRepository.findByIdAndOrgId(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        doc.setArchived(archived);
        documentRepository.save(doc);
        return documentPayload(doc);
    }

    @Transactional
    public Map<String, Object> batchSetDocumentEnabled(String orgId, List<Long> documentIds, boolean enabled) {
        return batchOperateDocuments(orgId, documentIds, enabled ? "enable" : "disable");
    }

    @Transactional
    public Map<String, Object> batchSetDocumentArchived(String orgId, List<Long> documentIds, boolean archived) {
        return batchOperateDocuments(orgId, documentIds, archived ? "archive" : "unarchive");
    }

    @Transactional
    public Map<String, Object> batchDeleteDocuments(String orgId, List<Long> documentIds) {
        return batchOperateDocuments(orgId, documentIds, "delete");
    }

    @Transactional
    public List<Map<String, Object>> listDocumentChunks(String orgId, Long documentId) {
        KbDocumentEntity doc = documentRepository.findByIdAndOrgId(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return chunkRepository.findByOrgIdAndDocumentIdAndStatusNotOrderByChunkIndexAscIdAsc(orgId, doc.getId(), "DELETED")
                .stream()
                .map(this::chunkPayload)
                .toList();
    }

    @Transactional
    public Map<String, Object> updateChunk(String orgId, Long chunkId, String content) {
        KbChunkEntity chunk = chunkRepository.findByIdAndOrgId(chunkId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Chunk not found"));
        if ("DELETED".equals(chunk.getStatus())) {
            throw new IllegalArgumentException("Chunk already deleted");
        }
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Chunk content is required");
        }
        String oldVectorId = chunk.getVectorId();
        if (oldVectorId != null && !oldVectorId.isBlank()) {
            vectorStoreClient.deleteByVectorIds(orgId, List.of(oldVectorId));
        }
        String contentHash = sha256(normalized);
        EmbeddingConfig embeddingConfig = embeddingConfigForKnowledgeBase(orgId, chunk.getKnowledgeBaseId());
        String vectorId = vectorStoreClient.upsert(new VectorUpsertCommand(
                orgId,
                chunk.getKnowledgeBaseId(),
                chunk.getDocumentId(),
                chunk.getId(),
                chunk.getChunkIndex(),
                normalized,
                contentHash,
                embeddingService.embed(
                        orgId,
                        embeddingConfig.provider(),
                        embeddingConfig.model(),
                        embeddingConfig.dimension(),
                        normalized)));
        chunk.updateContent(normalized, contentHash);
        chunk.setVectorId(vectorId);
        chunkRepository.save(chunk);
        recordKbChunkIndexingSafely(orgId, chunk.getKnowledgeBaseId(), chunk.getId(), contentHash, "chunk_update");
        return chunkPayload(chunk);
    }

    @Transactional
    public Map<String, Object> setChunkEnabled(String orgId, Long chunkId, boolean enabled) {
        KbChunkEntity chunk = chunkRepository.findByIdAndOrgId(chunkId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Chunk not found"));
        if ("DELETED".equals(chunk.getStatus())) {
            throw new IllegalArgumentException("Chunk already deleted");
        }
        if (enabled) {
            if (chunk.getVectorId() == null || chunk.getVectorId().isBlank()) {
                EmbeddingConfig embeddingConfig = embeddingConfigForKnowledgeBase(orgId, chunk.getKnowledgeBaseId());
                String vectorId = vectorStoreClient.upsert(new VectorUpsertCommand(
                        orgId,
                        chunk.getKnowledgeBaseId(),
                        chunk.getDocumentId(),
                        chunk.getId(),
                        chunk.getChunkIndex(),
                        chunk.getContent(),
                        chunk.getContentHash(),
                        embeddingService.embed(
                                orgId,
                                embeddingConfig.provider(),
                                embeddingConfig.model(),
                                embeddingConfig.dimension(),
                                chunk.getContent())));
                chunk.setVectorId(vectorId);
                recordKbChunkIndexingSafely(orgId, chunk.getKnowledgeBaseId(), chunk.getId(), chunk.getContentHash(), "chunk_enable");
            }
            chunk.enable();
        } else {
            if (chunk.getVectorId() != null && !chunk.getVectorId().isBlank()) {
                vectorStoreClient.deleteByVectorIds(orgId, List.of(chunk.getVectorId()));
            }
            chunk.disable();
        }
        chunkRepository.save(chunk);
        return chunkPayload(chunk);
    }

    @Transactional
    public Map<String, Object> deleteChunk(String orgId, Long chunkId) {
        KbChunkEntity chunk = chunkRepository.findByIdAndOrgId(chunkId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Chunk not found"));
        if ("DELETED".equals(chunk.getStatus())) {
            return chunkPayload(chunk);
        }
        if (chunk.getVectorId() != null && !chunk.getVectorId().isBlank()) {
            vectorStoreClient.deleteByVectorIds(orgId, List.of(chunk.getVectorId()));
        }
        chunk.markDeleted();
        chunkRepository.save(chunk);
        return chunkPayload(chunk);
    }

    @Transactional
    public Map<String, Object> batchSetChunkEnabled(String orgId, List<Long> chunkIds, boolean enabled) {
        return batchOperateChunks(orgId, chunkIds, enabled ? "enable" : "disable");
    }

    @Transactional
    public Map<String, Object> batchDeleteChunks(String orgId, List<Long> chunkIds) {
        return batchOperateChunks(orgId, chunkIds, "delete");
    }

    @Transactional
    public List<Map<String, Object>> listMetadataFields(String orgId, Long kbId) {
        return metadataFieldRepository.findByOrgIdAndKnowledgeBaseIdOrderByIdAsc(orgId, kbId).stream()
                .map(field -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", field.getId());
                    row.put("fieldKey", field.getFieldKey());
                    row.put("fieldName", field.getFieldName());
                    row.put("valueType", field.getValueType());
                    return row;
                })
                .toList();
    }

    @Transactional
    public Map<String, Object> createMetadataField(String orgId, Long kbId, MetadataFieldCommand command) {
        KnowledgeBaseEntity kb = kbRepository.findByIdAndOrgId(kbId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
        String fieldKey = normalizeFieldKey(command.fieldKey());
        if (metadataFieldRepository.findByOrgIdAndKnowledgeBaseIdAndFieldKey(orgId, kbId, fieldKey).isPresent()) {
            throw new IllegalArgumentException("Metadata field key already exists");
        }
        String fieldName = command.fieldName() == null || command.fieldName().isBlank() ? fieldKey : command.fieldName().trim();
        String valueType = normalizeValueType(command.valueType());
        KbMetadataFieldEntity created = metadataFieldRepository.save(new KbMetadataFieldEntity(
                orgId,
                kb.getId(),
                fieldKey,
                fieldName,
                valueType));
        Map<String, Object> row = new HashMap<>();
        row.put("id", created.getId());
        row.put("fieldKey", created.getFieldKey());
        row.put("fieldName", created.getFieldName());
        row.put("valueType", created.getValueType());
        return row;
    }

    @Transactional
    public List<Map<String, Object>> getDocumentMetadata(String orgId, Long documentId) {
        KbDocumentEntity doc = documentRepository.findByIdAndOrgId(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return documentMetadataRepository.findByOrgIdAndKnowledgeBaseIdAndDocumentId(
                        orgId,
                        doc.getKnowledgeBaseId(),
                        documentId)
                .stream()
                .map(item -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("fieldKey", item.getFieldKey());
                    row.put("value", item.getStringValue());
                    return row;
                })
                .toList();
    }

    @Transactional
    public List<Map<String, Object>> updateDocumentMetadata(String orgId, Long documentId, Map<String, String> metadata) {
        KbDocumentEntity doc = documentRepository.findByIdAndOrgId(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        Long kbId = doc.getKnowledgeBaseId();
        if (metadata == null || metadata.isEmpty()) {
            documentMetadataRepository.deleteByOrgIdAndKnowledgeBaseIdAndDocumentId(orgId, kbId, documentId);
            return List.of();
        }
        documentMetadataRepository.deleteByOrgIdAndKnowledgeBaseIdAndDocumentId(orgId, kbId, documentId);
        List<KbMetadataFieldEntity> fields = metadataFieldRepository.findByOrgIdAndKnowledgeBaseIdOrderByIdAsc(orgId, kbId);
        Map<String, KbMetadataFieldEntity> fieldMap = new HashMap<>();
        for (KbMetadataFieldEntity field : fields) {
            fieldMap.put(field.getFieldKey(), field);
        }
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = normalizeFieldKey(entry.getKey());
            if (!fieldMap.containsKey(key)) {
                throw new IllegalArgumentException("Unknown metadata field: " + key);
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (value.isBlank()) {
                continue;
            }
            KbDocumentMetadataEntity item = new KbDocumentMetadataEntity(orgId, kbId, documentId, key, value);
            documentMetadataRepository.save(item);
        }
        return getDocumentMetadata(orgId, documentId);
    }

    public List<Map<String, Object>> listDocumentAccessGrants(String orgId, Long documentId) {
        return kbAccessControlService.listDocumentGrants(orgId, documentId);
    }

    public List<Map<String, Object>> replaceDocumentAccessGrants(String orgId,
                                                                 Long documentId,
                                                                 String actorUserId,
                                                                 List<KbAccessControlService.GrantInput> grants) {
        return kbAccessControlService.replaceDocumentGrants(orgId, documentId, actorUserId, grants);
    }

    public List<Map<String, Object>> listChunkAccessGrants(String orgId, Long chunkId) {
        return kbAccessControlService.listChunkGrants(orgId, chunkId);
    }

    public List<Map<String, Object>> replaceChunkAccessGrants(String orgId,
                                                              Long chunkId,
                                                              String actorUserId,
                                                              List<KbAccessControlService.GrantInput> grants) {
        return kbAccessControlService.replaceChunkGrants(orgId, chunkId, actorUserId, grants);
    }

    @Transactional
    public Map<String, Object> previewChunking(String orgId, Long kbId, ChunkPreviewCommand command) {
        KnowledgeBaseEntity kb = kbRepository.findByIdAndOrgId(kbId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
        String sourceText = command.text() == null ? "" : command.text();
        if (sourceText.isBlank()) {
            throw new IllegalArgumentException("Preview text is required");
        }
        int chunkSize = sanitizeChunkSize(command.chunkSize() == null ? kb.getChunkSize() : command.chunkSize());
        int chunkOverlap = sanitizeChunkOverlap(chunkSize, command.chunkOverlap() == null ? kb.getChunkOverlap() : command.chunkOverlap());
        String delimiter = normalizeDelimiter(command.chunkDelimiter() == null ? kb.getChunkDelimiter() : command.chunkDelimiter());
        int maxChunks = Math.min(20, Math.max(1, command.maxChunks() == null ? 8 : command.maxChunks()));
        List<String> chunks = splitToChunks(sourceText, chunkSize, chunkOverlap, delimiter);
        ArrayList<Map<String, Object>> preview = new ArrayList<>();
        for (int i = 0; i < Math.min(chunks.size(), maxChunks); i++) {
            HashMap<String, Object> row = new HashMap<>();
            row.put("index", i);
            row.put("length", chunks.get(i).length());
            row.put("content", chunks.get(i));
            preview.add(row);
        }
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("chunkSize", chunkSize);
        payload.put("chunkOverlap", chunkOverlap);
        payload.put("chunkDelimiter", delimiter);
        payload.put("totalChunks", chunks.size());
        payload.put("previewChunks", preview);
        return payload;
    }

    @Transactional
    public Map<String, Object> testRetrieval(String orgId, Long kbId, RetrievalTestCommand command) {
        KnowledgeBaseEntity kb = kbRepository.findByIdAndOrgId(kbId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
        String query = command.query() == null ? "" : command.query().trim();
        if (query.isBlank()) {
            throw new IllegalArgumentException("Query is required");
        }
        int topK = sanitizeTopK(command.topK() == null ? kb.getTopK() : command.topK());
        double scoreThreshold = sanitizeScoreThreshold(command.scoreThreshold() == null ? kb.getScoreThreshold() : command.scoreThreshold());
        String strategy = normalizeRetrievalStrategy(command.retrievalStrategy() == null ? kb.getRetrievalStrategy() : command.retrievalStrategy());
        Map<String, String> filters = normalizeMetadataFilters(command.metadataFilters());
        validateMetadataFilterKeys(orgId, kbId, filters);
        List<VectorSearchHit> rawHits = vectorStoreClient.search(new VectorSearchQuery(
                orgId,
                List.of(String.valueOf(kbId)),
                query,
                embeddingService.embed(
                        orgId,
                        kb.getEmbeddingProvider(),
                        kb.getEmbeddingModel(),
                        kb.getEmbeddingDimension(),
                        query),
                Math.max(topK, 1)));
        ArrayList<Map<String, Object>> hits = new ArrayList<>();
        for (VectorSearchHit hit : rawHits) {
            if (hit.score() < scoreThreshold) {
                continue;
            }
            if (!isChunkSearchable(orgId, kbId, hit.chunkId())) {
                continue;
            }
            if (!matchesMetadataFilters(orgId, kbId, hit.documentId(), filters)) {
                continue;
            }
            HashMap<String, Object> row = new HashMap<>();
            row.put("vectorId", hit.vectorId());
            row.put("chunkId", hit.chunkId());
            row.put("documentId", hit.documentId());
            row.put("chunkIndex", hit.chunkIndex());
            row.put("score", hit.score());
            row.put("content", hit.content());
            row.put("source", "vector");
            hits.add(row);
            if (hits.size() >= topK) {
                break;
            }
        }

        if (hits.isEmpty()) {
            for (KbChunkEntity chunk : chunkRepository.findTop50ByOrgIdAndKnowledgeBaseIdInAndStatusAndEnabledTrueOrderByIdDesc(
                    orgId,
                    List.of(String.valueOf(kbId)),
                    "ACTIVE")) {
                if (!chunk.isSearchable()) {
                    continue;
                }
                if (!isChunkSearchable(orgId, kbId, chunk.getId())) {
                    continue;
                }
                if (!matchesMetadataFilters(orgId, kbId, chunk.getDocumentId(), filters)) {
                    continue;
                }
                HashMap<String, Object> row = new HashMap<>();
                row.put("vectorId", chunk.getVectorId() == null ? "" : chunk.getVectorId());
                row.put("chunkId", chunk.getId());
                row.put("documentId", chunk.getDocumentId());
                row.put("chunkIndex", chunk.getChunkIndex());
                row.put("score", 0.0);
                row.put("content", chunk.getContent());
                row.put("source", "fallback");
                hits.add(row);
                if (hits.size() >= topK) {
                    break;
                }
            }
        }

        saveRetrievalLog(orgId, kbId, query, strategy, topK, scoreThreshold, hits);

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("retrievalStrategy", strategy);
        payload.put("topK", topK);
        payload.put("scoreThreshold", scoreThreshold);
        payload.put("metadataFilters", filters);
        payload.put("hitCount", hits.size());
        payload.put("hits", hits);
        return payload;
    }

    @Transactional
    public List<Map<String, Object>> listRetrievalLogs(String orgId, Long kbId, Integer limit) {
        int safeLimit = Math.min(50, Math.max(1, limit == null ? 20 : limit));
        return retrievalLogRepository.findTop50ByOrgIdAndKnowledgeBaseIdOrderByIdDesc(orgId, kbId)
                .stream()
                .limit(safeLimit)
                .map(log -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", log.getId());
                    row.put("query", log.getQuery());
                    row.put("retrievalStrategy", log.getRetrievalStrategy());
                    row.put("topK", log.getTopK());
                    row.put("scoreThreshold", log.getScoreThreshold());
                    row.put("hitCount", log.getHitCount());
                    row.put("hitSummaryJson", log.getHitSummaryJson());
                    row.put("createdAt", log.getCreatedAt().toString());
                    return row;
                })
                .toList();
    }

    @Transactional
    public Map<String, Object> createEvalSuite(String orgId, Long kbId, EvalSuiteCommand command) {
        requireKnowledgeBase(orgId, kbId);
        String name = requireText(command.name(), "Suite name is required");
        KbEvalSuiteEntity created = evalSuiteRepository.save(new KbEvalSuiteEntity(
                orgId,
                kbId,
                name.length() > 160 ? name.substring(0, 160) : name,
                truncateText(command.description(), 1000)));
        return evalSuitePayload(created);
    }

    public List<Map<String, Object>> listEvalSuites(String orgId, Long kbId) {
        requireKnowledgeBase(orgId, kbId);
        return evalSuiteRepository.findByOrgIdAndKnowledgeBaseIdAndStatusOrderByIdDesc(
                        orgId,
                        kbId,
                        KbEvalSuiteEntity.STATUS_ACTIVE)
                .stream()
                .map(this::evalSuitePayload)
                .toList();
    }

    @Transactional
    public Map<String, Object> addEvalCase(String orgId, Long suiteId, EvalCaseCommand command) {
        KbEvalSuiteEntity suite = requireEvalSuite(orgId, suiteId);
        String query = requireText(command.query(), "Eval query is required");
        Map<String, String> metadataFilters = normalizeMetadataFilters(command.metadataFilters());
        validateMetadataFilterKeys(orgId, suite.getKnowledgeBaseId(), metadataFilters);
        KbEvalCaseEntity created = evalCaseRepository.save(new KbEvalCaseEntity(
                orgId,
                suite.getId(),
                suite.getKnowledgeBaseId(),
                query,
                command.expectedDocumentId(),
                trimToNull(command.expectedDocumentKeyword()),
                trimToNull(command.expectedChunkKeyword()),
                sanitizeEvalMinScore(command.minScore()),
                command.forbiddenDocumentId(),
                toJson(metadataFilters)));
        return evalCasePayload(created);
    }

    public List<Map<String, Object>> listEvalCases(String orgId, Long suiteId) {
        KbEvalSuiteEntity suite = requireEvalSuite(orgId, suiteId);
        return evalCaseRepository.findByOrgIdAndSuiteIdAndStatusOrderByIdAsc(
                        orgId,
                        suite.getId(),
                        KbEvalCaseEntity.STATUS_ACTIVE)
                .stream()
                .map(this::evalCasePayload)
                .toList();
    }

    @Transactional
    public Map<String, Object> runEvalSuite(String orgId, Long suiteId) {
        KbEvalSuiteEntity suite = requireEvalSuite(orgId, suiteId);
        List<KbEvalCaseEntity> cases = evalCaseRepository.findByOrgIdAndSuiteIdAndStatusOrderByIdAsc(
                orgId,
                suiteId,
                KbEvalCaseEntity.STATUS_ACTIVE);
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("Evaluation suite has no active cases");
        }

        KbEvalRunEntity run = evalRunRepository.save(new KbEvalRunEntity(orgId, suiteId, suite.getKnowledgeBaseId(), cases.size()));
        int passed = 0;
        int failed = 0;
        int expectedHits = 0;
        int forbiddenViolations = 0;
        int staleSources = 0;
        double topScoreSum = 0.0;
        ArrayList<Map<String, Object>> resultSummaries = new ArrayList<>();

        for (KbEvalCaseEntity evalCase : cases) {
            EvalCaseOutcome outcome = evaluateCase(orgId, suite.getKnowledgeBaseId(), evalCase);
            if (outcome.passed()) {
                passed++;
            } else {
                failed++;
            }
            if (outcome.expectedHit()) {
                expectedHits++;
            }
            if (outcome.forbiddenViolation()) {
                forbiddenViolations++;
            }
            if (outcome.staleSource()) {
                staleSources++;
            }
            topScoreSum += outcome.topScore();
            KbEvalCaseResultEntity saved = evalCaseResultRepository.save(new KbEvalCaseResultEntity(
                    orgId,
                    run.getId(),
                    evalCase.getId(),
                    outcome.passed() ? "PASSED" : "FAILED",
                    outcome.expectedHit(),
                    outcome.forbiddenViolation(),
                    outcome.staleSource(),
                    outcome.topScore(),
                    outcome.matchedDocumentId(),
                    outcome.matchedChunkId(),
                    toJson(outcome.summary())));
            resultSummaries.add(evalCaseResultPayload(saved));
        }

        double denominator = Math.max(1, cases.size());
        double hitRate = roundMetric((double) passed / denominator);
        double expectedRecall = roundMetric((double) expectedHits / denominator);
        double averageTopScore = roundMetric(topScoreSum / denominator);
        double staleSourceRate = roundMetric((double) staleSources / denominator);
        run.finish(
                failed == 0 ? "PASSED" : "FAILED",
                passed,
                failed,
                hitRate,
                expectedRecall,
                forbiddenViolations,
                averageTopScore,
                staleSourceRate,
                toJson(Map.of("results", resultSummaries)));
        evalRunRepository.save(run);
        return evalRunPayload(run);
    }

    public List<Map<String, Object>> listEvalRuns(String orgId, Long suiteId) {
        KbEvalSuiteEntity suite = requireEvalSuite(orgId, suiteId);
        return evalRunRepository.findTop20ByOrgIdAndSuiteIdOrderByIdDesc(orgId, suite.getId())
                .stream()
                .map(this::evalRunPayload)
                .toList();
    }

    public List<Map<String, Object>> listEvalRunResults(String orgId, Long runId) {
        KbEvalRunEntity run = evalRunRepository.findByIdAndOrgId(runId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Evaluation run not found"));
        return evalCaseResultRepository.findByOrgIdAndRunIdOrderByIdAsc(orgId, run.getId())
                .stream()
                .map(this::evalCaseResultPayload)
                .toList();
    }

    @Transactional
    public Map<String, Object> auditVectorStore(String orgId) {
        List<String> registeredVectorIds = chunkRepository.findByOrgIdAndStatusNot(orgId, "DELETED").stream()
                .map(KbChunkEntity::getVectorId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        VectorStoreAuditResult audit = vectorStoreClient.auditOrgVectors(orgId, registeredVectorIds);
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("success", audit.success());
        payload.put("scannedCount", audit.scannedCount());
        payload.put("registeredCount", audit.registeredCount());
        payload.put("orphanCount", audit.orphanCount());
        payload.put("orphanVectorIds", audit.orphanVectorIds());
        payload.put("message", audit.message());
        payload.put("status", audit.success() && audit.orphanCount() == 0 ? "OK" : audit.success() ? "NEEDS_CLEANUP" : "FAILED");
        return payload;
    }

    @Transactional
    public Map<String, Object> auditIndexDrift(String orgId, boolean repair) {
        List<KbChunkEntity> chunks = chunkRepository.findByOrgIdAndStatusNot(orgId, "DELETED");
        List<KbDocumentEntity> documents = documentRepository.findByOrgIdAndStatusNot(orgId, "DELETED");
        List<String> registeredVectorIds = chunks.stream()
                .map(KbChunkEntity::getVectorId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        VectorStoreAuditResult vectorAudit = vectorStoreClient.auditOrgVectors(orgId, registeredVectorIds);

        List<KbChunkEntity> missingVectorChunks = chunks.stream()
                .filter(KbChunkEntity::isSearchable)
                .filter(chunk -> chunk.getVectorId() == null || chunk.getVectorId().isBlank())
                .toList();
        List<KbDocumentEntity> publishedDocumentsWithoutChunks = documents.stream()
                .filter(doc -> doc.isEnabled() && !doc.isArchived() && "PUBLISHED".equals(doc.getStatus()))
                .filter(doc -> chunkRepository.countByOrgIdAndDocumentIdAndStatusAndEnabledTrue(orgId, doc.getId(), "ACTIVE") == 0)
                .toList();
        List<KbDocumentEntity> staleSyncDocuments = documents.stream()
                .filter(doc -> "FAILED".equals(doc.getStatus()) || "CLEANUP_FAILED".equals(doc.getStatus()))
                .toList();

        LinkedHashSet<Long> documentsToReindex = new LinkedHashSet<>();
        for (KbChunkEntity chunk : missingVectorChunks) {
            if (chunk.getDocumentId() != null) {
                documentsToReindex.add(chunk.getDocumentId());
            }
        }
        publishedDocumentsWithoutChunks.stream()
                .map(KbDocumentEntity::getId)
                .forEach(documentsToReindex::add);

        HashMap<String, Object> repairSummary = new HashMap<>();
        if (repair) {
            int repairedManualChunks = 0;
            for (KbChunkEntity chunk : missingVectorChunks) {
                if (chunk.getDocumentId() == null && repairChunkVector(orgId, chunk)) {
                    repairedManualChunks++;
                }
            }
            int reindexedDocuments = 0;
            for (Long documentId : documentsToReindex) {
                publishDocument(orgId, documentId);
                reindexedDocuments++;
            }
            VectorDeleteResult orphanCleanup = vectorAudit.success() && !vectorAudit.orphanVectorIds().isEmpty()
                    ? vectorStoreClient.deleteByVectorIds(orgId, vectorAudit.orphanVectorIds())
                    : VectorDeleteResult.success(0, 0);
            repairSummary.put("reindexedDocuments", reindexedDocuments);
            repairSummary.put("repairedManualChunks", repairedManualChunks);
            repairSummary.put("orphanCleanupSuccess", orphanCleanup.success());
            repairSummary.put("orphanDeleteRequested", orphanCleanup.requestedCount());
            repairSummary.put("orphanDeletedCount", orphanCleanup.deletedCount());
            repairSummary.put("orphanCleanupMessage", orphanCleanup.message());
        }

        int orphanCount = vectorAudit.success() ? vectorAudit.orphanCount() : 0;
        String status = !vectorAudit.success()
                ? "FAILED"
                : missingVectorChunks.isEmpty()
                && publishedDocumentsWithoutChunks.isEmpty()
                && staleSyncDocuments.isEmpty()
                && orphanCount == 0
                ? "OK"
                : "DRIFT_DETECTED";

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("status", status);
        payload.put("repairRequested", repair);
        payload.put("repairSummary", repairSummary);
        payload.put("registeredVectorCount", registeredVectorIds.size());
        payload.put("vectorAudit", Map.of(
                "success", vectorAudit.success(),
                "scannedCount", vectorAudit.scannedCount(),
                "orphanCount", vectorAudit.orphanCount(),
                "orphanVectorIds", vectorAudit.orphanVectorIds(),
                "message", vectorAudit.message()));
        payload.put("missingVectorChunkCount", missingVectorChunks.size());
        payload.put("missingVectorChunks", missingVectorChunks.stream().limit(50).map(this::chunkDriftPayload).toList());
        payload.put("publishedDocumentWithoutChunkCount", publishedDocumentsWithoutChunks.size());
        payload.put("publishedDocumentsWithoutChunks", publishedDocumentsWithoutChunks.stream().limit(50).map(this::documentDriftPayload).toList());
        payload.put("staleSyncDocumentCount", staleSyncDocuments.size());
        payload.put("staleSyncDocuments", staleSyncDocuments.stream().limit(50).map(this::documentDriftPayload).toList());
        payload.put("embeddingDriftCheck", "NOT_AVAILABLE_UNTIL_CHUNK_EMBEDDING_METADATA_IS_PERSISTED");
        return payload;
    }

    @Transactional
    public Map<String, Object> publishDocument(String orgId, Long documentId) {
        KbDocumentEntity doc = documentRepository.findByIdAndOrgId(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        doc.markIndexing();
        documentRepository.save(doc);
        if ("mq".equalsIgnoreCase(indexingMode)) {
            try {
                String taskPayload = objectMapper.writeValueAsString(new KbIndexTask(orgId, documentId));
                rabbitTemplate.convertAndSend(KbAsyncConfig.KB_INDEX_QUEUE, taskPayload);
            } catch (JsonProcessingException ex) {
                throw new IllegalArgumentException("Failed to enqueue indexing task");
            }
        } else {
            indexDocument(orgId, documentId);
        }
        KbDocumentEntity latest = documentRepository.findByIdAndOrgId(documentId, orgId).orElse(doc);
        return documentPayload(latest);
    }

    @Transactional
    public Map<String, Object> reindexDocument(String orgId, Long documentId) {
        return publishDocument(orgId, documentId);
    }

    @Transactional
    public Map<String, Object> unpublishDocument(String orgId, Long documentId) {
        KbDocumentEntity doc = documentRepository.findByIdAndOrgId(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        doc.setStatus("UNPUBLISHED");
        documentRepository.save(doc);
        CleanupResult cleanup = cleanupDocumentIndex(doc);
        if (cleanup.success()) {
            doc.markUnpublished();
        } else {
            doc.markCleanupFailed(cleanup.message());
        }
        documentRepository.save(doc);
        HashMap<String, Object> payload = new HashMap<>(documentPayload(doc));
        payload.put("cleanupStatus", cleanup.success() ? "COMPLETED" : "FAILED");
        payload.put("deletedVectors", cleanup.deletedVectors());
        payload.put("deletedChunks", cleanup.deletedChunks());
        return payload;
    }

    @Transactional
    public Map<String, Object> deleteKnowledgeBase(String orgId, Long id) {
        KnowledgeBaseEntity kb = kbRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
        kb.setStatus("DELETING");
        kbRepository.save(kb);

        boolean success = true;
        int deletedDocuments = 0;
        int deletedChunks = 0;
        int deletedVectors = 0;
        StringBuilder errors = new StringBuilder();
        for (KbDocumentEntity doc : documentRepository.findByOrgIdAndKnowledgeBaseIdAndStatusNot(orgId, id, "DELETED")) {
            CleanupResult result = cleanupDocumentForDelete(doc, true);
            deletedDocuments++;
            deletedChunks += result.deletedChunks();
            deletedVectors += result.deletedVectors();
            if (!result.success()) {
                success = false;
                errors.append(result.message()).append(' ');
            }
        }

        CleanupResult kbCleanup = cleanupKnowledgeBaseChunks(orgId, id);
        deletedChunks += kbCleanup.deletedChunks();
        deletedVectors += kbCleanup.deletedVectors();
        if (!kbCleanup.success()) {
            success = false;
            errors.append(kbCleanup.message()).append(' ');
        }

        agentKnowledgeBindingRepository.deleteByOrgIdAndKnowledgeBaseId(orgId, id);

        if (success) {
            kb.markDeleted();
        } else {
            kb.setStatus("CLEANUP_FAILED");
        }
        kbRepository.save(kb);

        HashMap<String, Object> payload = new HashMap<>(kbPayload(kb));
        payload.put("deletedDocuments", deletedDocuments);
        payload.put("deletedChunks", deletedChunks);
        payload.put("deletedVectors", deletedVectors);
        payload.put("cleanupStatus", success ? "COMPLETED" : "FAILED");
        if (!success) {
            payload.put("errorMessage", errors.toString().trim());
        }
        return payload;
    }

    @Transactional
    public Map<String, Object> deleteDocument(String orgId, Long id) {
        KbDocumentEntity doc = documentRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        CleanupResult cleanup = cleanupDocumentForDelete(doc, true);
        HashMap<String, Object> payload = new HashMap<>(documentPayload(doc));
        payload.put("cleanupStatus", cleanup.success() ? "COMPLETED" : "FAILED");
        payload.put("deletedVectors", cleanup.deletedVectors());
        payload.put("deletedChunks", cleanup.deletedChunks());
        if (!cleanup.success()) {
            payload.put("errorMessage", cleanup.message());
        }
        return payload;
    }

    @Transactional
    public Map<String, Object> addChunk(String orgId, String kbId, String content, String tags) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Chunk content is required");
        }
        String normalizedKbId = kbId == null ? "" : kbId.trim();
        String normalizedContent = content.trim();
        String contentHash = sha256(normalizedContent);
        KbChunkEntity chunk = chunkRepository.save(new KbChunkEntity(
                orgId,
                normalizedKbId,
                null,
                null,
                normalizedContent,
                tags,
                null,
                contentHash));
        EmbeddingConfig embeddingConfig = embeddingConfigForKnowledgeBase(orgId, normalizedKbId);
        String vectorId = vectorStoreClient.upsert(new VectorUpsertCommand(
                orgId,
                normalizedKbId,
                null,
                chunk.getId(),
                null,
                normalizedContent,
                contentHash,
                embeddingService.embed(
                        orgId,
                        embeddingConfig.provider(),
                        embeddingConfig.model(),
                        embeddingConfig.dimension(),
                        normalizedContent)));
        chunk.setVectorId(vectorId);
        chunkRepository.save(chunk);
        recordKbChunkIndexingSafely(orgId, normalizedKbId, chunk.getId(), contentHash, "chunk_add");
        parseLong(normalizedKbId).flatMap(id -> kbRepository.findByIdAndOrgId(id, orgId)).ifPresent(kb -> {
            if (!"DELETED".equals(kb.getStatus())) {
                kb.setStatus("ACTIVE");
                kbRepository.save(kb);
            }
        });
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("id", chunk.getId());
        payload.put("orgId", orgId);
        payload.put("knowledgeBaseId", normalizedKbId);
        payload.put("status", "INDEXED");
        payload.put("vectorId", vectorId);
        return payload;
    }

    @Transactional
    public void indexDocument(String orgId, Long documentId) {
        KbDocumentEntity doc = documentRepository.findByIdAndOrgId(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if ("DELETING".equals(doc.getStatus()) || "DELETED".equals(doc.getStatus())) {
            return;
        }
        ArrayList<KbChunkEntity> indexedChunks = new ArrayList<>();
        try {
            cleanupDocumentIndex(doc);
            String text = readSupportedText(doc);
            KnowledgeBaseEntity kbEntity = kbRepository.findByIdAndOrgId(doc.getKnowledgeBaseId(), orgId).orElse(null);
            int chunkSize = kbEntity == null ? DEFAULT_CHUNK_SIZE : sanitizeChunkSize(kbEntity.getChunkSize());
            int chunkOverlap = kbEntity == null ? DEFAULT_CHUNK_OVERLAP : sanitizeChunkOverlap(chunkSize, kbEntity.getChunkOverlap());
            String chunkDelimiter = kbEntity == null ? "\n" : normalizeDelimiter(kbEntity.getChunkDelimiter());
            String embeddingProvider = kbEntity == null ? defaultEmbeddingProvider : kbEntity.getEmbeddingProvider();
            String embeddingModel = kbEntity == null ? defaultEmbeddingModel : kbEntity.getEmbeddingModel();
            Integer embeddingDimension = kbEntity == null ? defaultEmbeddingDimension : kbEntity.getEmbeddingDimension();
            int chunkIndex = 0;
            for (String chunkText : splitToChunks(text, chunkSize, chunkOverlap, chunkDelimiter)) {
                if (chunkText.isBlank()) {
                    continue;
                }
                String contentHash = sha256(chunkText);
                KbChunkEntity chunk = chunkRepository.save(new KbChunkEntity(
                        doc.getOrgId(),
                        String.valueOf(doc.getKnowledgeBaseId()),
                        doc.getId(),
                        chunkIndex,
                        chunkText,
                        "indexed,doc-" + doc.getId(),
                        null,
                        contentHash
                ));
                String vectorId = vectorStoreClient.upsert(new VectorUpsertCommand(
                        doc.getOrgId(),
                        String.valueOf(doc.getKnowledgeBaseId()),
                        doc.getId(),
                        chunk.getId(),
                        chunkIndex,
                        chunkText,
                        contentHash,
                        embeddingService.embed(
                                doc.getOrgId(),
                                embeddingProvider,
                                embeddingModel,
                                embeddingDimension,
                                chunkText)));
                chunk.setVectorId(vectorId);
                chunkRepository.save(chunk);
                indexedChunks.add(chunk);
                chunkIndex++;
            }
            doc.markPublished();
            documentRepository.save(doc);
            recordKbDocumentIndexingSafely(doc, indexedChunks.size(), "document_index");
            kbRepository.findByIdAndOrgId(doc.getKnowledgeBaseId(), orgId).ifPresent(kb -> {
                kb.setStatus("ACTIVE");
                kbRepository.save(kb);
            });
        } catch (Exception ex) {
            cleanupIndexedChunks(doc.getOrgId(), indexedChunks);
            doc.markFailed(ex.getMessage());
            documentRepository.save(doc);
        }
    }

    private void recordKbDocumentIndexingSafely(KbDocumentEntity doc, int chunkCount, String operation) {
        if (doc == null || chunkCount <= 0) {
            return;
        }
        String sourceId = doc.getOrgId() + ":kb:" + doc.getKnowledgeBaseId()
                + ":doc:" + doc.getId() + ":v" + doc.getIndexVersion() + ":" + operation;
        billingUsageMeteringService.recordKbIndexingSafely(new BillingUsageMeteringService.KbIndexingMeteringInput(
                doc.getOrgId(),
                "",
                "",
                String.valueOf(doc.getKnowledgeBaseId()),
                doc.getId(),
                doc.getName(),
                doc.getFileSize() == null ? 0L : doc.getFileSize(),
                doc.getIndexVersion(),
                chunkCount,
                operation,
                sourceId,
                Instant.now()));
    }

    private void recordKbChunkIndexingSafely(String orgId,
                                             String knowledgeBaseId,
                                             Long chunkId,
                                             String contentHash,
                                             String operation) {
        if (orgId == null || orgId.isBlank() || knowledgeBaseId == null || knowledgeBaseId.isBlank() || chunkId == null) {
            return;
        }
        String hash = contentHash == null || contentHash.isBlank()
                ? "nohash"
                : contentHash.substring(0, Math.min(16, contentHash.length()));
        String sourceId = orgId + ":kb:" + knowledgeBaseId + ":chunk:" + chunkId + ":" + hash + ":" + operation;
        billingUsageMeteringService.recordKbIndexingSafely(new BillingUsageMeteringService.KbIndexingMeteringInput(
                orgId,
                "",
                "",
                knowledgeBaseId,
                null,
                "",
                0L,
                0,
                1,
                operation,
                sourceId,
                Instant.now()));
    }

    private CleanupResult cleanupDocumentForDelete(KbDocumentEntity doc, boolean deleteSource) {
        doc.markDeleting();
        documentRepository.save(doc);
        CleanupResult cleanup = cleanupDocumentIndex(doc);
        if (deleteSource) {
            try {
                Files.deleteIfExists(Path.of(doc.getStoragePath()));
            } catch (IOException ex) {
                cleanup = cleanup.merge(VectorDeleteResult.failure(1, "source file delete failed: " + ex.getMessage()));
            }
        }
        if (cleanup.success()) {
            doc.markDeleted();
        } else {
            doc.markCleanupFailed(cleanup.message());
        }
        documentRepository.save(doc);
        return cleanup;
    }

    private CleanupResult cleanupDocumentIndex(KbDocumentEntity doc) {
        List<KbChunkEntity> chunks = chunkRepository.findByOrgIdAndDocumentIdAndStatusNot(
                doc.getOrgId(), doc.getId(), "DELETED");
        List<String> vectorIds = chunks.stream()
                .map(KbChunkEntity::getVectorId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        VectorDeleteResult byIds = vectorStoreClient.deleteByVectorIds(doc.getOrgId(), vectorIds);
        VectorDeleteResult byDocument = vectorStoreClient.deleteByDocument(
                doc.getOrgId(),
                String.valueOf(doc.getKnowledgeBaseId()),
                doc.getId());
        for (KbChunkEntity chunk : chunks) {
            chunk.markDeleted();
        }
        chunkRepository.saveAll(chunks);
        return CleanupResult.from(chunks.size(), byIds).merge(byDocument);
    }

    private CleanupResult cleanupKnowledgeBaseChunks(String orgId, Long knowledgeBaseId) {
        List<KbChunkEntity> chunks = chunkRepository.findByOrgIdAndKnowledgeBaseIdAndStatusNot(
                orgId,
                String.valueOf(knowledgeBaseId),
                "DELETED");
        List<String> vectorIds = chunks.stream()
                .map(KbChunkEntity::getVectorId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        VectorDeleteResult byIds = vectorStoreClient.deleteByVectorIds(orgId, vectorIds);
        VectorDeleteResult byKnowledgeBase = vectorStoreClient.deleteByKnowledgeBase(orgId, String.valueOf(knowledgeBaseId));
        for (KbChunkEntity chunk : chunks) {
            chunk.markDeleted();
        }
        chunkRepository.saveAll(chunks);
        return CleanupResult.from(chunks.size(), byIds).merge(byKnowledgeBase);
    }

    private void cleanupIndexedChunks(String orgId, List<KbChunkEntity> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        List<String> vectorIds = chunks.stream()
                .map(KbChunkEntity::getVectorId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .toList();
        vectorStoreClient.deleteByVectorIds(orgId, vectorIds);
        for (KbChunkEntity chunk : chunks) {
            chunk.markDeleted();
        }
        chunkRepository.saveAll(chunks);
    }

    private String readSupportedText(KbDocumentEntity doc) throws IOException {
        String contentType = doc.getContentType() == null ? "" : doc.getContentType().toLowerCase();
        String name = doc.getName() == null ? "" : doc.getName().toLowerCase();
        Path path = Path.of(doc.getStoragePath());
        if (isDocxFile(contentType, name)) {
            return readDocxText(path);
        }
        if (isPdfFile(contentType, name)) {
            return readPdfText(path);
        }
        boolean supported = contentType.startsWith("text/")
                || "application/json".equals(contentType)
                || name.endsWith(".txt")
                || name.endsWith(".md")
                || name.endsWith(".markdown")
                || name.endsWith(".csv")
                || name.endsWith(".json");
        if (!supported) {
            throw new IllegalArgumentException("Unsupported file type. P0 indexing supports txt, md, csv, json, docx and text-based pdf files only.");
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private boolean isDocxFile(String contentType, String name) {
        return name.endsWith(".docx") || contentType.contains("wordprocessingml.document");
    }

    private boolean isPdfFile(String contentType, String name) {
        return name.endsWith(".pdf") || contentType.contains("application/pdf");
    }

    private String readPdfText(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("PDF file is encrypted and cannot be parsed.");
            }
            String text = new PDFTextStripper().getText(document);
            String normalized = text == null ? "" : text.replace('\u0000', ' ').trim();
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("PDF file does not contain readable text. Scanned PDFs require OCR before upload.");
            }
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IOException("Failed to parse PDF content: " + ex.getMessage(), ex);
        }
    }

    private String readDocxText(Path path) throws IOException {
        StringBuilder text = new StringBuilder();
        try (InputStream input = Files.newInputStream(path);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (!entry.isDirectory() && isDocxTextPart(entryName)) {
                    appendDocxXmlText(zip.readAllBytes(), text);
                }
                zip.closeEntry();
            }
        }
        String normalized = text.toString().replaceAll("[\\t ]+\\n", "\n").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("DOCX file does not contain readable text.");
        }
        return normalized;
    }

    private boolean isDocxTextPart(String entryName) {
        return "word/document.xml".equals(entryName)
                || entryName.matches("word/(header|footer|footnotes|endnotes)\\d*\\.xml");
    }

    private void appendDocxXmlText(byte[] xmlBytes, StringBuilder out) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        disableXmlExternalAccess(factory);
        try (InputStream xmlInput = new ByteArrayInputStream(xmlBytes)) {
            XMLStreamReader reader = factory.createXMLStreamReader(xmlInput, StandardCharsets.UTF_8.name());
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                    out.append(reader.getText());
                } else if (event == XMLStreamConstants.START_ELEMENT && "tab".equals(reader.getLocalName())) {
                    out.append('\t');
                } else if (event == XMLStreamConstants.START_ELEMENT && ("br".equals(reader.getLocalName()) || "cr".equals(reader.getLocalName()))) {
                    out.append('\n');
                } else if (event == XMLStreamConstants.END_ELEMENT && "p".equals(reader.getLocalName())) {
                    out.append('\n');
                }
            }
            reader.close();
        } catch (XMLStreamException ex) {
            throw new IOException("Failed to parse DOCX content: " + ex.getMessage(), ex);
        }
    }

    private void disableXmlExternalAccess(XMLInputFactory factory) {
        try {
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        } catch (IllegalArgumentException ignored) {
            // Some XMLInputFactory implementations do not expose this property.
        }
        try {
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        } catch (IllegalArgumentException ignored) {
            // Some XMLInputFactory implementations do not expose this property.
        }
    }

    private EvalCaseOutcome evaluateCase(String orgId, Long kbId, KbEvalCaseEntity evalCase) {
        Map<String, String> filters = parseMetadataFiltersJson(evalCase.getMetadataFiltersJson());
        RagService.RetrievalResult result = ragService.retrieveDetailed(
                orgId,
                List.of(String.valueOf(kbId)),
                evalCase.getQuery(),
                filters);
        List<RagService.RetrievedSource> sources = result.sources();
        double topScore = sources.isEmpty() ? 0.0 : sources.get(0).score();
        boolean minScoreMet = evalCase.getMinScore() == null || topScore >= evalCase.getMinScore();
        boolean forbiddenViolation = evalCase.getForbiddenDocumentId() != null
                && sources.stream().anyMatch(source -> evalCase.getForbiddenDocumentId().equals(source.documentId()));
        boolean staleSource = sources.stream().anyMatch(source -> "STALE".equals(source.freshnessStatus()));

        RagService.RetrievedSource expectedMatch = expectedMatch(evalCase, sources);
        boolean hasExplicitExpected = evalCase.getExpectedDocumentId() != null
                || evalCase.getExpectedDocumentKeyword() != null
                || evalCase.getExpectedChunkKeyword() != null;
        boolean expectedHit = hasExplicitExpected ? expectedMatch != null : !sources.isEmpty();
        boolean passed = expectedHit && minScoreMet && !forbiddenViolation;
        RagService.RetrievedSource evidence = expectedMatch == null && !sources.isEmpty() ? sources.get(0) : expectedMatch;
        HashMap<String, Object> summary = new HashMap<>();
        summary.put("query", evalCase.getQuery());
        summary.put("expectedHit", expectedHit);
        summary.put("minScoreMet", minScoreMet);
        summary.put("forbiddenViolation", forbiddenViolation);
        summary.put("staleSource", staleSource);
        summary.put("topScore", topScore);
        summary.put("metadataFilters", filters);
        summary.put("sourceCount", sources.size());
        summary.put("sources", sources.stream().map(RagService.RetrievedSource::toPayload).toList());
        return new EvalCaseOutcome(
                passed,
                expectedHit,
                forbiddenViolation,
                staleSource,
                topScore,
                evidence == null ? null : evidence.documentId(),
                evidence == null ? null : evidence.chunkId(),
                summary);
    }

    private RagService.RetrievedSource expectedMatch(KbEvalCaseEntity evalCase, List<RagService.RetrievedSource> sources) {
        for (RagService.RetrievedSource source : sources) {
            if (evalCase.getExpectedDocumentId() != null && !evalCase.getExpectedDocumentId().equals(source.documentId())) {
                continue;
            }
            if (evalCase.getExpectedDocumentKeyword() != null
                    && !containsIgnoreCase(source.documentName(), evalCase.getExpectedDocumentKeyword())) {
                continue;
            }
            if (evalCase.getExpectedChunkKeyword() != null
                    && !containsIgnoreCase(source.content(), evalCase.getExpectedChunkKeyword())) {
                continue;
            }
            return source;
        }
        return null;
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return value != null && value.toLowerCase().contains(keyword.toLowerCase());
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseMetadataFiltersJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, Map.class);
            HashMap<String, String> out = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    out.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            return normalizeMetadataFilters(out);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private KnowledgeBaseEntity requireKnowledgeBase(String orgId, Long kbId) {
        return kbRepository.findByIdAndOrgId(kbId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
    }

    private KbEvalSuiteEntity requireEvalSuite(String orgId, Long suiteId) {
        return evalSuiteRepository.findByIdAndOrgId(suiteId, orgId)
                .filter(item -> KbEvalSuiteEntity.STATUS_ACTIVE.equals(item.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Evaluation suite not found"));
    }

    private Map<String, Object> evalSuitePayload(KbEvalSuiteEntity suite) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", suite.getId());
        row.put("knowledgeBaseId", suite.getKnowledgeBaseId());
        row.put("name", suite.getName());
        row.put("description", suite.getDescription() == null ? "" : suite.getDescription());
        row.put("status", suite.getStatus());
        row.put("createdAt", suite.getCreatedAt().toString());
        row.put("updatedAt", suite.getUpdatedAt().toString());
        return row;
    }

    private Map<String, Object> evalCasePayload(KbEvalCaseEntity evalCase) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", evalCase.getId());
        row.put("suiteId", evalCase.getSuiteId());
        row.put("knowledgeBaseId", evalCase.getKnowledgeBaseId());
        row.put("query", evalCase.getQuery());
        row.put("expectedDocumentId", evalCase.getExpectedDocumentId() == null ? "" : evalCase.getExpectedDocumentId());
        row.put("expectedDocumentKeyword", evalCase.getExpectedDocumentKeyword() == null ? "" : evalCase.getExpectedDocumentKeyword());
        row.put("expectedChunkKeyword", evalCase.getExpectedChunkKeyword() == null ? "" : evalCase.getExpectedChunkKeyword());
        row.put("minScore", evalCase.getMinScore() == null ? "" : evalCase.getMinScore());
        row.put("forbiddenDocumentId", evalCase.getForbiddenDocumentId() == null ? "" : evalCase.getForbiddenDocumentId());
        row.put("metadataFilters", parseMetadataFiltersJson(evalCase.getMetadataFiltersJson()));
        row.put("status", evalCase.getStatus());
        row.put("createdAt", evalCase.getCreatedAt().toString());
        row.put("updatedAt", evalCase.getUpdatedAt().toString());
        return row;
    }

    private Map<String, Object> evalRunPayload(KbEvalRunEntity run) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", run.getId());
        row.put("suiteId", run.getSuiteId());
        row.put("knowledgeBaseId", run.getKnowledgeBaseId());
        row.put("status", run.getStatus());
        row.put("caseCount", run.getCaseCount());
        row.put("passedCount", run.getPassedCount());
        row.put("failedCount", run.getFailedCount());
        row.put("hitRate", run.getHitRate());
        row.put("expectedSourceRecall", run.getExpectedSourceRecall());
        row.put("forbiddenSourceViolations", run.getForbiddenSourceViolations());
        row.put("averageTopScore", run.getAverageTopScore());
        row.put("staleSourceRate", run.getStaleSourceRate());
        row.put("summaryJson", run.getSummaryJson() == null ? "" : run.getSummaryJson());
        row.put("startedAt", run.getStartedAt().toString());
        row.put("finishedAt", run.getFinishedAt() == null ? "" : run.getFinishedAt().toString());
        return row;
    }

    private Map<String, Object> evalCaseResultPayload(KbEvalCaseResultEntity result) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", result.getId());
        row.put("runId", result.getRunId());
        row.put("caseId", result.getCaseId());
        row.put("status", result.getStatus());
        row.put("expectedHit", result.isExpectedHit());
        row.put("forbiddenViolation", result.isForbiddenViolation());
        row.put("staleSource", result.isStaleSource());
        row.put("topScore", result.getTopScore());
        row.put("matchedDocumentId", result.getMatchedDocumentId() == null ? "" : result.getMatchedDocumentId());
        row.put("matchedChunkId", result.getMatchedChunkId() == null ? "" : result.getMatchedChunkId());
        row.put("resultSummaryJson", result.getResultSummaryJson() == null ? "" : result.getResultSummaryJson());
        row.put("createdAt", result.getCreatedAt().toString());
        return row;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String truncateText(String value, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private Double sanitizeEvalMinScore(Double value) {
        if (value == null) {
            return null;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double roundMetric(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private Map<String, Object> kbPayload(KnowledgeBaseEntity kb) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", kb.getId());
        row.put("orgId", kb.getOrgId());
        row.put("name", kb.getName());
        row.put("description", kb.getDescription() == null ? "" : kb.getDescription());
        row.put("status", kb.getStatus());
        row.put("createdAt", kb.getCreatedAt() == null ? "" : kb.getCreatedAt().toString());
        row.put("updatedAt", kb.getUpdatedAt() == null ? "" : kb.getUpdatedAt().toString());
        row.put("chunkSize", kb.getChunkSize());
        row.put("chunkOverlap", kb.getChunkOverlap());
        row.put("chunkDelimiter", kb.getChunkDelimiter());
        row.put("retrievalStrategy", kb.getRetrievalStrategy());
        row.put("topK", kb.getTopK());
        row.put("scoreThreshold", kb.getScoreThreshold());
        row.put("embeddingProvider", kb.getEmbeddingProvider());
        row.put("embeddingModel", kb.getEmbeddingModel());
        row.put("embeddingDimension", kb.getEmbeddingDimension());
        if (kb.getDeletedAt() != null) {
            row.put("deletedAt", kb.getDeletedAt().toString());
        }
        return row;
    }

    private Map<String, Object> documentPayload(KbDocumentEntity doc) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", doc.getId());
        row.put("orgId", doc.getOrgId());
        row.put("knowledgeBaseId", doc.getKnowledgeBaseId());
        row.put("name", doc.getName());
        row.put("status", doc.getStatus());
        row.put("contentType", doc.getContentType() == null ? "" : doc.getContentType());
        row.put("createdAt", doc.getCreatedAt().toString());
        row.put("updatedAt", doc.getUpdatedAt() == null ? "" : doc.getUpdatedAt().toString());
        row.put("indexedAt", doc.getIndexedAt() == null ? "" : doc.getIndexedAt().toString());
        row.put("fileSize", doc.getFileSize() == null ? 0L : doc.getFileSize());
        row.put("enabled", doc.isEnabled());
        row.put("archived", doc.isArchived());
        row.put("chunkCount", chunkRepository.countByOrgIdAndDocumentIdAndStatusAndEnabledTrue(
                doc.getOrgId(), doc.getId(), "ACTIVE"));
        row.put("errorMessage", doc.getErrorMessage() == null ? "" : doc.getErrorMessage());
        return row;
    }

    private UploadAdmission validateUploadAdmission(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (file.getSize() > maxUploadFileSizeBytes) {
            throw new IllegalArgumentException("File is too large. Maximum supported size is " + maxUploadFileSizeBytes + " bytes.");
        }
        String original = sanitizeOriginalFilename(file.getOriginalFilename());
        String extension = fileExtension(original);
        String contentType = file.getContentType() == null ? "" : file.getContentType().trim().toLowerCase();
        boolean extensionAllowed = allowedUploadExtensions.contains(extension);
        boolean contentAllowed = contentType.isBlank() || contentType.startsWith("text/") || allowedUploadContentTypes.contains(contentType);
        if (!extensionAllowed || !contentAllowed) {
            throw new IllegalArgumentException("Unsupported file type. Upload txt, md, csv, json, docx, or text-based pdf files only.");
        }
        return new UploadAdmission(original, original, contentType.isBlank() ? contentTypeForExtension(extension) : contentType);
    }

    private String sanitizeOriginalFilename(String value) {
        String name = value == null || value.isBlank() ? "document.txt" : value.trim();
        name = name.replace('\\', '/');
        int idx = name.lastIndexOf('/');
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        name = name.replaceAll("[\\r\\n\\t]", " ").replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            return "document.txt";
        }
        return name.length() > 160 ? name.substring(name.length() - 160) : name;
    }

    private String fileExtension(String filename) {
        String name = filename == null ? "" : filename.toLowerCase();
        int idx = name.lastIndexOf('.');
        return idx < 0 ? "" : name.substring(idx + 1);
    }

    private String contentTypeForExtension(String extension) {
        return switch (extension == null ? "" : extension) {
            case "json" -> "application/json";
            case "csv" -> "text/csv";
            case "md", "markdown" -> "text/markdown";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pdf" -> "application/pdf";
            default -> "text/plain";
        };
    }

    private Set<String> normalizeCsvSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(item -> item == null ? "" : item.trim().toLowerCase())
                .filter(item -> !item.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<String, Object> kbSettingsPayload(KnowledgeBaseEntity kb) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("knowledgeBaseId", kb.getId());
        row.put("chunkSize", kb.getChunkSize());
        row.put("chunkOverlap", kb.getChunkOverlap());
        row.put("chunkDelimiter", kb.getChunkDelimiter());
        row.put("retrievalStrategy", kb.getRetrievalStrategy());
        row.put("topK", kb.getTopK());
        row.put("scoreThreshold", kb.getScoreThreshold());
        row.put("embeddingProvider", kb.getEmbeddingProvider());
        row.put("embeddingModel", kb.getEmbeddingModel());
        row.put("embeddingDimension", kb.getEmbeddingDimension());
        return row;
    }

    private Map<String, Object> chunkPayload(KbChunkEntity chunk) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", chunk.getId());
        row.put("knowledgeBaseId", chunk.getKnowledgeBaseId());
        row.put("documentId", chunk.getDocumentId());
        row.put("chunkIndex", chunk.getChunkIndex());
        row.put("content", chunk.getContent());
        row.put("status", chunk.getStatus());
        row.put("enabled", chunk.isEnabled());
        row.put("vectorId", chunk.getVectorId() == null ? "" : chunk.getVectorId());
        return row;
    }

    private Map<String, Object> chunkDriftPayload(KbChunkEntity chunk) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("chunkId", chunk.getId());
        row.put("knowledgeBaseId", chunk.getKnowledgeBaseId());
        row.put("documentId", chunk.getDocumentId() == null ? "" : chunk.getDocumentId());
        row.put("chunkIndex", chunk.getChunkIndex() == null ? "" : chunk.getChunkIndex());
        row.put("status", chunk.getStatus());
        row.put("vectorId", chunk.getVectorId() == null ? "" : chunk.getVectorId());
        row.put("contentHash", chunk.getContentHash() == null ? "" : chunk.getContentHash());
        return row;
    }

    private Map<String, Object> documentDriftPayload(KbDocumentEntity document) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("documentId", document.getId());
        row.put("knowledgeBaseId", document.getKnowledgeBaseId());
        row.put("name", document.getName());
        row.put("status", document.getStatus());
        row.put("enabled", document.isEnabled());
        row.put("archived", document.isArchived());
        row.put("indexVersion", document.getIndexVersion());
        row.put("indexedAt", document.getIndexedAt() == null ? "" : document.getIndexedAt().toString());
        row.put("errorMessage", document.getErrorMessage() == null ? "" : document.getErrorMessage());
        return row;
    }

    private boolean repairChunkVector(String orgId, KbChunkEntity chunk) {
        Long kbId = parseLong(chunk.getKnowledgeBaseId()).orElse(null);
        if (kbId == null || !chunk.isSearchable()) {
            return false;
        }
        KnowledgeBaseEntity kb = kbRepository.findByIdAndOrgId(kbId, orgId).orElse(null);
        if (kb == null || !"ACTIVE".equals(kb.getStatus())) {
            return false;
        }
        String contentHash = chunk.getContentHash() == null || chunk.getContentHash().isBlank()
                ? sha256(chunk.getContent())
                : chunk.getContentHash();
        String vectorId = vectorStoreClient.upsert(new VectorUpsertCommand(
                orgId,
                chunk.getKnowledgeBaseId(),
                chunk.getDocumentId(),
                chunk.getId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                contentHash,
                embeddingService.embed(
                        orgId,
                        kb.getEmbeddingProvider(),
                        kb.getEmbeddingModel(),
                        kb.getEmbeddingDimension(),
                        chunk.getContent())));
        chunk.setVectorId(vectorId);
        chunkRepository.save(chunk);
        return true;
    }

    private Map<String, Object> batchOperateDocuments(String orgId, List<Long> rawIds, String action) {
        List<Long> ids = normalizeIds(rawIds);
        ArrayList<Map<String, Object>> failed = new ArrayList<>();
        int successCount = 0;
        for (Long id : ids) {
            try {
                switch (action) {
                    case "enable" -> setDocumentEnabled(orgId, id, true);
                    case "disable" -> setDocumentEnabled(orgId, id, false);
                    case "archive" -> setDocumentArchived(orgId, id, true);
                    case "unarchive" -> setDocumentArchived(orgId, id, false);
                    case "delete" -> deleteDocument(orgId, id);
                    default -> throw new IllegalArgumentException("Unsupported action: " + action);
                }
                successCount++;
            } catch (Exception ex) {
                HashMap<String, Object> row = new HashMap<>();
                row.put("id", id);
                row.put("message", ex.getMessage());
                failed.add(row);
            }
        }
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("requestedCount", ids.size());
        payload.put("successCount", successCount);
        payload.put("failedCount", failed.size());
        payload.put("failedItems", failed);
        return payload;
    }

    private Map<String, Object> batchOperateChunks(String orgId, List<Long> rawIds, String action) {
        List<Long> ids = normalizeIds(rawIds);
        ArrayList<Map<String, Object>> failed = new ArrayList<>();
        int successCount = 0;
        for (Long id : ids) {
            try {
                switch (action) {
                    case "enable" -> setChunkEnabled(orgId, id, true);
                    case "disable" -> setChunkEnabled(orgId, id, false);
                    case "delete" -> deleteChunk(orgId, id);
                    default -> throw new IllegalArgumentException("Unsupported action: " + action);
                }
                successCount++;
            } catch (Exception ex) {
                HashMap<String, Object> row = new HashMap<>();
                row.put("id", id);
                row.put("message", ex.getMessage());
                failed.add(row);
            }
        }
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("requestedCount", ids.size());
        payload.put("successCount", successCount);
        payload.put("failedCount", failed.size());
        payload.put("failedItems", failed);
        return payload;
    }

    private List<Long> normalizeIds(List<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            throw new IllegalArgumentException("ids is required");
        }
        return rawIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean isChunkSearchable(String orgId, Long kbId, Long chunkId) {
        if (chunkId == null) {
            return false;
        }
        KbChunkEntity chunk = chunkRepository.findByIdAndOrgId(chunkId, orgId).orElse(null);
        if (chunk == null || !chunk.isSearchable() || !String.valueOf(kbId).equals(chunk.getKnowledgeBaseId())) {
            return false;
        }
        Long documentId = chunk.getDocumentId();
        if (documentId == null) {
            return true;
        }
        KbDocumentEntity doc = documentRepository.findByIdAndOrgId(documentId, orgId).orElse(null);
        return doc != null
                && "PUBLISHED".equals(doc.getStatus())
                && doc.isEnabled()
                && !doc.isArchived()
                && kbId.equals(doc.getKnowledgeBaseId());
    }

    private void saveRetrievalLog(String orgId,
                                  Long kbId,
                                  String query,
                                  String strategy,
                                  int topK,
                                  double scoreThreshold,
                                  List<Map<String, Object>> hits) {
        try {
            String summary = objectMapper.writeValueAsString(hits);
            retrievalLogRepository.save(new KbRetrievalLogEntity(
                    orgId,
                    kbId,
                    query,
                    strategy,
                    topK,
                    scoreThreshold,
                    hits.size(),
                    summary));
        } catch (Exception ignored) {
            // Retrieval test should not fail just because log persistence failed.
        }
    }

    private int sanitizeChunkSize(Integer value) {
        if (value == null) {
            return DEFAULT_CHUNK_SIZE;
        }
        return Math.min(1200, Math.max(80, value));
    }

    private int sanitizeChunkOverlap(int chunkSize, Integer value) {
        if (value == null) {
            return Math.min(DEFAULT_CHUNK_OVERLAP, chunkSize - 1);
        }
        return Math.min(Math.max(0, value), Math.max(0, chunkSize - 1));
    }

    private int sanitizeTopK(Integer value) {
        if (value == null) {
            return DEFAULT_TOP_K;
        }
        return Math.min(20, Math.max(1, value));
    }

    private double sanitizeScoreThreshold(Double value) {
        if (value == null) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, value));
    }

    private String normalizeEmbeddingProvider(String value) {
        if (value == null || value.isBlank()) {
            return "local";
        }
        return value.trim();
    }

    private String normalizeEmbeddingModel(String value) {
        if (value == null || value.isBlank()) {
            return "local-hash";
        }
        return value.trim();
    }

    private int sanitizeEmbeddingDimension(Integer value) {
        if (value == null) {
            return 1024;
        }
        return Math.min(4096, Math.max(4, value));
    }

    private EmbeddingConfig embeddingConfigForKnowledgeBase(String orgId, String knowledgeBaseId) {
        return parseLong(knowledgeBaseId)
                .flatMap(id -> kbRepository.findByIdAndOrgId(id, orgId))
                .map(kb -> new EmbeddingConfig(
                        kb.getEmbeddingProvider(),
                        kb.getEmbeddingModel(),
                        kb.getEmbeddingDimension()))
                .orElse(new EmbeddingConfig(
                        defaultEmbeddingProvider,
                        defaultEmbeddingModel,
                        defaultEmbeddingDimension));
    }

    private String normalizeRetrievalStrategy(String value) {
        if (value == null || value.isBlank()) {
            return "VECTOR";
        }
        String normalized = value.trim().toUpperCase();
        if ("VECTOR".equals(normalized)) {
            return normalized;
        }
        return "VECTOR";
    }

    private String normalizeDelimiter(String value) {
        if (value == null || value.isBlank()) {
            return "\n";
        }
        String normalized = value.trim();
        if ("\\n".equals(normalized)) {
            return "\n";
        }
        return normalized;
    }

    private String normalizeFieldKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Metadata field key is required");
        }
        String normalized = value.trim().toLowerCase().replace(' ', '_');
        if (!normalized.matches("[a-z0-9_\\-]{2,64}")) {
            throw new IllegalArgumentException("Metadata field key should match [a-z0-9_-]{2,64}");
        }
        return normalized;
    }

    private String normalizeValueType(String value) {
        if (value == null || value.isBlank()) {
            return "string";
        }
        String normalized = value.trim().toLowerCase();
        if ("string".equals(normalized) || "number".equals(normalized) || "time".equals(normalized)) {
            return normalized;
        }
        return "string";
    }

    private Map<String, String> normalizeMetadataFilters(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        HashMap<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = normalizeFieldKey(entry.getKey());
            String value = entry.getValue().trim();
            if (!value.isBlank()) {
                out.put(key, value);
            }
        }
        return out;
    }

    private void validateMetadataFilterKeys(String orgId, Long kbId, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        List<KbMetadataFieldEntity> fields = metadataFieldRepository.findByOrgIdAndKnowledgeBaseIdOrderByIdAsc(orgId, kbId);
        Map<String, KbMetadataFieldEntity> fieldMap = new HashMap<>();
        for (KbMetadataFieldEntity field : fields) {
            fieldMap.put(field.getFieldKey(), field);
        }
        for (String filterKey : filters.keySet()) {
            if (!fieldMap.containsKey(filterKey)) {
                throw new IllegalArgumentException("Unknown metadata filter field: " + filterKey);
            }
        }
    }

    private boolean matchesMetadataFilters(String orgId, Long kbId, Long documentId, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        if (documentId == null) {
            return false;
        }
        List<KbDocumentMetadataEntity> metadata = documentMetadataRepository.findByOrgIdAndKnowledgeBaseIdAndDocumentId(
                orgId, kbId, documentId);
        if (metadata.isEmpty()) {
            return false;
        }
        Map<String, String> found = metadata.stream()
                .collect(Collectors.toMap(KbDocumentMetadataEntity::getFieldKey, KbDocumentMetadataEntity::getStringValue, (a, b) -> b));
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            String actual = found.get(filter.getKey());
            if (actual == null || !actual.equalsIgnoreCase(filter.getValue())) {
                return false;
            }
        }
        return true;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private java.util.Optional<Long> parseLong(String value) {
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    private record EmbeddingConfig(String provider, String model, Integer dimension) {
    }

    private record CleanupResult(boolean success, int deletedChunks, int deletedVectors, String message) {

        static CleanupResult from(int deletedChunks, VectorDeleteResult result) {
            return new CleanupResult(
                    result.success(),
                    deletedChunks,
                    result.deletedCount(),
                    result.message());
        }

        CleanupResult merge(VectorDeleteResult result) {
            return new CleanupResult(
                    success && result.success(),
                    deletedChunks,
                    deletedVectors + result.deletedCount(),
                    join(message, result.message()));
        }

        private static String join(String left, String right) {
            if (left == null || left.isBlank()) {
                return right == null ? "" : right;
            }
            if (right == null || right.isBlank()) {
                return left;
            }
            return left + "; " + right;
        }
    }

    private List<String> splitToChunks(String text, int size, int overlap, String delimiter) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replace("\r", "\n").trim();
        String useDelimiter = delimiter == null || delimiter.isBlank() ? "\n" : delimiter;
        ArrayList<String> segments = new ArrayList<>();
        if ("\n".equals(useDelimiter)) {
            for (String line : normalized.split("\\n+")) {
                String trimmed = line.trim();
                if (!trimmed.isBlank()) {
                    segments.add(trimmed);
                }
            }
        } else {
            for (String part : normalized.split(java.util.regex.Pattern.quote(useDelimiter))) {
                String trimmed = part.trim();
                if (!trimmed.isBlank()) {
                    segments.add(trimmed);
                }
            }
        }
        String merged = String.join(" ", segments);
        int idx = 0;
        ArrayList<String> chunks = new ArrayList<>();
        int safeStep = Math.max(1, size - Math.max(0, overlap));
        while (idx < merged.length()) {
            int end = Math.min(merged.length(), idx + size);
            chunks.add(merged.substring(idx, end));
            idx += safeStep;
        }
        return chunks;
    }

    public record KbSettingsCommand(
            Integer chunkSize,
            Integer chunkOverlap,
            String chunkDelimiter,
            String retrievalStrategy,
            Integer topK,
            Double scoreThreshold,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension
    ) {
    }

    public record ChunkPreviewCommand(
            String text,
            Integer chunkSize,
            Integer chunkOverlap,
            String chunkDelimiter,
            Integer maxChunks
    ) {
    }

    public record RetrievalTestCommand(
            String query,
            Integer topK,
            Double scoreThreshold,
            String retrievalStrategy,
            Map<String, String> metadataFilters
    ) {
    }

    public record EvalSuiteCommand(
            String name,
            String description
    ) {
    }

    public record EvalCaseCommand(
            String query,
            Long expectedDocumentId,
            String expectedDocumentKeyword,
            String expectedChunkKeyword,
            Double minScore,
            Long forbiddenDocumentId,
            Map<String, String> metadataFilters
    ) {
    }

    public record MetadataFieldCommand(
            String fieldKey,
            String fieldName,
            String valueType
    ) {
    }

    private record EvalCaseOutcome(
            boolean passed,
            boolean expectedHit,
            boolean forbiddenViolation,
            boolean staleSource,
            double topScore,
            Long matchedDocumentId,
            Long matchedChunkId,
            Map<String, Object> summary
    ) {
    }

    private record UploadAdmission(String originalFilename, String safeFilename, String contentType) {
    }
}
