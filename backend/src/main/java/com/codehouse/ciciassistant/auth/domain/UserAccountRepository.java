package com.codehouse.ciciassistant.auth.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, String> {

    Optional<UserAccountEntity> findByPrimaryMobile(String primaryMobile);

    Optional<UserAccountEntity> findByEmailIgnoreCase(String email);

    Optional<UserAccountEntity> findByPublicIdIgnoreCase(String publicId);

    @Query("""
            select account
            from UserAccountEntity account
            where not exists (
                select 1
                from UserEntity member
                where member.account.id = account.id
            )
            and (
                :keyword = ''
                or lower(account.primaryMobile) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(account.displayName, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(account.email, '')) like lower(concat('%', :keyword, '%'))
            )
            order by account.createdAt desc
            """)
    Page<UserAccountEntity> searchPersonalAccounts(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select account
            from UserAccountEntity account
            where :keyword = ''
               or lower(account.primaryMobile) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(account.displayName, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(account.email, '')) like lower(concat('%', :keyword, '%'))
            order by account.createdAt desc
            """)
    Page<UserAccountEntity> searchRegisteredAccounts(@Param("keyword") String keyword, Pageable pageable);
}
