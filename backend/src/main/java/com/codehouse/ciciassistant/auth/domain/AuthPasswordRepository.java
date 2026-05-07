package com.codehouse.ciciassistant.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthPasswordRepository extends JpaRepository<AuthPasswordEntity, String> {
}
