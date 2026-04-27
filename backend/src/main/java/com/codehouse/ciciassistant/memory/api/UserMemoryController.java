package com.codehouse.ciciassistant.memory.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.memory.domain.UserMemoryEntity;
import com.codehouse.ciciassistant.memory.service.UserMemoryService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/agents/{agentId}/memories")
public class UserMemoryController {

    private final UserMemoryService memoryService;

    public UserMemoryController(UserMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        List<UserMemoryEntity> memories = memoryService.listAll(orgId, userId, agentId);
        return ApiResponse.ok(memories.stream().map(this::toPayload).toList());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@PathVariable String agentId,
                                                    @RequestBody MemoryRequest request) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        UserMemoryEntity entity = memoryService.create(orgId, userId, agentId,
                request.category(), request.content(), request.memoryKey());
        return ApiResponse.ok(toPayload(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String agentId,
                                                    @PathVariable Long id,
                                                    @RequestBody MemoryRequest request) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        boolean enabled = request.enabled() != null ? request.enabled() : true;
        boolean pinned = request.pinned() != null ? request.pinned() : false;
        UserMemoryEntity entity = memoryService.update(orgId, userId, id,
                request.category(), request.content(), enabled, pinned);
        return ApiResponse.ok(toPayload(entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String agentId, @PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        memoryService.delete(orgId, userId, id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping
    public ApiResponse<Void> deleteAll(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        memoryService.deleteAll(orgId, userId, agentId);
        return ApiResponse.ok(null);
    }

    private Map<String, Object> toPayload(UserMemoryEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("agentId", e.getAgentId());
        m.put("category", e.getCategory());
        m.put("source", e.getSource());
        m.put("content", e.getContent());
        m.put("memoryKey", e.getMemoryKey());
        m.put("confidence", e.getConfidence());
        m.put("enabled", e.isEnabled());
        m.put("pinned", e.isPinned());
        m.put("createdAt", e.getCreatedAt());
        m.put("updatedAt", e.getUpdatedAt());
        return m;
    }

    private String currentUser() {
        return TenantContext.getUserId()
                .orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }

    public record MemoryRequest(
            String category,
            String content,
            String memoryKey,
            Boolean enabled,
            Boolean pinned
    ) {}
}
