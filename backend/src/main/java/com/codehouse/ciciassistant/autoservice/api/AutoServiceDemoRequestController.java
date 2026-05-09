package com.codehouse.ciciassistant.autoservice.api;

import com.codehouse.ciciassistant.autoservice.service.AutoServiceDemoRequestService;
import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AutoServiceDemoRequestController {

    private final AutoServiceDemoRequestService service;

    public AutoServiceDemoRequestController(AutoServiceDemoRequestService service) {
        this.service = service;
    }

    @PostMapping("/api/autoservice/demo-requests")
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(service.create(new AutoServiceDemoRequestService.CreateCommand(
                request.site(),
                request.locale(),
                request.companyName(),
                request.contactName(),
                request.mobile(),
                request.email(),
                request.roleTitle(),
                request.scenario(),
                request.sourcePath()
        )), "Demo request submitted");
    }

    @GetMapping("/platform/autoservice/demo-requests")
    @RequirePlatformRole
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return ApiResponse.ok(service.list(status, q, limit));
    }

    @PatchMapping("/platform/autoservice/demo-requests/{id}")
    @RequirePlatformRole
    public ApiResponse<Map<String, Object>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        String actorId = TenantContext.getUserId().orElse("");
        return ApiResponse.ok(service.updateStatus(id, request.status(), actorId, request.handledNote()));
    }

    public record CreateRequest(
            @NotBlank String site,
            @NotBlank String locale,
            @NotBlank @Size(max = 128) String companyName,
            @NotBlank @Size(max = 64) String contactName,
            @NotBlank @Size(max = 64) String mobile,
            @Email @Size(max = 128) String email,
            @Size(max = 128) String roleTitle,
            @Size(max = 2000) String scenario,
            @Size(max = 256) String sourcePath
    ) {
    }

    public record UpdateStatusRequest(
            @NotBlank String status,
            @Size(max = 2000) String handledNote
    ) {
    }
}
