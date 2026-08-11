package com.codehouse.ciciassistant.ai.config;

import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.model.domain.CompanyModelConfigEntity;
import com.codehouse.ciciassistant.model.domain.CompanyModelConfigRepository;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionEntity;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrchestratorBootstrapData {

    @Bean
    CommandLineRunner bootstrapOrchestratorData(PlatformAccountProperties properties,
                                                CompanyModelConfigRepository modelRepo,
                                                ToolDefinitionRepository toolRepo) {
        return args -> {
            String companyId = properties.getGovernanceCompanyId();
            if (companyId == null || companyId.isBlank()) {
                companyId = PlatformAccountProperties.LEGACY_DEFAULT_GOVERNANCE_COMPANY_ID;
            }
            final String bootstrapCompanyId = companyId.trim();
            if (modelRepo.findByCompanyIdAndSceneCode(bootstrapCompanyId, "chat").isEmpty()) {
                modelRepo.save(new CompanyModelConfigEntity(bootstrapCompanyId, "chat", "mock", "cici-default"));
            }
            if (toolRepo.findByCompanyIdAndEnabledTrue(bootstrapCompanyId).isEmpty()) {
                toolRepo.save(new ToolDefinitionEntity(
                        bootstrapCompanyId,
                        "time.now",
                        "Return current server time",
                        "read",
                        true
                ));
            }
        };
    }
}
