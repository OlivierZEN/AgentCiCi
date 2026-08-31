package com.codehouse.ciciassistant.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class DemoExampleApplicationFlywayMigrationTest {
    private static final String URL_ENV = "DEMO_EXAMPLE_MIGRATION_TEST_URL";
    private static final String USERNAME_ENV = "DEMO_EXAMPLE_MIGRATION_TEST_USERNAME";
    private static final String PASSWORD_ENV = "DEMO_EXAMPLE_MIGRATION_TEST_PASSWORD";

    @Test
    void seedsARealDraftConnectionWithoutPretendingItWasTestedOrActivated() throws Exception {
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
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             ResultSet result = connection.createStatement().executeQuery("""
                     SELECT a.catalog_status,v.initialization_engine,v.provider_binding_key,
                            c.binding_key,c.app_code,c.status,c.active_revision_id,
                            r.revision_number,r.base_url,r.contract_version,r.auth_type,r.secret_ref,
                            r.health_path,r.activate_path,r.reconcile_path,r.suspend_path,
                            r.resume_path,r.upgrade_path,r.timeout_ms,r.max_attempts,r.test_status
                     FROM internal_application a
                     JOIN internal_application_version v
                       ON v.app_code=a.app_code AND v.version='1.0.0'
                     JOIN internal_application_provider_connection c ON c.app_code=a.app_code
                     JOIN internal_application_provider_connection_revision r
                       ON r.binding_key=c.binding_key AND r.revision_number=1
                     WHERE a.app_code='demo-example'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("catalog_status")).isEqualTo("PUBLISHED");
            assertThat(result.getString("initialization_engine")).isEqualTo("NONE");
            assertThat(result.getString("provider_binding_key")).isNull();
            assertThat(result.getString("binding_key")).isEqualTo("demo-example.lifecycle");
            assertThat(result.getString("app_code")).isEqualTo("demo-example");
            assertThat(result.getString("status")).isEqualTo("DRAFT");
            assertThat(result.getString("active_revision_id")).isNull();
            assertThat(result.getInt("revision_number")).isEqualTo(1);
            assertThat(result.getString("base_url")).isEqualTo("https://service.example.test");
            assertThat(result.getString("contract_version")).isEqualTo("v1");
            assertThat(result.getString("auth_type")).isEqualTo("HMAC_SHA256_SECRET_REF");
            assertThat(result.getString("secret_ref")).isEqualTo("demo-example.lifecycle-key");
            assertThat(result.getString("health_path")).isEqualTo("/internal/tenant-lifecycle/v1/health");
            assertThat(result.getString("activate_path")).isEqualTo("/internal/tenant-lifecycle/v1/activations");
            assertThat(result.getString("reconcile_path")).isEqualTo("/internal/tenant-lifecycle/v1/reconciliations");
            assertThat(result.getString("suspend_path")).isEqualTo("/internal/tenant-lifecycle/v1/suspensions");
            assertThat(result.getString("resume_path")).isEqualTo("/internal/tenant-lifecycle/v1/resumptions");
            assertThat(result.getString("upgrade_path")).isEqualTo("/internal/tenant-lifecycle/v1/upgrades");
            assertThat(result.getInt("timeout_ms")).isEqualTo(10000);
            assertThat(result.getInt("max_attempts")).isEqualTo(2);
            assertThat(result.getString("test_status")).isEqualTo("NOT_TESTED");
            assertThat(result.next()).isFalse();
        }
    }
}
