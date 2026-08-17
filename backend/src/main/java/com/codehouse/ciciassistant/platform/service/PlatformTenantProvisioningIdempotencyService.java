package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.common.error.ConflictException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PlatformTenantProvisioningIdempotencyService {

    private final JdbcTemplate jdbcTemplate;

    public PlatformTenantProvisioningIdempotencyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lock(String idempotencyKey) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?))", resultSet -> { }, idempotencyKey);
    }

    public PlatformTenantLifecycleService.TenantProvisionView findReplay(String idempotencyKey, String fingerprint) {
        List<PlatformTenantLifecycleService.TenantProvisionView> rows = jdbcTemplate.query("""
                SELECT request.request_fingerprint,
                       request.company_id,
                       company.name,
                       company.status,
                       request.owner_member_id,
                       request.owner_account_id,
                       request.reused_existing_account,
                       request.owner_activation_required,
                       request.owner_resolution
                FROM platform_tenant_provisioning_request request
                LEFT JOIN company ON company.id = request.company_id
                WHERE request.idempotency_key = ?
                  AND request.status = 'SUCCEEDED'
                """, (rs, rowNum) -> {
            if (!fingerprint.equals(rs.getString("request_fingerprint"))) {
                throw new ConflictException("该幂等键已用于另一笔租户开通请求，请刷新后重试。");
            }
            return new PlatformTenantLifecycleService.TenantProvisionView(
                    rs.getString("company_id"),
                    rs.getString("name"),
                    rs.getString("status"),
                    rs.getString("owner_member_id"),
                    rs.getString("owner_account_id"),
                    rs.getBoolean("reused_existing_account"),
                    rs.getBoolean("owner_activation_required"),
                    rs.getString("owner_resolution")
            );
        }, idempotencyKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void start(String idempotencyKey, String fingerprint) {
        jdbcTemplate.update("""
                INSERT INTO platform_tenant_provisioning_request(
                    idempotency_key,
                    request_fingerprint,
                    status
                ) VALUES (?, ?, 'PROCESSING')
                """, idempotencyKey, fingerprint);
    }

    public void complete(String idempotencyKey, PlatformTenantLifecycleService.TenantProvisionView result) {
        jdbcTemplate.update("""
                UPDATE platform_tenant_provisioning_request
                SET status = 'SUCCEEDED',
                    company_id = ?,
                    owner_member_id = ?,
                    owner_account_id = ?,
                    owner_resolution = ?,
                    reused_existing_account = ?,
                    owner_activation_required = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE idempotency_key = ?
                """,
                result.companyId(),
                result.ownerMemberId(),
                result.ownerAccountId(),
                result.ownerResolution(),
                result.reusedExistingAccount(),
                result.ownerActivationRequired(),
                idempotencyKey);
    }
}
