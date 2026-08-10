package com.codehouse.ciciassistant.auth.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
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

    @Query("""
            select m
            from UserEntity m
            join fetch m.account
            join fetch m.company
            where m.account.id in :accountIds
              and m.memberStatus = :memberStatus
            order by m.createdAt desc
            """)
    List<UserEntity> findByAccount_IdInAndMemberStatusOrderByCreatedAtDesc(
            @Param("accountIds") Collection<String> accountIds,
            @Param("memberStatus") String memberStatus);

    long countByCompany_IdAndRoleCodeAndMemberStatus(String companyId, String roleCode, String memberStatus);

    long countByCompany_IdAndMemberStatus(String companyId, String memberStatus);

    List<UserEntity> findByCompany_IdOrderByCreatedAtDesc(String companyId);

    List<UserEntity> findByCompany_IdAndRoleCodeOrderByCreatedAtAsc(String companyId, String roleCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m
            from UserEntity m
            join fetch m.account
            where m.company.id = :companyId
              and m.roleCode = :roleCode
            order by m.createdAt asc
            """)
    List<UserEntity> lockByCompanyIdAndRoleCode(
            @Param("companyId") String companyId,
            @Param("roleCode") String roleCode);

    Optional<UserEntity> findFirstByCompany_IdAndRoleCodeAndMemberStatusOrderByCreatedAtAsc(
            String companyId, String roleCode, String memberStatus);

    Optional<UserEntity> findByIdAndCompany_Id(String id, String companyId);

    Optional<UserEntity> findByCcUsername(String ccUsername);

    Optional<UserEntity> findByCompany_IdAndCcUsernameIgnoreCaseAndMemberStatus(
            String companyId,
            String ccUsername,
            String memberStatus);
}
