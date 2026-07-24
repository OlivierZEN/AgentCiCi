package com.codehouse.ciciassistant.billing.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingCreditLedgerRepository extends JpaRepository<BillingCreditLedgerEntity, Long> {

    List<BillingCreditLedgerEntity> findByCompanyIdOrderByOccurredAtAsc(String companyId);

    List<BillingCreditLedgerEntity> findByCompanyIdOrderByIdAsc(String companyId);

    List<BillingCreditLedgerEntity> findTop50ByCompanyIdOrderByOccurredAtDesc(String companyId);

    List<BillingCreditLedgerEntity> findTop50ByCompanyIdOrderByIdDesc(String companyId);
}
