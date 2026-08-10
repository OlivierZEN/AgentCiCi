package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.CompanyProvisioningService;
import com.codehouse.ciciassistant.auth.service.KeycloakIdentityProvisioningService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Governed recovery for a newly provisioned tenant whose original Owner never
 * completed unified-identity activation.  It deliberately reuses an already
 * active HUMAN account instead of setting passwords or forging activation.
 */
@Service
public class PlatformTenantOwnerRecoveryService {

    private final CompanyRepository companyRepository;
    private final CompanyProvisioningService companyProvisioningService;
    private final UserRepository userRepository;
    private final KeycloakIdentityProvisioningService identityProvisioningService;
    private final PlatformAuditService auditService;

    public PlatformTenantOwnerRecoveryService(
            CompanyRepository companyRepository,
            CompanyProvisioningService companyProvisioningService,
            UserRepository userRepository,
            KeycloakIdentityProvisioningService identityProvisioningService,
            PlatformAuditService auditService) {
        this.companyRepository = companyRepository;
        this.companyProvisioningService = companyProvisioningService;
        this.userRepository = userRepository;
        this.identityProvisioningService = identityProvisioningService;
        this.auditService = auditService;
    }

    @Transactional
    public OwnerRecoveryView recover(
            String companyId,
            String replacementOwnerMobile,
            String actorId,
            String actorRole) {
        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("租户不存在"));
        if (!"ACTIVE".equals(company.getStatus())) {
            throw new ForbiddenException("仅可恢复正常运行租户的 Owner");
        }
        String mobile = normalizeMobile(replacementOwnerMobile);
        UserAccountEntity replacement = companyProvisioningService.findMobileAccount(mobile)
                .orElseThrow(() -> new IllegalArgumentException("替代 Owner 统一账号不存在"));
        if (!UserAccountEntity.STATUS_ACTIVE.equals(replacement.getStatus())) {
            throw new ForbiddenException("替代 Owner 全局账号不可用");
        }

        List<UserEntity> owners = userRepository.lockByCompanyIdAndRoleCode(companyId, RoleCodes.OWNER);
        if (owners.isEmpty()) {
            throw new ResourceNotFoundException("租户 Owner 不存在，不能自动建立所有权");
        }
        UserEntity activeOwner = owners.stream()
                .filter(member -> UserEntity.STATUS_ACTIVE.equals(member.getMemberStatus()))
                .findFirst()
                .orElse(null);
        if (activeOwner != null) {
            if (replacement.getId().equals(activeOwner.getAccountId())) {
                return toView(companyId, activeOwner, true, true);
            }
            throw new ForbiddenException("租户已有有效 Owner，请由当前 Owner 执行所有权转让");
        }

        identityProvisioningService.requireActiveHumanIdentity(replacement);

        List<UserEntity> members = userRepository.findByCompany_IdOrderByCreatedAtDesc(companyId);
        UserEntity target = members.stream()
                .filter(member -> replacement.getId().equals(member.getAccountId()))
                .findFirst()
                .orElse(null);
        boolean reusedMembership = target != null;
        if (target == null) {
            target = companyProvisioningService.createOwnerMembership(
                    company, replacement, replacement.getDisplayName());
        } else {
            target.setRoleCode(RoleCodes.OWNER);
            target.setMemberStatus(UserEntity.STATUS_ACTIVE);
        }

        for (UserEntity member : owners) {
            if (!member.getId().equals(target.getId()) && RoleCodes.OWNER.equals(member.getRoleCode())) {
                member.setRoleCode(RoleCodes.ORG_ADMIN);
            }
        }
        userRepository.saveAll(owners);
        userRepository.save(target);
        auditService.log(
                companyId,
                actorId,
                actorRole,
                "platform.tenant.owner.recover",
                "tenant",
                companyId,
                "Recovered tenant Owner with an already-active HUMAN account; ownerMemberId=" + target.getId());
        return toView(companyId, target, reusedMembership, false);
    }

    private static OwnerRecoveryView toView(
            String companyId,
            UserEntity owner,
            boolean reusedMembership,
            boolean alreadyRecovered) {
        return new OwnerRecoveryView(
                companyId,
                owner.getId(),
                owner.getAccountId(),
                owner.getAccount().getPublicId(),
                reusedMembership,
                alreadyRecovered);
    }

    private static String normalizeMobile(String value) {
        String mobile = value == null ? "" : value.trim();
        if (!mobile.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        return mobile;
    }

    public record OwnerRecoveryView(
            String companyId,
            String ownerMemberId,
            String ownerAccountId,
            String ownerPublicId,
            boolean reusedMembership,
            boolean alreadyRecovered) {
    }
}
