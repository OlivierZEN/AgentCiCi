package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentAccessGrantEntity;
import com.codehouse.ciciassistant.agent.domain.AgentAccessGrantRepository;
import com.codehouse.ciciassistant.agent.domain.AgentChannelBindingEntity;
import com.codehouse.ciciassistant.agent.domain.AgentChannelBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentKnowledgeBindingEntity;
import com.codehouse.ciciassistant.agent.domain.AgentKnowledgeBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentPublishConfigEntity;
import com.codehouse.ciciassistant.agent.domain.AgentPublishConfigRepository;
import com.codehouse.ciciassistant.agent.domain.AgentSpecEntity;
import com.codehouse.ciciassistant.agent.domain.AgentSpecRepository;
import com.codehouse.ciciassistant.agent.domain.AgentToolBindingEntity;
import com.codehouse.ciciassistant.agent.domain.AgentToolBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.common.util.AvatarDataUrlValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.tool.service.ToolNameNormalizer;
import com.codehouse.ciciassistant.tool.tavily.TavilyToolService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentDefinitionService {

    private static final Pattern AGENT_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,63}$");

    private static final String DEFAULT_HANDOFF_RULE =
            "当命中知识不足、置信度较低或触发高风险操作时，转交人工。";

    private record BuiltinAgentSeed(
            String agentId,
            String name,
            String summary,
            String greeting,
            String model,
            String systemPrompt,
            boolean builtin,
            String executionMode,
            String specText,
            List<String> toolIds,
            List<String> channels
    ) {
    }

    /**
     * Aligns persisted agents with runtime / skill defaults ({@code cici-system}, {@code sales-agent},
     * {@code approval-agent}). Idempotent per company.
     */
    private static final List<BuiltinAgentSeed> BUILTIN_AGENTS = List.of(
            new BuiltinAgentSeed(
                    "cici-system",
                    "思思（CiCi）",
                    "系统内置默认智能体，常驻系统入口，可承接多渠道会话并复用统一知识与动作策略。",
                    "你好，我是思思（CiCi），作为系统内置默认智能体，可以帮你完成通用问答、知识检索和协作分流。",
                    "gpt-4.1",
                    "你是企业内部可执行 Agent。先判断用户请求类型，再决定是直接回答、检索知识库还是调用业务工具；不允许编造制度、价格或承诺。",
                    true,
                    "copilot",
                    String.join(
                            "\n",
                            List.of(
                                    "你是系统内置默认智能体思思（CiCi）。",
                                    "优先承接通用问答、多渠道入口消息和跨场景协作请求。",
                                    "先判断是知识问答、业务查询还是待办协作，再决定检索知识库还是调用业务工具；遇到需要事实、记录或对象结构的场景，优先主动调用可用工具。",
                                    "当请求命中高风险操作、权限边界不清或需要人工审批时，必须转人工或升级确认。",
                                    "输出保持专业、清晰，并给出下一步建议。")),
                    List.of(
                            "rag-search",
                            CloudccOpenApiService.toolName(),
                            CloudccOpenApiService.toolNameGetStandardObjects(),
                            CloudccOpenApiService.toolNameGetCustomObjects(),
                            CloudccOpenApiService.toolNameGetObjectFields(),
                            ToolNameNormalizer.GET_PENDING_APPROVALS,
                            TavilyToolService.TOOL_SEARCH,
                            TavilyToolService.TOOL_EXTRACT),
                    List.of("wechat", "dingtalk", "feishu", "web")),
            new BuiltinAgentSeed(
                    "sales-agent",
                    "售前跟进 Agent",
                    "面向销售和售前团队，负责客户问答、报价建议和人工转交。",
                    "你好，我是售前跟进 Agent，可以帮你准备产品答复、报价说明和下一步动作。",
                    "gpt-4.1",
                    "你是企业内部可执行 Agent。先判断用户请求类型，再决定是直接回答、检索知识库还是调用业务工具；不允许编造制度、价格或承诺。",
                    false,
                    "copilot",
                    String.join(
                            "\n",
                            List.of(
                                    "你是售前跟进 Agent，服务销售和售前团队。",
                                    "先判断请求是产品问答、报价请求还是实施咨询。",
                                    "产品问答优先检索知识库，报价请求需要查询客户档案后再调用报价工具。",
                                    "若知识命中低于 0.7，或涉及价格承诺、实施排期，必须转人工确认。",
                                    "最后输出结论、依据、推荐动作。")),
                    List.of("rag-search", CloudccOpenApiService.toolName(), ToolNameNormalizer.QUOTE_GENERATOR_LEGACY),
                    List.of("wechat", "dingtalk")),
            new BuiltinAgentSeed(
                    "approval-agent",
                    "审批推进 Agent",
                    "聚焦审批待办、催办和风险提醒，适合内部协同。",
                    "你好，我可以帮你汇总待审批事项、定位卡点并生成催办建议。",
                    "gpt-4.1",
                    "你是企业内部可执行 Agent。先判断用户请求类型，再决定是直接回答、检索知识库还是调用业务工具；不允许编造制度、价格或承诺。",
                    false,
                    "auto",
                    String.join(
                            "\n",
                            List.of(
                                    "你是审批推进 Agent，负责识别审批卡点并输出催办建议。",
                                    "先拉取审批待办，再结合知识库中的流程规则判断是否存在超时风险。",
                                    "如遇跨部门审批或高风险异常，调用 MCP 工作流创建协同提醒。",
                                    "输出需包含当前状态、风险判断、催办对象和建议动作。")),
                    List.of("rag-search", ToolNameNormalizer.GET_PENDING_APPROVALS, ToolNameNormalizer.MCP_WORKFLOW_WILDCARD),
                    List.of("dingtalk", "feishu")),
            new BuiltinAgentSeed(
                    "after-sales-agent",
                    "售后服务 Agent",
                    "面向企业微信微信客服客户会话，基于已授权知识库回答售后咨询并在不确定时引导人工接管。",
                    "您好，我是售后服务 Agent。请描述您遇到的问题，我会先根据服务知识库给出处理建议。",
                    "gpt-4.1",
                    "你是面向外部客户的售后服务 Agent。只能基于已授权知识库和客户本轮文字描述沟通，不查询 CRM、订单、客户档案或其他业务系统；如果知识库没有依据，必须说明需要人工客服进一步核实。",
                    true,
                    "copilot",
                    String.join(
                            "\n",
                            List.of(
                                    "你是售后服务 Agent，主要服务企业微信「微信客服」里的外部客户。",
                                    "优先检索售后知识库、FAQ、产品手册、质保政策、物流说明、退换修流程和服务话术。",
                                    "当前阶段不接 CRM、OMS、ERP、工单或订单系统，不得声称已经查询客户、订单、物流或工单实时数据。",
                                    "客户提供订单号、手机号、序列号时，只能作为人工核实时的上下文记录；若问题需要实时业务数据，明确建议转人工处理。",
                                    "回答要短、清楚、适合微信对话，并给出下一步需要客户补充的信息。")),
                    List.of("rag-search"),
                    List.of("wechat_kf", "wecom", "web")));

    private static final Map<String, Object> DEFAULT_PUBLISH_CONFIGS = Map.of(
            "feishu",
            Map.of(
                    "appId", "",
                    "appSecret", "",
                    "defaultAgentCode", "cici",
                    "pairingCommandHint", "配对",
                    "autoSyncSchedulesOnPublish", true));

    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentSpecRepository agentSpecRepository;
    private final AgentKnowledgeBindingRepository agentKnowledgeBindingRepository;
    private final AgentToolBindingRepository agentToolBindingRepository;
    private final AgentChannelBindingRepository agentChannelBindingRepository;
    private final AgentWorkflowVersionRepository agentWorkflowVersionRepository;
    private final AgentPublishConfigRepository agentPublishConfigRepository;
    private final AgentAccessGrantRepository agentAccessGrantRepository;
    private final ObjectMapper objectMapper;
    private final AgentWorkflowExecutionLogService workflowExecutionLogService;
    private final AgentRuntimeScheduleSyncService runtimeScheduleSyncService;
    private final AgentWorkflowSkillRefService agentWorkflowSkillRefService;
    private final AgentProductionReadinessService productionReadinessService;

    public AgentDefinitionService(AgentDefinitionRepository agentDefinitionRepository,
                                  AgentSpecRepository agentSpecRepository,
                                  AgentKnowledgeBindingRepository agentKnowledgeBindingRepository,
                                  AgentToolBindingRepository agentToolBindingRepository,
                                  AgentChannelBindingRepository agentChannelBindingRepository,
                                  AgentWorkflowVersionRepository agentWorkflowVersionRepository,
                                  AgentPublishConfigRepository agentPublishConfigRepository,
                                  AgentAccessGrantRepository agentAccessGrantRepository,
                                  ObjectMapper objectMapper,
                                  AgentWorkflowExecutionLogService workflowExecutionLogService,
                                  AgentRuntimeScheduleSyncService runtimeScheduleSyncService,
                                  AgentWorkflowSkillRefService agentWorkflowSkillRefService,
                                  AgentProductionReadinessService productionReadinessService) {
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentSpecRepository = agentSpecRepository;
        this.agentKnowledgeBindingRepository = agentKnowledgeBindingRepository;
        this.agentToolBindingRepository = agentToolBindingRepository;
        this.agentChannelBindingRepository = agentChannelBindingRepository;
        this.agentWorkflowVersionRepository = agentWorkflowVersionRepository;
        this.agentPublishConfigRepository = agentPublishConfigRepository;
        this.agentAccessGrantRepository = agentAccessGrantRepository;
        this.objectMapper = objectMapper;
        this.workflowExecutionLogService = workflowExecutionLogService;
        this.runtimeScheduleSyncService = runtimeScheduleSyncService;
        this.agentWorkflowSkillRefService = agentWorkflowSkillRefService;
        this.productionReadinessService = productionReadinessService;
    }

    /**
     * Idempotently creates built-in agent rows for the org (matches runtime agent ids such as {@code cici-system}).
     */
    public void warmupBuiltinAgents(String companyId) {
        ensureBuiltinAgents(companyId);
    }

    public List<AgentDefinitionEntity> list(String companyId) {
        ensureBuiltinAgents(companyId);
        return agentDefinitionRepository.findByCompanyIdAndEnabledTrueOrderByBuiltinDescUpdatedAtDesc(companyId);
    }

    public List<AgentListItem> listWithChannels(String companyId) {
        List<AgentDefinitionEntity> definitions = list(companyId);
        if (definitions.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> channelsByAgentId = new LinkedHashMap<>();
        for (AgentDefinitionEntity definition : definitions) {
            channelsByAgentId.put(definition.getAgentId(), new ArrayList<>());
        }
        agentChannelBindingRepository
                .findByCompanyIdAndAgentIdInAndEnabledTrueOrderByIdAsc(companyId, new ArrayList<>(channelsByAgentId.keySet()))
                .forEach(binding -> {
                    List<String> channels = channelsByAgentId.get(binding.getAgentId());
                    if (channels != null) {
                        channels.add(binding.getChannelId());
                    }
                });
        return definitions.stream()
                .map(definition -> new AgentListItem(definition, List.copyOf(channelsByAgentId.getOrDefault(definition.getAgentId(), List.of()))))
                .toList();
    }

    public AgentDetail get(String companyId, String agentId) {
        ensureBuiltinAgents(companyId);
        AgentDefinitionEntity definition = getDefinition(companyId, normalizeAgentId(agentId));
        String normalizedAgentId = definition.getAgentId();
        String specText = agentSpecRepository.findByCompanyIdAndAgentId(companyId, normalizedAgentId)
                .map(AgentSpecEntity::getSpecText)
                .orElse("");
        List<Long> knowledgeBaseIds = agentKnowledgeBindingRepository
                .findByCompanyIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(companyId, normalizedAgentId)
                .stream()
                .map(AgentKnowledgeBindingEntity::getKnowledgeBaseId)
                .toList();
        List<String> toolIds = agentToolBindingRepository
                .findByCompanyIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(companyId, normalizedAgentId)
                .stream()
                .map(AgentToolBindingEntity::getToolId)
                .toList();
        List<String> channels = agentChannelBindingRepository
                .findByCompanyIdAndAgentIdAndEnabledTrueOrderByIdAsc(companyId, normalizedAgentId)
                .stream()
                .map(AgentChannelBindingEntity::getChannelId)
                .toList();
        Map<String, Object> publishConfigs = loadPublishConfigs(companyId, normalizedAgentId);
        return new AgentDetail(definition, specText, knowledgeBaseIds, toolIds, channels, publishConfigs);
    }

    @Transactional
    public AgentDetail create(String companyId, CreateCommand command) {
        String agentId = normalizeAgentId(command.agentId());
        if (agentDefinitionRepository.existsByCompanyIdAndAgentId(companyId, agentId)) {
            throw new IllegalArgumentException("Agent already exists: " + agentId);
        }
        AgentDefinitionEntity created = new AgentDefinitionEntity(
                companyId,
                agentId,
                requireText(command.name(), "name"),
                trimToNull(command.summary()),
                trimToNull(command.greeting()),
                requireText(command.model(), "model"),
                trimToNull(command.systemPrompt()),
                trimToNull(command.handoffRule()),
                normalizeSafetyLevel(command.safetyLevel()),
                normalizeExecutionMode(command.executionMode()),
                trimToNull(command.versionLabel()),
                AvatarDataUrlValidator.normalizeNullableDataUrl(command.avatarBase64(), "avatarBase64"),
                trimToNull(command.ownerUserId()),
                command.builtin() != null && command.builtin(),
                command.enabled() == null || command.enabled()
        );
        agentDefinitionRepository.save(created);

        String specText = trimToNull(command.specText());
        agentSpecRepository.save(new AgentSpecEntity(companyId, agentId, specText == null ? "" : specText));

        replaceBindings(companyId, agentId, new ReplaceBindingsCommand(
                command.knowledgeBaseIds(),
                command.toolIds(),
                command.channels()
        ));
        replacePublishConfigs(companyId, agentId, command.publishConfigs());
        return get(companyId, agentId);
    }

    @Transactional
    public AgentDefinitionEntity updateDefinition(String companyId, String requestedAgentId, UpsertDefinitionCommand command) {
        ensureBuiltinAgents(companyId);
        AgentDefinitionEntity definition = getDefinition(companyId, normalizeAgentId(requestedAgentId));
        AvatarPatch avatarPatch = resolveAvatarPatch(command.avatarBase64());
        definition.update(
                requireText(command.name(), "name"),
                trimToNull(command.summary()),
                trimToNull(command.greeting()),
                requireText(command.model(), "model"),
                trimToNull(command.systemPrompt()),
                trimToNull(command.handoffRule()),
                normalizeSafetyLevel(command.safetyLevel()),
                normalizeExecutionMode(command.executionMode()),
                trimToNull(command.versionLabel()),
                avatarPatch.avatarBase64(),
                avatarPatch.replace(),
                command.enabled() == null || command.enabled()
        );
        return definition;
    }

    @Transactional
    public AgentDefinitionEntity updateSystemPrompt(String companyId, String requestedAgentId, String systemPrompt) {
        AgentDefinitionEntity definition = getDefinition(companyId, normalizeAgentId(requestedAgentId));
        definition.updateSystemPrompt(trimToNull(systemPrompt));
        return definition;
    }

    @Transactional
    public AgentDeleteResult deleteCustomAgent(String companyId, String requestedAgentId) {
        ensureBuiltinAgents(companyId);
        String agentId = normalizeAgentId(requestedAgentId);
        AgentDefinitionEntity definition = agentDefinitionRepository.findByCompanyIdAndAgentId(companyId, agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found: " + agentId));
        if (!definition.isEnabled()) {
            throw new ResourceNotFoundException("Agent not found: " + agentId);
        }
        if (definition.isBuiltin()) {
            throw new ConflictException("System built-in Agents cannot be deleted");
        }
        definition.markDeleted();
        return new AgentDeleteResult(
                definition.getAgentId(),
                definition.getName(),
                "Agent 已从构建列表隐藏；历史运行、审计、OpenAPI 调用和版本证据仍会保留。");
    }

    @Transactional
    public AgentSpecEntity updateSpec(String companyId, String requestedAgentId, String specText) {
        ensureBuiltinAgents(companyId);
        String agentId = normalizeAgentId(requestedAgentId);
        getDefinition(companyId, agentId);
        Optional<AgentSpecEntity> found = agentSpecRepository.findByCompanyIdAndAgentId(companyId, agentId);
        if (found.isPresent()) {
            AgentSpecEntity entity = found.get();
            entity.updateSpecText(specText == null ? "" : specText.trim());
            return entity;
        }
        return agentSpecRepository.save(new AgentSpecEntity(companyId, agentId, specText == null ? "" : specText.trim()));
    }

    @Transactional
    public AgentBindings replaceBindings(String companyId, String requestedAgentId, ReplaceBindingsCommand command) {
        ensureBuiltinAgents(companyId);
        String agentId = normalizeAgentId(requestedAgentId);
        getDefinition(companyId, agentId);

        agentKnowledgeBindingRepository.deleteByCompanyIdAndAgentId(companyId, agentId);
        agentKnowledgeBindingRepository.flush();
        agentToolBindingRepository.deleteByCompanyIdAndAgentId(companyId, agentId);
        agentToolBindingRepository.flush();
        agentChannelBindingRepository.deleteByCompanyIdAndAgentId(companyId, agentId);
        agentChannelBindingRepository.flush();

        List<Long> knowledgeBaseIds = distinctLongs(command.knowledgeBaseIds());
        for (int i = 0; i < knowledgeBaseIds.size(); i++) {
            agentKnowledgeBindingRepository.save(new AgentKnowledgeBindingEntity(companyId, agentId, knowledgeBaseIds.get(i), i + 1, true));
        }

        List<String> toolIds = ToolNameNormalizer.canonicalizeAll(distinctStrings(command.toolIds()));
        for (int i = 0; i < toolIds.size(); i++) {
            agentToolBindingRepository.save(new AgentToolBindingEntity(companyId, agentId, toolIds.get(i), i + 1, true));
        }

        List<String> channels = distinctStrings(command.channels()).stream()
                .map(this::normalizeChannel)
                .toList();
        for (String channel : channels) {
            agentChannelBindingRepository.save(new AgentChannelBindingEntity(companyId, agentId, channel, true));
        }

        return new AgentBindings(knowledgeBaseIds, toolIds, channels);
    }

    @Transactional
    public Map<String, Object> replacePublishConfigs(String companyId, String requestedAgentId, Map<String, Object> publishConfigs) {
        ensureBuiltinAgents(companyId);
        String agentId = normalizeAgentId(requestedAgentId);
        getDefinition(companyId, agentId);

        List<String> channels = distinctStrings(new ArrayList<>(publishConfigs == null ? List.of() : publishConfigs.keySet()));
        for (String channel : channels) {
            String normalizedChannel = normalizeChannel(channel);
            Object configValue = publishConfigs.get(channel);
            String configJson = toJson(configValue == null ? Map.of() : configValue);
            Optional<AgentPublishConfigEntity> found = agentPublishConfigRepository.findByCompanyIdAndAgentIdAndChannelId(
                    companyId,
                    agentId,
                    normalizedChannel
            );
            if (found.isPresent()) {
                found.get().updateConfigJson(configJson);
            } else {
                agentPublishConfigRepository.save(new AgentPublishConfigEntity(companyId, agentId, normalizedChannel, configJson));
            }
        }
        return loadPublishConfigs(companyId, agentId);
    }

    public List<AgentWorkflowVersionEntity> listVersions(String companyId, String requestedAgentId) {
        ensureBuiltinAgents(companyId);
        String agentId = normalizeAgentId(requestedAgentId);
        getDefinition(companyId, agentId);
        return agentWorkflowVersionRepository.findByCompanyIdAndAgentIdOrderByVersionNoDesc(companyId, agentId);
    }

    @Transactional
    public AgentWorkflowVersionEntity publishVersion(String companyId, String requestedAgentId, Integer versionNo) {
        ensureBuiltinAgents(companyId);
        String agentId = normalizeAgentId(requestedAgentId);
        AgentDefinitionEntity definition = getDefinition(companyId, agentId);
        productionReadinessService.requirePublishReady(companyId, agentId, versionNo);
        AgentWorkflowVersionEntity target = agentWorkflowVersionRepository.findByCompanyIdAndAgentIdAndVersionNo(companyId, agentId, versionNo)
                .orElseThrow(() -> new IllegalArgumentException("Agent version not found: " + versionNo));

        agentWorkflowVersionRepository.findByCompanyIdAndAgentIdAndPublishStatus(companyId, agentId, "PUBLISHED")
                .ifPresent(previous -> previous.setPublishStatus("ARCHIVED"));
        target.setPublishStatus("PUBLISHED");
        definition.setPublishedVersionId(target.getId());
        agentWorkflowSkillRefService.ensureWorkflowSkillRefs(companyId, agentId, target);
        try {
            workflowExecutionLogService.append(
                    companyId,
                    agentId,
                    target.getId(),
                    target.getVersionNo(),
                    AgentWorkflowExecutionLogService.SOURCE_MANUAL_PUBLISH,
                    AgentWorkflowExecutionLogService.STATUS_SUCCESS,
                    0,
                    "Published workflow v" + target.getVersionNo() + " as active runtime version.",
                    null);
        } catch (RuntimeException ignored) {
            // observability must not block publish
        }
        if (shouldAutoSyncSchedulesOnPublish(companyId, agentId)) {
            try {
                runtimeScheduleSyncService.syncFromCompiledVersion(companyId, agentId, target.getId());
            } catch (RuntimeException ignored) {
                // schedule sync must not block publish
            }
        }
        return target;
    }

    @Transactional
    public AgentWorkflowVersionEntity rollbackVersion(String companyId, String requestedAgentId, Integer versionNo) {
        return publishVersion(companyId, requestedAgentId, versionNo);
    }

    private boolean shouldAutoSyncSchedulesOnPublish(String companyId, String agentId) {
        return agentPublishConfigRepository.findByCompanyIdAndAgentIdAndChannelId(companyId, agentId, "feishu")
                .map(AgentPublishConfigEntity::getConfigJson)
                .map(this::fromJsonObject)
                .map(raw -> raw.get("autoSyncSchedulesOnPublish"))
                .map(flag -> flag instanceof Boolean ? (Boolean) flag : "true".equalsIgnoreCase(String.valueOf(flag)))
                .orElse(true);
    }

    private void ensureBuiltinAgents(String companyId) {
        for (BuiltinAgentSeed seed : BUILTIN_AGENTS) {
            if (agentDefinitionRepository.existsByCompanyIdAndAgentId(companyId, seed.agentId())) {
                continue;
            }
            create(
                    companyId,
                    new CreateCommand(
                            seed.agentId(),
                            seed.name(),
                            seed.summary(),
                            seed.greeting(),
                            seed.model(),
                            seed.systemPrompt(),
                            DEFAULT_HANDOFF_RULE,
                            "BALANCED",
                            seed.executionMode().toUpperCase(Locale.ROOT),
                            "v0.1",
                            null,
                            null,
                            seed.builtin(),
                            true,
                            seed.specText(),
                            List.of(),
                            seed.toolIds(),
                            seed.channels(),
                            DEFAULT_PUBLISH_CONFIGS));
            ensureOrgDefaultRunGrants(companyId, seed.agentId());
        }
    }

    private void ensureOrgDefaultRunGrants(String companyId, String agentId) {
        List<AgentAccessGrantEntity> existing = agentAccessGrantRepository.findByCompanyIdAndAgentIdAndStatus(
                companyId,
                agentId,
                AgentAccessGrantEntity.STATUS_ACTIVE);
        for (String permission : List.of("VIEW", "RUN")) {
            boolean present = existing.stream().anyMatch(item ->
                    "COMPANY".equals(item.getPrincipalType()) && permission.equals(item.getPermission()));
            if (!present) {
                agentAccessGrantRepository.save(new AgentAccessGrantEntity(
                        companyId,
                        agentId,
                        "COMPANY",
                        companyId,
                        permission,
                        "DEFAULT_POLICY",
                        null,
                        null));
            }
        }
    }

    private AgentDefinitionEntity getDefinition(String companyId, String agentId) {
        return agentDefinitionRepository.findByCompanyIdAndAgentIdAndEnabledTrue(companyId, agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found: " + agentId));
    }

    private Map<String, Object> loadPublishConfigs(String companyId, String agentId) {
        Map<String, Object> result = new LinkedHashMap<>();
        agentPublishConfigRepository.findByCompanyIdAndAgentIdOrderByChannelIdAsc(companyId, agentId)
                .forEach(item -> result.put(item.getChannelId(), fromJsonObject(item.getConfigJson())));
        return result;
    }

    private String normalizeAgentId(String raw) {
        String text = safe(raw).trim().toLowerCase(Locale.ROOT);
        if (!AGENT_ID_PATTERN.matcher(text).matches()) {
            throw new IllegalArgumentException("agentId must match ^[a-z0-9][a-z0-9-]{1,63}$");
        }
        return text;
    }

    private String normalizeSafetyLevel(String safetyLevel) {
        String text = safe(safetyLevel).trim().toUpperCase(Locale.ROOT);
        if ("STRICT".equals(text)) {
            return "STRICT";
        }
        return "BALANCED";
    }

    private String normalizeExecutionMode(String executionMode) {
        String text = safe(executionMode).trim().toUpperCase(Locale.ROOT);
        if ("AUTO".equals(text)) {
            return "AUTO";
        }
        return "COPILOT";
    }

    private AvatarPatch resolveAvatarPatch(String rawAvatarBase64) {
        if (rawAvatarBase64 == null) {
            return new AvatarPatch(false, null);
        }
        String normalized = AvatarDataUrlValidator.normalizeNullableDataUrl(rawAvatarBase64, "avatarBase64");
        return new AvatarPatch(true, normalized);
    }

    private String normalizeChannel(String raw) {
        String text = safe(raw).trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            throw new IllegalArgumentException("channelId cannot be blank");
        }
        return text;
    }

    private List<Long> distinctLongs(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Long value : values) {
            if (value == null || value <= 0L) {
                continue;
            }
            if (!result.contains(value)) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private List<String> distinctStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized == null) {
                continue;
            }
            if (!result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private String requireText(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        String text = safe(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize publish config", e);
        }
    }

    private Map<String, Object> fromJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    public record AgentDetail(
            AgentDefinitionEntity definition,
            String specText,
            List<Long> knowledgeBaseIds,
            List<String> toolIds,
            List<String> channels,
            Map<String, Object> publishConfigs
    ) {
    }

    public record AgentListItem(
            AgentDefinitionEntity definition,
            List<String> channels
    ) {
    }

    public record AgentBindings(
            List<Long> knowledgeBaseIds,
            List<String> toolIds,
            List<String> channels
    ) {
    }

    public record AgentDeleteResult(
            String agentId,
            String name,
            String retentionMessage
    ) {
    }

    private record AvatarPatch(boolean replace, String avatarBase64) {
    }

    public record CreateCommand(
            String agentId,
            String name,
            String summary,
            String greeting,
            String model,
            String systemPrompt,
            String handoffRule,
            String safetyLevel,
            String executionMode,
            String versionLabel,
            String avatarBase64,
            String ownerUserId,
            Boolean builtin,
            Boolean enabled,
            String specText,
            List<Long> knowledgeBaseIds,
            List<String> toolIds,
            List<String> channels,
            Map<String, Object> publishConfigs
    ) {
    }

    public record UpsertDefinitionCommand(
            String name,
            String summary,
            String greeting,
            String model,
            String systemPrompt,
            String handoffRule,
            String safetyLevel,
            String executionMode,
            String versionLabel,
            String avatarBase64,
            Boolean enabled
    ) {
    }

    public record ReplaceBindingsCommand(
            List<Long> knowledgeBaseIds,
            List<String> toolIds,
            List<String> channels
    ) {
    }
}
