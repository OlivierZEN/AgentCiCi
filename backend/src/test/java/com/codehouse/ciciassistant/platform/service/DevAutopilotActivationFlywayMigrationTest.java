package com.codehouse.ciciassistant.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class DevAutopilotActivationFlywayMigrationTest {
    private static final String URL_ENV = "DEVAUTOPILOT_ACTIVATION_MIGRATION_TEST_URL";
    private static final String USERNAME_ENV = "DEVAUTOPILOT_ACTIVATION_MIGRATION_TEST_USERNAME";
    private static final String PASSWORD_ENV = "DEVAUTOPILOT_ACTIVATION_MIGRATION_TEST_PASSWORD";

    @Test
    void upgradesTheExistingActivationTableWithRecoveryCheckpointsAndLease() throws Exception {
        String jdbcUrl = System.getenv(URL_ENV);
        String username = System.getenv(USERNAME_ENV);
        String password = System.getenv(PASSWORD_ENV);
        Assumptions.assumeTrue(jdbcUrl != null && !jdbcUrl.isBlank()
                        && username != null && !username.isBlank()
                        && password != null && !password.isBlank(),
                "Fresh migration database is supplied only by the verification command");

        Flyway.configure()
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .target("118")
                .load()
                .migrate();
        Flyway.configure()
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             ResultSet columns = connection.createStatement().executeQuery("""
                     SELECT column_name
                     FROM information_schema.columns
                     WHERE table_schema='public' AND table_name='tenant_application_activation'
                       AND column_name IN ('activation_stage','failed_stage','attempt_count','last_attempt_at',
                                           'lease_token','lease_expires_at')
                     ORDER BY column_name
                     """)) {
            List<String> actual = new ArrayList<>();
            while (columns.next()) actual.add(columns.getString(1));
            assertThat(actual).containsExactly(
                    "activation_stage", "attempt_count", "failed_stage", "last_attempt_at",
                    "lease_expires_at", "lease_token");
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             ResultSet migration = connection.createStatement().executeQuery("""
                     SELECT success FROM flyway_schema_history WHERE version='119'
                     """)) {
            assertThat(migration.next()).isTrue();
            assertThat(migration.getBoolean(1)).isTrue();
        }
    }
}
