package com.codehouse.ciciassistant.auth.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    @Query("""
            select m
            from UserEntity m
            where m.org.id = :orgId
              and m.account.primaryMobile = :mobile
              and m.memberStatus = 'ACTIVE'
            """)
    Optional<UserEntity> findByOrgIdAndMobile(@Param("orgId") String orgId, @Param("mobile") String mobile);

    Optional<UserEntity> findByOrg_IdAndAccount_Id(String orgId, String accountId);

    Optional<UserEntity> findByOrg_IdAndAccount_IdAndMemberStatus(String orgId, String accountId, String memberStatus);

    List<UserEntity> findByAccount_IdAndMemberStatusOrderByCreatedAtDesc(String accountId, String memberStatus);

    long countByOrg_IdAndRoleCodeAndMemberStatus(String orgId, String roleCode, String memberStatus);

    long countByOrg_IdAndMemberStatus(String orgId, String memberStatus);

    List<UserEntity> findByOrg_IdOrderByCreatedAtDesc(String orgId);

    Optional<UserEntity> findFirstByOrg_IdAndRoleCodeAndMemberStatusOrderByCreatedAtAsc(
            String orgId, String roleCode, String memberStatus);

    Optional<UserEntity> findByIdAndOrg_Id(String id, String orgId);

    Optional<UserEntity> findByCcUsername(String ccUsername);
}
