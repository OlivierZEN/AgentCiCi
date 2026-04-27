package com.codehouse.ciciassistant.feishu.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeishuBotBindingRepository extends JpaRepository<FeishuBotBindingEntity, Long> {

    Optional<FeishuBotBindingEntity> findByOrgIdAndTenantKeyAndOpenIdAndStatus(
            String orgId, String tenantKey, String openId, String status);

    Optional<FeishuBotBindingEntity> findByOrgIdAndTenantKeyAndOpenId(
            String orgId, String tenantKey, String openId);

    Optional<FeishuBotBindingEntity> findFirstByOrgIdAndChatIdAndStatusOrderByUpdatedAtDesc(
            String orgId, String chatId, String status);

    List<FeishuBotBindingEntity> findByOrgIdAndUserIdAndStatusOrderByUpdatedAtDesc(
            String orgId, String userId, String status);
}
