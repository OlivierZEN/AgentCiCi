package com.codehouse.ciciassistant.memory.api;

import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.memory.domain.MemoryCandidateEntity;
import com.codehouse.ciciassistant.memory.domain.MemoryRecordEntity;
import com.codehouse.ciciassistant.memory.service.MemoryCandidateGovernanceService;
import com.codehouse.ciciassistant.memory.service.MemoryLifecycleService;
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
    private final MemoryCandidateGovernanceService candidates; private final MemoryLifecycleService lifecycle; private final com.codehouse.ciciassistant.memory.domain.MemoryRecordRepository records; private final AgentAccessControlService access;
    public AgentMemoryCandidateController(MemoryCandidateGovernanceService candidates, MemoryLifecycleService lifecycle, com.codehouse.ciciassistant.memory.domain.MemoryRecordRepository records, AgentAccessControlService access) { this.candidates=candidates; this.lifecycle=lifecycle; this.records=records; this.access=access; }
    @GetMapping public ApiResponse<List<CandidateView>> list(@PathVariable String agentId) { requireManage(agentId); return ApiResponse.ok(candidates.list(TenantContext.requireOrgId(), agentId).stream().map(CandidateView::from).toList()); }
    @PostMapping("/{candidateId}/approve") public ApiResponse<RecordView> approve(@PathVariable String agentId, @PathVariable Long candidateId, @Valid @RequestBody ReviewRequest request) { requireManage(agentId); var record=candidates.approve(TenantContext.requireOrgId(), agentId, candidateId, actor(), request.reason()); return ApiResponse.ok(RecordView.from(record)); }
    @PostMapping("/{candidateId}/reject") public ApiResponse<Void> reject(@PathVariable String agentId, @PathVariable Long candidateId, @Valid @RequestBody ReviewRequest request) { requireManage(agentId); candidates.reject(TenantContext.requireOrgId(), agentId, candidateId, actor(), request.reason()); return ApiResponse.ok(null); }
    @GetMapping("/records") public ApiResponse<List<RecordView>> records(@PathVariable String agentId) { requireManage(agentId); return ApiResponse.ok(records.findByOrgIdAndAgentIdOrderByUpdatedAtDesc(TenantContext.requireOrgId(), agentId).stream().map(RecordView::from).toList()); }
    @PostMapping("/records/{recordId}/revoke") public ApiResponse<java.util.Map<String, Object>> revoke(@PathVariable String agentId, @PathVariable Long recordId, @Valid @RequestBody ReviewRequest request) { requireManage(agentId); boolean vectorRemoved=lifecycle.revokeRecord(TenantContext.requireOrgId(), agentId, recordId, actor(), request.reason()); return ApiResponse.ok(java.util.Map.of("recordId", recordId, "vectorRemoved", vectorRemoved)); }
    @PostMapping("/subjects/delete") public ApiResponse<MemoryLifecycleService.DeletionResult> deleteSubject(@PathVariable String agentId, @Valid @RequestBody SubjectDeleteRequest request) { requireManage(agentId); return ApiResponse.ok(lifecycle.deleteSubject(TenantContext.requireOrgId(), request.applicationCode(), request.subjectType(), request.externalSubjectRef(), agentId, actor(), request.reason())); }
    private void requireManage(String agentId) { access.require(TenantContext.requireOrgId(), actor(), TenantContext.getRoles(), agentId, AgentPermission.MANAGE); }
    private String actor() { return TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context")); }
    public record ReviewRequest(@NotBlank String reason) {}
    public record RecordView(Long id, String status, String memoryType, String sensitivity, Instant validTo) { static RecordView from(MemoryRecordEntity item) { return new RecordView(item.getId(), item.getStatus(), item.getMemoryType(), item.getSensitivity(), item.getValidTo()); } }
    public record CandidateView(Long id, String agentId, String status, String memoryType, String sensitivity, Instant validTo, Instant updatedAt) { static CandidateView from(MemoryCandidateEntity item) { return new CandidateView(item.getId(), item.getAgentId(), item.getStatus(), item.getMemoryType(), item.getSensitivity(), item.getValidTo(), item.getUpdatedAt()); } }
    public record SubjectDeleteRequest(@NotBlank String applicationCode, @NotBlank String subjectType, @NotBlank String externalSubjectRef, @NotBlank String reason) {}
}
