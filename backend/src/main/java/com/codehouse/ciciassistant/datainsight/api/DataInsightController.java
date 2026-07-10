package com.codehouse.ciciassistant.datainsight.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.datainsight.service.DataInsightService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/data-insights")
public class DataInsightController {

    private final DataInsightService dataInsightService;

    public DataInsightController(DataInsightService dataInsightService) {
        this.dataInsightService = dataInsightService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(dataInsightService.dashboard(TenantContext.requireOrgId()));
    }
}

