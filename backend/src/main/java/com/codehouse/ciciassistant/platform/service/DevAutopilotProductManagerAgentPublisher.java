package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.service.AgentCompileService;
import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publishes the tenant-local product-manager Agent installed by the signed DevAutopilot template. */
@Service
public class DevAutopilotProductManagerAgentPublisher {
    static final String STANDARD_SPEC = """
            你是本租户的研发产品经理智能体，负责研发需求澄清、项目查询、任务规划、设计评审和交付验收。
            业务事实只能通过已绑定的 Semattice 研发交付工具读取或写入，不得编造项目、需求、任务或交付状态。
            创建、变更、评审和验收等高影响操作必须先展示明确方案并取得人类确认。
            只能处理当前租户的数据和身份，不能访问、推断或复用其他租户的资源。
            """;

    private final AgentDefinitionService definitions;
    private final AgentCompileService compiler;

    public DevAutopilotProductManagerAgentPublisher(AgentDefinitionService definitions,
                                                     AgentCompileService compiler) {
        this.definitions = definitions;
        this.compiler = compiler;
    }

    @Transactional
    public Publication ensurePublished(String companyId, String agentId) {
        AgentDefinitionService.AgentDetail detail = definitions.get(companyId, agentId);
        AgentDefinitionEntity definition = detail.definition();
        if (definition.getPublishedVersionId() != null) {
            return new Publication(definition.getPublishedVersionId(), false);
        }

        String specText = detail.specText() == null || detail.specText().isBlank()
                ? STANDARD_SPEC
                : detail.specText();
        if (detail.specText() == null || detail.specText().isBlank()) {
            definitions.updateSpec(companyId, agentId, specText);
        }

        List<String> channels = new ArrayList<>(detail.channels() == null ? List.of() : detail.channels());
        if (!channels.contains("web")) {
            channels.add("web");
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
                List.of(),
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
        return new Publication(publishedVersionId, true);
    }

    public record Publication(Long publishedVersionId, boolean changed) {
    }
}
