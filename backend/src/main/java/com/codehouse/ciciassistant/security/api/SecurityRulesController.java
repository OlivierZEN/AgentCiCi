package com.codehouse.ciciassistant.security.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.security.service.SecurityRulesService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/security-rules")
@RequireOrgAdmin
public class SecurityRulesController {

    private final SecurityRulesService service;

    public SecurityRulesController(SecurityRulesService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(service.overview(TenantContext.requireOrgId()));
    }

    @GetMapping("/rules")
    public ApiResponse<List<SecurityRulesService.RuleView>> listRules() {
        return ApiResponse.ok(service.listRules(TenantContext.requireOrgId()));
    }

    @PostMapping("/rules")
    public ApiResponse<SecurityRulesService.RuleView> createRule(@Valid @RequestBody RuleRequest request) {
        return ApiResponse.ok(service.createRule(TenantContext.requireOrgId(), request.toCommand()));
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<SecurityRulesService.RuleView> updateRule(@PathVariable Long id,
                                                                 @Valid @RequestBody RuleRequest request) {
        return ApiResponse.ok(service.updateRule(TenantContext.requireOrgId(), id, request.toCommand()));
    }

    @PostMapping("/test")
    public ApiResponse<SecurityRulesService.TestResult> testRule(@Valid @RequestBody TestRequest request) {
        return ApiResponse.ok(service.testRule(
                TenantContext.requireOrgId(),
                request.text(),
                request.rule().toCommand()
        ));
    }

    @GetMapping("/events")
    public ApiResponse<List<SecurityRulesService.EventView>> listEvents(@RequestParam(required = false) Boolean reviewed,
                                                                        @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.listEvents(TenantContext.requireOrgId(), reviewed, limit));
    }

    @PostMapping("/events/{id}/review")
    public ApiResponse<SecurityRulesService.EventView> reviewEvent(@PathVariable Long id,
                                                                   @RequestBody ReviewRequest request) {
        return ApiResponse.ok(service.reviewEvent(
                TenantContext.requireOrgId(),
                id,
                new SecurityRulesService.ReviewCommand(request.result(), request.note()),
                TenantContext.getUserId().orElse("system")
        ));
    }

    public record RuleRequest(@NotBlank String name,
                              @NotBlank String ruleType,
                              @NotBlank String category,
                              @NotBlank String matchType,
                              @NotBlank String patternText,
                              @NotBlank String severity,
                              @NotBlank String action,
                              boolean enabled,
                              String description) {

        SecurityRulesService.RuleCommand toCommand() {
            return new SecurityRulesService.RuleCommand(
                    name,
                    ruleType,
                    category,
                    matchType,
                    patternText,
                    severity,
                    action,
                    enabled,
                    description
            );
        }
    }

    public record TestRequest(@NotBlank String text, @Valid RuleRequest rule) {
    }

    public record ReviewRequest(String result, String note) {
    }
}
