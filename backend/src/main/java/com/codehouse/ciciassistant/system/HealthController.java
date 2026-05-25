package com.codehouse.ciciassistant.system;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system")
public class HealthController {

    private final String appVersion;
    private final String imageTag;
    private final String gitCommit;

    public HealthController(
            @Value("${info.app.version:dev}") String appVersion,
            @Value("${info.app.image-tag:latest}") String imageTag,
            @Value("${info.app.git-commit:unknown}") String gitCommit) {
        this.appVersion = appVersion;
        this.imageTag = imageTag;
        this.gitCommit = gitCommit;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "service", "cc-cici-assistant-backend",
                "status", "UP",
                "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/version")
    public ApiResponse<Map<String, Object>> version() {
        return ApiResponse.ok(Map.of(
                "service", "cc-cici-assistant-backend",
                "version", appVersion,
                "imageTag", imageTag,
                "gitCommit", gitCommit,
                "timestamp", Instant.now().toString()
        ));
    }
}
