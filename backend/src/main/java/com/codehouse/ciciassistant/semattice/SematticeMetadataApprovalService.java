package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds the durable, tenant-local fact that a distinct organization administrator approved a
 * Semattice metadata operation. Only still-valid approvals for the original requester are
 * copied into that requester's short-lived OACT.
 */
@Service
public class SematticeMetadataApprovalService {

    private static final Duration APPROVAL_TTL = Duration.ofMinutes(15);
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    public SematticeMetadataApprovalService(JdbcTemplate jdbcTemplate, UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
    }

    @Transactional
    public ApprovalView request(String companyId, String requesterMemberId, String subjectType, String subjectId, String summary) {
        requireActiveAdmin(companyId, requesterMemberId);
        String normalizedType = normalizeSubjectType(subjectType);
        String normalizedSubjectId = normalizeUuid(subjectId, "subjectId");
        String normalizedSummary = required(summary, "summary", 500);
        Instant now = Instant.now();
        ApprovalView value = new ApprovalView(UUID.randomUUID().toString(), normalizedType, normalizedSubjectId,
                normalizedSummary, requesterMemberId, null, "PENDING", null, now, null);
        jdbcTemplate.update("""
                INSERT INTO semattice_metadata_approval
                    (approval_id, company_id, subject_type, subject_id, summary, requester_member_id, state, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """, value.approvalId(), companyId, value.subjectType(), value.subjectId(), value.summary(),
                requesterMemberId, now, now);
        return value;
    }

    @Transactional
    public ApprovalView approve(String companyId, String approverMemberId, String approvalId) {
        requireActiveAdmin(companyId, approverMemberId);
        ApprovalView pending = find(companyId, normalizeUuid(approvalId, "approvalId"));
        if (!"PENDING".equals(pending.state())) {
            throw new ConflictException("审批请求不处于待审批状态");
        }
        if (pending.requesterMemberId().equals(approverMemberId)) {
            throw new ForbiddenException("元数据审批必须由不同的组织管理员完成");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(APPROVAL_TTL);
        int updated = jdbcTemplate.update("""
                UPDATE semattice_metadata_approval
                   SET state = 'APPROVED', approver_member_id = ?, approved_at = ?, expires_at = ?, updated_at = ?
                 WHERE approval_id = ? AND company_id = ? AND state = 'PENDING'
                """, approverMemberId, now, expiresAt, now, pending.approvalId(), companyId);
        if (updated != 1) {
            throw new ConflictException("审批请求已被其他操作更新，请刷新后重试");
        }
        return find(companyId, pending.approvalId());
    }

    @Transactional(readOnly = true)
    public List<String> approvedIdsForRequester(String companyId, String requesterMemberId) {
        return jdbcTemplate.queryForList("""
                SELECT approval_id
                  FROM semattice_metadata_approval
                 WHERE company_id = ? AND requester_member_id = ?
                   AND state = 'APPROVED' AND expires_at > ?
                 ORDER BY approved_at ASC
                """, String.class, companyId, requesterMemberId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ApprovalView> list(String companyId) {
        return jdbcTemplate.query("""
                SELECT approval_id, subject_type, subject_id, summary, requester_member_id,
                       approver_member_id, state, expires_at, created_at, approved_at
                  FROM semattice_metadata_approval
                 WHERE company_id = ?
                 ORDER BY created_at DESC
                """, (row, ignored) -> map(row), companyId);
    }

    private ApprovalView find(String companyId, String approvalId) {
        List<ApprovalView> values = jdbcTemplate.query("""
                SELECT approval_id, subject_type, subject_id, summary, requester_member_id,
                       approver_member_id, state, expires_at, created_at, approved_at
                  FROM semattice_metadata_approval
                 WHERE company_id = ? AND approval_id = ?
                """, (row, ignored) -> map(row), companyId, approvalId);
        if (values.isEmpty()) {
            throw new ResourceNotFoundException("元数据审批请求不存在");
        }
        return values.get(0);
    }

    private void requireActiveAdmin(String companyId, String memberId) {
        UserEntity member = userRepository.findByIdAndCompany_Id(memberId, companyId)
                .orElseThrow(() -> new ForbiddenException("当前成员不属于目标组织"));
        if (!UserEntity.STATUS_ACTIVE.equals(member.getMemberStatus()) || !RoleCodes.isOrgAdminRole(member.getRoleCode())) {
            throw new ForbiddenException("需要有效的组织管理员身份");
        }
    }

    private ApprovalView map(ResultSet row) throws SQLException {
        return new ApprovalView(row.getString("approval_id"), row.getString("subject_type"), row.getString("subject_id"),
                row.getString("summary"), row.getString("requester_member_id"), row.getString("approver_member_id"),
                row.getString("state"), row.getTimestamp("expires_at") == null ? null : row.getTimestamp("expires_at").toInstant(),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("approved_at") == null ? null : row.getTimestamp("approved_at").toInstant());
    }

    private String normalizeSubjectType(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!"METADATA_VERSION".equals(normalized) && !"CHANGESET".equals(normalized)) {
            throw new IllegalArgumentException("subjectType 必须为 METADATA_VERSION 或 CHANGESET");
        }
        return normalized;
    }

    private String normalizeUuid(String value, String field) {
        try {
            return UUID.fromString(value == null ? "" : value.trim()).toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " 必须为 UUID");
        }
    }

    private String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " 不能为空且长度不能超过 " + maxLength);
        }
        return normalized;
    }

    public record ApprovalView(String approvalId, String subjectType, String subjectId, String summary,
                               String requesterMemberId, String approverMemberId, String state,
                               Instant expiresAt, Instant createdAt, Instant approvedAt) {
    }
}
