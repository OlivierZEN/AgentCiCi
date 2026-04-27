package com.codehouse.ciciassistant.auth.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByOrgIdAndMobile(String orgId, String mobile);

    List<UserEntity> findByOrg_IdOrderByCreatedAtDesc(String orgId);

    Optional<UserEntity> findByIdAndOrg_Id(String id, String orgId);

    Optional<UserEntity> findByCcUsername(String ccUsername);
}
