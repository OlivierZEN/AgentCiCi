package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class UserAccountPublicIdIntegrationTest {

    private static final String URL_ENV = "USER_ACCOUNT_PUBLIC_ID_MIGRATION_TEST_URL";
    private static final String USERNAME_ENV = "USER_ACCOUNT_PUBLIC_ID_MIGRATION_TEST_USERNAME";
    private static final String PASSWORD_ENV = "USER_ACCOUNT_PUBLIC_ID_MIGRATION_TEST_PASSWORD";

    @Test
    void backfillsLegacyAccountsAndGeneratesImmutablePublicIdsForNewAccounts() throws Exception {
        String jdbcUrl = System.getenv(URL_ENV);
        String username = System.getenv(USERNAME_ENV);
        String password = System.getenv(PASSWORD_ENV);
        Assumptions.assumeTrue(jdbcUrl != null && !jdbcUrl.isBlank()
                        && username != null && !username.isBlank()
                        && password != null && !password.isBlank(),
                "Fresh migration database is supplied only by the verification command");

        migrate(jdbcUrl, username, password, "96");
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO user_account (id, primary_mobile, display_name, status, created_at, updated_at)
                    VALUES ('legacy-public-id-account', '13900000091', '历史用户', 'ACTIVE',
                            TIMESTAMP '2024-03-18 08:00:00', TIMESTAMP '2024-03-18 08:00:00')
                    """);
        }

        migrate(jdbcUrl, username, password, null);
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            assertThat(readPublicId(statement, "legacy-public-id-account"))
                    .matches("^U2024[A-Z0-9]{8}$");

            statement.executeUpdate("""
                    INSERT INTO user_account (id, primary_mobile, display_name, status, created_at, updated_at)
                    VALUES ('new-public-id-account', '13900000092', '新用户', 'ACTIVE',
                            TIMESTAMP '2026-07-26 10:00:00', TIMESTAMP '2026-07-26 10:00:00')
                    """);
            String newPublicId = readPublicId(statement, "new-public-id-account");
            assertThat(newPublicId).matches("^U2026[A-Z0-9]{8}$");
            assertThat(newPublicId).isNotEqualTo(readPublicId(statement, "legacy-public-id-account"));

            assertThatThrownBy(() -> statement.executeUpdate("""
                    UPDATE user_account SET public_id = 'U2026AAAAAAAA'
                    WHERE id = 'new-public-id-account'
                    """))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("immutable");
        }
    }

    private static void migrate(String jdbcUrl, String username, String password, String target) {
        FluentConfiguration configuration = Flyway.configure()
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static String readPublicId(Statement statement, String accountId) throws SQLException {
        try (ResultSet result = statement.executeQuery("""
                SELECT public_id FROM user_account WHERE id = '%s'
                """.formatted(accountId))) {
            assertThat(result.next()).isTrue();
            return result.getString("public_id");
        }
    }
}
