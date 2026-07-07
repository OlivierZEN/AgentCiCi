package com.codehouse.ciciassistant.customer.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.customer.service.CustomerWorkbenchService;
import com.codehouse.ciciassistant.customer.service.CustomerWorkbenchService.AssistantCommand;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customer-workbench")
public class CustomerWorkbenchController {

    private final CustomerWorkbenchService service;

    public CustomerWorkbenchController(CustomerWorkbenchService service) {
        this.service = service;
    }

    @GetMapping("/accounts")
    public ApiResponse<List<Map<String, Object>>> accounts() {
        return ApiResponse.ok(service.listAccounts(orgId(), userId()));
    }

    @GetMapping("/accounts/{accountId}")
    public ApiResponse<Map<String, Object>> account(@PathVariable String accountId) {
        return ApiResponse.ok(service.accountDetail(orgId(), userId(), accountId));
    }

    @GetMapping("/accounts/{accountId}/timeline")
    public ApiResponse<List<Map<String, Object>>> timeline(@PathVariable String accountId) {
        return ApiResponse.ok(service.timeline(orgId(), accountId));
    }

    @GetMapping("/accounts/{accountId}/recommendations")
    public ApiResponse<List<Map<String, Object>>> recommendations(@PathVariable String accountId) {
        return ApiResponse.ok(service.recommendations(orgId(), accountId));
    }

    @PostMapping("/recommendations/{recommendationId}/accept")
    public ApiResponse<Map<String, Object>> accept(@PathVariable String recommendationId) {
        return ApiResponse.ok(service.acceptRecommendation(orgId(), recommendationId), "建议已采纳");
    }

    @PostMapping("/recommendations/{recommendationId}/apply")
    public ApiResponse<Map<String, Object>> apply(@PathVariable String recommendationId) {
        return ApiResponse.ok(service.applyRecommendation(orgId(), userId(), recommendationId), "CRM 落地动作已记录");
    }

    @PostMapping("/assistant")
    public ApiResponse<Map<String, Object>> assistant(@RequestBody(required = false) AssistantCommand command) {
        return ApiResponse.ok(service.assistant(orgId(), userId(), command));
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
