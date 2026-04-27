package com.codehouse.ciciassistant.kb.service;

import com.codehouse.ciciassistant.kb.domain.KbChunkEntity;
import com.codehouse.ciciassistant.kb.domain.KbChunkRepository;
import com.codehouse.ciciassistant.kb.domain.KbDocumentEntity;
import com.codehouse.ciciassistant.kb.domain.KbDocumentRepository;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseEntity;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.codehouse.ciciassistant.kb.config.KbAsyncConfig;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository kbRepository;
    private final KbDocumentRepository documentRepository;
    private final KbChunkRepository chunkRepository;
    private final VectorStoreClient vectorStoreClient;
    private final EmbeddingService embeddingService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String indexingMode;
    private final Path storageRoot;

    public KnowledgeBaseService(KnowledgeBaseRepository kbRepository,
                                KbDocumentRepository documentRepository,
                                KbChunkRepository chunkRepository,
                                VectorStoreClient vectorStoreClient,
                                EmbeddingService embeddingService,
                                RabbitTemplate rabbitTemplate,
                                ObjectMapper objectMapper,
                                @Value("${app.kb.storage-dir:./data/kb-files}") String storageDir,
                                @Value("${app.kb.indexing.mode:local}") String indexingMode) {
        this.kbRepository = kbRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.vectorStoreClient = vectorStoreClient;
        this.embeddingService = embeddingService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.indexingMode = indexingMode;
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public Map<String, Object> createKnowledgeBase(String orgId, String name, String description) {
        KnowledgeBaseEntity entity = kbRepository.save(new KnowledgeBaseEntity(orgId, name, description));
        return Map.of(
                "id", entity.getId(),
                "orgId", entity.getOrgId(),
                "name", entity.getName(),
                "description", entity.getDescription() == null ? "" : entity.getDescription(),
                "status", entity.getStatus()
        );
    }

    @Transactional
    public Map<String, Object> updateKnowledgeBase(String orgId, Long id, String name, String description) {
        KnowledgeBaseEntity kb = kbRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
        kb.update(name, description, kb.getStatus());
        kbRepository.save(kb);
        return Map.of(
                "id", kb.getId(),
                "orgId", kb.getOrgId(),
                "name", kb.getName(),
                "description", kb.getDescription() == null ? "" : kb.getDescription(),
                "status", kb.getStatus()
        );
    }

    public List<Map<String, Object>> listKnowledgeBases(String orgId) {
        java.util.ArrayList<Map<String, Object>> out = new java.util.ArrayList<>();
        for (KnowledgeBaseEntity item : kbRepository.findByOrgIdOrderByIdDesc(orgId)) {
            java.util.HashMap<String, Object> row = new java.util.HashMap<>();
            row.put("id", item.getId());
            row.put("orgId", item.getOrgId());
            row.put("name", item.getName());
            row.put("description", item.getDescription() == null ? "" : item.getDescription());
            row.put("status", item.getStatus());
            out.add(row);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> uploadDocument(String orgId, Long kbId, MultipartFile file) {
        KnowledgeBaseEntity kb = kbRepository.findByIdAndOrgId(kbId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        try {
            Files.createDirectories(storageRoot.resolve(orgId).resolve(String.valueOf(kbId)));
            String safeName = UUID.randomUUID() + "-" + file.getOriginalFilename();
            Path path = storageRoot.resolve(orgId).resolve(String.valueOf(kbId)).resolve(safeName);
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

            KbDocumentEntity doc = documentRepository.save(new KbDocumentEntity(
                    orgId,
                    kb.getId(),
                    file.getOriginalFilename() == null ? safeName : file.getOriginalFilename(),
                    file.getContentType(),
                    path.toString()
            ));
            return Map.of(
                    "id", doc.getId(),
                    "orgId", doc.getOrgId(),
                    "knowledgeBaseId", doc.getKnowledgeBaseId(),
                    "name", doc.getName(),
                    "status", doc.getStatus()
            );
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to store file: " + ex.getMessage());
        }
    }

    public List<Map<String, Object>> listDocuments(String orgId, Long kbId) {
        java.util.ArrayList<Map<String, Object>> out = new java.util.ArrayList<>();
        for (KbDocumentEntity doc : documentRepository.findByOrgIdAndKnowledgeBaseIdOrderByIdDesc(orgId, kbId)) {
            java.util.HashMap<String, Object> row = new java.util.HashMap<>();
            row.put("id", doc.getId());
            row.put("orgId", doc.getOrgId());
            row.put("knowledgeBaseId", doc.getKnowledgeBaseId());
            row.put("name", doc.getName());
            row.put("status", doc.getStatus());
            row.put("contentType", doc.getContentType() == null ? "" : doc.getContentType());
            row.put("createdAt", doc.getCreatedAt().toString());
            out.add(row);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> publishDocument(String orgId, Long documentId) {
        KbDocumentEntity doc = documentRepository.findByIdAndOrgId(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        doc.setStatus("INDEXING");
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
        return Map.of("id", doc.getId(), "status", "INDEXING");
    }

    @Transactional
    public Map<String, Object> deleteKnowledgeBase(String orgId, Long id) {
        kbRepository.deleteByIdAndOrgId(id, orgId);
        return Map.of("id", id, "status", "DELETED");
    }

    @Transactional
    public Map<String, Object> deleteDocument(String orgId, Long id) {
        KbDocumentEntity doc = documentRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        try {
            Files.deleteIfExists(Path.of(doc.getStoragePath()));
        } catch (IOException ignored) {
        }
        documentRepository.deleteByIdAndOrgId(id, orgId);
        return Map.of("id", id, "status", "DELETED");
    }

    @Transactional
    public void indexDocument(String orgId, Long documentId) {
        KbDocumentEntity doc = documentRepository.findByIdAndOrgId(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        try {
            String text = Files.readString(Path.of(doc.getStoragePath()), StandardCharsets.UTF_8);
            for (String chunk : splitToChunks(text, 280)) {
                if (!chunk.isBlank()) {
                    String vectorId = vectorStoreClient.upsert(
                            doc.getOrgId(),
                            String.valueOf(doc.getKnowledgeBaseId()),
                            chunk,
                            embeddingService.embed(chunk));
                    chunkRepository.save(new KbChunkEntity(
                            doc.getOrgId(),
                            String.valueOf(doc.getKnowledgeBaseId()),
                            chunk,
                            "indexed,doc-" + doc.getId(),
                            vectorId
                    ));
                }
            }
            doc.setStatus("PUBLISHED");
            documentRepository.save(doc);
        } catch (Exception ex) {
            doc.setStatus("FAILED");
            documentRepository.save(doc);
        }
    }

    private List<String> splitToChunks(String text, int size) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replace("\r", " ").replace("\n", " ").trim();
        int idx = 0;
        java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
        while (idx < normalized.length()) {
            int end = Math.min(normalized.length(), idx + size);
            chunks.add(normalized.substring(idx, end));
            idx = end;
        }
        return chunks;
    }
}
