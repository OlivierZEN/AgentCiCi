package com.codehouse.ciciassistant.embed.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.embed.service.EmbedAppService;
import com.codehouse.ciciassistant.embed.service.EmbedTokenService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireOrgAdmin
@RequestMapping("/embed/v1/admin/apps")
public class EmbedAdminController {

    private final EmbedAppService embedAppService;
    private final EmbedTokenService embedTokenService;

    public EmbedAdminController(EmbedAppService embedAppService, EmbedTokenService embedTokenService) {
        this.embedAppService = embedAppService;
        this.embedTokenService = embedTokenService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(embedAppService.listAdminApps(TenantContext.requireCompanyId()));
    }

    @GetMapping("/{appCode}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String appCode) {
        return ApiResponse.ok(embedAppService.adminDetail(TenantContext.requireCompanyId(), appCode));
    }

    @GetMapping("/{appCode}/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions(@PathVariable String appCode,
                                                           @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(embedAppService.recentSessions(TenantContext.requireCompanyId(), appCode, limit));
    }

    @PutMapping("/{appCode}/config")
    public ApiResponse<Map<String, Object>> updateConfig(@PathVariable String appCode,
                                                         @RequestBody EmbedAppService.ConfigCommand command) {
        return ApiResponse.ok(embedAppService.updateConfig(TenantContext.requireCompanyId(), appCode, command));
    }

    @PostMapping("/{appCode}/debug-token")
    public ApiResponse<EmbedTokenService.TokenIssue> debugToken(@PathVariable String appCode,
                                                                @RequestBody EmbedTokenService.TokenCommand command) {
        String userId = TenantContext.getUserId().orElse("");
        return ApiResponse.ok(embedTokenService.issueAdminDebugToken(TenantContext.requireCompanyId(), userId, appCode, command));
    }
}
