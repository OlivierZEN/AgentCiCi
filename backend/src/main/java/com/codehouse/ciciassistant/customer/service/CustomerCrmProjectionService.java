package com.codehouse.ciciassistant.customer.service;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.customer.domain.CustomerFollowSubscriptionEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerFollowSubscriptionRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerSignalEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerSignalRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CustomerCrmProjectionService {

    private static final Logger log = LoggerFactory.getLogger(CustomerCrmProjectionService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(45);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final CloudccOpenApiService cloudcc;
    private final CustomerInteractionEventRepository eventRepository;
    private final CustomerWorkbenchRecommendationRepository recommendationRepository;
    private final CustomerSignalRepository signalRepository;
    private final CustomerFollowSubscriptionRepository followRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> loadLocks = new ConcurrentHashMap<>();

    public CustomerCrmProjectionService(CloudccOpenApiService cloudcc,
                                        CustomerInteractionEventRepository eventRepository,
                                        CustomerWorkbenchRecommendationRepository recommendationRepository,
                                        CustomerSignalRepository signalRepository,
                                        CustomerFollowSubscriptionRepository followRepository,
                                        ObjectMapper objectMapper) {
        this.cloudcc = cloudcc;
        this.eventRepository = eventRepository;
        this.recommendationRepository = recommendationRepository;
        this.signalRepository = signalRepository;
        this.followRepository = followRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> queue(String orgId, String userId, QueueQuery query) {
        Dataset dataset = dataset(orgId, userId, query.refresh());
        Set<String> followed = followRepository.findByOrgIdAndUserId(orgId, userId).stream()
                .map(CustomerFollowSubscriptionEntity::getCrmAccountId).collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> all = dataset.accounts().stream()
                .map(account -> accountView(orgId, account, dataset, followed.contains(text(account, "id"))))
                .filter(item -> modeMatches(item, query.mode()))
                .toList();
        Map<String, Long> filterCounts = filterCounts(all, query.mode());
        List<Map<String, Object>> filtered = all.stream()
                .filter(item -> filterMatches(item, query.filter()))
                .filter(item -> searchMatches(item, query.query()))
                .sorted(queueComparator(query.sort(), query.direction()))
                .toList();
        int size = Math.max(1, Math.min(100, query.size()));
        int page = Math.max(1, query.page());
        int from = Math.min(filtered.size(), (page - 1) * size);
        int to = Math.min(filtered.size(), from + size);
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / size);
        return mapOf(
                "items", filtered.subList(from, to),
                "page", page,
                "size", size,
                "totalElements", filtered.size(),
                "totalPages", totalPages,
                "filterCounts", filterCounts,
                "dataAsOf", dataset.loadedAt().toString(),
                "source", "CLOUDCC_LIVE",
                "mode", normalizeMode(query.mode())
        );
    }

    public Map<String, Object> detail(String orgId, String userId, String accountId, boolean refresh) {
        Dataset dataset = dataset(orgId, userId, refresh);
        Map<String, Object> account = dataset.accounts().stream()
                .filter(item -> accountId.equals(text(item, "id"))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前用户无权访问该客户或客户不存在"));
        boolean followed = followRepository.findByOrgIdAndUserIdAndCrmAccountId(orgId, userId, accountId).isPresent();
        Map<String, Object> view = new LinkedHashMap<>(accountView(orgId, account, dataset, followed));
        List<Map<String, Object>> opportunities = related(dataset.opportunities(), "khmc", accountId);
        List<Map<String, Object>> contacts = related(dataset.contacts(), "khmc", accountId);
        List<Map<String, Object>> tasks = related(dataset.tasks(), "relateid", accountId);
        List<Map<String, Object>> events = related(dataset.events(), "relateid", accountId);
        List<Map<String, Object>> cases = related(dataset.cases(), "khmc", accountId);
        List<Map<String, Object>> contracts = related(dataset.contracts(), "khmc", accountId);
        List<Map<String, Object>> timeline = timeline(orgId, accountId, tasks, events);
        List<Map<String, Object>> signals = signals(account, opportunities, contacts, tasks, cases, contracts, timeline);
        persistSignals(orgId, accountId, signals);

        view.put("industry", text(account, "hangye"));
        view.put("contact", contacts.isEmpty() ? "" : contactLabel(contacts.get(0)));
        Map<String, Object> summary = summary(view, opportunities, contacts, tasks, cases, contracts);
        view.put("summary", summary.get("text"));
        view.put("summaryMeta", summary);
        view.put("risks", signals.stream().filter(item -> !"INFO".equals(item.get("severity")))
                .map(item -> String.valueOf(item.get("detail"))).toList());
        view.put("newCustomerSignals", signals.stream().filter(item -> "NEW".equals(item.get("mode")))
                .map(item -> String.valueOf(item.get("title"))).toList());
        view.put("existingCustomerSignals", signals.stream().filter(item -> "EXISTING".equals(item.get("mode")))
                .map(item -> String.valueOf(item.get("title"))).toList());
        view.put("signals", signals);
        view.put("nextActions", openTasks(tasks).stream().map(this::taskLabel).toList());
        view.put("timeline", timeline);
        view.put("metrics", metrics(view, timeline, opportunities, tasks, cases, contracts, signals));
        view.put("serviceIssues", cases.stream().map(this::caseView).toList());
        view.put("valueItems", valueItems(contracts, opportunities));
        view.put("renewal", renewalView(contracts, opportunities));
        view.put("relationshipMap", contacts.stream().map(this::contactView).toList());
        view.put("opportunities", opportunities.stream().map(this::opportunityView).toList());
        view.put("crmConnection", mapOf("ready", true, "mode", "BOUND", "label", "CloudCC CRM 已连接",
                "dataAsOf", dataset.loadedAt().toString()));
        return view;
    }

    public List<Map<String, Object>> timeline(String orgId, String userId, String accountId, boolean refresh) {
        Dataset dataset = dataset(orgId, userId, refresh);
        if (dataset.accounts().stream().noneMatch(item -> accountId.equals(text(item, "id")))) {
            throw new IllegalArgumentException("当前用户无权访问该客户或客户不存在");
        }
        return timeline(orgId, accountId, related(dataset.tasks(), "relateid", accountId),
                related(dataset.events(), "relateid", accountId));
    }

    public Map<String, Object> integrationStatus(String orgId, String userId) {
        try {
            Dataset value = dataset(orgId, userId, false);
            return mapOf("status", "CONNECTED", "ready", true, "label", "CloudCC CRM 已连接",
                    "dataAsOf", value.loadedAt().toString(), "visibleAccounts", value.accounts().size());
        } catch (RuntimeException ex) {
            return mapOf("status", "DISCONNECTED", "ready", false, "label", "CloudCC CRM 连接异常",
                    "message", ex.getMessage() == null ? "连接失败" : ex.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> follow(String orgId, String userId, String accountId, boolean followed) {
        Dataset dataset = dataset(orgId, userId, false);
        if (dataset.accounts().stream().noneMatch(item -> accountId.equals(text(item, "id")))) {
            throw new IllegalArgumentException("当前用户无权关注该客户");
        }
        if (followed) {
            followRepository.findByOrgIdAndUserIdAndCrmAccountId(orgId, userId, accountId)
                    .orElseGet(() -> followRepository.save(new CustomerFollowSubscriptionEntity(orgId, userId, accountId, "RISK_AND_ACTION")));
        } else {
            followRepository.deleteByOrgIdAndUserIdAndCrmAccountId(orgId, userId, accountId);
        }
        return mapOf("accountId", accountId, "followed", followed);
    }

    public List<Map<String, Object>> notifications(String orgId, String userId) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) queue(orgId, userId,
                new QueueQuery("all", "all", "risk", "desc", "", 1, 100, false)).get("items");
        return items.stream().filter(item -> bool(item, "followed") || number(item, "riskCount") > 0)
                .limit(20)
                .map(item -> mapOf("accountId", item.get("accountId"), "accountName", item.get("name"),
                        "customerMode", item.get("customerMode"),
                        "severity", number(item, "riskCount") >= 2 ? "HIGH" : "MEDIUM",
                        "title", number(item, "riskCount") + " 个客户风险待处理",
                        "occurredAt", item.get("updatedAt"), "read", false))
                .toList();
    }

    public void invalidate(String orgId, String userId) {
        cache.remove(cacheKey(orgId, userId));
    }

    private Dataset dataset(String orgId, String userId, boolean refresh) {
        String key = cacheKey(orgId, userId);
        CacheEntry current = cache.get(key);
        if (!refresh && current != null && current.expiresAt().isAfter(Instant.now())) return current.dataset();
        synchronized (loadLocks.computeIfAbsent(key, ignored -> new Object())) {
            current = cache.get(key);
            if (!refresh && current != null && current.expiresAt().isAfter(Instant.now())) return current.dataset();
            Dataset loaded = new Dataset(
                    queryRequired(orgId, userId, "Account", "id,name,ownerid,lastcontactdate,lastmodifydate,hangye,fenji,unconnecteddays"),
                    queryOptional(orgId, userId, "Contact", "id,name,khmc,ownerid,zhiwu,contactrole,scbclxbcrq"),
                    queryOptional(orgId, userId, "Opportunity", "id,name,khmc,ownerid,jieduan,jine,jsrq,xyb,latestcontact,lastmodifydate"),
                    queryOptional(orgId, userId, "Task", "id,name,subject,relateid,relateobj,status,priority,expiredate,ownerid,remark,lastmodifydate"),
                    queryOptional(orgId, userId, "Event", "id,name,subject,relateid,relateobj,status,type,begintime,endtime,ownerid,remark,lastmodifydate"),
                    queryOptional(orgId, userId, "cloudcccase", "id,name,khmc,ownerid,zhuangtai,yxj,duedate,zhuti,problemdescription,lastmodifydate"),
                    queryOptional(orgId, userId, "contract", "id,name,khmc,ownerid,zhuangtai,htksrq,htjsrq,htje,lastmodifydate"),
                    Instant.now());
            cache.put(key, new CacheEntry(loaded, Instant.now().plus(CACHE_TTL)));
            return loaded;
        }
    }

    private List<Map<String, Object>> queryRequired(String orgId, String userId, String objectApiName, String fields) {
        try {
            return cloudcc.queryAllRecords(orgId, userId, objectApiName, fields, "");
        } catch (RuntimeException ex) {
            log.warn("CloudCC required object query failed: org={}, user={}, object={}, message={}",
                    orgId, userId, objectApiName, ex.getMessage());
            return cloudcc.queryAllRecords(orgId, userId, objectApiName, "id,name", "");
        }
    }

    private List<Map<String, Object>> queryOptional(String orgId, String userId, String objectApiName, String fields) {
        try {
            return cloudcc.queryAllRecords(orgId, userId, objectApiName, fields, "");
        } catch (RuntimeException ex) {
            log.info("CloudCC optional object unavailable for current user: org={}, user={}, object={}, message={}",
                    orgId, userId, objectApiName, ex.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> accountView(String orgId, Map<String, Object> account, Dataset dataset, boolean followed) {
        String accountId = text(account, "id");
        List<Map<String, Object>> opportunities = related(dataset.opportunities(), "khmc", accountId);
        List<Map<String, Object>> contacts = related(dataset.contacts(), "khmc", accountId);
        List<Map<String, Object>> tasks = related(dataset.tasks(), "relateid", accountId);
        List<Map<String, Object>> events = related(dataset.events(), "relateid", accountId);
        List<Map<String, Object>> cases = related(dataset.cases(), "khmc", accountId);
        List<Map<String, Object>> contracts = related(dataset.contracts(), "khmc", accountId);
        boolean existing = !contracts.isEmpty() || opportunities.stream().anyMatch(this::closedWon);
        int overdueTasks = (int) openTasks(tasks).stream().filter(this::overdue).count();
        int openCases = (int) cases.stream().filter(item -> !caseClosed(item)).count();
        int daysWithoutContact = integer(account, "unconnecteddays", daysSince(instant(account, "lastcontactdate")));
        int riskCount = openCases + overdueTasks + (daysWithoutContact > 30 ? 1 : 0)
                + (int) opportunities.stream().filter(item -> !closed(item) && text(item, "xyb").isBlank()).count();
        int nextActionCount = openTasks(tasks).size();
        int progressScore = progressScore(opportunities, contacts, tasks);
        int healthScore = healthScore(contracts, openCases, overdueTasks, daysWithoutContact);
        int renewalDays = renewalDays(contracts);
        String segment = segment(account, existing, riskCount);
        Instant last = latestInteraction(account, tasks, events, opportunities);
        Map<String, Object> latest = latestInteractionRecord(tasks, events);
        return mapOf(
                "accountId", accountId,
                "name", text(account, "name"),
                "owner", firstNonBlank(text(account, "owneridccname"), text(account, "ownerid"), "未分配"),
                "customerMode", existing ? "EXISTING" : "NEW",
                "segment", segment,
                "healthScore", healthScore,
                "progressScore", progressScore,
                "riskCount", riskCount,
                "nextActionCount", nextActionCount,
                "pendingRecommendationCount", recommendationRepository.countByOrgIdAndCrmAccountIdAndStatus(
                        orgId, accountId, CustomerWorkbenchRecommendationEntity.STATUS_PENDING),
                "opportunityCount", opportunities.size(),
                "renewalDays", renewalDays,
                "interactionCount", tasks.size() + events.size(),
                "lastInteraction", interactionSummary(latest),
                "lastInteractionType", latest.isEmpty() ? "CRM" : text(latest, "_source"),
                "stage", stage(opportunities, contracts),
                "tags", tags(account, riskCount, existing),
                "followed", followed,
                "updatedAt", (last == null ? dataset.loadedAt() : last).toString(),
                "dataAsOf", dataset.loadedAt().toString(),
                "source", "CLOUDCC_LIVE"
        );
    }

    private List<Map<String, Object>> timeline(String orgId, String accountId,
                                               List<Map<String, Object>> tasks,
                                               List<Map<String, Object>> events) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : tasks) out.add(interactionView(item, "CRM_TASK", "NEW_CUSTOMER"));
        for (Map<String, Object> item : events) out.add(interactionView(item, "CRM_EVENT", "MIXED"));
        for (CustomerInteractionEventEntity item : eventRepository.findByOrgIdAndCrmAccountIdOrderByOccurredAtDesc(orgId, accountId)) {
            out.add(mapOf("eventId", item.getPublicId(), "accountId", accountId, "sourceType", item.getSourceType(),
                    "occurredAt", item.getOccurredAt().toString(), "subject", item.getSubject(), "summary", item.getAiSummary(),
                    "sentiment", item.getSentiment(), "intentTags", readList(item.getIntentTags()),
                    "lifecycleArea", item.getLifecycleArea(), "sourceRecordId", item.getPublicId()));
        }
        return out.stream().sorted(Comparator.comparing(item -> instant(item, "occurredAt"),
                Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    private Map<String, Object> interactionView(Map<String, Object> item, String source, String lifecycle) {
        Instant at = firstInstant(item, "begintime", "lastmodifydate", "expiredate");
        return mapOf("eventId", text(item, "id"), "accountId", text(item, "relateid"), "sourceType", source,
                "occurredAt", (at == null ? Instant.EPOCH : at).toString(),
                "subject", firstNonBlank(text(item, "subject"), text(item, "name"), "CRM 互动"),
                "summary", firstNonBlank(text(item, "remark"), text(item, "executeresult"), "CRM 中未填写互动内容"),
                "sentiment", "NEUTRAL", "intentTags", List.of(firstNonBlank(text(item, "type"), text(item, "status"), source)),
                "lifecycleArea", lifecycle, "sourceRecordId", text(item, "id"));
    }

    private List<Map<String, Object>> signals(Map<String, Object> account,
                                              List<Map<String, Object>> opportunities,
                                              List<Map<String, Object>> contacts,
                                              List<Map<String, Object>> tasks,
                                              List<Map<String, Object>> cases,
                                              List<Map<String, Object>> contracts,
                                              List<Map<String, Object>> timeline) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (contacts.isEmpty()) out.add(signal("NEW", "RELATION_GAP", "关键联系人待补齐", "CRM 中没有关联联系人。", "HIGH", List.of()));
        if (opportunities.isEmpty()) out.add(signal("NEW", "OPPORTUNITY_GAP", "商机尚未建立", "客户存在但没有关联业务机会。", "MEDIUM", List.of()));
        opportunities.stream().filter(item -> !closed(item) && text(item, "xyb").isBlank()).findFirst()
                .ifPresent(item -> out.add(signal("NEW", "NEXT_STEP_GAP", "商机下一步为空",
                        "业务机会“" + text(item, "name") + "”缺少下一步行动。", "HIGH", List.of(text(item, "id")))));
        long overdue = openTasks(tasks).stream().filter(this::overdue).count();
        if (overdue > 0) out.add(signal("NEW", "OVERDUE_TASK", "存在逾期跟进任务", overdue + " 个任务已超过到期日期。", "HIGH",
                openTasks(tasks).stream().filter(this::overdue).map(item -> text(item, "id")).toList()));
        List<Map<String, Object>> openCases = cases.stream().filter(item -> !caseClosed(item)).toList();
        if (!openCases.isEmpty()) out.add(signal("EXISTING", "SERVICE_RISK", "服务问题尚未闭环",
                openCases.size() + " 个个案仍处于未关闭状态。", "HIGH", openCases.stream().map(item -> text(item, "id")).toList()));
        int renewalDays = renewalDays(contracts);
        if (renewalDays >= 0 && renewalDays <= 90) out.add(signal("EXISTING", "RENEWAL_WINDOW", "进入续约窗口",
                "最近合同将在 " + renewalDays + " 天后到期。", renewalDays <= 30 ? "HIGH" : "MEDIUM",
                contracts.stream().map(item -> text(item, "id")).toList()));
        if (!contracts.isEmpty() && openCases.isEmpty()) out.add(signal("EXISTING", "VALUE_STABLE", "履约关系稳定",
                "存在有效合同且当前没有未关闭服务个案。", "INFO", contracts.stream().map(item -> text(item, "id")).toList()));
        if (timeline.isEmpty()) out.add(signal(contracts.isEmpty() ? "NEW" : "EXISTING", "INTERACTION_GAP", "缺少近期互动",
                "当前客户没有可见的任务或事件记录。", "MEDIUM", List.of()));
        return out;
    }

    @Transactional
    protected void persistSignals(String orgId, String accountId, List<Map<String, Object>> signals) {
        Set<String> activeIds = new LinkedHashSet<>();
        for (Map<String, Object> signal : signals) {
            String publicId = stableId("sig", orgId, accountId, text(signal, "type"));
            activeIds.add(publicId);
            CustomerSignalEntity entity = signalRepository.findByOrgIdAndPublicId(orgId, publicId)
                    .orElseGet(() -> new CustomerSignalEntity(publicId, orgId, accountId, text(signal, "mode"), text(signal, "type"),
                            text(signal, "title"), text(signal, "detail"), text(signal, "severity"), toJson(signal.get("evidence")), null, Instant.now()));
            entity.refresh(text(signal, "mode"), text(signal, "title"), text(signal, "detail"), text(signal, "severity"),
                    toJson(signal.get("evidence")), Instant.now());
            signalRepository.save(entity);
        }
        for (CustomerSignalEntity existing : signalRepository.findByOrgIdAndCrmAccountIdOrderByUpdatedAtDesc(orgId, accountId)) {
            if (!activeIds.contains(existing.getPublicId())) {
                existing.resolve();
                signalRepository.save(existing);
            }
        }
    }

    private Map<String, Object> metrics(Map<String, Object> view, List<Map<String, Object>> timeline,
                                        List<Map<String, Object>> opportunities, List<Map<String, Object>> tasks,
                                        List<Map<String, Object>> cases, List<Map<String, Object>> contracts,
                                        List<Map<String, Object>> signals) {
        return mapOf(
                "pendingRecommendations", metric(view.get("pendingRecommendationCount"), "PENDING 建议", "AgentCiCi", "recommendations"),
                "risks", metric(signals.stream().filter(item -> "HIGH".equals(item.get("severity"))).count(), "高风险信号", "CRM+规则", "signals"),
                "nextActions", metric(openTasks(tasks).size(), "未完成 CRM 任务", "Task", "actions"),
                "interactions", metric(timeline.size(), "可见互动事实", "Task/Event/互动库", "timeline"),
                "health", metric(view.get("healthScore"), "客户健康度", "合同/个案/任务/互动", "overview"),
                "renewalDays", metric(Math.max(-1, renewalDays(contracts)), "最近合同到期天数", "Contract", "renewal"),
                "openIssues", metric(cases.stream().filter(item -> !caseClosed(item)).count(), "未关闭个案", "Case", "service"),
                "expansionSignals", metric(opportunities.stream().filter(item -> text(item, "jieduan").contains("增购")).count(), "增购业务机会", "Opportunity", "renewal")
        );
    }

    private Map<String, Object> metric(Object value, String definition, String source, String target) {
        return mapOf("value", value, "definition", definition, "source", source,
                "lastCalculatedAt", Instant.now().toString(), "drilldownTarget", target);
    }

    private Map<String, Object> signal(String mode, String type, String title, String detail, String severity, List<String> evidence) {
        return mapOf("mode", mode, "type", type, "title", title, "detail", detail, "severity", severity,
                "status", "OPEN", "evidence", evidence, "updatedAt", Instant.now().toString());
    }

    private Map<String, Object> summary(Map<String, Object> view, List<Map<String, Object>> opportunities,
                                        List<Map<String, Object>> contacts, List<Map<String, Object>> tasks,
                                        List<Map<String, Object>> cases, List<Map<String, Object>> contracts) {
        return mapOf("text", view.get("name") + "当前有 " + opportunities.size() + " 个业务机会、" + contacts.size()
                        + " 位联系人、" + openTasks(tasks).size() + " 个未完成任务、" + cases.stream().filter(item -> !caseClosed(item)).count()
                        + " 个未关闭个案和 " + contracts.size() + " 份合同。",
                "facts", List.of("业务机会 " + opportunities.size(), "联系人 " + contacts.size(), "合同 " + contracts.size()),
                "generatedAt", Instant.now().toString(), "source", "CLOUDCC_LIVE");
    }

    private List<Map<String, Object>> valueItems(List<Map<String, Object>> contracts, List<Map<String, Object>> opportunities) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> contract : contracts) out.add(mapOf("id", text(contract, "id"), "title", text(contract, "name"),
                "status", text(contract, "zhuangtai"), "amount", decimal(contract, "htje"), "source", "Contract"));
        for (Map<String, Object> opportunity : opportunities) if (closedWon(opportunity)) out.add(mapOf("id", text(opportunity, "id"),
                "title", text(opportunity, "name"), "status", text(opportunity, "jieduan"), "amount", decimal(opportunity, "jine"), "source", "Opportunity"));
        return out;
    }

    private Map<String, Object> renewalView(List<Map<String, Object>> contracts, List<Map<String, Object>> opportunities) {
        return mapOf("days", renewalDays(contracts), "contracts", contracts.stream().map(this::contractView).toList(),
                "expansionOpportunities", opportunities.stream().filter(item -> text(item, "jieduan").contains("增购")).map(this::opportunityView).toList());
    }

    private Map<String, Object> contactView(Map<String, Object> item) {
        return mapOf("id", text(item, "id"), "name", text(item, "name"), "title", text(item, "zhiwu"),
                "role", text(item, "contactrole"), "lastContactAt", instantText(item, "scbclxbcrq"), "owner", text(item, "owneridccname"));
    }

    private Map<String, Object> opportunityView(Map<String, Object> item) {
        return mapOf("id", text(item, "id"), "name", text(item, "name"), "stage", text(item, "jieduan"),
                "amount", decimal(item, "jine"), "closeDate", text(item, "jsrq"), "nextStep", text(item, "xyb"));
    }

    private Map<String, Object> contractView(Map<String, Object> item) {
        return mapOf("id", text(item, "id"), "name", text(item, "name"), "status", text(item, "zhuangtai"),
                "startDate", text(item, "htksrq"), "endDate", text(item, "htjsrq"), "amount", decimal(item, "htje"));
    }

    private Map<String, Object> caseView(Map<String, Object> item) {
        return mapOf("id", text(item, "id"), "number", text(item, "name"), "title", text(item, "zhuti"),
                "status", text(item, "zhuangtai"), "priority", text(item, "yxj"), "dueAt", instantText(item, "duedate"),
                "description", text(item, "problemdescription"));
    }

    private Map<String, Long> filterCounts(List<Map<String, Object>> items, String mode) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", (long) items.size());
        if ("existing".equals(normalizeMode(mode))) {
            counts.put("renewal", items.stream().filter(item -> number(item, "renewalDays") >= 0 && number(item, "renewalDays") <= 90).count());
            counts.put("health", items.stream().filter(item -> number(item, "healthScore") < 75).count());
            counts.put("service", items.stream().filter(item -> number(item, "riskCount") > 0).count());
            counts.put("expansion", items.stream().filter(item -> number(item, "opportunityCount") > 0).count());
        } else {
            counts.put("focus", items.stream().filter(item -> number(item, "progressScore") >= 70).count());
            counts.put("follow", items.stream().filter(item -> number(item, "nextActionCount") > 0).count());
            counts.put("risk", items.stream().filter(item -> number(item, "riskCount") > 0).count());
            counts.put("recommendations", items.stream().filter(item -> number(item, "pendingRecommendationCount") > 0).count());
        }
        return counts;
    }

    private boolean modeMatches(Map<String, Object> item, String mode) {
        String normalized = normalizeMode(mode);
        if ("all".equals(normalized)) return true;
        return "existing".equals(normalized) ? "EXISTING".equals(item.get("customerMode")) : "NEW".equals(item.get("customerMode"));
    }

    private boolean filterMatches(Map<String, Object> item, String filter) {
        String value = filter == null ? "all" : filter;
        return switch (value) {
            case "focus" -> number(item, "progressScore") >= 70;
            case "follow" -> number(item, "nextActionCount") > 0;
            case "risk", "service" -> number(item, "riskCount") > 0;
            case "recommendations" -> number(item, "pendingRecommendationCount") > 0;
            case "health" -> number(item, "healthScore") < 75;
            case "expansion" -> number(item, "opportunityCount") > 0;
            case "renewal" -> number(item, "renewalDays") >= 0 && number(item, "renewalDays") <= 90;
            default -> true;
        };
    }

    private boolean searchMatches(Map<String, Object> item, String query) {
        if (query == null || query.isBlank()) return true;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return (text(item, "name") + " " + text(item, "owner") + " " + text(item, "stage") + " " + item.get("tags"))
                .toLowerCase(Locale.ROOT).contains(needle);
    }

    private Comparator<Map<String, Object>> queueComparator(String sort, String direction) {
        Comparator<Map<String, Object>> comparator = switch (sort == null ? "priority" : sort) {
            case "risk" -> Comparator.comparingInt(item -> number(item, "riskCount"));
            case "health" -> Comparator.comparingInt(item -> number(item, "healthScore"));
            case "interaction" -> Comparator.comparing(item -> instant(item, "updatedAt"), Comparator.nullsFirst(Comparator.naturalOrder()));
            case "renewal" -> Comparator.comparingInt(item -> number(item, "renewalDays"));
            default -> Comparator.comparingInt(item -> number(item, "progressScore") + number(item, "riskCount") * 10);
        };
        return "asc".equalsIgnoreCase(direction) ? comparator : comparator.reversed();
    }

    private int progressScore(List<Map<String, Object>> opportunities, List<Map<String, Object>> contacts, List<Map<String, Object>> tasks) {
        int stage = opportunities.stream().mapToInt(item -> stageScore(text(item, "jieduan"))).max().orElse(15);
        if (!contacts.isEmpty()) stage += 8;
        if (opportunities.stream().anyMatch(item -> !text(item, "xyb").isBlank())) stage += 6;
        if (!openTasks(tasks).isEmpty()) stage += 4;
        return Math.max(0, Math.min(100, stage));
    }

    private int stageScore(String stage) {
        if (stage == null) return 15;
        if (stage.startsWith("1-")) return 20;
        if (stage.startsWith("2-")) return 35;
        if (stage.startsWith("3-")) return 50;
        if (stage.startsWith("4-")) return 65;
        if (stage.startsWith("5-")) return 75;
        if (stage.startsWith("6-")) return 85;
        if (stage.startsWith("7-")) return 100;
        if (stage.contains("增购")) return 70;
        if (stage.contains("价值")) return 60;
        return 15;
    }

    private int healthScore(List<Map<String, Object>> contracts, int openCases, int overdueTasks, int daysWithoutContact) {
        int score = contracts.isEmpty() ? 62 : 88;
        score -= Math.min(32, openCases * 8);
        score -= Math.min(20, overdueTasks * 5);
        if (daysWithoutContact > 30) score -= Math.min(20, (daysWithoutContact - 30) / 3);
        int renewal = renewalDays(contracts);
        if (renewal >= 0 && renewal <= 30) score -= 8;
        return Math.max(0, Math.min(100, score));
    }

    private String segment(Map<String, Object> account, boolean existing, int riskCount) {
        String level = text(account, "fenji");
        if (level.contains("战略") || level.contains("重点")) return "STRATEGIC";
        if (riskCount >= 2) return "RISK";
        return existing ? "EXISTING" : "NEW";
    }

    private String stage(List<Map<String, Object>> opportunities, List<Map<String, Object>> contracts) {
        return opportunities.stream().max(Comparator.comparingInt(item -> stageScore(text(item, "jieduan"))))
                .map(item -> text(item, "jieduan"))
                .orElseGet(() -> contracts.isEmpty() ? "待建立商机" : firstNonBlank(text(contracts.get(0), "zhuangtai"), "客户经营"));
    }

    private List<String> tags(Map<String, Object> account, int riskCount, boolean existing) {
        Set<String> tags = new LinkedHashSet<>();
        if (!text(account, "hangye").isBlank()) tags.add(text(account, "hangye"));
        if (!text(account, "fenji").isBlank()) tags.add(text(account, "fenji"));
        tags.add(existing ? "老客户" : "新客户");
        if (riskCount > 0) tags.add("风险 " + riskCount);
        return List.copyOf(tags);
    }

    private List<Map<String, Object>> openTasks(List<Map<String, Object>> tasks) {
        return tasks.stream().filter(item -> !"已完成".equals(text(item, "status"))).toList();
    }

    private boolean overdue(Map<String, Object> task) {
        Instant due = instant(task, "expiredate");
        return due != null && due.isBefore(Instant.now()) && !"已完成".equals(text(task, "status"));
    }

    private boolean closed(Map<String, Object> opportunity) {
        String stage = text(opportunity, "jieduan");
        return stage.startsWith("7-") || stage.startsWith("8-") || stage.startsWith("9-");
    }

    private boolean closedWon(Map<String, Object> opportunity) { return text(opportunity, "jieduan").startsWith("7-"); }
    private boolean caseClosed(Map<String, Object> item) { return "关闭".equals(text(item, "zhuangtai")); }

    private int renewalDays(List<Map<String, Object>> contracts) {
        return contracts.stream().map(item -> localDate(item, "htjsrq")).filter(java.util.Objects::nonNull)
                .mapToInt(date -> (int) ChronoUnit.DAYS.between(LocalDate.now(BUSINESS_ZONE), date)).filter(days -> days >= 0).min().orElse(-1);
    }

    private String taskLabel(Map<String, Object> task) {
        return firstNonBlank(text(task, "subject"), text(task, "name"), "CRM 跟进任务")
                + (text(task, "expiredate").isBlank() ? "" : "（" + text(task, "expiredate") + "）");
    }

    private String contactLabel(Map<String, Object> contact) {
        return text(contact, "name") + (text(contact, "zhiwu").isBlank() ? "" : " " + text(contact, "zhiwu"));
    }

    private Map<String, Object> latestInteractionRecord(List<Map<String, Object>> tasks, List<Map<String, Object>> events) {
        List<Map<String, Object>> all = new ArrayList<>();
        for (Map<String, Object> task : tasks) { Map<String, Object> copy = new HashMap<>(task); copy.put("_source", "CRM_TASK"); all.add(copy); }
        for (Map<String, Object> event : events) { Map<String, Object> copy = new HashMap<>(event); copy.put("_source", "CRM_EVENT"); all.add(copy); }
        return all.stream().max(Comparator.comparing(item -> firstInstant(item, "begintime", "lastmodifydate", "expiredate"),
                Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(Map.of());
    }

    private String interactionSummary(Map<String, Object> item) {
        if (item.isEmpty()) return "暂无可见互动";
        return firstNonBlank(text(item, "subject"), text(item, "name"), text(item, "remark"), "CRM 互动");
    }

    private Instant latestInteraction(Map<String, Object> account, List<Map<String, Object>> tasks,
                                      List<Map<String, Object>> events, List<Map<String, Object>> opportunities) {
        List<Instant> values = new ArrayList<>();
        add(values, instant(account, "lastcontactdate"));
        add(values, instant(account, "lastmodifydate"));
        tasks.forEach(item -> add(values, firstInstant(item, "lastmodifydate", "expiredate")));
        events.forEach(item -> add(values, firstInstant(item, "begintime", "lastmodifydate")));
        opportunities.forEach(item -> add(values, firstInstant(item, "latestcontact", "lastmodifydate")));
        return values.stream().max(Comparator.naturalOrder()).orElse(null);
    }

    private void add(List<Instant> values, Instant value) { if (value != null) values.add(value); }
    private int daysSince(Instant value) { return value == null ? 999 : (int) Math.max(0, Duration.between(value, Instant.now()).toDays()); }

    private List<Map<String, Object>> related(List<Map<String, Object>> items, String field, String accountId) {
        return items.stream().filter(item -> accountId.equals(text(item, field))).toList();
    }

    private String normalizeMode(String mode) {
        if (mode == null) return "new";
        if ("existing".equalsIgnoreCase(mode)) return "existing";
        if ("all".equalsIgnoreCase(mode)) return "all";
        return "new";
    }

    private String cacheKey(String orgId, String userId) { return orgId + ":" + userId; }
    private String stableId(String prefix, String... values) {
        String seed = String.join(":", values);
        return prefix + "_" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalArgumentException("JSON 序列化失败", ex); }
    }

    @SuppressWarnings("unchecked")
    private List<Object> readList(String json) {
        try { Object value = objectMapper.readValue(json, Object.class); return value instanceof List<?> list ? new ArrayList<>((List<Object>) list) : List.of(); }
        catch (Exception ex) { return List.of(); }
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) map.put(String.valueOf(values[i]), values[i + 1]);
        return map;
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean bool(Map<String, Object> map, String key) { return Boolean.parseBoolean(text(map, key)); }
    private static int number(Map<String, Object> map, String key) { return integer(map, key, 0); }
    private static int integer(Map<String, Object> map, String key, int fallback) {
        try { return (int) Math.round(Double.parseDouble(text(map, key))); } catch (Exception ex) { return fallback; }
    }
    private static double decimal(Map<String, Object> map, String key) {
        try { return Double.parseDouble(text(map, key)); } catch (Exception ex) { return 0D; }
    }
    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private static Instant firstInstant(Map<String, Object> map, String... keys) {
        for (String key : keys) { Instant value = instant(map, key); if (value != null) return value; }
        return null;
    }

    private static String instantText(Map<String, Object> map, String key) {
        Instant value = instant(map, key); return value == null ? "" : value.toString();
    }

    private static Instant instant(Map<String, Object> map, String key) {
        String raw = text(map, key);
        if (raw.isBlank()) return null;
        try { return Instant.parse(raw); } catch (DateTimeParseException ignored) {}
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))) {
            try { return LocalDateTime.parse(raw, formatter).atZone(BUSINESS_ZONE).toInstant(); }
            catch (DateTimeParseException ignored) {}
        }
        try { return LocalDate.parse(raw).atStartOfDay(BUSINESS_ZONE).toInstant(); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private static LocalDate localDate(Map<String, Object> map, String key) {
        String raw = text(map, key);
        if (raw.isBlank()) return null;
        try { return LocalDate.parse(raw.substring(0, Math.min(10, raw.length()))); }
        catch (Exception ex) { return null; }
    }

    public record QueueQuery(String mode, String filter, String sort, String direction, String query,
                             int page, int size, boolean refresh) {}
    private record CacheEntry(Dataset dataset, Instant expiresAt) {}
    private record Dataset(List<Map<String, Object>> accounts, List<Map<String, Object>> contacts,
                           List<Map<String, Object>> opportunities, List<Map<String, Object>> tasks,
                           List<Map<String, Object>> events, List<Map<String, Object>> cases,
                           List<Map<String, Object>> contracts, Instant loadedAt) {}
}
