package com.codehouse.ciciassistant.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class AgentMemoryFlywayMigrationTest {

    private static final String URL_ENV = "AGENT_MEMORY_MIGRATION_TEST_URL";
    private static final String USERNAME_ENV = "AGENT_MEMORY_MIGRATION_TEST_USERNAME";
    private static final String PASSWORD_ENV = "AGENT_MEMORY_MIGRATION_TEST_PASSWORD";

    @Test
    void migratesTheGenericMemoryAndCredentialBindingSchemaIntoAnExplicitFreshDatabase() throws Exception {
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
             ResultSet tables = connection.createStatement().executeQuery("""
                     SELECT table_name
                     FROM information_schema.tables
                     WHERE table_schema = 'public'
                       AND table_name IN ('memory_subject', 'memory_record', 'memory_conversation_snapshot',
                                           'memory_candidate', 'memory_evidence', 'memory_vector_fragment',
                                           'agent_api_memory_binding')
                     ORDER BY table_name
                     """)) {
            java.util.List<String> found = new java.util.ArrayList<>();
            while (tables.next()) {
                found.add(tables.getString(1));
            }
            assertThat(found).containsExactly(
                    "agent_api_memory_binding", "memory_candidate", "memory_conversation_snapshot", "memory_evidence", "memory_record", "memory_subject", "memory_vector_fragment");
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             ResultSet columns = connection.createStatement().executeQuery("""
                     SELECT table_name || ':' || column_name FROM information_schema.columns
                     WHERE table_schema = 'public' AND table_name IN ('memory_candidate', 'memory_record') AND column_name = 'agent_id'
                     ORDER BY table_name
                     """)) {
            java.util.List<String> found = new java.util.ArrayList<>();
            while (columns.next()) found.add(columns.getString(1));
            assertThat(found).containsExactly("memory_candidate:agent_id", "memory_record:agent_id");
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             ResultSet identityColumns = connection.createStatement().executeQuery("""
                     SELECT column_name
                     FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = 'company_member'
                       AND column_name IN ('org_id', 'company_id')
                     ORDER BY column_name
                     """)) {
            java.util.List<String> found = new java.util.ArrayList<>();
            while (identityColumns.next()) found.add(identityColumns.getString(1));
            assertThat(found).containsExactly("company_id");
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             ResultSet legacyColumns = connection.createStatement().executeQuery("""
                     SELECT count(*)
                     FROM information_schema.columns
                     WHERE table_schema = 'public' AND column_name = 'org_id'
                     """)) {
            assertThat(legacyColumns.next()).isTrue();
            assertThat(legacyColumns.getInt(1)).isZero();
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             ResultSet companyColumns = connection.createStatement().executeQuery("""
                     SELECT count(*)
                     FROM information_schema.columns
                     WHERE table_schema = 'public' AND column_name = 'company_id'
                     """)) {
            assertThat(companyColumns.next()).isTrue();
            assertThat(companyColumns.getInt(1)).isGreaterThanOrEqualTo(131);
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             ResultSet companies = connection.createStatement().executeQuery("""
                     SELECT table_name
                     FROM information_schema.tables
                     WHERE table_schema = 'public'
                       AND table_name IN ('org', 'company', 'organization_member', 'company_member')
                     ORDER BY table_name
                     """)) {
            java.util.List<String> found = new java.util.ArrayList<>();
            while (companies.next()) found.add(companies.getString(1));
            assertThat(found).containsExactly("company", "company_member");
        }
    }
}
