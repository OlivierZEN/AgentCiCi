package com.codehouse.ciciassistant.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "semattice_provisioning_binding")
public class SematticeProvisioningBindingEntity {

    public static final String RESERVED = "RESERVED";
    public static final String PROVISIONED = "PROVISIONED";
    public static final String FAILED = "FAILED";

    @Id
    @Column(name = "reservation_id", nullable = false, length = 64)
    private String reservationId;

    @Column(name = "company_id", nullable = false, length = 64, unique = true)
    private String companyId;

    @Column(name = "idempotency_key", nullable = false, length = 128, unique = true)
    private String idempotencyKey;

    @Column(name = "state", nullable = false, length = 32)
    private String state;

    @Column(name = "semattice_tenant_id", length = 64)
    private String nativeTenantId;

    @Column(name = "semattice_operation_id", length = 128)
    private String nativeOperationId;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SematticeProvisioningBindingEntity() {
    }

    public SematticeProvisioningBindingEntity(String reservationId, String companyId, String idempotencyKey) {
        this.reservationId = reservationId;
        this.companyId = companyId;
        this.idempotencyKey = idempotencyKey;
        this.state = RESERVED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getReservationId() { return reservationId; }
    public String getCompanyId() { return companyId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getState() { return state; }
    public String getSematticeTenantId() { return nativeTenantId; }
    public String getSematticeOperationId() { return nativeOperationId; }
    public String getFailureCode() { return failureCode; }

    public void complete(String tenantId, String operationId, boolean succeeded, String failureCode) {
        this.nativeTenantId = tenantId;
        this.nativeOperationId = operationId;
        this.state = succeeded ? PROVISIONED : FAILED;
        this.failureCode = succeeded ? null : failureCode;
        this.updatedAt = Instant.now();
    }
}
