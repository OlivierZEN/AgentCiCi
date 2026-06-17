package com.codehouse.ciciassistant.kb.service;

import com.codehouse.ciciassistant.agent.domain.AgentKnowledgeBindingRepository;
import com.codehouse.ciciassistant.billing.service.BillingUsageMeteringService;
import com.codehouse.ciciassistant.kb.domain.KbChunkEntity;
import com.codehouse.ciciassistant.kb.domain.KbChunkRepository;
import com.codehouse.ciciassistant.kb.domain.KbDocumentMetadataEntity;
import com.codehouse.ciciassistant.kb.domain.KbDocumentMetadataRepository;
import com.codehouse.ciciassistant.kb.domain.KbDocumentEntity;
import com.codehouse.ciciassistant.kb.domain.KbDocumentRepository;
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
    private final AgentKnowledgeBindingRepository agentKnowledgeBindingRepository;
    private final ModelProviderService modelProviderService;
    private final BillingUsageMeteringService billingUsageMeteringService;
    private final VectorStoreClient vectorStoreClient;
    private final EmbeddingService embeddingService;
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
                                AgentKnowledgeBindingRepository agentKnowledgeBindingRepository,
                                ModelProviderService modelProviderService,
                                BillingUsageMeteringService billingUsageMeteringService,
                                VectorStoreClient vectorStoreClient,
                                EmbeddingService embeddingService,
                                RabbitTemplate rabbitTemplate,
                                ObjectMapper objectMapper,
                                @Value("${app.kb.storage-dir:./data/kb-files}") String storageDir,
                                @Value("${app.kb.indexing.mode:local}") String indexingMode,
                                @Value("${app.kb.embedding.provider:local}") String defaultEmbeddingProvider,
                                @Value("${app.kb.embedding.model:local-hash}") String defaultEmbeddingModel,
                                @Value("${app.kb.embedding.dimension:1024}") Integer defaultEmbeddingDimension,
                                @Value("${app.kb.upload.max-file-size-bytes:26214400}") Long maxUploadFileSizeBytes,
                                @Value("${app.kb.upload.allowed-extensions:txt,md,markdown,csv,json,docx}") String allowedUploadExtensions,
                                @Value("${app.kb.upload.allowed-content-types:text/plain,text/markdown,text/csv,application/csv,application/json,application/vnd.openxmlformats-officedocument.wordprocessingml.document}") String allowedUploadContentTypes) {
        this.kbRepository = kbRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.metadataFieldRepository = metadataFieldRepository;
        this.documentMetadataRepository = documentMetadataRepository;
        this.retrievalLogRepository = retrievalLogRepository;
        this.agentKnowledgeBindingRepository = agentKnowledgeBindingRepository;
        this.modelProviderService = modelProviderService;
        this.billingUsageMeteringService = billingUsageMeteringService;
        this.vectorStoreClient = vectorStoreClient;
        this.embeddingService = embeddingService;
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
        payload.put("supportedParserLabels", List.of("TXT", "Markdown", "CSV", "JSON", "DOCX"));
        payload.put("unsupportedParserLabels", List.of("PDF"));
        payload.put("pdfPolicy", "PDF parsing is not enabled in this build. Upload txt, md, csv, json, or docx, or extract PDF text before upload.");
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
            throw new IllegalArgumentException("PDF parsing is not enabled. Upload txt, md, csv, json, or docx, or extract PDF text before upload.");
        }
        boolean supported = contentType.startsWith("text/")
                || "application/json".equals(contentType)
                || name.endsWith(".txt")
                || name.endsWith(".md")
                || name.endsWith(".markdown")
                || name.endsWith(".csv")
                || name.endsWith(".json");
        if (!supported) {
            throw new IllegalArgumentException("Unsupported file type. P0 indexing supports txt, md, csv, json and docx files only.");
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private boolean isDocxFile(String contentType, String name) {
        return name.endsWith(".docx") || contentType.contains("wordprocessingml.document");
    }

    private boolean isPdfFile(String contentType, String name) {
        return name.endsWith(".pdf") || contentType.contains("application/pdf");
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
        if ("pdf".equals(extension) || "application/pdf".equals(contentType)) {
            throw new IllegalArgumentException("PDF parsing is not enabled. Upload txt, md, csv, json, or docx, or extract PDF text before upload.");
        }
        boolean extensionAllowed = allowedUploadExtensions.contains(extension);
        boolean contentAllowed = contentType.isBlank() || contentType.startsWith("text/") || allowedUploadContentTypes.contains(contentType);
        if (!extensionAllowed || !contentAllowed) {
            throw new IllegalArgumentException("Unsupported file type. Upload txt, md, csv, json, or docx files only.");
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

    public record MetadataFieldCommand(
            String fieldKey,
            String fieldName,
            String valueType
    ) {
    }

    private record UploadAdmission(String originalFilename, String safeFilename, String contentType) {
    }
}
