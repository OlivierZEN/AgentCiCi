package com.codehouse.ciciassistant.auth.config;

import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.domain.PlatformAccountEntity;
import com.codehouse.ciciassistant.auth.domain.PlatformAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthBootstrapData {

    @Bean
    CommandLineRunner bootstrapOrg(CompanyRepository companyRepository) {
        return args -> companyRepository.findById("demo-org")
                .orElseGet(() -> companyRepository.save(new CompanyEntity("demo-org", "Demo Company", "ACTIVE")));
    }

    @Bean
    CommandLineRunner bootstrapPlatformAccount(PlatformAccountProperties properties,
                                               PlatformAccountRepository platformAccountRepository,
                                               ObjectMapper objectMapper) {
        return args -> {
            if (!properties.isEnabled()) {
                return;
            }
            String email = normalizeEmail(properties.getEmail());
            String mobile = normalizeMobile(properties.getMobile());
            String displayName = normalizeDisplayName(properties.getDisplayName(), email, mobile);
            List<String> roles = properties.getRoles() == null ? List.of() : properties.getRoles().stream()
                    .map(AuthBootstrapData::normalizeText)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .toList();
            if (email.isBlank() || mobile.isBlank() || roles.isEmpty()) {
                return;
            }
            String rolesJson = objectMapper.writeValueAsString(roles);
            platformAccountRepository.findByEmailIgnoreCase(email)
                    .or(() -> platformAccountRepository.findByMobile(mobile))
                    .ifPresentOrElse(account -> {
                        account.updateBootstrapFields(email, mobile, displayName, rolesJson);
                        platformAccountRepository.save(account);
                    }, () -> platformAccountRepository.save(new PlatformAccountEntity(email, mobile, displayName, rolesJson)));
        };
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizeMobile(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeDisplayName(String displayName, String email, String mobile) {
        String normalized = normalizeText(displayName);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return email.isBlank() ? mobile : email;
    }
}
