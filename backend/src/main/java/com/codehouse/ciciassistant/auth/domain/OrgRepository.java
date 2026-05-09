package com.codehouse.ciciassistant.auth.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgRepository extends JpaRepository<OrgEntity, String> {

    List<OrgEntity> findAllByOrderByIdAsc();
}
