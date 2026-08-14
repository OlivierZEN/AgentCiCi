package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.ai.domain.ChatAttachmentEntity;
import com.codehouse.ciciassistant.ai.domain.ChatAttachmentRepository;
import com.codehouse.ciciassistant.ai.domain.ChatMessageEntity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatAttachmentService {

    public static final long MAX_IMAGE_BYTES = 20L * 1024L * 1024L;
    public static final int MAX_IMAGES_PER_SESSION = 10;
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final ChatAttachmentRepository repository;
    private final Path storageRoot;

    public ChatAttachmentService(ChatAttachmentRepository repository,
                                 @Value("${app.kb.storage-dir:./data/kb-files}") String storageDir) {
        this.repository = repository;
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize().resolve("chat-attachments");
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Map<String, Object> upload(String companyId, String userId, String sessionId,
                                      String clientAttachmentId, MultipartFile file) {
        String safeSessionId = requireIdentifier(sessionId, 64, "sessionId");
        String safeClientId = requireIdentifier(clientAttachmentId, 96, "clientAttachmentId");
        return repository.findByCompanyIdAndUserIdAndSessionIdAndClientAttachmentId(
                        companyId, userId, safeSessionId, safeClientId)
                .map(this::view)
                .orElseGet(() -> uploadNew(companyId, userId, safeSessionId, safeClientId, file));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String companyId, String userId, String sessionId) {
        List<Map<String, Object>> attachments = repository
                .findByCompanyIdAndUserIdAndSessionIdOrderBySlotNoAsc(companyId, userId, requireIdentifier(sessionId, 64, "sessionId"))
                .stream().map(this::view).toList();
        return Map.of(
                "attachments", attachments,
                "used", attachments.size(),
                "limit", MAX_IMAGES_PER_SESSION,
                "remaining", Math.max(0, MAX_IMAGES_PER_SESSION - attachments.size()));
    }

    @Transactional(readOnly = true)
    public AttachmentContent content(String companyId, String userId, String sessionId, String publicId) {
        ChatAttachmentEntity attachment = requireOwned(companyId, userId, sessionId, publicId);
        Path path = safeStoredPath(attachment);
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ATTACHMENT_CONTENT_NOT_FOUND");
        }
        return new AttachmentContent(new FileSystemResource(path), attachment.getOriginalName(), attachment.getContentType());
    }

    @Transactional
    public Map<String, Object> delete(String companyId, String userId, String sessionId, String publicId) {
        ChatAttachmentEntity attachment = requireOwned(companyId, userId, sessionId, publicId);
        if (attachment.getMessageId() != null || ChatAttachmentEntity.STATUS_ATTACHED.equals(attachment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ATTACHMENT_ALREADY_ATTACHED");
        }
        Path path = safeStoredPath(attachment);
        repository.delete(attachment);
        repository.flush();
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The database is authoritative. Orphan cleanup remains a governed maintenance task.
        }
        return Map.of("attachmentId", publicId, "deleted", true);
    }

    @Transactional(readOnly = true)
    public List<ChatAttachmentEntity> requireReadyForMessage(String companyId, String userId, String sessionId,
                                                              List<String> attachmentIds) {
        List<String> requested = normalizeIds(attachmentIds);
        if (requested.isEmpty()) {
            return List.of();
        }
        if (requested.size() > MAX_IMAGES_PER_SESSION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CONVERSATION_IMAGE_LIMIT_EXCEEDED");
        }
        List<ChatAttachmentEntity> found = repository.findByCompanyIdAndUserIdAndSessionIdAndPublicIdIn(
                companyId, userId, requireIdentifier(sessionId, 64, "sessionId"), requested);
        if (found.size() != requested.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND");
        }
        Map<String, ChatAttachmentEntity> byId = new LinkedHashMap<>();
        found.forEach(item -> byId.put(item.getPublicId(), item));
        List<ChatAttachmentEntity> ordered = new ArrayList<>();
        for (String id : requested) {
            ChatAttachmentEntity item = byId.get(id);
            if (item == null || !ChatAttachmentEntity.STATUS_READY.equals(item.getStatus()) || item.getMessageId() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "ATTACHMENT_NOT_READY");
            }
            ordered.add(item);
        }
        return List.copyOf(ordered);
    }

    public void attachToMessage(List<ChatAttachmentEntity> attachments, ChatMessageEntity message) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        if (message == null || message.getId() == null) {
            throw new IllegalArgumentException("message must be persisted before attaching files");
        }
        attachments.forEach(item -> item.attachTo(message.getId()));
        repository.saveAll(attachments);
    }

    public Object buildModelContent(String text, List<ChatAttachmentEntity> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return text;
        }
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", text == null || text.isBlank() ? "请分析这些图片。" : text));
        for (ChatAttachmentEntity attachment : attachments) {
            Path path = safeStoredPath(attachment);
            try {
                byte[] bytes = Files.readAllBytes(path);
                String dataUrl = "data:" + attachment.getContentType() + ";base64," + Base64.getEncoder().encodeToString(bytes);
                content.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "ATTACHMENT_CONTENT_NOT_FOUND", exception);
            }
        }
        return List.copyOf(content);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> viewsForMessage(Long messageId) {
        if (messageId == null) {
            return List.of();
        }
        return repository.findByMessageIdOrderBySlotNoAsc(messageId).stream().map(this::view).toList();
    }

    private Map<String, Object> uploadNew(String companyId, String userId, String sessionId,
                                          String clientAttachmentId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ATTACHMENT_EMPTY");
        }
        byte[] bytes = readBounded(file);
        String detectedType = detectImageType(bytes);
        validateDeclaredType(file.getContentType(), detectedType);
        List<Integer> usedSlots = repository.findUsedSlots(companyId, sessionId);
        int slot = firstFreeSlot(usedSlots);
        if (slot < 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CONVERSATION_IMAGE_LIMIT_EXCEEDED");
        }
        String publicId = "att_" + UUID.randomUUID().toString().replace("-", "");
        String extension = extensionFor(detectedType);
        Path directory = storageRoot.resolve(safeSegment(companyId)).resolve(safeSegment(sessionId)).normalize();
        ensureUnderStorage(directory);
        Path finalPath = directory.resolve(publicId + "." + extension).normalize();
        ensureUnderStorage(finalPath);
        Path temporaryPath = directory.resolve(publicId + ".uploading").normalize();
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
            ChatAttachmentEntity saved = repository.saveAndFlush(new ChatAttachmentEntity(
                    publicId, companyId, userId, sessionId, slot, clientAttachmentId,
                    safeFilename(file.getOriginalFilename(), publicId + "." + extension),
                    detectedType, bytes.length, sha256(bytes), finalPath.toString()));
            return view(saved);
        } catch (DataIntegrityViolationException conflict) {
            deleteQuietly(temporaryPath);
            deleteQuietly(finalPath);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CONVERSATION_IMAGE_LIMIT_EXCEEDED", conflict);
        } catch (IOException exception) {
            deleteQuietly(temporaryPath);
            deleteQuietly(finalPath);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ATTACHMENT_STORAGE_FAILED", exception);
        }
    }

    private byte[] readBounded(MultipartFile file) {
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_TOO_LARGE");
        }
        try (InputStream input = file.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_TOO_LARGE");
                }
                output.write(buffer, 0, read);
            }
            byte[] bytes = output.toByteArray();
            if (bytes.length == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ATTACHMENT_EMPTY");
            }
            return bytes;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ATTACHMENT_READ_FAILED", exception);
        }
    }

    static String detectImageType(byte[] bytes) {
        if (bytes != null && bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes != null && bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes != null && bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_TYPE");
    }

    private static void validateDeclaredType(String declaredType, String detectedType) {
        String declared = declaredType == null ? "" : declaredType.toLowerCase(Locale.ROOT).trim();
        if (!SUPPORTED_MIME_TYPES.contains(detectedType)
                || (!declared.isBlank() && !"application/octet-stream".equals(declared) && !detectedType.equals(declared))) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_TYPE");
        }
    }

    private ChatAttachmentEntity requireOwned(String companyId, String userId, String sessionId, String publicId) {
        return repository.findByCompanyIdAndUserIdAndSessionIdAndPublicId(
                        companyId, userId, requireIdentifier(sessionId, 64, "sessionId"), requireIdentifier(publicId, 64, "attachmentId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND"));
    }

    private Path safeStoredPath(ChatAttachmentEntity attachment) {
        Path path = Path.of(attachment.getStoragePath()).toAbsolutePath().normalize();
        ensureUnderStorage(path);
        return path;
    }

    private void ensureUnderStorage(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(storageRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ATTACHMENT_STORAGE_PATH_INVALID");
        }
    }

    private Map<String, Object> view(ChatAttachmentEntity item) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", item.getPublicId());
        view.put("clientAttachmentId", item.getClientAttachmentId());
        view.put("sessionId", item.getSessionId());
        view.put("slot", item.getSlotNo());
        view.put("name", item.getOriginalName());
        view.put("contentType", item.getContentType());
        view.put("sizeBytes", item.getSizeBytes());
        view.put("sha256", item.getSha256());
        view.put("status", item.getStatus());
        view.put("messageId", item.getMessageId() == null ? "" : String.valueOf(item.getMessageId()));
        view.put("contentUrl", "/ai/sessions/" + item.getSessionId() + "/attachments/" + item.getPublicId() + "/content");
        view.put("createdAt", item.getCreatedAt().toString());
        return view;
    }

    private static List<String> normalizeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            normalized.add(requireIdentifier(id, 64, "attachmentId"));
        }
        return List.copyOf(normalized);
    }

    private static int firstFreeSlot(List<Integer> usedSlots) {
        Set<Integer> used = new LinkedHashSet<>(usedSlots == null ? List.of() : usedSlots);
        for (int slot = 1; slot <= MAX_IMAGES_PER_SESSION; slot++) {
            if (!used.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private static String requireIdentifier(String value, int maxLength, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > maxLength || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_" + field.toUpperCase(Locale.ROOT));
        }
        return normalized;
    }

    private static String safeSegment(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String safeFilename(String value, String fallback) {
        String name = value == null ? "" : value.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\\t]", " ").trim();
        String result = name.isBlank() ? fallback : name;
        return result.length() <= 255 ? result : result.substring(0, 255);
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_TYPE");
        };
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate attachment digest", exception);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static void registerRollbackCleanup(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteQuietly(path);
                }
            }
        });
    }

    public record AttachmentContent(Resource resource, String filename, String contentType) {
    }
}
