package com.codehouse.ciciassistant.ai.config;

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
    CommandLineRunner bootstrapOrchestratorData(CompanyModelConfigRepository modelRepo, ToolDefinitionRepository toolRepo) {
        return args -> {
            if (modelRepo.findByCompanyIdAndSceneCode("demo-org", "chat").isEmpty()) {
                modelRepo.save(new CompanyModelConfigEntity("demo-org", "chat", "mock", "cici-default"));
            }
            if (toolRepo.findByCompanyIdAndEnabledTrue("demo-org").isEmpty()) {
                toolRepo.save(new ToolDefinitionEntity(
                        "demo-org",
                        "time.now",
                        "Return current server time",
                        "read",
                        true
                ));
            }
        };
    }
}
