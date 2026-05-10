package com.codehouse.ciciassistant.wecom.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.codehouse.ciciassistant.wecom.domain.WecomKfAccountEntity;
import com.codehouse.ciciassistant.wecom.service.WecomKfConfigService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireOrgAdmin
@RequestMapping("/admin/wecom/kf-accounts")
public class AdminWecomKfAccountController {

    private final WecomKfConfigService configService;

    public AdminWecomKfAccountController(WecomKfConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(configService.list(orgId).stream().map(configService::toPayload).toList());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createOrUpdate(@Valid @RequestBody UpsertRequest request) {
        WecomKfAccountEntity account = configService.upsert(
                TenantContext.requireOrgId(),
                TenantContext.getUserId().orElse("system"),
                toCommand(request));
        return ApiResponse.ok(configService.toPayload(account));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody UpsertRequest request) {
        WecomKfAccountEntity account = configService.update(
                TenantContext.requireOrgId(),
                id,
                TenantContext.getUserId().orElse("system"),
                toCommand(request));
        return ApiResponse.ok(configService.toPayload(account));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Map<String, Object>> enable(@PathVariable Long id) {
        return ApiResponse.ok(configService.toPayload(configService.setEnabled(TenantContext.requireOrgId(), id, true)));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Map<String, Object>> disable(@PathVariable Long id) {
        return ApiResponse.ok(configService.toPayload(configService.setEnabled(TenantContext.requireOrgId(), id, false)));
    }

    private WecomKfConfigService.UpsertCommand toCommand(UpsertRequest request) {
        return new WecomKfConfigService.UpsertCommand(
                request.corpId(),
                request.openKfId(),
                request.name(),
                request.secret(),
                request.token(),
                request.encodingAesKey(),
                request.agentId(),
                request.runAsUserId(),
                request.enabled());
    }

    public record UpsertRequest(String corpId,
                                String openKfId,
                                String name,
                                String secret,
                                String token,
                                String encodingAesKey,
                                String agentId,
                                String runAsUserId,
                                Boolean enabled) {
    }
}
