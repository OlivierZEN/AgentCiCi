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
            where m.company.id = :companyId
              and m.account.primaryMobile = :mobile
              and m.memberStatus = 'ACTIVE'
            """)
    Optional<UserEntity> findByCompanyIdAndMobile(@Param("companyId") String companyId, @Param("mobile") String mobile);

    Optional<UserEntity> findByCompany_IdAndAccount_Id(String companyId, String accountId);

    Optional<UserEntity> findByCompany_IdAndAccount_IdAndMemberStatus(String companyId, String accountId, String memberStatus);

    List<UserEntity> findByAccount_IdAndMemberStatusOrderByCreatedAtDesc(String accountId, String memberStatus);

    long countByCompany_IdAndRoleCodeAndMemberStatus(String companyId, String roleCode, String memberStatus);

    long countByCompany_IdAndMemberStatus(String companyId, String memberStatus);

    List<UserEntity> findByCompany_IdOrderByCreatedAtDesc(String companyId);

    Optional<UserEntity> findFirstByCompany_IdAndRoleCodeAndMemberStatusOrderByCreatedAtAsc(
            String companyId, String roleCode, String memberStatus);

    Optional<UserEntity> findByIdAndCompany_Id(String id, String companyId);

    Optional<UserEntity> findByCcUsername(String ccUsername);

    Optional<UserEntity> findByCompany_IdAndCcUsernameIgnoreCaseAndMemberStatus(
            String companyId,
            String ccUsername,
            String memberStatus);
}
