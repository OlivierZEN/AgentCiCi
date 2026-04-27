package com.codehouse.ciciassistant.system;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "service", "cc-cici-assistant-backend",
                "status", "UP",
                "timestamp", Instant.now().toString()
        ));
    }
}
