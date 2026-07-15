package com.codehouse.ciciassistant.agent;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SkillDagMigrationTest {

    @Test
    void concurrentImpactIndexesShouldBeRetrySafeAfterAnInterruptedBuild() throws IOException {
        String sql = new ClassPathResource("db/migration/V81__skill_dag_impact_index.sql")
                .getContentAsString(UTF_8);

        assertDropBeforeCreate(sql, "idx_agent_workflow_skill_ref_org_skill_impact");
        assertDropBeforeCreate(sql, "idx_agent_skill_binding_org_skill_impact");
        assertThat(sql).doesNotContain("CREATE INDEX CONCURRENTLY IF NOT EXISTS");
    }

    private void assertDropBeforeCreate(String sql, String indexName) {
        int dropPosition = sql.indexOf("DROP INDEX CONCURRENTLY IF EXISTS " + indexName);
        int createPosition = sql.indexOf("CREATE INDEX CONCURRENTLY " + indexName);

        assertThat(dropPosition).isGreaterThanOrEqualTo(0);
        assertThat(createPosition).isGreaterThan(dropPosition);
    }
}
