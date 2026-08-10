package com.codehouse.ciciassistant.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.service.AgentCompileService;
import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.agent.service.AgentSkillBindingService;
import com.codehouse.ciciassistant.skill.service.SkillDefinitionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DevAutopilotProductManagerAgentPublisherTest {
    private static final String COMPANY_ID = "org00000000000000001";
    private static final String AGENT_ID = "devautopilot-pm";

    private final AgentDefinitionService definitions = Mockito.mock(AgentDefinitionService.class);
    private final AgentCompileService compiler = Mockito.mock(AgentCompileService.class);
    private final AgentSkillBindingService skillBindings = Mockito.mock(AgentSkillBindingService.class);
    private final SkillDefinitionService skills = Mockito.mock(SkillDefinitionService.class);
    private final DevAutopilotProductManagerAgentPublisher publisher =
            new DevAutopilotProductManagerAgentPublisher(definitions, compiler, skillBindings, skills);

    @Test
    void compilesAndPublishesAnExistingUnpublishedTemplateAgent() {
        AgentDefinitionEntity definition = definition();
        AgentDefinitionService.AgentDetail detail = new AgentDefinitionService.AgentDetail(
                definition,
                "",
                List.of(),
                List.of("semattice_project_delivery_query"),
                List.of(),
                Map.of());
        when(definitions.get(COMPANY_ID, AGENT_ID)).thenReturn(detail, publishedDetail(definition(), 42L));
        when(skillBindings.ensureBinding(COMPANY_ID, AGENT_ID,
                DevAutopilotProductManagerAgentPublisher.DELIVERY_SKILL_CODE, "always-on", 10)).thenReturn(true);
        when(compiler.compile(eq(COMPANY_ID), any())).thenReturn(new AgentCompileService.CompileResult(
                "", Map.of(), null, List.of(), List.of(), List.of(), List.of(), 1, true,
                "compiled", List.of()));

        DevAutopilotProductManagerAgentPublisher.Publication result =
                publisher.ensurePublished(COMPANY_ID, AGENT_ID);

        assertThat(result).isEqualTo(new DevAutopilotProductManagerAgentPublisher.Publication(42L, true));
        verify(skills).ensurePublishedPlatformSkillVersion(
                COMPANY_ID, DevAutopilotProductManagerAgentPublisher.DELIVERY_SKILL_CODE);
        verify(definitions).updateSpec(COMPANY_ID, AGENT_ID, DevAutopilotProductManagerAgentPublisher.STANDARD_SPEC);
        ArgumentCaptor<AgentDefinitionService.ReplaceBindingsCommand> bindings =
                ArgumentCaptor.forClass(AgentDefinitionService.ReplaceBindingsCommand.class);
        verify(definitions).replaceBindings(eq(COMPANY_ID), eq(AGENT_ID), bindings.capture());
        assertThat(bindings.getValue().channels()).containsExactly("web");
        ArgumentCaptor<AgentCompileService.CompileCommand> compile =
                ArgumentCaptor.forClass(AgentCompileService.CompileCommand.class);
        verify(compiler).compile(eq(COMPANY_ID), compile.capture());
        assertThat(compile.getValue().specText()).isEqualTo(DevAutopilotProductManagerAgentPublisher.STANDARD_SPEC);
        assertThat(compile.getValue().channels()).containsExactly("web");
        assertThat(compile.getValue().skillRefs())
                .containsExactly(DevAutopilotProductManagerAgentPublisher.DELIVERY_SKILL_CODE);
        verify(definitions).publishVersion(COMPANY_ID, AGENT_ID, 1);
    }

    @Test
    void upgradesAnAlreadyPublishedTemplateAgentWhenItsManagedSkillWasMissing() {
        AgentDefinitionEntity definition = definition();
        definition.setPublishedVersionId(9L);
        AgentDefinitionEntity upgraded = definition();
        when(definitions.get(COMPANY_ID, AGENT_ID)).thenReturn(publishedDetail(definition, 9L), publishedDetail(upgraded, 10L));
        when(skillBindings.ensureBinding(COMPANY_ID, AGENT_ID,
                DevAutopilotProductManagerAgentPublisher.DELIVERY_SKILL_CODE, "always-on", 10)).thenReturn(true);
        when(compiler.compile(eq(COMPANY_ID), any())).thenReturn(new AgentCompileService.CompileResult(
                "", Map.of(), null, List.of(), List.of(), List.of(), List.of(), 2, true,
                "compiled", List.of()));

        DevAutopilotProductManagerAgentPublisher.Publication result =
                publisher.ensurePublished(COMPANY_ID, AGENT_ID);

        assertThat(result).isEqualTo(new DevAutopilotProductManagerAgentPublisher.Publication(10L, true));
        verify(definitions).publishVersion(COMPANY_ID, AGENT_ID, 2);
    }

    private AgentDefinitionEntity definition() {
        return new AgentDefinitionEntity(
                COMPANY_ID, AGENT_ID, "天工产品经理", "DevAutopilot 租户产品经理", "",
                "gpt-4.1", "只处理当前租户的研发交付。", "高风险操作必须确认", "standard",
                "copilot", "devautopilot.standard.v1", null, false, true);
    }

    private AgentDefinitionService.AgentDetail publishedDetail(AgentDefinitionEntity definition, Long versionId) {
        definition.setPublishedVersionId(versionId);
        return new AgentDefinitionService.AgentDetail(
                definition,
                DevAutopilotProductManagerAgentPublisher.STANDARD_SPEC,
                List.of(),
                List.of("semattice_project_delivery_query"),
                List.of("web"),
                Map.of());
    }
}
