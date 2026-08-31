package com.codehouse.ciciassistant.openapi.service;

import com.codehouse.ciciassistant.ai.domain.ChatSessionEntity;
import com.codehouse.ciciassistant.ai.domain.ChatSessionRepository;
import com.codehouse.ciciassistant.ai.service.ChatAttachmentService;
import com.codehouse.ciciassistant.openapi.domain.AgentApiFileEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiFileRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentOpenApiAttachmentService {

    static final long MAX_BYTES = 15L * 1024L * 1024L;
    static final int MAX_FILES = 5;
    private static final Duration FILE_TTL = Duration.ofHours(24);

    private final AgentApiFileRepository fileRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatAttachmentService chatAttachmentService;
    private final SafeRemoteFileFetcher remoteFileFetcher;
    private final Path storageRoot;

    public AgentOpenApiAttachmentService(AgentApiFileRepository fileRepository,
                                         ChatSessionRepository chatSessionRepository,
                                         ChatAttachmentService chatAttachmentService,
                                         SafeRemoteFileFetcher remoteFileFetcher,
                                         @Value("${app.kb.storage-dir:./data/kb-files}") String storageDir) {
        this.fileRepository = fileRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatAttachmentService = chatAttachmentService;
        this.remoteFileFetcher = remoteFileFetcher;
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize().resolve("agent-open-api-files");
    }

    @Transactional
    public Map<String, Object> upload(AgentOpenApiAuthService.AuthenticatedCredential auth,
                                      MultipartFile file,
                                      String externalUserId,
                                      String externalSessionId) {
        if (file == null || file.isEmpty()) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_REFERENCE", "file is required");
        }
        byte[] bytes = readBounded(file);
        return store(auth, text(externalUserId), text(externalSessionId), safeFilename(file.getOriginalFilename(), "upload-file"),
                file.getContentType(), bytes, "UPLOAD", "", "", "");
    }

    @Transactional
    public Map<String, Object> importRemote(AgentOpenApiAuthService.AuthenticatedCredential auth,
                                            String url,
                                            String suggestedName,
                                            String externalUserId,
                                            String externalSessionId,
                                            String idempotencyKey) {
        String keyHash = text(idempotencyKey).isBlank() ? "" : sha256(text(idempotencyKey));
        if (!keyHash.isBlank()) {
            AgentApiFileEntity existing = fileRepository
                    .findFirstByCompanyIdAndCredentialIdAndAgentIdAndExternalUserIdAndExternalSessionIdAndImportIdempotencyKeyHashAndStatus(
                            auth.credential().getCompanyId(), auth.credential().getId(), auth.credential().getAgentId(),
                            text(externalUserId), text(externalSessionId), keyHash, "READY")
                    .orElse(null);
            if (existing != null) return payload(existing);
        }
        URI safeUri = SafeRemoteFileFetcher.requireSafeUri(url);
        SafeRemoteFileFetcher.FetchedFile fetched = remoteFileFetcher.fetch(safeUri.toString(), suggestedName);
        return store(auth, text(externalUserId), text(externalSessionId), fetched.name(), fetched.declaredMimeType(),
                fetched.bytes(), "REMOTE_URL", fetched.host(), sha256(safeUri.toString()), keyHash);
    }

    @Transactional
    public List<String> materializeReferences(AgentOpenApiAuthService.AuthenticatedCredential auth,
                                              List<Map<String, Object>> files,
                                              String externalUserId,
                                              String externalSessionId) {
        if (files == null || files.isEmpty()) return List.of();
        if (files.size() > MAX_FILES) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_REFERENCE", "No more than 5 files are allowed");
        }
        LinkedHashSet<String> seenIds = new LinkedHashSet<>();
        LinkedHashSet<String> seenUrls = new LinkedHashSet<>();
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> item : files) {
            String id = text(firstRaw(item, "upload_file_id", "file_id", "id"));
            String url = text(item == null ? null : item.get("url"));
            if ((id.isBlank() && url.isBlank()) || (!id.isBlank() && !url.isBlank())) {
                throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_REFERENCE",
                        "Each file must contain exactly one of upload_file_id or url");
            }
            if (!id.isBlank()) {
                if (!id.matches("file_[A-Za-z0-9]{8,64}") || !seenIds.add(id)) {
                    throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_REFERENCE", "File references must be valid and unique");
                }
                requireScoped(auth, id, externalUserId, externalSessionId, false);
                ids.add(id);
                continue;
            }
            URI safeUri = SafeRemoteFileFetcher.requireSafeUri(url);
            String normalizedUrl = safeUri.toString();
            if (!seenUrls.add(normalizedUrl)) {
                throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_REFERENCE", "File URLs must be unique");
            }
            String name = text(item.get("name"));
            Map<String, Object> imported = importRemote(auth, normalizedUrl, name, externalUserId, externalSessionId, "");
            ids.add(String.valueOf(imported.get("id")));
        }
        return List.copyOf(ids);
    }

    @Transactional
    public List<String> bridgeToRuntime(AgentOpenApiAuthService.AuthenticatedCredential auth,
                                        String externalUserId,
                                        String externalSessionId,
                                        String internalSessionId,
                                        List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) return List.of();
        ensureRuntimeSession(auth, externalSessionId, internalSessionId);
        List<String> attachmentIds = new ArrayList<>();
        for (String fileId : fileIds) {
            AgentApiFileEntity file = requireScoped(auth, fileId, externalUserId, externalSessionId, true);
            Path path = storedPath(file);
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(path);
            } catch (IOException exception) {
                throw new AgentOpenApiException(HttpStatus.BAD_GATEWAY, "ATTACHMENT_BINDING_FAILED", "File content is unavailable");
            }
            if (bytes.length != file.getSizeBytes() || !sha256(bytes).equals(file.getSha256())) {
                throw new AgentOpenApiException(HttpStatus.BAD_GATEWAY, "ATTACHMENT_BINDING_FAILED", "File integrity check failed");
            }
            try {
                Map<String, Object> attachment = chatAttachmentService.importOpenApiFile(
                        auth.credential().getCompanyId(), auth.credential().getRunAsUserId(), internalSessionId,
                        "openapi-" + fileId, file.getName(), file.getDetectedMimeType(), bytes);
                attachmentIds.add(String.valueOf(attachment.get("id")));
            } catch (ResponseStatusException exception) {
                throw mapRuntimeFailure(exception);
            }
        }
        return List.copyOf(attachmentIds);
    }

    private void ensureRuntimeSession(AgentOpenApiAuthService.AuthenticatedCredential auth,
                                      String externalSessionId,
                                      String internalSessionId) {
        String companyId = auth.credential().getCompanyId();
        String userId = auth.credential().getRunAsUserId();
        ChatSessionEntity existing = chatSessionRepository.findById(internalSessionId).orElse(null);
        if (existing != null) {
            if (!companyId.equals(existing.getCompanyId()) || !userId.equals(existing.getUserId())) {
                throw new AgentOpenApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "File was not found");
            }
            return;
        }
        ChatSessionEntity created = new ChatSessionEntity(
                internalSessionId, companyId, userId, auth.credential().getAgentId(), "新会话",
                "openapi", "USER", text(externalSessionId).isBlank() ? null : text(externalSessionId));
        try {
            chatSessionRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException conflict) {
            chatSessionRepository.findByIdAndCompanyId(internalSessionId, companyId)
                    .filter(session -> userId.equals(session.getUserId()))
                    .orElseThrow(() -> conflict);
        }
    }

    private AgentApiFileEntity requireScoped(AgentOpenApiAuthService.AuthenticatedCredential auth,
                                             String fileId,
                                             String externalUserId,
                                             String externalSessionId,
                                             boolean bindMissingScope) {
        AgentApiFileEntity file = bindMissingScope
                ? fileRepository.findForUpdateByFileIdAndCompanyIdAndCredentialIdAndAgentId(
                        fileId, auth.credential().getCompanyId(), auth.credential().getId(), auth.credential().getAgentId()).orElse(null)
                : fileRepository.findByFileIdAndCompanyIdAndCredentialIdAndAgentId(
                        fileId, auth.credential().getCompanyId(), auth.credential().getId(), auth.credential().getAgentId()).orElse(null);
        if (file == null) throw fileNotFound();
        String user = text(externalUserId);
        String session = text(externalSessionId);
        if ((!text(file.getExternalUserId()).isBlank() && !text(file.getExternalUserId()).equals(user))
                || (!text(file.getExternalSessionId()).isBlank() && !text(file.getExternalSessionId()).equals(session))) {
            throw fileNotFound();
        }
        if (file.getExpiresAt() != null && !file.getExpiresAt().isAfter(Instant.now())) {
            throw new AgentOpenApiException(HttpStatus.GONE, "FILE_EXPIRED", "File has expired");
        }
        if (!"READY".equals(file.getStatus())) {
            throw new AgentOpenApiException(HttpStatus.CONFLICT, "FILE_NOT_READY", "File is not ready");
        }
        if (bindMissingScope) {
            file.bindExternalUser(user);
            file.bindExternalSession(session);
            fileRepository.save(file);
        }
        return file;
    }

    private Map<String, Object> store(AgentOpenApiAuthService.AuthenticatedCredential auth,
                                      String externalUserId,
                                      String externalSessionId,
                                      String name,
                                      String declaredMimeType,
                                      byte[] bytes,
                                      String sourceType,
                                      String sourceHost,
                                      String sourceUrlHash,
                                      String idempotencyHash) {
        String detected;
        try {
            detected = ChatAttachmentService.detectAttachmentType(bytes, name, declaredMimeType);
        } catch (ResponseStatusException exception) {
            throw mapRuntimeFailure(exception);
        }
        String fileId = "file_" + UUID.randomUUID().toString().replace("-", "");
        String extension = extensionFor(detected);
        Path directory = storageRoot.resolve(safeSegment(auth.credential().getCompanyId()))
                .resolve(String.valueOf(auth.credential().getId())).normalize();
        ensureUnderStorage(directory);
        Path finalPath = directory.resolve(fileId + "." + extension).normalize();
        Path temporaryPath = directory.resolve(fileId + ".uploading").normalize();
        ensureUnderStorage(finalPath);
        ensureUnderStorage(temporaryPath);
        try {
            Files.createDirectories(directory);
            Files.write(temporaryPath, bytes);
            try {
                Files.move(temporaryPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporaryPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
            registerRollbackCleanup(finalPath);
            String relativeKey = storageRoot.relativize(finalPath).toString();
            AgentApiFileEntity saved = fileRepository.save(new AgentApiFileEntity(
                    fileId, auth.credential().getCompanyId(), auth.credential().getId(), auth.credential().getAgentId(),
                    externalUserId, externalSessionId, safeFilename(name, fileId + "." + extension), bytes.length,
                    text(declaredMimeType), relativeKey, sourceType, text(sourceHost), sourceUrlHash, detected,
                    detected.startsWith("image/") ? "IMAGE" : "DOCUMENT", sha256(bytes), idempotencyHash,
                    Instant.now().plus(FILE_TTL)));
            return payload(saved);
        } catch (IOException exception) {
            try { Files.deleteIfExists(temporaryPath); } catch (IOException ignored) { }
            try { Files.deleteIfExists(finalPath); } catch (IOException ignored) { }
            throw new AgentOpenApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ATTACHMENT_BINDING_FAILED", "File storage failed");
        }
    }

    private byte[] readBounded(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new AgentOpenApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File exceeds 15MB");
        }
        try (InputStream input = file.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_BYTES) {
                    throw new AgentOpenApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File exceeds 15MB");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (AgentOpenApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_REFERENCE", "File could not be read");
        }
    }

    private Map<String, Object> payload(AgentApiFileEntity file) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", file.getFileId());
        result.put("name", file.getName());
        result.put("mime_type", file.getDetectedMimeType());
        result.put("kind", text(file.getFileKind()).toLowerCase(Locale.ROOT));
        result.put("size", file.getSizeBytes());
        result.put("status", text(file.getStatus()).toLowerCase(Locale.ROOT));
        result.put("created_at", file.getCreatedAt().toString());
        result.put("expires_at", file.getExpiresAt() == null ? "" : file.getExpiresAt().toString());
        return result;
    }

    private Path storedPath(AgentApiFileEntity file) {
        Path path = storageRoot.resolve(file.getStorageKey()).normalize();
        ensureUnderStorage(path);
        if (!Files.isRegularFile(path)) {
            throw new AgentOpenApiException(HttpStatus.BAD_GATEWAY, "ATTACHMENT_BINDING_FAILED", "File content is unavailable");
        }
        return path;
    }

    private void ensureUnderStorage(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(storageRoot)) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_REFERENCE", "File storage key is invalid");
        }
    }

    private void registerRollbackCleanup(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) return;
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            }
        });
    }

    private AgentOpenApiException mapRuntimeFailure(ResponseStatusException exception) {
        String reason = exception.getReason() == null ? "" : exception.getReason();
        if (reason.contains("TOO_LARGE")) return new AgentOpenApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File exceeds the allowed size");
        if (reason.contains("UNSUPPORTED") || reason.contains("CONTENT_TYPE_MISMATCH")) return new AgentOpenApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "File type is not supported");
        if (reason.contains("TEXT_EXTRACTION")) return new AgentOpenApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_PROCESSING_FAILED", "File processing failed");
        return new AgentOpenApiException(HttpStatus.BAD_GATEWAY, "ATTACHMENT_BINDING_FAILED", "File could not be bound to the Agent runtime");
    }

    private static AgentOpenApiException fileNotFound() {
        return new AgentOpenApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "File was not found");
    }

    private static Object firstRaw(Map<String, Object> map, String... keys) {
        if (map == null) return "";
        for (String key : keys) if (map.get(key) != null) return map.get(key);
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String safeSegment(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String safeFilename(String value, String fallback) {
        String name = value == null ? "" : value.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\r\\n\\t]", " ").trim();
        if (name.isBlank()) name = fallback;
        return name.length() <= 255 ? name : name.substring(0, 255);
    }

    private static String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "text/plain" -> "txt";
            case "text/markdown" -> "md";
            case "text/csv" -> "csv";
            case "application/json" -> "json";
            case "application/pdf" -> "pdf";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            default -> throw new AgentOpenApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "File type is not supported");
        };
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
