package com.codehouse.ciciassistant.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.ontology.domain.OntologyVersionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OntologyPersistenceIntegrationTest {

    @Autowired
    private OntologyWorkspaceRepository workspaces;

    @Autowired
    private OntologyVersionRepository versions;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void scopesWorkspaceLookupToOrganization() {
        OntologyWorkspaceEntity saved = workspaces.save(
                new OntologyWorkspaceEntity(
                        "org-a",
                        "project-delivery",
                        "项目交付",
                        "通用性样例",
                        "user-a"));

        assertThat(workspaces.findByIdAndOrgId(saved.getId(), "org-a")).isPresent();
        assertThat(workspaces.findByIdAndOrgId(saved.getId(), "org-b")).isEmpty();
    }

    @Test
    void ordersVersionsWithinWorkspaceAndScopesToOrganization() {
        OntologyWorkspaceEntity workspace = workspaces.save(
                new OntologyWorkspaceEntity(
                        "org-version-a",
                        "service-operations",
                        "服务运营",
                        "版本隔离样例",
                        "user-a"));
        versions.save(new OntologyVersionEntity(
                "org-version-a", workspace.getId(), 1, 1L, "hash-1", "{}",
                "{}", "type Query { ping: String }", "{}", "{}", "user-a"));
        versions.save(new OntologyVersionEntity(
                "org-version-a", workspace.getId(), 2, 2L, "hash-2", "{}",
                "{}", "type Query { pong: String }", "{}", "{}", "user-a"));

        assertThat(versions.findByWorkspaceIdAndOrgIdOrderByVersionNoDesc(
                workspace.getId(), "org-version-a"))
                .extracting(OntologyVersionEntity::getVersionNo)
                .containsExactly(2, 1);
        assertThat(versions.findByWorkspaceIdAndOrgIdOrderByVersionNoDesc(
                workspace.getId(), "org-version-b"))
                .isEmpty();
    }

    @Test
    void preservesTheDomainNeutralDocumentContractThroughJson() throws Exception {
        OntologyDocument document = new OntologyDocument(
                "project-delivery",
                "项目交付",
                "通用性样例",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        String json = objectMapper.writeValueAsString(document);

        assertThat(objectMapper.readValue(json, OntologyDocument.class)).isEqualTo(document);
    }
}
