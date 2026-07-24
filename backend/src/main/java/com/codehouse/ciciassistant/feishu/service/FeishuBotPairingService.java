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

    public Map<String, Object> getBindingStatus(String companyId, String userId) {
        Optional<FeishuBotBindingEntity> active = getActiveBindingForUser(companyId, userId);
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

    public FeishuPairingCodeStore.PairingCode createPairingCode(String companyId, String userId, String agentCode) {
        if (feishuBotConfigService.getEnabledConfig(companyId).isEmpty()) {
            throw new IllegalArgumentException("飞书机器人尚未启用或配置不完整");
        }
        String normalizedAgentCode = normalizeAgentCode(agentCode, companyId);
        return pairingCodeStore.createCode(companyId, userId, normalizedAgentCode);
    }

    @Transactional
    public FeishuBotBindingEntity consumePairingCode(String companyId, String code, String tenantKey,
                                                     String openId, String unionId, String chatId) {
        FeishuPairingCodeStore.PairCodePayload payload = pairingCodeStore.consumeCode(companyId, code);

        List<FeishuBotBindingEntity> activeForUser = bindingRepository
                .findByCompanyIdAndUserIdAndStatusOrderByUpdatedAtDesc(companyId, payload.userId(), FeishuBotBindingEntity.STATUS_ACTIVE);
        for (FeishuBotBindingEntity item : activeForUser) {
            if (!tenantKey.equals(item.getTenantKey()) || !openId.equals(item.getOpenId())) {
                item.unbind();
                bindingRepository.save(item);
            }
        }

        FeishuBotBindingEntity binding = bindingRepository
                .findByCompanyIdAndTenantKeyAndOpenId(companyId, tenantKey, openId)
                .orElseGet(() -> new FeishuBotBindingEntity(
                        companyId,
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

    public Optional<FeishuBotBindingEntity> findActiveBinding(String companyId, String tenantKey, String openId) {
        return bindingRepository.findByCompanyIdAndTenantKeyAndOpenIdAndStatus(
                companyId, tenantKey, openId, FeishuBotBindingEntity.STATUS_ACTIVE);
    }

    @Transactional
    public FeishuBotBindingEntity ensureAutoBinding(String companyId, String tenantKey, String openId,
                                                    String unionId, String chatId) {
        Optional<FeishuBotBindingEntity> existing = findActiveBinding(companyId, tenantKey, openId);
        if (existing.isPresent()) {
            return existing.get();
        }
        String agentCode = feishuBotConfigService.getEnabledConfig(companyId)
                .map(FeishuBotConfigService.FeishuBotConfig::defaultAgentCode)
                .orElse("cici");
        String userId = resolveFallbackUserId(companyId);
        FeishuBotBindingEntity binding = bindingRepository
                .findByCompanyIdAndTenantKeyAndOpenId(companyId, tenantKey, openId)
                .orElseGet(() -> new FeishuBotBindingEntity(
                        companyId,
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
    public void unbindCurrentUser(String companyId, String userId) {
        List<FeishuBotBindingEntity> activeBindings = bindingRepository
                .findByCompanyIdAndUserIdAndStatusOrderByUpdatedAtDesc(companyId, userId, FeishuBotBindingEntity.STATUS_ACTIVE);
        if (activeBindings.isEmpty()) {
            throw new IllegalArgumentException("当前用户还没有飞书绑定");
        }
        for (FeishuBotBindingEntity item : activeBindings) {
            item.unbind();
            bindingRepository.save(item);
        }
    }

    public Optional<FeishuBotBindingEntity> findActiveBindingForUser(String companyId, String userId) {
        return getActiveBindingForUser(companyId, userId);
    }

    private Optional<FeishuBotBindingEntity> getActiveBindingForUser(String companyId, String userId) {
        List<FeishuBotBindingEntity> activeBindings = bindingRepository
                .findByCompanyIdAndUserIdAndStatusOrderByUpdatedAtDesc(companyId, userId, FeishuBotBindingEntity.STATUS_ACTIVE);
        return activeBindings.stream().findFirst();
    }

    private String normalizeAgentCode(String agentCode, String companyId) {
        String normalized = agentCode == null || agentCode.isBlank()
                ? feishuBotConfigService.getEnabledConfig(companyId).map(FeishuBotConfigService.FeishuBotConfig::defaultAgentCode).orElse("cici")
                : agentCode.trim();
        if (!"cici".equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException("当前版本仅支持绑定系统内置 CiCi");
        }
        return normalized.toLowerCase();
    }

    private String resolveFallbackUserId(String companyId) {
        List<UserEntity> users = userRepository.findByCompany_IdOrderByCreatedAtDesc(companyId);
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
