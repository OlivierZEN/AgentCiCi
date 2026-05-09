package com.codehouse.ciciassistant.autoservice.domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutoServiceDemoRequestRepository extends JpaRepository<AutoServiceDemoRequestEntity, Long> {

    @Query("""
            select r
            from AutoServiceDemoRequestEntity r
            where (:status is null or r.status = :status)
              and (
                :keyword is null
                or lower(r.companyName) like lower(concat('%', :keyword, '%'))
                or lower(r.contactName) like lower(concat('%', :keyword, '%'))
                or lower(r.mobile) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(r.email, '')) like lower(concat('%', :keyword, '%'))
              )
            order by r.createdAt desc, r.id desc
            """)
    List<AutoServiceDemoRequestEntity> search(
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);
}
