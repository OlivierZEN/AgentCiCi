package com.codehouse.ciciassistant.model.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OrgModelConfigRepository extends JpaRepository<OrgModelConfigEntity, Long> {

    Optional<OrgModelConfigEntity> findByOrgIdAndSceneCode(String orgId, String sceneCode);

    List<OrgModelConfigEntity> findByOrgId(String orgId);

    List<OrgModelConfigEntity> findByOrgIdAndProvider(String orgId, String provider);

    @Modifying
    @Transactional
    @Query("delete from OrgModelConfigEntity e where e.orgId = :orgId and e.sceneCode = :sceneCode")
    void deleteByOrgIdAndSceneCode(@Param("orgId") String orgId, @Param("sceneCode") String sceneCode);
}
