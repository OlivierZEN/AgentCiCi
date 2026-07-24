package com.codehouse.ciciassistant.feishu.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeishuBotBindingRepository extends JpaRepository<FeishuBotBindingEntity, Long> {

    Optional<FeishuBotBindingEntity> findByCompanyIdAndTenantKeyAndOpenIdAndStatus(
            String companyId, String tenantKey, String openId, String status);

    Optional<FeishuBotBindingEntity> findByCompanyIdAndTenantKeyAndOpenId(
            String companyId, String tenantKey, String openId);

    Optional<FeishuBotBindingEntity> findFirstByCompanyIdAndChatIdAndStatusOrderByUpdatedAtDesc(
            String companyId, String chatId, String status);

    List<FeishuBotBindingEntity> findByCompanyIdAndUserIdAndStatusOrderByUpdatedAtDesc(
            String companyId, String userId, String status);
}
