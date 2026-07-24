package com.codehouse.ciciassistant.model.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CompanyModelConfigRepository extends JpaRepository<CompanyModelConfigEntity, Long> {

    Optional<CompanyModelConfigEntity> findByCompanyIdAndSceneCode(String companyId, String sceneCode);

    List<CompanyModelConfigEntity> findByCompanyId(String companyId);

    List<CompanyModelConfigEntity> findByCompanyIdAndProvider(String companyId, String provider);

    @Modifying
    @Transactional
    @Query("delete from CompanyModelConfigEntity e where e.companyId = :companyId and e.sceneCode = :sceneCode")
    void deleteByCompanyIdAndSceneCode(@Param("companyId") String companyId, @Param("sceneCode") String sceneCode);
}
