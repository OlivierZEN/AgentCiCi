package com.codehouse.ciciassistant.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ai.service.AliyunAsrService;
import com.codehouse.ciciassistant.ai.service.AliyunBailianClient;
import com.codehouse.ciciassistant.ai.service.ModelRouterService;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionAssetEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionAssetRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionBatchEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionBatchRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventRepository;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class CustomerInteractionIngestionServiceTest {

    @TempDir
    Path storageRoot;

    private CustomerInteractionBatchRepository batchRepository;
    private CustomerInteractionAssetRepository assetRepository;
    private CustomerWorkbenchService workbenchService;
    private ModelRouterService modelRouterService;
    private ModelProviderService modelProviderService;
    private CustomerInteractionIngestionService service;

    @BeforeEach
    void setUp() {
        batchRepository = mock(CustomerInteractionBatchRepository.class);
        assetRepository = mock(CustomerInteractionAssetRepository.class);
        workbenchService = mock(CustomerWorkbenchService.class);
        modelRouterService = mock(ModelRouterService.class);
        modelProviderService = mock(ModelProviderService.class);
        when(batchRepository.save(any(CustomerInteractionBatchEntity.class))).thenAnswer(invocation -> {
            CustomerInteractionBatchEntity batch = invocation.getArgument(0);
            if (batch.getId() == null) ReflectionTestUtils.setField(batch, "id", 41L);
            return batch;
        });
        when(assetRepository.save(any(CustomerInteractionAssetEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assetRepository.findByBatchIdOrderBySortOrderAsc(any())).thenReturn(List.of());
        when(batchRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(anyString(), any())).thenReturn(List.of());
        service = new CustomerInteractionIngestionService(
                batchRepository,
                assetRepository,
                mock(CustomerInteractionEventRepository.class),
                workbenchService,
                mock(CustomerMemoryService.class),
                mock(AliyunAsrService.class),
                mock(AliyunBailianClient.class),
                modelRouterService,
                modelProviderService,
                new ObjectMapper(),
                command -> { },
                storageRoot.toString());
    }

    @Test
    void rejectsEmptyAndUnsupportedMaterialsBeforePersisting() {
        assertThatThrownBy(() -> service.createBatch(
                "org-1", "user-1", "account-1", "WECHAT", Instant.now().toString(), "", "", "短", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少提供");

        MockMultipartFile executable = new MockMultipartFile(
                "files", "payload.exe", "application/octet-stream", "unsafe".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.createBatch(
                "org-1", "user-1", "account-1", "WECHAT", Instant.now().toString(), "", "", "", List.of(executable)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    void storesOnlyOneImmutableAssetWhenSameFileIsUploadedTwice() {
        byte[] content = "客户确认下周评审方案，并请补充权限清单。".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile first = new MockMultipartFile("files", "沟通记录.txt", "text/plain", content);
        MockMultipartFile duplicate = new MockMultipartFile("files", "沟通记录副本.txt", "text/plain", content);

        Map<String, Object> result = service.createBatch(
                "org-1", "user-1", "account-1", "WECHAT", Instant.now().toString(), "方案沟通", "", "", List.of(first, duplicate));

        ArgumentCaptor<CustomerInteractionAssetEntity> assetCaptor = ArgumentCaptor.forClass(CustomerInteractionAssetEntity.class);
        verify(assetRepository, times(1)).save(assetCaptor.capture());
        assertThat(assetCaptor.getValue().getInputType()).isEqualTo("DOCUMENT");
        assertThat(assetCaptor.getValue().getOriginalName()).isEqualTo("沟通记录.txt");
        assertThat(assetCaptor.getValue().getSha256()).hasSize(64);
        assertThat(result.get("status")).isEqualTo(CustomerInteractionBatchEntity.STATUS_QUEUED);
    }

    @Test
    void extractsTextAndKeepsConfirmableDraftWhenAiAnalysisIsUnavailable() throws Exception {
        CustomerInteractionBatchEntity batch = batch("batch-1");
        Path textFile = storageRoot.resolve("source.txt");
        Files.writeString(textFile, "客户确认下周二进行方案评审，要求会前补充权限清单。", StandardCharsets.UTF_8);
        CustomerInteractionAssetEntity asset = new CustomerInteractionAssetEntity(
                "asset-1", batch.getId(), "org-1", "DOCUMENT", "沟通记录.txt", "text/plain",
                Files.size(textFile), "hash", textFile.toString(), 0);
        when(batchRepository.findByPublicId("batch-1")).thenReturn(Optional.of(batch));
        when(assetRepository.findByBatchIdOrderBySortOrderAsc(batch.getId())).thenReturn(List.of(asset));
        when(modelRouterService.route("org-1", "customer-insight")).thenReturn(Map.of("provider", "aliyun", "modelName", "qwen-plus"));
        when(modelProviderService.credentialsForProvider("org-1", "aliyun")).thenReturn(Map.of("enabled", "false"));

        service.processBatch("batch-1");

        assertThat(asset.getStatus()).isEqualTo(CustomerInteractionAssetEntity.STATUS_READY);
        assertThat(batch.getStatus()).isEqualTo(CustomerInteractionBatchEntity.STATUS_PARTIAL);
        assertThat(batch.getCombinedText()).contains("【DOCUMENT · 沟通记录.txt】", "下周二进行方案评审");
        assertThat(batch.getAnalysisJson()).contains("\"degraded\":true");
    }

    @Test
    void confirmationWritesCrmOnceAndSubsequentRequestIsIdempotent() {
        CustomerInteractionBatchEntity batch = batch("batch-2");
        batch.markProcessed("客户确认下周二评审方案。", "{}", false, "");
        when(batchRepository.findByOrgIdAndPublicId("org-1", "batch-2")).thenReturn(Optional.of(batch));
        when(workbenchService.saveInteraction(anyString(), anyString(), anyString(), any()))
                .thenReturn(Map.of("eventId", "event-1", "deduplicated", false));
        var command = new CustomerInteractionIngestionService.ConfirmationCommand(
                "WECHAT", "方案沟通", "客户确认下周二评审方案，并要求会前补充权限清单。", Instant.now().toString());

        Map<String, Object> first = service.confirm("org-1", "user-1", "batch-2", command);
        Map<String, Object> second = service.confirm("org-1", "user-1", "batch-2", command);

        verify(workbenchService, times(1)).saveInteraction(anyString(), anyString(), anyString(), any());
        assertThat(first.get("confirmedEventId")).isEqualTo("event-1");
        assertThat(second.get("deduplicated")).isEqualTo(true);
    }

    private CustomerInteractionBatchEntity batch(String publicId) {
        CustomerInteractionBatchEntity batch = new CustomerInteractionBatchEntity(
                publicId, "org-1", "account-1", "user-1", "WECHAT", Instant.now(), "方案沟通", "", "");
        ReflectionTestUtils.setField(batch, "id", 41L);
        return batch;
    }
}
