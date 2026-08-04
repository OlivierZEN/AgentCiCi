package com.codehouse.ciciassistant.aitable;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai-table")
public class AiTableDataController {

    private final AiTableDataService service;

    public AiTableDataController(AiTableDataService service) {
        this.service = service;
    }

    @GetMapping("/catalog")
    public ApiResponse<Map<String, Object>> catalog() {
        return ApiResponse.ok(service.catalog(companyId(), userId()));
    }

    @GetMapping("/objects/{objectApiName}/records")
    public ApiResponse<Map<String, Object>> records(@PathVariable String objectApiName,
                                                      @RequestParam(defaultValue = "25") int limit,
                                                      @RequestParam(defaultValue = "") String after,
                                                      @RequestParam(defaultValue = "") String query) {
        return ApiResponse.ok(service.records(companyId(), userId(), objectApiName, limit, after, query));
    }

    private String companyId() {
        return TenantContext.requireCompanyId();
    }

    private String userId() {
        return TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
    }
}
