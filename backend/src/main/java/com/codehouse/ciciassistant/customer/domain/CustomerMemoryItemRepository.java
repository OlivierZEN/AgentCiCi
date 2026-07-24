package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerMemoryItemRepository extends JpaRepository<CustomerMemoryItemEntity, Long> {
    List<CustomerMemoryItemEntity> findByCompanyIdAndCrmAccountIdAndStatusOrderByOccurredAtDesc(
            String companyId, String crmAccountId, String status);
    List<CustomerMemoryItemEntity> findByCompanyIdAndSourceEventId(String companyId, String sourceEventId);
    void deleteByCompanyIdAndSourceEventId(String companyId, String sourceEventId);
}
