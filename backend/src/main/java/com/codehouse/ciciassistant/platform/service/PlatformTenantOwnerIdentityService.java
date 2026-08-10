package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.KeycloakIdentityProvisioningService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and reconciles the unified identity of the tenant's current Owner.
 * It never transfers ownership or accepts an email/mobile weak match.
 */
@Service
public class PlatformTenantOwnerIdentityService {

    public static final String AUDIT_EVENT = "platform.tenant.owner_identity.reconcile";

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AccountExternalIdentityRepository identityRepository;
    private final KeycloakIdentityProvisioningService identityProvisioningService;
    private final PlatformAuditService auditService;

    public PlatformTenantOwnerIdentityService(
            CompanyRepository companyRepository,
            UserRepository userRepository,
            AccountExternalIdentityRepository identityRepository,
            KeycloakIdentityProvisioningService identityProvisioningService,
            PlatformAuditService auditService) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.identityProvisioningService = identityProvisioningService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public OwnerIdentityView get(String companyId) {
        CompanyEntity company = requireCompany(companyId);
        UserEntity owner = requireUniqueOwner(
                userRepository.findByCompany_IdAndRoleCodeOrderByCreatedAtAsc(companyId, RoleCodes.OWNER));
        return toView(company, owner);
    }

    @Transactional
    public OwnerIdentityView reconcile(
            String companyId,
            String publicId,
            String idempotencyKey,
            String actorId,
            String actorRole) {
        CompanyEntity company = requireCompany(companyId);
        if (!"ACTIVE".equals(company.getStatus())) {
            throw new ForbiddenException("仅可协调正常运行租户的 Owner 统一身份");
        }
        UserEntity owner = requireUniqueOwner(userRepository.lockByCompanyIdAndRoleCode(companyId, RoleCodes.OWNER));
        UserAccountEntity account = owner.getAccount();
        String expectedPublicId = trim(account.getPublicId());
        if (expectedPublicId.isBlank() || !expectedPublicId.equals(trim(publicId).toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Owner 公共编号不匹配");
        }

        String idempotencyFragment = "idempotencyKey=" + trim(idempotencyKey) + ";";
        if (auditService.hasEventDetail(companyId, AUDIT_EVENT, owner.getId(), idempotencyFragment)) {
            return toView(company, owner);
        }
        OwnerIdentityView before = toView(company, owner);
        if (!before.recoverable()) {
            throw new IllegalStateException("当前 Owner 统一身份无需协调");
        }

        KeycloakIdentityProvisioningService.ProvisionResult result = identityProvisioningService
                .ensureHumanIdentity(account);
        owner.setMemberStatus(result.activationRequired()
                ? UserEntity.STATUS_PENDING_ACTIVATION
                : UserEntity.STATUS_ACTIVE);
        userRepository.saveAndFlush(owner);
        auditService.log(
                companyId,
                actorId,
                actorRole,
                AUDIT_EVENT,
                "tenant_owner_identity",
                owner.getId(),
                idempotencyFragment + "activationRequired=" + result.activationRequired());
        return toView(company, owner);
    }

    private CompanyEntity requireCompany(String companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("租户不存在"));
    }

    private UserEntity requireUniqueOwner(List<UserEntity> owners) {
        if (owners.isEmpty()) {
            throw new ResourceNotFoundException("租户 Owner 不存在");
        }
        if (owners.size() != 1) {
            throw new IllegalStateException("租户 Owner 数据异常，请先完成所有权治理");
        }
        return owners.getFirst();
    }

    private OwnerIdentityView toView(CompanyEntity company, UserEntity owner) {
        UserAccountEntity account = owner.getAccount();
        boolean bound = identityRepository.findByAccount_Id(account.getId()).isPresent();
        String identityState;
        if (!bound) {
            identityState = "MISSING";
        } else if (UserEntity.STATUS_PENDING_ACTIVATION.equals(owner.getMemberStatus())) {
            identityState = "PENDING_ACTIVATION";
        } else if (UserEntity.STATUS_ACTIVE.equals(owner.getMemberStatus())) {
            identityState = "ACTIVE";
        } else {
            identityState = "BLOCKED";
        }
        boolean recoverable = "ACTIVE".equals(company.getStatus())
                && UserAccountEntity.STATUS_ACTIVE.equals(account.getStatus())
                && !trim(account.getEmail()).isBlank()
                && ("MISSING".equals(identityState) || "PENDING_ACTIVATION".equals(identityState));
        String displayName = trim(owner.getNickname());
        if (displayName.isBlank()) displayName = trim(account.getDisplayName());
        if (displayName.isBlank()) displayName = "Owner";
        return new OwnerIdentityView(
                company.getId(),
                owner.getId(),
                displayName,
                maskEmail(account.getEmail()),
                maskMobile(account.getPrimaryMobile()),
                trim(account.getPublicId()),
                owner.getMemberStatus(),
                identityState,
                recoverable);
    }

    static String maskEmail(String value) {
        String email = trim(value);
        int at = email.indexOf('@');
        if (at <= 0) return "未配置";
        String local = email.substring(0, at);
        return local.substring(0, 1) + "***" + email.substring(at);
    }

    static String maskMobile(String value) {
        String mobile = trim(value);
        if (mobile.length() < 7) return "未配置";
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public record OwnerIdentityView(
            String companyId,
            String memberId,
            String displayName,
            String maskedEmail,
            String maskedMobile,
            String publicId,
            String memberStatus,
            String identityState,
            boolean recoverable) {
    }
}
