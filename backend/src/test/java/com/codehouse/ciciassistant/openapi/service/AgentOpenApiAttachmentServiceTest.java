package com.codehouse.ciciassistant.openapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ai.domain.ChatSessionRepository;
import com.codehouse.ciciassistant.ai.service.ChatAttachmentService;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiFileEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiFileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class AgentOpenApiAttachmentServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistUploadAndBridgeRealBytesIntoSharedAttachmentRuntime() throws Exception {
        AgentApiFileRepository repository = mock(AgentApiFileRepository.class);
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        ChatAttachmentService chatAttachmentService = mock(ChatAttachmentService.class);
        SafeRemoteFileFetcher fetcher = mock(SafeRemoteFileFetcher.class);
        AgentOpenApiAttachmentService service = new AgentOpenApiAttachmentService(
                repository, chatSessionRepository, chatAttachmentService, fetcher, tempDir.toString());
        AtomicReference<AgentApiFileEntity> savedFile = new AtomicReference<>();
        when(repository.save(any(AgentApiFileEntity.class))).thenAnswer(invocation -> {
            AgentApiFileEntity entity = invocation.getArgument(0);
            savedFile.set(entity);
            return entity;
        });
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};

        Map<String, Object> uploaded = service.upload(auth(),
                new MockMultipartFile("file", "purchase.png", "image/png", png),
                "customer-001", "conversation-001");

        assertThat(uploaded).containsEntry("mime_type", "image/png").containsEntry("status", "ready");
        AgentApiFileEntity entity = savedFile.get();
        assertThat(entity.getStorageKey()).doesNotStartWith("agent-open-api://");
        assertThat(Files.readAllBytes(tempDir.resolve("agent-open-api-files").resolve(entity.getStorageKey())))
                .containsExactly(png);

        when(repository.findForUpdateByFileIdAndCompanyIdAndCredentialIdAndAgentId(
                entity.getFileId(), "company-1", 7L, "agent-1")).thenReturn(Optional.of(entity));
        when(chatAttachmentService.importOpenApiFile(
                eq("company-1"), eq("run-user"), eq("internal-session"), eq("openapi-" + entity.getFileId()),
                eq("purchase.png"), eq("image/png"), any(byte[].class)))
                .thenReturn(Map.of("id", "att-runtime-1"));

        List<String> attachmentIds = service.bridgeToRuntime(auth(), "customer-001", "conversation-001",
                "internal-session", List.of(entity.getFileId()));

        assertThat(attachmentIds).containsExactly("att-runtime-1");
        verify(chatAttachmentService).importOpenApiFile(
                eq("company-1"), eq("run-user"), eq("internal-session"), eq("openapi-" + entity.getFileId()),
                eq("purchase.png"), eq("image/png"), any(byte[].class));
    }

    @Test
    void shouldFailClosedForAmbiguousDuplicateOrCrossScopeReferences() {
        AgentApiFileRepository repository = mock(AgentApiFileRepository.class);
        AgentOpenApiAttachmentService service = new AgentOpenApiAttachmentService(
                repository, mock(ChatSessionRepository.class), mock(ChatAttachmentService.class),
                mock(SafeRemoteFileFetcher.class), tempDir.toString());

        assertThatThrownBy(() -> service.materializeReferences(auth(),
                List.of(Map.of("upload_file_id", "file_12345678", "url", "https://files.example.com/a.png")),
                "customer-001", "conversation-001"))
                .isInstanceOf(AgentOpenApiException.class)
                .extracting("code").isEqualTo("INVALID_FILE_REFERENCE");

        when(repository.findByFileIdAndCompanyIdAndCredentialIdAndAgentId(
                "file_12345678", "company-1", 7L, "agent-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.materializeReferences(auth(),
                List.of(Map.of("upload_file_id", "file_12345678")), "customer-001", "conversation-001"))
                .isInstanceOf(AgentOpenApiException.class)
                .extracting("code").isEqualTo("FILE_NOT_FOUND");
    }

    private AgentOpenApiAuthService.AuthenticatedCredential auth() {
        AgentApiCredentialEntity credential = mock(AgentApiCredentialEntity.class);
        when(credential.getId()).thenReturn(7L);
        when(credential.getCompanyId()).thenReturn("company-1");
        when(credential.getAgentId()).thenReturn("agent-1");
        when(credential.getRunAsUserId()).thenReturn("run-user");
        return new AgentOpenApiAuthService.AuthenticatedCredential(credential, null, "127.0.0.1", null);
    }
}
