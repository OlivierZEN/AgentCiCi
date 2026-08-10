package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.service.AgentCompileService;
import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.agent.service.AgentSkillBindingService;
import com.codehouse.ciciassistant.skill.service.SkillDefinitionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publishes the tenant-local product-manager Agent installed by the signed DevAutopilot template. */
@Service
public class DevAutopilotProductManagerAgentPublisher {
    static final String DELIVERY_SKILL_CODE = "semattice-project-delivery-management";

    static final String STANDARD_SPEC = """
            你是本租户的研发产品经理智能体，负责研发需求澄清、项目查询、任务规划、设计评审和交付验收。
            用户所说的“项目”默认指 DevAutopilot 中由 Semattice 管理的研发交付项目，不得解释为 CRM 项目。
            业务事实只能通过已绑定的 Semattice 研发交付工具读取或写入，不得编造项目、需求、任务或交付状态。
            用户表达创建项目的意图时，必须先调用受控创建工具生成草案并补齐必要信息；只有收到规定的精确确认指令后才能执行创建。
            创建、变更、评审和验收等高影响操作必须先展示明确方案并取得人类确认。
            只能处理当前租户的数据和身份，不能访问、推断或复用其他租户的资源。
            """;

    private final AgentDefinitionService definitions;
    private final AgentCompileService compiler;
    private final AgentSkillBindingService skillBindings;
    private final SkillDefinitionService skills;

    public DevAutopilotProductManagerAgentPublisher(AgentDefinitionService definitions,
                                                     AgentCompileService compiler,
                                                     AgentSkillBindingService skillBindings,
                                                     SkillDefinitionService skills) {
        this.definitions = definitions;
        this.compiler = compiler;
        this.skillBindings = skillBindings;
        this.skills = skills;
    }

    @Transactional
    public Publication ensurePublished(String companyId, String agentId) {
        AgentDefinitionService.AgentDetail detail = definitions.get(companyId, agentId);
        AgentDefinitionEntity definition = detail.definition();
        Long previousPublishedVersionId = definition.getPublishedVersionId();
        skills.ensurePublishedPlatformSkillVersion(companyId, DELIVERY_SKILL_CODE);
        boolean bindingChanged = skillBindings.ensureBinding(
                companyId, agentId, DELIVERY_SKILL_CODE, "always-on", 10);

        String specText = STANDARD_SPEC;
        boolean specChanged = !STANDARD_SPEC.equals(detail.specText());
        if (specChanged) {
            definitions.updateSpec(companyId, agentId, STANDARD_SPEC);
        }

        List<String> channels = new ArrayList<>(detail.channels() == null ? List.of() : detail.channels());
        boolean channelChanged = false;
        if (!channels.contains("web")) {
            channels.add("web");
            channelChanged = true;
            definitions.replaceBindings(companyId, agentId, new AgentDefinitionService.ReplaceBindingsCommand(
                    detail.knowledgeBaseIds(), detail.toolIds(), channels));
        }

        AgentCompileService.CompileResult compiled = compiler.compile(companyId, new AgentCompileService.CompileCommand(
                agentId,
                definition.getName(),
                definition.getSummary(),
                definition.getGreeting(),
                definition.getModel(),
                definition.getSystemPrompt(),
                specText,
                channels,
                detail.knowledgeBaseIds(),
                detail.toolIds(),
                List.of(DELIVERY_SKILL_CODE),
                definition.getHandoffRule(),
                definition.getSafetyLevel(),
                definition.getExecutionMode(),
                definition.getVersionLabel()
        ));
        if (compiled.draftVersionNo() == null) {
            throw new IllegalStateException("DevAutopilot product-manager Agent did not produce a publishable version");
        }
        definitions.publishVersion(companyId, agentId, compiled.draftVersionNo());
        Long publishedVersionId = definitions.get(companyId, agentId).definition().getPublishedVersionId();
        if (publishedVersionId == null) {
            throw new IllegalStateException("DevAutopilot product-manager Agent was not published");
        }
        boolean changed = bindingChanged || specChanged || channelChanged || compiled.changed()
                || !Objects.equals(previousPublishedVersionId, publishedVersionId);
        return new Publication(publishedVersionId, changed);
    }

    public record Publication(Long publishedVersionId, boolean changed) {
    }
}
