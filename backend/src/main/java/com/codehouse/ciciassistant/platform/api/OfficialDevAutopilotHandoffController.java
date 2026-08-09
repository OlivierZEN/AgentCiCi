package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.platform.service.DevAutopilotHandoffService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public only in routing terms: a high-entropy, single-use handoff ticket is required. */
@Validated
@RestController
@RequestMapping("/openapi/v1/official/devautopilot/handoff")
public class OfficialDevAutopilotHandoffController {
    private final DevAutopilotHandoffService handoffs;

    public OfficialDevAutopilotHandoffController(DevAutopilotHandoffService handoffs) {
        this.handoffs = handoffs;
    }

    @PostMapping("/exchange")
    public ApiResponse<Map<String, Object>> exchange(@Valid @RequestBody ExchangeRequest request) {
        DevAutopilotHandoffService.ExchangedAccess access = handoffs.exchange(request.ticket());
        return ApiResponse.ok(Map.of(
                "accessToken", access.accessToken(),
                "tokenType", "Bearer",
                "expiresAt", access.expiresAt(),
                "tenantId", access.tenantId(),
                "companyId", access.companyId()), "DevAutopilot session exchanged");
    }

    public record ExchangeRequest(@NotBlank String ticket) {
    }
}
