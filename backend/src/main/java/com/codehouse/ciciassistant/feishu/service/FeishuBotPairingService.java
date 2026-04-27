package com.codehouse.ciciassistant.feishu.service;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.feishu.domain.FeishuBotBindingEntity;
import com.codehouse.ciciassistant.feishu.domain.FeishuBotBindingRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeishuBotPairingService {

    private final FeishuPairingCodeStore pairingCodeStore;
    private final FeishuBotBindingRepository bindingRepository;
    private final FeishuBotConfigService feishuBotConfigService;
    private final UserRepository userRepository;

    public FeishuBotPairingService(FeishuPairingCodeStore pairingCodeStore,
                                   FeishuBotBindingRepository bindingRepository,
                                   FeishuBotConfigService feishuBotConfigService,
                                   UserRepository userRepository) {
        this.pairingCodeStore = pairingCodeStore;
        this.bindingRepository = bindingRepository;
        this.feishuBotConfigService = feishuBotConfigService;
        this.userRepository = userRepository;
    }

    public Map<String, Object> getBindingStatus(String orgId, String userId) {
        Optional<FeishuBotBindingEntity> active = getActiveBindingForUser(orgId, userId);
        return active.<Map<String, Object>>map(binding -> Map.of(
                        "paired", true,
                        "agentCode", binding.getAgentCode(),
                        "tenantKey", binding.getTenantKey(),
                        "openId", binding.getOpenId(),
                        "pairedAt", binding.getPairedAt().toString(),
                        "lastMessageAt", binding.getLastMessageAt() == null ? "" : binding.getLastMessageAt().toString()
                ))
                .orElseGet(() -> Map.of("paired", false));
    }

    public FeishuPairingCodeStore.PairingCode createPairingCode(String orgId, String userId, String agentCode) {
        if (feishuBotConfigService.getEnabledConfig(orgId).isEmpty()) {
            throw new IllegalArgumentException("飞书机器人尚未启用或配置不完整");
        }
        String normalizedAgentCode = normalizeAgentCode(agentCode, orgId);
        return pairingCodeStore.createCode(orgId, userId, normalizedAgentCode);
    }

    @Transactional
    public FeishuBotBindingEntity consumePairingCode(String orgId, String code, String tenantKey,
                                                     String openId, String unionId, String chatId) {
        FeishuPairingCodeStore.PairCodePayload payload = pairingCodeStore.consumeCode(orgId, code);

        List<FeishuBotBindingEntity> activeForUser = bindingRepository
                .findByOrgIdAndUserIdAndStatusOrderByUpdatedAtDesc(orgId, payload.userId(), FeishuBotBindingEntity.STATUS_ACTIVE);
        for (FeishuBotBindingEntity item : activeForUser) {
            if (!tenantKey.equals(item.getTenantKey()) || !openId.equals(item.getOpenId())) {
                item.unbind();
                bindingRepository.save(item);
            }
        }

        FeishuBotBindingEntity binding = bindingRepository
                .findByOrgIdAndTenantKeyAndOpenId(orgId, tenantKey, openId)
                .orElseGet(() -> new FeishuBotBindingEntity(
                        orgId,
                        payload.userId(),
                        tenantKey,
                        openId,
                        unionId,
                        chatId,
                        payload.agentCode()
                ));
        binding.rebind(payload.userId(), unionId, chatId, payload.agentCode());
        return bindingRepository.save(binding);
    }

    public Optional<FeishuBotBindingEntity> findActiveBinding(String orgId, String tenantKey, String openId) {
        return bindingRepository.findByOrgIdAndTenantKeyAndOpenIdAndStatus(
                orgId, tenantKey, openId, FeishuBotBindingEntity.STATUS_ACTIVE);
    }

    @Transactional
    public FeishuBotBindingEntity ensureAutoBinding(String orgId, String tenantKey, String openId,
                                                    String unionId, String chatId) {
        Optional<FeishuBotBindingEntity> existing = findActiveBinding(orgId, tenantKey, openId);
        if (existing.isPresent()) {
            return existing.get();
        }
        String agentCode = feishuBotConfigService.getEnabledConfig(orgId)
                .map(FeishuBotConfigService.FeishuBotConfig::defaultAgentCode)
                .orElse("cici");
        String userId = resolveFallbackUserId(orgId);
        FeishuBotBindingEntity binding = bindingRepository
                .findByOrgIdAndTenantKeyAndOpenId(orgId, tenantKey, openId)
                .orElseGet(() -> new FeishuBotBindingEntity(
                        orgId,
                        userId,
                        tenantKey,
                        openId,
                        unionId,
                        chatId,
                        agentCode
                ));
        binding.rebind(userId, unionId, chatId, agentCode);
        return bindingRepository.save(binding);
    }

    @Transactional
    public void touchMessage(FeishuBotBindingEntity binding, String chatId) {
        binding.touchMessage(chatId);
        bindingRepository.save(binding);
    }

    @Transactional
    public void touchProfile(FeishuBotBindingEntity binding, String displayName, String avatarUrl) {
        binding.touchProfile(displayName, avatarUrl);
        bindingRepository.save(binding);
    }

    @Transactional
    public void unbindCurrentUser(String orgId, String userId) {
        List<FeishuBotBindingEntity> activeBindings = bindingRepository
                .findByOrgIdAndUserIdAndStatusOrderByUpdatedAtDesc(orgId, userId, FeishuBotBindingEntity.STATUS_ACTIVE);
        if (activeBindings.isEmpty()) {
            throw new IllegalArgumentException("当前用户还没有飞书绑定");
        }
        for (FeishuBotBindingEntity item : activeBindings) {
            item.unbind();
            bindingRepository.save(item);
        }
    }

    public Optional<FeishuBotBindingEntity> findActiveBindingForUser(String orgId, String userId) {
        return getActiveBindingForUser(orgId, userId);
    }

    private Optional<FeishuBotBindingEntity> getActiveBindingForUser(String orgId, String userId) {
        List<FeishuBotBindingEntity> activeBindings = bindingRepository
                .findByOrgIdAndUserIdAndStatusOrderByUpdatedAtDesc(orgId, userId, FeishuBotBindingEntity.STATUS_ACTIVE);
        return activeBindings.stream().findFirst();
    }

    private String normalizeAgentCode(String agentCode, String orgId) {
        String normalized = agentCode == null || agentCode.isBlank()
                ? feishuBotConfigService.getEnabledConfig(orgId).map(FeishuBotConfigService.FeishuBotConfig::defaultAgentCode).orElse("cici")
                : agentCode.trim();
        if (!"cici".equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException("当前版本仅支持绑定系统内置 CiCi");
        }
        return normalized.toLowerCase();
    }

    private String resolveFallbackUserId(String orgId) {
        List<UserEntity> users = userRepository.findByOrg_IdOrderByCreatedAtDesc(orgId);
        if (users.isEmpty()) {
            throw new IllegalStateException("当前组织没有可用系统用户，无法自动创建飞书绑定");
        }
        return users.stream()
                .filter(item -> RoleCodes.ORG_ADMIN.equals(item.getRoleCode()))
                .findFirst()
                .orElse(users.get(0))
                .getId();
    }
}
