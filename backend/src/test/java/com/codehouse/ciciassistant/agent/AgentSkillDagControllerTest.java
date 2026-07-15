package com.codehouse.ciciassistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.api.AgentSkillDagController;
import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.agent.service.SkillDependencyGraphService;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentSkillDagControllerTest {

    private static final String ORG_ID = "demo-org";
    private static final String USER_ID = "member-1";
    private static final String AGENT_ID = "sales-agent";

    @Mock
    private SkillDependencyGraphService graphService;
    @Mock
    private AgentAccessControlService accessControlService;

    private AgentSkillDagController controller;

    @BeforeEach
    void setUp() {
        TenantContext.setOrgId(ORG_ID);
        TenantContext.setUserId(USER_ID);
        TenantContext.setRoles(List.of(RoleCodes.ORG_USER));
        controller = new AgentSkillDagController(graphService, accessControlService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldRequireViewPermissionAndForwardVersionSelection() {
        SkillDependencyGraphService.GraphView graph = new SkillDependencyGraphService.GraphView(
                new SkillDependencyGraphService.GraphScope(
                        "AGENT_WORKFLOW", AGENT_ID, "销售助手", 101L, 3, "PUBLISHED"),
                "PINNED_WORKFLOW_VERSION",
                List.of(),
                List.of(),
                new SkillDependencyGraphService.GraphSummary(1, 1, 0, 0, 0, 0),
                List.of());
        when(graphService.getAgentGraph(ORG_ID, AGENT_ID, 3)).thenReturn(graph);

        ApiResponse<SkillDependencyGraphService.GraphView> response = controller.getSkillDag(AGENT_ID, 3);

        verify(accessControlService).require(
                ORG_ID,
                USER_ID,
                List.of(RoleCodes.ORG_USER),
                AGENT_ID,
                AgentPermission.VIEW);
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(graph);
    }
}
