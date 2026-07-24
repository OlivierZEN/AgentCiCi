package com.codehouse.ciciassistant.kb.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbSourceDocumentMapRepository extends JpaRepository<KbSourceDocumentMapEntity, Long> {

    Optional<KbSourceDocumentMapEntity> findByCompanyIdAndDataSourceIdAndExternalDocumentId(String companyId, Long dataSourceId, String externalDocumentId);
}
