package com.codehouse.ciciassistant.billing.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingCreditLedgerRepository extends JpaRepository<BillingCreditLedgerEntity, Long> {

    List<BillingCreditLedgerEntity> findByOrgIdOrderByOccurredAtAsc(String orgId);

    List<BillingCreditLedgerEntity> findByOrgIdOrderByIdAsc(String orgId);

    List<BillingCreditLedgerEntity> findTop50ByOrgIdOrderByOccurredAtDesc(String orgId);

    List<BillingCreditLedgerEntity> findTop50ByOrgIdOrderByIdDesc(String orgId);
}
