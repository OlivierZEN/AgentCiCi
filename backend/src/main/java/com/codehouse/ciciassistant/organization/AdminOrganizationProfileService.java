package com.codehouse.ciciassistant.organization;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.OrgEntity;
import com.codehouse.ciciassistant.auth.domain.OrgRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.ops.service.AuditService;
import com.codehouse.ciciassistant.platform.domain.OrganizationExportJobEntity;
import com.codehouse.ciciassistant.platform.domain.OrganizationExportJobRepository;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOrganizationProfileService {

    private final OrgRepository orgRepository;
    private final OrganizationProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final OrganizationExportJobRepository exportJobRepository;
    private final AuditService auditService;

    public AdminOrganizationProfileService(OrgRepository orgRepository,
                                           OrganizationProfileRepository profileRepository,
                                           UserRepository userRepository,
                                           OrganizationExportJobRepository exportJobRepository,
                                           AuditService auditService) {
        this.orgRepository = orgRepository;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.exportJobRepository = exportJobRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public OrganizationProfileView getProfile(String orgId) {
        OrgEntity org = requireOrg(orgId);
        OrganizationProfileEntity profile = profileRepository.findById(orgId).orElse(null);
        return toView(org, profile);
    }

    @Transactional
    public OrganizationProfileView updateProfile(String orgId, String actorId, ProfileUpdateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (command.orgId() != null) {
            throw new IllegalArgumentException("组织 ID 是系统标识，不允许修改");
        }
        String name = required(command.name(), "组织名称", 2, 128);
        validateOptional(command.shortName(), "组织简称", 64);
        validateOptional(command.contactName(), "联系人", 128);
        validateOptional(command.contactPhone(), "联系电话", 64);
        validateEmail(command.contactEmail());
        validateWebsite(command.website());
        validateOptional(command.industry(), "行业", 128);
        validateOptional(command.organizationSize(), "组织规模", 64);
        String timezone = command.timezone() == null || command.timezone().isBlank()
                ? "Asia/Shanghai"
                : command.timezone().trim();
        validateTimezone(timezone);
        validateOptional(command.notes(), "备注", 4000);

        OrgEntity org = requireOrg(orgId);
        String oldName = org.getName();
        org.setName(name);
        orgRepository.save(org);

        OrganizationProfileEntity profile = profileRepository.findById(orgId)
                .orElseGet(() -> new OrganizationProfileEntity(orgId, actorId));
        profile.update(new ProfileUpdateCommand(
                null,
                name,
                command.shortName(),
                command.contactName(),
                command.contactPhone(),
                command.contactEmail(),
                command.website(),
                command.industry(),
                command.organizationSize(),
                timezone,
                command.notes()
        ), actorId);
        profileRepository.save(profile);

        auditService.log(orgId, actorId, "organization.profile.update", auditDetail(oldName, name, command));
        if (!Objects.equals(oldName, name)) {
            auditService.log(orgId, actorId, "organization.name.update",
                    "name: old=\"" + safeAudit(oldName) + "\", new=\"" + safeAudit(name) + "\"");
        }
        return toView(org, profile);
    }

    private OrganizationProfileView toView(OrgEntity org, OrganizationProfileEntity profile) {
        OwnerView owner = userRepository
                .findFirstByOrg_IdAndRoleCodeAndMemberStatusOrderByCreatedAtAsc(
                        org.getId(), RoleCodes.OWNER, UserEntity.STATUS_ACTIVE)
                .map(AdminOrganizationProfileService::ownerView)
                .orElse(null);
        long memberCount = userRepository.countByOrg_IdAndMemberStatus(org.getId(), UserEntity.STATUS_ACTIVE);
        List<ExportJobSummary> recentExportJobs = exportJobRepository.findTop20ByOrgIdOrderByCreatedAtDesc(org.getId())
                .stream()
                .limit(5)
                .map(AdminOrganizationProfileService::exportSummary)
                .toList();
        return new OrganizationProfileView(
                org.getId(),
                org.getName(),
                value(profile == null ? null : profile.getShortName()),
                org.getStatus(),
                value(profile == null ? null : profile.getContactName()),
                value(profile == null ? null : profile.getContactPhone()),
                value(profile == null ? null : profile.getContactEmail()),
                value(profile == null ? null : profile.getWebsite()),
                value(profile == null ? null : profile.getIndustry()),
                value(profile == null ? null : profile.getOrganizationSize()),
                value(profile == null ? "Asia/Shanghai" : profile.getTimezone()),
                value(profile == null ? null : profile.getNotes()),
                owner,
                memberCount,
                profile == null ? null : profile.getCreatedAt(),
                profile == null ? null : profile.getUpdatedAt(),
                profile == null ? null : profile.getUpdatedBy(),
                recentExportJobs
        );
    }

    private OrgEntity requireOrg(String orgId) {
        return orgRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("组织不存在"));
    }

    private static OwnerView ownerView(UserEntity user) {
        String displayName = firstNonBlank(
                user.getNickname(),
                user.getAccount() == null ? null : user.getAccount().getDisplayName(),
                user.getMobile(),
                "Owner"
        );
        return new OwnerView(user.getId(), displayName, value(user.getMobile()));
    }

    private static ExportJobSummary exportSummary(OrganizationExportJobEntity job) {
        return new ExportJobSummary(
                job.getId(),
                job.getStatus(),
                value(job.getReason()),
                job.getCreatedAt(),
                job.getFinishedAt(),
                job.getUpdatedAt()
        );
    }

    private static String required(String value, String field, int minLength, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < minLength || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "长度必须为 " + minLength + "-" + maxLength + " 个字符");
        }
        return normalized;
    }

    private static void validateOptional(String value, String field, int maxLength) {
        if (value != null && value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + "长度不能超过 " + maxLength + " 个字符");
        }
    }

    private static void validateEmail(String value) {
        validateOptional(value, "联系邮箱", 256);
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("联系邮箱格式不正确");
        }
    }

    private static void validateWebsite(String value) {
        validateOptional(value, "官网", 256);
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
                throw new IllegalArgumentException("官网必须是 http(s) URL");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("官网必须是 http(s) URL");
        }
    }

    private static void validateTimezone(String value) {
        validateOptional(value, "时区", 64);
        try {
            ZoneId.of(value);
        } catch (java.time.DateTimeException ex) {
            throw new IllegalArgumentException("时区不正确");
        }
    }

    private static String auditDetail(String oldName, String newName, ProfileUpdateCommand command) {
        StringBuilder detail = new StringBuilder();
        if (!Objects.equals(oldName, newName)) {
            detail.append("name: old=\"").append(safeAudit(oldName)).append("\", new=\"")
                    .append(safeAudit(newName)).append("\"; ");
        }
        if (command.contactEmail() != null) {
            detail.append("contactEmail provided; ");
        }
        if (command.contactPhone() != null) {
            detail.append("contactPhone provided; ");
        }
        if (command.notes() != null) {
            detail.append("notes updated; ");
        }
        return detail.isEmpty() ? "profile fields updated" : detail.toString().trim();
    }

    private static String safeAudit(String value) {
        return value == null ? "" : value.replace("\"", "'");
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    public record ProfileUpdateCommand(
            String orgId,
            String name,
            String shortName,
            String contactName,
            String contactPhone,
            String contactEmail,
            String website,
            String industry,
            String organizationSize,
            String timezone,
            String notes
    ) {
    }

    public record OrganizationProfileView(
            String orgId,
            String name,
            String shortName,
            String status,
            String contactName,
            String contactPhone,
            String contactEmail,
            String website,
            String industry,
            String organizationSize,
            String timezone,
            String notes,
            OwnerView owner,
            long memberCount,
            Instant createdAt,
            Instant updatedAt,
            String updatedBy,
            List<ExportJobSummary> recentExportJobs
    ) {
    }

    public record OwnerView(String memberId, String displayName, String mobile) {
    }

    public record ExportJobSummary(
            Long id,
            String status,
            String reason,
            Instant createdAt,
            Instant finishedAt,
            Instant updatedAt
    ) {
    }
}
