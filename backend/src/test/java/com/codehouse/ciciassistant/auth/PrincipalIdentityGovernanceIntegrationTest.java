package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PrincipalIdentityGovernanceIntegrationTest {

    @Test
    void createsHumanPrincipalAndMirrorsLegacyKeycloakBinding() throws Exception {
        String jdbcUrl = System.getenv("USER_ACCOUNT_PUBLIC_ID_MIGRATION_TEST_URL");
        String username = System.getenv("USER_ACCOUNT_PUBLIC_ID_MIGRATION_TEST_USERNAME");
        String password = System.getenv("USER_ACCOUNT_PUBLIC_ID_MIGRATION_TEST_PASSWORD");
        Assumptions.assumeTrue(jdbcUrl != null && username != null && password != null,
                "Fresh migration database is supplied only by the verification command");
        migrate(jdbcUrl, username, password);
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            assertThat(single(statement, "SELECT to_regclass('public.service_principal_scope')"))
                    .isEqualTo("service_principal_scope");
            statement.executeUpdate("""
                    INSERT INTO user_account (id, primary_mobile, display_name, status, created_at, updated_at)
                    VALUES ('principal-test-account', '13900000093', '主体测试', 'ACTIVE', now(), now())
                    """);
            assertThat(single(statement, "SELECT principal_type || ':' || lifecycle_status FROM principal WHERE id = 'principal-test-account'"))
                    .isEqualTo("HUMAN:ACTIVE");
            statement.executeUpdate("""
                    INSERT INTO account_external_identity (id, account_id, issuer, subject, created_at, updated_at)
                    VALUES ('principal-test-identity', 'principal-test-account', 'https://sso.example/realms/agentcici', 'kc-subject', now(), now())
                    """);
            assertThat(single(statement, "SELECT principal_id || ':' || identity_type FROM principal_identity WHERE issuer = 'https://sso.example/realms/agentcici' AND subject = 'kc-subject'"))
                    .isEqualTo("principal-test-account:HUMAN_USER");
            statement.executeUpdate("""
                    UPDATE account_external_identity
                    SET subject = 'kc-subject-rebound', updated_at = now()
                    WHERE id = 'principal-test-identity'
                    """);
            assertThat(single(statement, "SELECT subject FROM principal_identity WHERE id = 'principal-test-identity'"))
                    .isEqualTo("kc-subject-rebound");
        }
    }

    private static void migrate(String jdbcUrl, String username, String password) {
        FluentConfiguration configuration = Flyway.configure()
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration");
        configuration.load().migrate();
    }

    private static String single(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}
