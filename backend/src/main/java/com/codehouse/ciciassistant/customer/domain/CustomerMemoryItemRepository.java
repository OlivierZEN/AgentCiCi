package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerMemoryItemRepository extends JpaRepository<CustomerMemoryItemEntity, Long> {
    List<CustomerMemoryItemEntity> findByOrgIdAndCrmAccountIdAndStatusOrderByOccurredAtDesc(
            String orgId, String crmAccountId, String status);
    List<CustomerMemoryItemEntity> findByOrgIdAndSourceEventId(String orgId, String sourceEventId);
    void deleteByOrgIdAndSourceEventId(String orgId, String sourceEventId);
}
