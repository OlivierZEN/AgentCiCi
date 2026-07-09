package com.codehouse.ciciassistant.customer.service;

import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchSnapshotEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchSnapshotRepository;
import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import com.codehouse.ciciassistant.skill.service.SkillDefinitionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerWorkbenchService {

    public static final String SKILL_CODE = "customer-interaction-workbench";

    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

    private final CustomerWorkbenchSnapshotRepository snapshotRepository;
    private final CustomerInteractionEventRepository eventRepository;
    private final CustomerWorkbenchRecommendationRepository recommendationRepository;
    private final CloudccAccessTokenService cloudccAccessTokenService;
    private final SkillDefinitionService skillDefinitionService;
    private final ObjectMapper objectMapper;

    public CustomerWorkbenchService(CustomerWorkbenchSnapshotRepository snapshotRepository,
                                    CustomerInteractionEventRepository eventRepository,
                                    CustomerWorkbenchRecommendationRepository recommendationRepository,
                                    CloudccAccessTokenService cloudccAccessTokenService,
                                    SkillDefinitionService skillDefinitionService,
                                    ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.eventRepository = eventRepository;
        this.recommendationRepository = recommendationRepository;
        this.cloudccAccessTokenService = cloudccAccessTokenService;
        this.skillDefinitionService = skillDefinitionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<Map<String, Object>> listAccounts(String orgId, String userId) {
        ensureDemoData(orgId, userId);
        return snapshotRepository.findByOrgIdOrderByUpdatedAtDesc(orgId).stream()
                .map(item -> accountListView(orgId, item))
                .toList();
    }

    @Transactional
    public Map<String, Object> accountDetail(String orgId, String userId, String accountId) {
        ensureDemoData(orgId, userId);
        CustomerWorkbenchSnapshotEntity snapshot = requireSnapshot(orgId, accountId);
        Map<String, Object> view = new LinkedHashMap<>(snapshotView(snapshot));
        view.put("timeline", timeline(orgId, accountId));
        view.put("recommendations", recommendations(orgId, accountId));
        view.put("crmConnection", crmConnectionView(orgId, userId));
        return view;
    }

    @Transactional
    public List<Map<String, Object>> timeline(String orgId, String accountId) {
        return eventRepository.findByOrgIdAndCrmAccountIdOrderByOccurredAtDesc(orgId, accountId).stream()
                .map(this::eventView)
                .toList();
    }

    @Transactional
    public List<Map<String, Object>> recommendations(String orgId, String accountId) {
        return recommendationRepository.findByOrgIdAndCrmAccountIdOrderByUpdatedAtDesc(orgId, accountId).stream()
                .map(this::recommendationView)
                .toList();
    }

    @Transactional
    public Map<String, Object> acceptRecommendation(String orgId, String publicId) {
        CustomerWorkbenchRecommendationEntity recommendation = requireRecommendation(orgId, publicId);
        recommendation.accept();
        return recommendationView(recommendationRepository.save(recommendation));
    }

    @Transactional
    public Map<String, Object> applyRecommendation(String orgId, String userId, String publicId) {
        CustomerWorkbenchRecommendationEntity recommendation = requireRecommendation(orgId, publicId);
        if (!CustomerWorkbenchRecommendationEntity.STATUS_ACCEPTED.equals(recommendation.getStatus())
                && !CustomerWorkbenchRecommendationEntity.STATUS_APPLIED.equals(recommendation.getStatus())) {
            recommendation.accept();
        }
        String simulatedCrmId = "demo-crm-" + recommendation.getRecommendationType().toLowerCase(Locale.ROOT)
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        recommendation.apply(simulatedCrmId);
        Map<String, Object> view = recommendationView(recommendationRepository.save(recommendation));
        view.put("writeMode", cloudccAccessTokenService.getSessionContext(orgId, userId).isPresent() ? "CRM_READY_SIMULATED" : "DEMO_SIMULATED");
        view.put("message", "建议已进入确认后的 CRM 落地流程；当前演示环境写入为可审计模拟结果。");
        return view;
    }

    @Transactional
    public Map<String, Object> assistant(String orgId, String userId, AssistantCommand command) {
        ensureDemoData(orgId, userId);
        String text = command == null || command.message() == null ? "" : command.message().trim();
        String accountId = command == null ? "" : blankToEmpty(command.accountId());
        CustomerWorkbenchSnapshotEntity snapshot = accountId.isBlank()
                ? snapshotRepository.findByOrgIdOrderByUpdatedAtDesc(orgId).stream().findFirst().orElseThrow()
                : requireSnapshot(orgId, accountId);
        Map<String, Object> snapshotMap = readMap(snapshot.getSnapshotJson());
        String lower = text.toLowerCase(Locale.ROOT);
        String reply;
        String action = "NONE";
        Map<String, Object> actionPayload = new LinkedHashMap<>();
        if (text.contains("下一个") || lower.contains("next")) {
            List<CustomerWorkbenchSnapshotEntity> accounts = snapshotRepository.findByOrgIdOrderByUpdatedAtDesc(orgId);
            int index = Math.max(0, accounts.indexOf(snapshot));
            CustomerWorkbenchSnapshotEntity next = accounts.get((index + 1) % accounts.size());
            reply = "已切换到 " + next.getAccountName() + "。我会优先看最近互动、风险和可落地建议。";
            action = "SWITCH_ACCOUNT";
            actionPayload.put("accountId", next.getCrmAccountId());
        } else if (text.contains("风险")) {
            reply = snapshot.getAccountName() + " 当前风险数为 " + snapshot.getRiskCount()
                    + "。主要风险：" + joinList(snapshotMap.get("risks")) + "。建议先处理置信度最高的 CRM 落地建议。";
        } else if (text.contains("老客户") || text.contains("经营") || text.contains("续约")) {
            reply = snapshot.getAccountName() + " 的老客户经营重点：健康度 " + snapshot.getHealthScore()
                    + "，续约/增购线索为 " + joinList(snapshotMap.get("existingCustomerSignals")) + "。";
        } else if (text.contains("新客户") || text.contains("推进") || text.contains("商机")) {
            reply = snapshot.getAccountName() + " 的新客户推进重点：推进分 " + snapshot.getProgressScore()
                    + "，下一步建议：" + joinList(snapshotMap.get("nextActions")) + "。";
        } else if (text.contains("任务") || text.contains("跟进")) {
            reply = "我已整理出可创建的跟进任务建议。请在中间栏 CRM 落地建议中点击采纳，再确认写入 CRM。";
            action = "FOCUS_RECOMMENDATIONS";
        } else {
            reply = "我可以帮你总结互动、查看风险、切换客户、生成跟进任务建议，或分别分析新客户推进和老客户经营。";
        }
        return mapOf(
                "reply", reply,
                "action", action,
                "actionPayload", actionPayload,
                "account", accountListView(orgId, snapshot),
                "crmConnection", crmConnectionView(orgId, userId)
        );
    }

    @Transactional
    public Map<String, Object> seedDemoData(String orgId, String userId, boolean reset) {
        if (reset) {
            recommendationRepository.deleteAll(recommendationRepository.findAll().stream()
                    .filter(item -> orgId.equals(item.getOrgId()))
                    .toList());
            eventRepository.deleteAll(eventRepository.findAll().stream()
                    .filter(item -> orgId.equals(item.getOrgId()))
                    .toList());
            snapshotRepository.deleteAll(snapshotRepository.findAll().stream()
                    .filter(item -> orgId.equals(item.getOrgId()))
                    .toList());
        }
        ensureDemoData(orgId, userId);
        return mapOf(
                "accounts", snapshotRepository.countByOrgId(orgId),
                "events", eventRepository.countByOrgId(orgId),
                "recommendations", recommendationRepository.countByOrgId(orgId)
        );
    }

    private void ensureDemoData(String orgId, String userId) {
        skillDefinitionService.ensurePhaseOneDefaults(orgId);
        if (snapshotRepository.countByOrgId(orgId) > 0) {
            return;
        }
        List<DemoAccount> accounts = demoAccounts();
        String publicIdPrefix = publicIdPrefix(orgId);
        for (DemoAccount account : accounts) {
            snapshotRepository.save(new CustomerWorkbenchSnapshotEntity(
                    publicIdPrefix + "_cw_" + account.id(),
                    orgId,
                    account.id(),
                    account.name(),
                    account.owner(),
                    account.segment(),
                    account.health(),
                    account.progress(),
                    account.risks().size(),
                    account.nextActions().size(),
                    toJson(mapOf(
                            "industry", account.industry(),
                            "contact", account.contact(),
                            "lastInteraction", account.lastInteraction(),
                            "stage", account.stage(),
                            "summary", account.summary(),
                            "risks", account.risks(),
                            "newCustomerSignals", account.newSignals(),
                            "existingCustomerSignals", account.existingSignals(),
                            "nextActions", account.nextActions(),
                            "tags", account.tags()
                    ))
            ));
            int i = 0;
            for (String interaction : account.interactions()) {
                i++;
                eventRepository.save(new CustomerInteractionEventEntity(
                        publicIdPrefix + "_cwe_" + account.id() + "_" + i,
                        orgId,
                        account.id(),
                        "contact-" + account.id(),
                        i % 3 == 0 ? "MEETING" : (i % 3 == 1 ? "WECHAT" : "PHONE"),
                        Instant.parse("2026-07-0" + Math.min(6, i + 1) + "T0" + Math.min(9, i + 1) + ":30:00Z"),
                        account.name() + "互动摘要 " + i,
                        interaction,
                        interaction,
                        account.segment().equals("RISK") ? "NEGATIVE" : "NEUTRAL",
                        toJson(account.tags()),
                        account.segment().equals("NEW") ? "NEW_CUSTOMER" : (account.segment().equals("EXISTING") ? "EXISTING_CUSTOMER" : "MIXED")
                ));
            }
            seedRecommendation(publicIdPrefix, orgId, account.id(), "CREATE_TASK", "创建下一次跟进任务",
                    "最近互动中出现明确待办，建议写入 CRM 任务并设置截止时间。",
                    0.91, mapOf("objectApiName", "Task", "subject", "跟进 " + account.name(), "accountId", account.id()));
            seedRecommendation(publicIdPrefix, orgId, account.id(), account.segment().equals("NEW") ? "CREATE_OPPORTUNITY" : "UPDATE_RISK",
                    account.segment().equals("NEW") ? "创建商机推进记录" : "更新客户经营风险",
                    account.segment().equals("NEW") ? "客户已出现预算、需求或决策链信号。" : "客户服务或续约信号需要主管关注。",
                    0.82, mapOf("accountId", account.id(), "source", "customer-interaction-workbench"));
        }
    }

    private void seedRecommendation(String publicIdPrefix,
                                    String orgId,
                                    String accountId,
                                    String type,
                                    String title,
                                    String rationale,
                                    double confidence,
                                    Map<String, Object> payload) {
        recommendationRepository.save(new CustomerWorkbenchRecommendationEntity(
                publicIdPrefix + "_cwr_" + accountId + "_" + type.toLowerCase(Locale.ROOT),
                orgId,
                accountId,
                type,
                title,
                rationale,
                BigDecimal.valueOf(confidence),
                toJson(payload)
        ));
    }

    private String publicIdPrefix(String orgId) {
        String normalized = orgId == null ? "" : orgId.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "org_unknown";
        }
        return "org_" + (normalized.length() > 12 ? normalized.substring(0, 12) : normalized);
    }

    private Map<String, Object> accountListView(String orgId, CustomerWorkbenchSnapshotEntity item) {
        Map<String, Object> snapshot = readMap(item.getSnapshotJson());
        return mapOf(
                "accountId", item.getCrmAccountId(),
                "name", item.getAccountName(),
                "owner", item.getOwnerName(),
                "segment", item.getSegment(),
                "healthScore", item.getHealthScore(),
                "progressScore", item.getProgressScore(),
                "riskCount", item.getRiskCount(),
                "nextActionCount", item.getNextActionCount(),
                "pendingRecommendationCount", recommendationRepository.countByOrgIdAndCrmAccountIdAndStatus(
                        orgId, item.getCrmAccountId(), CustomerWorkbenchRecommendationEntity.STATUS_PENDING),
                "lastInteraction", snapshot.getOrDefault("lastInteraction", ""),
                "stage", snapshot.getOrDefault("stage", ""),
                "tags", snapshot.getOrDefault("tags", List.of()),
                "updatedAt", item.getUpdatedAt().toString()
        );
    }

    private Map<String, Object> snapshotView(CustomerWorkbenchSnapshotEntity item) {
        Map<String, Object> snapshot = readMap(item.getSnapshotJson());
        snapshot.putAll(mapOf(
                "accountId", item.getCrmAccountId(),
                "name", item.getAccountName(),
                "owner", item.getOwnerName(),
                "segment", item.getSegment(),
                "healthScore", item.getHealthScore(),
                "progressScore", item.getProgressScore(),
                "riskCount", item.getRiskCount(),
                "nextActionCount", item.getNextActionCount()
        ));
        return snapshot;
    }

    private Map<String, Object> eventView(CustomerInteractionEventEntity item) {
        return mapOf(
                "eventId", item.getPublicId(),
                "accountId", item.getCrmAccountId(),
                "sourceType", item.getSourceType(),
                "occurredAt", item.getOccurredAt().toString(),
                "subject", item.getSubject(),
                "summary", item.getAiSummary(),
                "sentiment", item.getSentiment(),
                "intentTags", readList(item.getIntentTags()),
                "lifecycleArea", item.getLifecycleArea()
        );
    }

    private Map<String, Object> recommendationView(CustomerWorkbenchRecommendationEntity item) {
        return mapOf(
                "recommendationId", item.getPublicId(),
                "accountId", item.getCrmAccountId(),
                "type", item.getRecommendationType(),
                "title", item.getTitle(),
                "rationale", item.getRationale(),
                "confidence", item.getConfidence(),
                "status", item.getStatus(),
                "crmPayload", readMap(item.getCrmPayload()),
                "appliedCrmId", item.getAppliedCrmId(),
                "updatedAt", item.getUpdatedAt().toString()
        );
    }

    private Map<String, Object> crmConnectionView(String orgId, String userId) {
        boolean ready = cloudccAccessTokenService.getSessionContext(orgId, userId).isPresent();
        return mapOf(
                "ready", ready,
                "mode", ready ? "BOUND" : "DEMO",
                "label", ready ? "CloudCC CRM 已连接" : "演示模式"
        );
    }

    private CustomerWorkbenchSnapshotEntity requireSnapshot(String orgId, String accountId) {
        return snapshotRepository.findByOrgIdAndCrmAccountId(orgId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("客户不存在"));
    }

    private CustomerWorkbenchRecommendationEntity requireRecommendation(String orgId, String publicId) {
        return recommendationRepository.findByOrgIdAndPublicId(orgId, publicId)
                .orElseThrow(() -> new IllegalArgumentException("建议不存在"));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 序列化失败", ex);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_REF);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private List<Object> readList(String json) {
        Object value = readJson(json);
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String joinList(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return String.join("；", list.stream().map(String::valueOf).toList());
        }
        return "暂无明确信号";
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private List<DemoAccount> demoAccounts() {
        return List.of(
                demo("demo-account-001", "北京智造科技有限公司", "NEW", "张伟", "制造业", "李娜 技术负责人", "方案评审中", 72, 86,
                        "最近三次沟通围绕 MES 集成、实施周期和预算窗口展开。",
                        List.of("决策链未完全确认", "预算审批窗口较紧"),
                        List.of("MES 集成需求明确", "方案评审会已约定", "预算窗口在 7 月中旬"),
                        List.of("暂无存量经营信号"),
                        List.of("约技术评审复盘", "补齐决策链联系人", "创建商机产品明细"),
                        List.of("重点推进", "方案沟通", "预算窗口"),
                        List.of("微信沟通确认 MES 集成是首要关注点。", "电话回访中客户询问实施周期和费用拆分。", "方案评审会约定由技术和采购共同参与。")),
                demo("demo-account-002", "上海云链信息技术有限公司", "RISK", "李娜", "软件服务", "陈峰 客户经理", "服务风险中", 48, 58,
                        "客户连续反馈响应慢，存在续约延期风险。",
                        List.of("续约窗口临近", "服务响应满意度下降", "关键人态度转弱"),
                        List.of("有增购数据治理模块兴趣"),
                        List.of("续约窗口 45 天内", "服务问题需闭环", "可用客户成功拜访挽回"),
                        List.of("安排客户成功主管回访", "创建服务风险", "准备续约价值复盘"),
                        List.of("续约风险", "服务反馈", "主管关注"),
                        List.of("客户反馈上周问题未及时回复。", "电话中提到续约暂缓，需要先看到整改计划。", "售后会议记录显示数据同步问题已复现。")),
                demo("demo-account-003", "广州海创智联有限公司", "EXISTING", "王磊", "装备制造", "周倩 信息化总监", "增购识别", 82, 67,
                        "一期系统稳定运行，客户开始讨论售后服务和移动端扩展。",
                        List.of("移动端预算尚未确认"),
                        List.of("售后场景有扩展机会"),
                        List.of("使用满意度较高", "增购意向明确", "关键人关系稳定"),
                        List.of("准备增购方案", "邀请客户参加成功案例交流"),
                        List.of("健康客户", "增购机会", "关系稳定"),
                        List.of("微信中客户询问移动端巡检能力。", "季度回访确认一期上线效果稳定。", "客户希望看到同行案例。")),
                demo("demo-account-004", "深圳未来视界科技有限公司", "NEW", "陈晨", "高科技", "刘洋 采购经理", "竞品比较", 64, 74,
                        "客户正在比较两家竞品，重点关注总拥有成本和权限体系。",
                        List.of("竞品方案仍在评估", "采购只看价格缺少业务价值材料"),
                        List.of("权限治理需求强", "已有明确采购角色"),
                        List.of("暂无存量经营信号"),
                        List.of("补充 TCO 对比", "安排权限治理演示", "确认业务决策人"),
                        List.of("竞品对比", "权限关注", "待演示"),
                        List.of("客户要求补充权限体系说明。", "电话中明确正在比较竞品报价。", "采购希望本周拿到 TCO 对比。")),
                demo("demo-account-005", "杭州数智动力有限公司", "RISK", "刘洋", "数据服务", "郭敏 运营负责人", "响应放缓", 55, 52,
                        "最近两周互动频率明显下降，原计划试点被客户内部项目挤压。",
                        List.of("连续三次触达未回复", "试点优先级下降"),
                        List.of("数据质量痛点仍存在"),
                        List.of("老客户扩展动力不足"),
                        List.of("换用价值复盘话术", "请求主管协助触达", "降低试点启动门槛"),
                        List.of("响应放缓", "试点受阻", "需主管协助"),
                        List.of("微信消息两天未回复。", "电话中助理表示负责人在忙内部项目。", "上次会议确认数据质量问题仍未解决。")),
                demo("demo-account-006", "成都智云互联有限公司", "EXISTING", "周敏", "云服务", "马杰 CIO", "健康经营", 88, 70,
                        "客户使用稳定，对知识库和客服场景的扩展接受度高。",
                        List.of("预算需要 Q3 确认"),
                        List.of("客服知识场景可推进"),
                        List.of("健康度高", "关键人愿意共创", "续约风险低"),
                        List.of("准备客服场景 PoC", "沉淀成功案例", "维护 CIO 关系"),
                        List.of("健康客户", "扩展机会", "案例共创"),
                        List.of("客户称当前系统稳定。", "会议中 CIO 希望探索客服知识场景。", "客户愿意提供内部案例素材。")),
                demo("demo-account-007", "南京星河软件有限公司", "NEW", "张伟", "软件服务", "孙菲 销售经理", "初步接触", 60, 61,
                        "客户刚完成首次沟通，需求集中在销售过程管理和报表。",
                        List.of("决策链未知", "预算未确认"),
                        List.of("销售管理需求明确"),
                        List.of("暂无存量经营信号"),
                        List.of("安排需求澄清会", "确认预算和项目窗口"),
                        List.of("首次接触", "需求澄清", "报表关注"),
                        List.of("活动后首次沟通完成。", "客户关注销售漏斗和主管报表。", "尚未透露预算。")),
                demo("demo-account-008", "武汉联创节能科技有限公司", "EXISTING", "赵鹏", "能源", "何涛 总经理", "续约准备", 78, 66,
                        "合同还有 60 天到期，客户满意但希望降低运维成本。",
                        List.of("续约价格敏感"),
                        List.of("能源项目看板可增购"),
                        List.of("续约窗口明确", "总经理关系稳定", "运维成本是谈判点"),
                        List.of("准备续约价值报告", "列出运维降本证据"),
                        List.of("续约窗口", "价格敏感", "价值复盘"),
                        List.of("电话确认续约窗口为 60 天内。", "客户提到运维成本压力。", "总经理认可当前项目价值。")),
                demo("demo-account-009", "苏州精密制造集团", "STRATEGIC", "李娜", "制造业", "许强 副总裁", "战略客户经营", 84, 76,
                        "集团客户多部门并行推进，存在跨业务线协同机会。",
                        List.of("集团采购流程复杂", "多部门需求口径不一"),
                        List.of("集团级平台机会", "多业务线扩展"),
                        List.of("战略客户", "关系多点覆盖", "可进入集团规划"),
                        List.of("补齐权力地图", "组织集团级方案会", "拆分业务线机会"),
                        List.of("战略客户", "集团机会", "权力地图"),
                        List.of("副总裁提到集团统一平台规划。", "两个业务线分别提出不同诉求。", "采购流程需要集团审批。")),
                demo("demo-account-010", "青岛港航物流有限公司", "EXISTING", "王磊", "物流", "邓丽 运营总监", "服务改进", 69, 62,
                        "客户对派工效率认可，但投诉报表不够及时。",
                        List.of("投诉报表滞后", "运营团队希望看到整改节奏"),
                        List.of("服务看板增购机会"),
                        List.of("核心流程使用稳定", "局部服务体验需改善"),
                        List.of("创建服务改进任务", "准备服务看板方案"),
                        List.of("服务风险", "看板机会", "运营关注"),
                        List.of("客户反馈投诉报表滞后。", "运营总监认可派工效率提升。", "希望每周看到整改节奏。")),
                demo("demo-account-011", "宁波启明医疗器械有限公司", "NEW", "陈晨", "医疗器械", "郑琳 信息主管", "合规评估", 68, 71,
                        "客户关注合规审计、权限和国产化部署能力。",
                        List.of("合规资料待补", "信息安全审批较严"),
                        List.of("审计和权限需求明确", "部署窗口在 Q3"),
                        List.of("暂无存量经营信号"),
                        List.of("补合规资料包", "安排安全架构评审"),
                        List.of("合规关注", "安全评审", "Q3窗口"),
                        List.of("客户要求补充审计能力说明。", "电话中提到国产化部署要求。", "安全审批需要信息主管背书。")),
                demo("demo-account-012", "天津北辰装备有限公司", "EXISTING", "周敏", "装备制造", "韩旭 生产部长", "增购培育", 75, 64,
                        "生产部门认可现有流程，正在关注移动巡检和备件管理。",
                        List.of("IT 预算优先级待确认"),
                        List.of("移动巡检和备件管理有机会"),
                        List.of("生产部门满意", "IT 预算待确认", "可通过试点推动"),
                        List.of("做移动巡检小范围试点", "拉 IT 负责人参会"),
                        List.of("增购培育", "生产认可", "试点建议"),
                        List.of("生产部长认可现有流程。", "客户询问移动巡检是否支持离线。", "备件管理被列为下季度优化项。"))
        );
    }

    private DemoAccount demo(String id,
                             String name,
                             String segment,
                             String owner,
                             String industry,
                             String contact,
                             String stage,
                             int health,
                             int progress,
                             String summary,
                             List<String> risks,
                             List<String> newSignals,
                             List<String> existingSignals,
                             List<String> nextActions,
                             List<String> tags,
                             List<String> interactions) {
        return new DemoAccount(id, name, segment, owner, industry, contact, stage, health, progress, summary,
                risks, newSignals, existingSignals, nextActions, tags, interactions);
    }

    private record DemoAccount(String id,
                               String name,
                               String segment,
                               String owner,
                               String industry,
                               String contact,
                               String stage,
                               int health,
                               int progress,
                               String summary,
                               List<String> risks,
                               List<String> newSignals,
                               List<String> existingSignals,
                               List<String> nextActions,
                               List<String> tags,
                               List<String> interactions) {
        String lastInteraction() {
            return interactions.isEmpty() ? "" : interactions.get(0);
        }
    }

    public record AssistantCommand(String accountId, String message) {
    }
}
