package com.codehouse.ciciassistant.auth.config;

import com.codehouse.ciciassistant.auth.domain.OrgEntity;
import com.codehouse.ciciassistant.auth.domain.OrgRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthBootstrapData {

    @Bean
    CommandLineRunner bootstrapOrg(OrgRepository orgRepository) {
        return args -> orgRepository.findById("demo-org")
                .orElseGet(() -> orgRepository.save(new OrgEntity("demo-org", "Demo Organization", "ACTIVE")));
    }
}
