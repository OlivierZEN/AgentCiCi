package com.codehouse.ciciassistant.feishu.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.feishu.service.FeishuBotPairingService;
import com.codehouse.ciciassistant.feishu.service.FeishuPairingCodeStore;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/feishu/bot/pairing")
public class FeishuBotPairingController {

    private final FeishuBotPairingService feishuBotPairingService;

    public FeishuBotPairingController(FeishuBotPairingService feishuBotPairingService) {
        this.feishuBotPairingService = feishuBotPairingService;
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(feishuBotPairingService.getBindingStatus(companyId, userId));
    }

    @PostMapping("/code")
    public ApiResponse<Map<String, Object>> createCode(@Valid @RequestBody CreatePairingCodeRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        FeishuPairingCodeStore.PairingCode code = feishuBotPairingService.createPairingCode(companyId, userId, request.agentCode());
        return ApiResponse.ok(Map.of(
                "code", code.code(),
                "agentCode", code.agentCode(),
                "expiresInSeconds", code.expiresInSeconds(),
                "command", "配对 " + code.code()
        ));
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> unbind() {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        feishuBotPairingService.unbindCurrentUser(companyId, userId);
        return ApiResponse.okMessage("已解除当前飞书绑定");
    }

    public record CreatePairingCodeRequest(String agentCode) {
    }
}
