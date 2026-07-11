package com.codehouse.ciciassistant.customer.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.customer.service.CustomerWorkbenchService;
import com.codehouse.ciciassistant.customer.service.CustomerInteractionIngestionService;
import com.codehouse.ciciassistant.customer.service.CustomerInteractionIngestionService.ConfirmationCommand;
import com.codehouse.ciciassistant.customer.service.CustomerWorkbenchService.AssistantCommand;
import com.codehouse.ciciassistant.customer.service.CustomerWorkbenchService.FollowCommand;
import com.codehouse.ciciassistant.customer.service.CustomerWorkbenchService.InteractionCommand;
import com.codehouse.ciciassistant.customer.service.CustomerWorkbenchService.RecommendationDismiss;
import com.codehouse.ciciassistant.customer.service.CustomerWorkbenchService.RecommendationDraft;
import com.codehouse.ciciassistant.customer.service.CustomerWorkbenchService.RecommendationFeedbackCommand;
import com.codehouse.ciciassistant.customer.service.CustomerCrmProjectionService.QueueQuery;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/customer-workbench")
public class CustomerWorkbenchController {

    private final CustomerWorkbenchService service;
    private final CustomerInteractionIngestionService ingestionService;

    public CustomerWorkbenchController(CustomerWorkbenchService service,
                                       CustomerInteractionIngestionService ingestionService) {
        this.service = service;
        this.ingestionService = ingestionService;
    }

    @GetMapping("/accounts")
    public ApiResponse<List<Map<String, Object>>> accounts() {
        return ApiResponse.ok(service.listAccounts(orgId(), userId()));
    }

    @GetMapping("/queue")
    public ApiResponse<Map<String, Object>> queue(
            @RequestParam(defaultValue = "new") String mode,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "priority") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.ok(service.queue(orgId(), userId(),
                new QueueQuery(mode, filter, sort, direction, query, page, size, refresh)));
    }

    @GetMapping("/accounts/{accountId}")
    public ApiResponse<Map<String, Object>> account(@PathVariable String accountId) {
        return ApiResponse.ok(service.accountDetail(orgId(), userId(), accountId));
    }

    @GetMapping("/accounts/{accountId}/timeline")
    public ApiResponse<List<Map<String, Object>>> timeline(@PathVariable String accountId) {
        return ApiResponse.ok(service.timeline(orgId(), userId(), accountId));
    }

    @GetMapping("/accounts/{accountId}/assistant-history")
    public ApiResponse<List<Map<String, String>>> assistantHistory(@PathVariable String accountId) {
        return ApiResponse.ok(service.assistantHistory(orgId(), userId(), accountId));
    }

    @PostMapping("/accounts/{accountId}/interactions")
    public ApiResponse<Map<String, Object>> saveInteraction(@PathVariable String accountId,
                                                             @RequestBody InteractionCommand command) {
        return ApiResponse.ok(service.saveInteraction(orgId(), userId(), accountId, command), "互动事实已保存");
    }

    @PostMapping(value = "/accounts/{accountId}/interaction-batches", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> createInteractionBatch(
            @PathVariable String accountId,
            @RequestParam String sourceType,
            @RequestParam String occurredAt,
            @RequestParam(defaultValue = "") String subject,
            @RequestParam(defaultValue = "") String narrationText,
            @RequestParam(defaultValue = "") String pastedText,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return ApiResponse.ok(ingestionService.createBatch(
                orgId(), userId(), accountId, sourceType, occurredAt, subject, narrationText, pastedText, files),
                "原始材料已保存，正在整理");
    }

    @GetMapping("/accounts/{accountId}/interaction-batches")
    public ApiResponse<List<Map<String, Object>>> interactionBatches(@PathVariable String accountId) {
        return ApiResponse.ok(ingestionService.listBatches(orgId(), userId(), accountId));
    }

    @GetMapping("/interaction-batches/{batchId}")
    public ApiResponse<Map<String, Object>> interactionBatch(@PathVariable String batchId) {
        return ApiResponse.ok(ingestionService.getBatch(orgId(), userId(), batchId));
    }

    @GetMapping("/interaction-batches/{batchId}/assets/{assetId}")
    public ResponseEntity<Resource> interactionAsset(@PathVariable String batchId, @PathVariable String assetId) {
        var asset = ingestionService.asset(orgId(), userId(), batchId, assetId);
        MediaType contentType;
        try { contentType = MediaType.parseMediaType(asset.contentType()); }
        catch (Exception ex) { contentType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(asset.filename(), StandardCharsets.UTF_8).build().toString())
                .body(asset.resource());
    }

    @PostMapping("/interaction-batches/{batchId}/retry")
    public ApiResponse<Map<String, Object>> retryInteractionBatch(@PathVariable String batchId) {
        return ApiResponse.ok(ingestionService.retry(orgId(), userId(), batchId), "已重新提交整理任务");
    }

    @PostMapping("/interaction-batches/{batchId}/confirm")
    public ApiResponse<Map<String, Object>> confirmInteractionBatch(@PathVariable String batchId,
                                                                    @RequestBody ConfirmationCommand command) {
        return ApiResponse.ok(ingestionService.confirm(orgId(), userId(), batchId, command), "互动记录已确认并归集");
    }

    @GetMapping("/accounts/{accountId}/recommendations")
    public ApiResponse<List<Map<String, Object>>> recommendations(@PathVariable String accountId) {
        return ApiResponse.ok(service.recommendations(orgId(), userId(), accountId));
    }

    @PostMapping("/recommendations/{recommendationId}/accept")
    public ApiResponse<Map<String, Object>> accept(@PathVariable String recommendationId) {
        return ApiResponse.ok(service.acceptRecommendation(orgId(), userId(), recommendationId), "建议已采纳");
    }

    @PatchMapping("/recommendations/{recommendationId}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String recommendationId,
                                                    @RequestBody RecommendationDraft command) {
        return ApiResponse.ok(service.updateRecommendation(orgId(), userId(), recommendationId, command), "建议已更新，需重新确认");
    }

    @PostMapping("/recommendations/{recommendationId}/dismiss")
    public ApiResponse<Map<String, Object>> dismiss(@PathVariable String recommendationId,
                                                     @RequestBody(required = false) RecommendationDismiss command) {
        return ApiResponse.ok(service.dismissRecommendation(orgId(), userId(), recommendationId, command), "建议已忽略");
    }

    @PostMapping("/recommendations/{recommendationId}/confirm")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable String recommendationId) {
        return ApiResponse.ok(service.confirmRecommendation(orgId(), userId(), recommendationId), "建议已确认，可执行写回");
    }

    @PostMapping("/recommendations/{recommendationId}/apply")
    public ApiResponse<Map<String, Object>> apply(@PathVariable String recommendationId) {
        return ApiResponse.ok(service.applyRecommendation(orgId(), userId(), recommendationId), "CRM 落地动作已记录");
    }

    @PostMapping("/recommendations/{recommendationId}/feedback")
    public ApiResponse<Map<String, Object>> recommendationFeedback(@PathVariable String recommendationId,
                                                                    @RequestBody RecommendationFeedbackCommand command) {
        return ApiResponse.ok(service.recommendationFeedback(orgId(), userId(), recommendationId, command), "建议反馈已记录");
    }

    @PostMapping("/assistant")
    public ApiResponse<Map<String, Object>> assistant(@RequestBody(required = false) AssistantCommand command) {
        return ApiResponse.ok(service.assistant(orgId(), userId(), command));
    }

    @PostMapping(value = "/assistant/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter assistantStream(@RequestBody(required = false) AssistantCommand command) {
        return service.assistantStream(orgId(), userId(), command);
    }

    @GetMapping("/integration-status")
    public ApiResponse<Map<String, Object>> integrationStatus() {
        return ApiResponse.ok(service.integrationStatus(orgId(), userId()));
    }

    @PostMapping("/accounts/{accountId}/follow")
    public ApiResponse<Map<String, Object>> follow(@PathVariable String accountId, @RequestBody FollowCommand command) {
        return ApiResponse.ok(service.follow(orgId(), userId(), accountId, command.followed()));
    }

    @GetMapping("/notifications")
    public ApiResponse<List<Map<String, Object>>> notifications() {
        return ApiResponse.ok(service.notifications(orgId(), userId()));
    }

    @GetMapping("/supervisor-summary")
    public ApiResponse<Map<String, Object>> supervisorSummary() {
        return ApiResponse.ok(service.supervisorSummary(orgId(), userId()));
    }

    @PostMapping("/demo-data")
    public ApiResponse<Map<String, Object>> demoData(@RequestParam(defaultValue = "false") boolean reset) {
        return ApiResponse.ok(service.seedDemoData(orgId(), userId(), reset), "客户互动工作台演示数据已准备");
    }

    private String orgId() {
        return TenantContext.requireOrgId();
    }

    private String userId() {
        return TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
    }
}
