package com.codehouse.ciciassistant.billing.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingEditionConfigRepository extends JpaRepository<BillingEditionConfigEntity, Long> {

    List<BillingEditionConfigEntity> findByOrgIdOrderByItemTypeAscItemCodeAscVersionNoDesc(String orgId);

    List<BillingEditionConfigEntity> findByOrgIdAndItemTypeAndItemCodeOrderByVersionNoDesc(String orgId,
                                                                                           String itemType,
                                                                                           String itemCode);

    List<BillingEditionConfigEntity> findByOrgIdAndItemTypeAndItemCodeAndPublishStatusOrderByVersionNoDesc(String orgId,
                                                                                                            String itemType,
                                                                                                            String itemCode,
                                                                                                            String publishStatus);

    Optional<BillingEditionConfigEntity> findByIdAndOrgId(Long id, String orgId);
}
