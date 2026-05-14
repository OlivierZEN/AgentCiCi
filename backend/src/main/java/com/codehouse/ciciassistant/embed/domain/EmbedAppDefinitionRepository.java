package com.codehouse.ciciassistant.embed.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmbedAppDefinitionRepository extends JpaRepository<EmbedAppDefinitionEntity, String> {

    List<EmbedAppDefinitionEntity> findAllByOrderByAppCodeAsc();
}
