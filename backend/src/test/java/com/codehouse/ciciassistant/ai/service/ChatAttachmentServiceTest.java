package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ai.domain.ChatAttachmentEntity;
import com.codehouse.ciciassistant.ai.domain.ChatAttachmentRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class ChatAttachmentServiceTest {

    private static final byte[] PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01
    };

    @TempDir
    Path tempDir;

    @Test
    void uploadsValidatedImageAndBuildsMultimodalModelContent() {
        ChatAttachmentRepository repository = org.mockito.Mockito.mock(ChatAttachmentRepository.class);
        when(repository.findByCompanyIdAndUserIdAndSessionIdAndClientAttachmentId(
                "company-1", "user-1", "session-1", "client-1")).thenReturn(Optional.empty());
        when(repository.findUsedSlots("company-1", "session-1")).thenReturn(List.of());
        when(repository.saveAndFlush(any(ChatAttachmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ChatAttachmentService service = new ChatAttachmentService(repository, tempDir.toString());

        Map<String, Object> uploaded = service.upload(
                "company-1", "user-1", "session-1", "client-1",
                new MockMultipartFile("file", "screen.png", "image/png", PNG));

        assertThat(uploaded).containsEntry("status", "READY").containsEntry("slot", 1);
        ArgumentCaptor<ChatAttachmentEntity> captor = ArgumentCaptor.forClass(ChatAttachmentEntity.class);
        org.mockito.Mockito.verify(repository).saveAndFlush(captor.capture());
        Object content = service.buildModelContent("请说明截图内容", List.of(captor.getValue()));
        assertThat(content).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content;
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).containsEntry("type", "text").containsEntry("text", "请说明截图内容");
        assertThat(parts.get(1)).containsEntry("type", "image_url");
        assertThat(String.valueOf(((Map<?, ?>) parts.get(1).get("image_url")).get("url")))
                .startsWith("data:image/png;base64,");
    }

    @Test
    void rejectsContentWhoseSignatureDoesNotMatchDeclaredImageType() {
        ChatAttachmentRepository repository = org.mockito.Mockito.mock(ChatAttachmentRepository.class);
        when(repository.findByCompanyIdAndUserIdAndSessionIdAndClientAttachmentId(
                "company-1", "user-1", "session-1", "client-1")).thenReturn(Optional.empty());
        ChatAttachmentService service = new ChatAttachmentService(repository, tempDir.toString());

        assertThatThrownBy(() -> service.upload(
                "company-1", "user-1", "session-1", "client-1",
                new MockMultipartFile("file", "fake.png", "image/png", "not-an-image".getBytes())))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(exception.getReason()).isEqualTo("UNSUPPORTED_IMAGE_TYPE");
                });
    }

    @Test
    void rejectsDeclaredMimeThatDoesNotMatchDetectedImageSignature() {
        ChatAttachmentRepository repository = org.mockito.Mockito.mock(ChatAttachmentRepository.class);
        when(repository.findByCompanyIdAndUserIdAndSessionIdAndClientAttachmentId(
                "company-1", "user-1", "session-1", "client-1")).thenReturn(Optional.empty());
        ChatAttachmentService service = new ChatAttachmentService(repository, tempDir.toString());

        assertThatThrownBy(() -> service.upload(
                "company-1", "user-1", "session-1", "client-1",
                new MockMultipartFile("file", "wrong.jpg", "image/jpeg", PNG)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(exception.getReason()).isEqualTo("UNSUPPORTED_IMAGE_TYPE");
                });
    }

    @Test
    void rejectsReportedSizeAboveTwentyMibBeforeReadingContent() throws Exception {
        ChatAttachmentRepository repository = org.mockito.Mockito.mock(ChatAttachmentRepository.class);
        when(repository.findByCompanyIdAndUserIdAndSessionIdAndClientAttachmentId(
                "company-1", "user-1", "session-1", "client-1")).thenReturn(Optional.empty());
        MultipartFile oversized = org.mockito.Mockito.mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(ChatAttachmentService.MAX_IMAGE_BYTES + 1);
        ChatAttachmentService service = new ChatAttachmentService(repository, tempDir.toString());

        assertThatThrownBy(() -> service.upload("company-1", "user-1", "session-1", "client-1", oversized))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(exception.getReason()).isEqualTo("ATTACHMENT_TOO_LARGE");
                });
        org.mockito.Mockito.verify(oversized, org.mockito.Mockito.never()).getInputStream();
    }

    @Test
    void rejectsEleventhImageInSameConversation() {
        ChatAttachmentRepository repository = org.mockito.Mockito.mock(ChatAttachmentRepository.class);
        when(repository.findByCompanyIdAndUserIdAndSessionIdAndClientAttachmentId(
                "company-1", "user-1", "session-1", "client-11")).thenReturn(Optional.empty());
        when(repository.findUsedSlots("company-1", "session-1"))
                .thenReturn(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        ChatAttachmentService service = new ChatAttachmentService(repository, tempDir.toString());

        assertThatThrownBy(() -> service.upload(
                "company-1", "user-1", "session-1", "client-11",
                new MockMultipartFile("file", "screen.png", "image/png", PNG)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).isEqualTo("CONVERSATION_IMAGE_LIMIT_EXCEEDED");
                });
    }

    @Test
    void failsClosedWhenRequestedAttachmentIsNotOwnedByCurrentSession() {
        ChatAttachmentRepository repository = org.mockito.Mockito.mock(ChatAttachmentRepository.class);
        when(repository.findByCompanyIdAndUserIdAndSessionIdAndPublicIdIn(
                "company-1", "user-1", "session-1", List.of("att_missing"))).thenReturn(List.of());
        ChatAttachmentService service = new ChatAttachmentService(repository, tempDir.toString());

        assertThatThrownBy(() -> service.requireReadyForMessage(
                "company-1", "user-1", "session-1", List.of("att_missing")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("ATTACHMENT_NOT_FOUND");
                });
    }
}
