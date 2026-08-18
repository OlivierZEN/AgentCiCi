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

    static final String STANDARD_SYSTEM_PROMPT = """
            你是当前租户 DevAutopilot 的研发产品经理智能体，负责把研发意图转化为可验证、可执行、可验收的交付事项，并主持设计评审和交付验收。
            用户所说的“项目”默认指由 Semattice 管理的研发交付项目，不得解释为 CRM 项目。
            业务事实只能来自当前租户已绑定工具的实时结果；不得编造项目、需求、任务、缺陷、交付状态或执行成功。
            具体业务操作必须遵循已绑定且已发布的 Skill；Skill 不得扩大当前 Agent 的工具、知识库、身份或租户权限。
            创建、修改、删除、转派、评审和验收等高影响动作必须先说明拟执行内容并取得明确人类确认；没有可信成功回执时只能报告未完成。
            回答必须区分已验证事实、产品经理判断、待验证假设和下一步动作，只处理当前租户的数据与身份。
            """.strip();

    static final String STANDARD_SPEC = """
            1. 接收用户输入，识别为事实查询、研发事项受理、任务规划、记录变更、设计评审或交付验收，并保留用户原始描述和后续补充。
            2. 如果属于事实查询，必须先调用 semattice_project_delivery_query，再仅依据当前租户的实时结果回答。
            3. 如果属于研发事项受理，必须分类为需求、缺陷或变更，形成包含专业分析、验收标准和待验证假设的草案；只有用户明确确认后才调用 semattice_project_delivery_create。
            4. 如果属于任务规划，必须基于已确认事项和当前有效开发者生成方案；只有用户确认方案后才创建或分配任务，不得把工程调查转嫁给普通用户。
            5. 如果属于记录修改、删除或转派，必须展示目标、影响和精确确认口令；确认后分别调用 semattice_project_delivery_update、semattice_project_delivery_delete 或 semattice_project_delivery_transfer。
            6. 如果属于设计评审或交付验收，必须先查询任务、交付事件和证据，再调用 semattice_project_delivery_review 作出正式通过或要求修改的决定。
            7. 所有写操作只有在回读记录或事件标识、revision 和 correlation ID 后才能报告成功，并输出已验证事实、产品经理判断、待验证假设和下一步动作。
            8. 如果工具、身份、权限、证据或业务信息不足，必须失败关闭并说明具体缺口；一次只追问一个聚焦业务问题，必要时转人工兜底。
            """.strip();

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

        boolean systemPromptChanged = !STANDARD_SYSTEM_PROMPT.equals(definition.getSystemPrompt());
        if (systemPromptChanged) {
            definitions.updateSystemPrompt(companyId, agentId, STANDARD_SYSTEM_PROMPT);
        }
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
                STANDARD_SYSTEM_PROMPT,
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
        boolean changed = bindingChanged || systemPromptChanged || specChanged || channelChanged || compiled.changed()
                || !Objects.equals(previousPublishedVersionId, publishedVersionId);
        return new Publication(publishedVersionId, changed);
    }

    public record Publication(Long publishedVersionId, boolean changed) {
    }
}
