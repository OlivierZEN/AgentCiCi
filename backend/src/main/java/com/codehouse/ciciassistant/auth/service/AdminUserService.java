package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.util.AvatarDataUrlValidator;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<Map<String, Object>> listUsers(String orgId) {
        return userRepository.findByOrg_IdOrderByCreatedAtDesc(orgId).stream()
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> updateRole(String orgId, String actorUserId, String targetUserId, String newRoleCode) {
        if (!RoleCodes.isValidRole(newRoleCode)) {
            throw new IllegalArgumentException("Invalid role code");
        }
        UserEntity target = userRepository.findByIdAndOrg_Id(targetUserId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (targetUserId.equals(actorUserId)
                && RoleCodes.ORG_USER.equals(newRoleCode)
                && RoleCodes.ORG_ADMIN.equals(target.getRoleCode())) {
            long adminCount = userRepository.findByOrg_IdOrderByCreatedAtDesc(orgId).stream()
                    .filter(u -> RoleCodes.ORG_ADMIN.equals(u.getRoleCode()))
                    .count();
            if (adminCount <= 1) {
                throw new ForbiddenException("不能移除唯一的管理员");
            }
        }
        target.setRoleCode(newRoleCode);
        userRepository.save(target);
        return toRow(target);
    }

    @Transactional
    public Map<String, Object> updateProfile(
            String orgId,
            String userId,
            String mobile,
            String nickname,
            String ccUsername,
            String ccSafetymark,
            String avatarBase64) {
        UserEntity target = userRepository.findByIdAndOrg_Id(userId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String mobileValue = trimOrNull(mobile);
        if (mobileValue != null && !mobileValue.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        if (mobileValue != null) {
            UserEntity existing = userRepository.findByOrgIdAndMobile(orgId, mobileValue).orElse(null);
            if (existing != null && !existing.getId().equals(target.getId())) {
                throw new IllegalArgumentException("该手机号已被其他用户使用");
            }
            target.setMobile(mobileValue);
        }
        String ccUsernameValue = trimOrNull(ccUsername);
        if (ccUsernameValue != null) {
            UserEntity existing = userRepository.findByCcUsername(ccUsernameValue).orElse(null);
            if (existing != null && !existing.getId().equals(target.getId())) {
                throw new IllegalArgumentException("CloudCC用户名已被其他用户绑定");
            }
        }
        target.setNickname(trimOrNull(nickname));
        target.setCcUsername(ccUsernameValue);
        target.setCcSafetymark(trimOrNull(ccSafetymark));
        target.setAvatarBase64(AvatarDataUrlValidator.normalizeNullableDataUrl(avatarBase64, "avatarBase64"));
        userRepository.save(target);
        return toRow(target);
    }

    private Map<String, Object> toRow(UserEntity u) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", u.getId());
        row.put("mobile", u.getMobile());
        row.put("roleCode", u.getRoleCode());
        row.put("nickname", u.getNickname() == null ? "" : u.getNickname());
        row.put("ccUsername", u.getCcUsername() == null ? "" : u.getCcUsername());
        row.put("ccSafetymark", u.getCcSafetymark() == null ? "" : u.getCcSafetymark());
        row.put("avatarBase64", u.getAvatarBase64() == null ? "" : u.getAvatarBase64());
        row.put("createdAt", u.getCreatedAt().toString());
        return row;
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }
}
