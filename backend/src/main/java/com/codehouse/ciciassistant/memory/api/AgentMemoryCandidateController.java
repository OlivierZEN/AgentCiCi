package com.codehouse.ciciassistant.memory.api;

import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.memory.domain.MemoryCandidateEntity;
import com.codehouse.ciciassistant.memory.service.MemoryCandidateGovernanceService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agents/{agentId}/memory/candidates")
public class AgentMemoryCandidateController {
    private final MemoryCandidateGovernanceService candidates; private final AgentAccessControlService access;
    public AgentMemoryCandidateController(MemoryCandidateGovernanceService candidates, AgentAccessControlService access) { this.candidates=candidates; this.access=access; }
    @GetMapping public ApiResponse<List<CandidateView>> list(@PathVariable String agentId) { requireManage(agentId); return ApiResponse.ok(candidates.list(TenantContext.requireOrgId(), agentId).stream().map(CandidateView::from).toList()); }
    @PostMapping("/{candidateId}/approve") public ApiResponse<RecordView> approve(@PathVariable String agentId, @PathVariable Long candidateId, @Valid @RequestBody ReviewRequest request) { requireManage(agentId); var record=candidates.approve(TenantContext.requireOrgId(), agentId, candidateId, actor(), request.reason()); return ApiResponse.ok(new RecordView(record.getId(), record.getStatus())); }
    @PostMapping("/{candidateId}/reject") public ApiResponse<Void> reject(@PathVariable String agentId, @PathVariable Long candidateId, @Valid @RequestBody ReviewRequest request) { requireManage(agentId); candidates.reject(TenantContext.requireOrgId(), agentId, candidateId, actor(), request.reason()); return ApiResponse.ok(null); }
    private void requireManage(String agentId) { access.require(TenantContext.requireOrgId(), actor(), TenantContext.getRoles(), agentId, AgentPermission.MANAGE); }
    private String actor() { return TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context")); }
    public record ReviewRequest(@NotBlank String reason) {}
    public record RecordView(Long id, String status) {}
    public record CandidateView(Long id, String agentId, String status, String memoryType, String sensitivity, Instant validTo, Instant updatedAt) { static CandidateView from(MemoryCandidateEntity item) { return new CandidateView(item.getId(), item.getAgentId(), item.getStatus(), item.getMemoryType(), item.getSensitivity(), item.getValidTo(), item.getUpdatedAt()); } }
}
