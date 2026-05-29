package com.codehouse.ciciassistant.billing.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingCreditLedgerRepository extends JpaRepository<BillingCreditLedgerEntity, Long> {

    List<BillingCreditLedgerEntity> findTop50ByOrgIdOrderByOccurredAtDesc(String orgId);
}
