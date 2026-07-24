package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.domain.SematticeProvisioningBindingEntity;
import com.codehouse.ciciassistant.auth.domain.SematticeProvisioningBindingRepository;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SematticeProvisioningService {

    private static final Pattern COMPANY_ID_PATTERN = Pattern.compile("^org[a-z0-9]{17}$");
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$");

    private final CompanyRepository companyRepository;
    private final SematticeProvisioningBindingRepository bindingRepository;
    private final PlatformAuditService platformAuditService;

    public SematticeProvisioningService(CompanyRepository companyRepository,
                                        SematticeProvisioningBindingRepository bindingRepository,
                                        PlatformAuditService platformAuditService) {
        this.companyRepository = companyRepository;
        this.bindingRepository = bindingRepository;
        this.platformAuditService = platformAuditService;
    }

    @Transactional
    public BindingView reserve(String companyId, String idempotencyKey) {
        String normalizedCompanyId = required(companyId, "company_id");
        String key = required(idempotencyKey, "idempotency_key");
        if (!COMPANY_ID_PATTERN.matcher(normalizedCompanyId).matches() || !IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw invalid();
        }
        SematticeProvisioningBindingEntity replay = bindingRepository.findByIdempotencyKey(key).orElse(null);
        if (replay != null) {
            if (!replay.getCompanyId().equals(normalizedCompanyId)) {
                throw conflict();
            }
            return view(replay);
        }
        CompanyEntity org = companyRepository.findById(normalizedCompanyId).orElseThrow(this::notFound);
        if (!"ACTIVE".equals(org.getStatus())) {
            throw unavailable();
        }
        SematticeProvisioningBindingEntity existing = bindingRepository.findByCompanyId(normalizedCompanyId).orElse(null);
        if (existing != null) {
            throw conflict();
        }
        try {
            SematticeProvisioningBindingEntity created = bindingRepository.saveAndFlush(
                    new SematticeProvisioningBindingEntity(UUID.randomUUID().toString(), normalizedCompanyId, key));
            audit(normalizedCompanyId, "reserve", created.getReservationId());
            return view(created);
        } catch (DataIntegrityViolationException exception) {
            SematticeProvisioningBindingEntity resolved = bindingRepository.findByIdempotencyKey(key)
                    .or(() -> bindingRepository.findByCompanyId(normalizedCompanyId))
                    .orElseThrow(this::conflict);
            if (!resolved.getCompanyId().equals(normalizedCompanyId) || !resolved.getIdempotencyKey().equals(key)) {
                throw conflict();
            }
            return view(resolved);
        }
    }

    @Transactional
    public BindingView complete(String reservationId, String companyId, String tenantId, String operationId,
                                Boolean succeeded, String failureCode) {
        if (succeeded == null) {
            throw invalid();
        }
        boolean success = succeeded;
        SematticeProvisioningBindingEntity binding = bindingRepository.findById(required(reservationId, "reservation_id"))
                .orElseThrow(this::notFound);
        if (!binding.getCompanyId().equals(required(companyId, "company_id"))) {
            throw conflict();
        }
        if (SematticeProvisioningBindingEntity.PROVISIONED.equals(binding.getState())) {
            if (success && equals(binding.getSematticeTenantId(), tenantId)
                    && equals(binding.getSematticeOperationId(), operationId)) {
                return view(binding);
            }
            throw conflict();
        }
        if (success && (blank(tenantId) || blank(operationId))) {
            throw invalid();
        }
        if (!success && blank(failureCode)) {
            throw invalid();
        }
        String normalizedTenantId = optional(tenantId);
        String normalizedOperationId = optional(operationId);
        String normalizedFailureCode = optional(failureCode);
        if (success && (normalizedTenantId == null || normalizedOperationId == null)) {
            throw invalid();
        }
        binding.complete(normalizedTenantId, normalizedOperationId, success, normalizedFailureCode);
        SematticeProvisioningBindingEntity saved = bindingRepository.save(binding);
        audit(saved.getCompanyId(), success ? "provisioned" : "failed", saved.getReservationId());
        return view(saved);
    }

    @Transactional(readOnly = true)
    public BindingView getProvisioningStatus(String companyId) {
        String normalizedCompanyId = required(companyId, "company_id");
        if (!COMPANY_ID_PATTERN.matcher(normalizedCompanyId).matches()) {
            throw invalid();
        }
        return bindingRepository.findByCompanyId(normalizedCompanyId)
                .map(this::view)
                .orElse(new BindingView(null, normalizedCompanyId, "NOT_PROVISIONED", null, null, null));
    }

    private BindingView view(SematticeProvisioningBindingEntity binding) {
        return new BindingView(binding.getReservationId(), binding.getCompanyId(), binding.getState(),
                binding.getSematticeTenantId(), binding.getSematticeOperationId(), binding.getFailureCode());
    }

    private void audit(String companyId, String action, String reservationId) {
        platformAuditService.log(companyId, "semattice", "INTERNAL_SERVICE", "platform.native_provisioning." + action,
                "semattice_provisioning", reservationId, "Semattice provisioning state changed");
    }

    private String required(String raw, String name) {
        String value = trim(raw);
        if (value == null || value.length() > 128) {
            throw invalid();
        }
        return value;
    }

    private String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String optional(String raw) {
        String value = trim(raw);
        if (value != null && value.length() > 128) {
            throw invalid();
        }
        return value;
    }

    private boolean blank(String value) { return trim(value) == null; }
    private boolean equals(String left, String right) { return java.util.Objects.equals(left, trim(right)); }
    private ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid provisioning request"); }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "company was not found"); }
    private ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "company is not eligible for provisioning"); }
    private ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "company provisioning is already reserved or bound"); }

    public record BindingView(String reservationId, String companyId, String state, String sematticeTenantId,
                              String sematticeOperationId, String failureCode) { }
}
