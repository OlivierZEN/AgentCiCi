package com.codehouse.ciciassistant.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.service.SkillDependencyGraphService;
import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.platform.api.PlatformController;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.codehouse.ciciassistant.platform.service.PlatformGovernanceService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformSkillDependencyGraphControllerTest {

    private static final String GOVERNANCE_ORG_ID = "demo-org";
    private static final long SKILL_ID = 201L;

    @Mock
    private PlatformGovernanceService platformGovernanceService;
    @Mock
    private PlatformAuditService platformAuditService;
    @Mock
    private PlatformAccountProperties platformAccountProperties;
    @Mock
    private SkillDependencyGraphService graphService;

    private PlatformController controller;

    @BeforeEach
    void setUp() {
        when(platformAccountProperties.getGovernanceCompanyId()).thenReturn(GOVERNANCE_ORG_ID);
        controller = new PlatformController(
                platformGovernanceService,
                platformAuditService,
                platformAccountProperties,
                graphService);
    }

    @Test
    void shouldExposePlatformProtectedSkillDependencyGraph() {
        SkillDependencyGraphService.GraphView graph = new SkillDependencyGraphService.GraphView(
                new SkillDependencyGraphService.GraphScope(
                        "SKILL_IMPACT", String.valueOf(SKILL_ID), "CRM 经营分析", null, null, "PUBLISHED"),
                "SKILL_IMPACT",
                List.of(),
                List.of(),
                new SkillDependencyGraphService.GraphSummary(0, 0, 1, 0, 0, 0),
                List.of());
        when(graphService.getSkillImpactGraph(GOVERNANCE_ORG_ID, SKILL_ID)).thenReturn(graph);

        ApiResponse<SkillDependencyGraphService.GraphView> response = controller.skillDependencyGraph(SKILL_ID);

        verify(graphService).getSkillImpactGraph(GOVERNANCE_ORG_ID, SKILL_ID);
        assertThat(response.data()).isSameAs(graph);
        assertThat(PlatformController.class.isAnnotationPresent(RequirePlatformRole.class)).isTrue();
    }
}
