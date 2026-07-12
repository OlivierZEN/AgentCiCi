package com.codehouse.ciciassistant.customer.service;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService.PageRecords;
import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.customer.domain.CustomerFollowSubscriptionEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerFollowSubscriptionRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerSignalEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerSignalRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerCrmProjectionService {

    private static final Logger log = LoggerFactory.getLogger(CustomerCrmProjectionService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration FAILED_RETRY_DELAY = Duration.ofSeconds(30);
    private static final int RECORD_LIMIT = 10_000;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final CloudccOpenApiService cloudcc;
    private final CustomerInteractionEventRepository eventRepository;
    private final CustomerWorkbenchRecommendationRepository recommendationRepository;
    private final CustomerSignalRepository signalRepository;
    private final CustomerFollowSubscriptionRepository followRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Dataset>> activeLoads = new ConcurrentHashMap<>();
    private final Map<String, SyncState> syncStates = new ConcurrentHashMap<>();
    private final ExecutorService syncExecutor = Executors.newFixedThreadPool(2, runnable -> daemonThread(runnable, "crm-dataset-sync"));
    private final ExecutorService queryExecutor = Executors.newFixedThreadPool(3, runnable -> daemonThread(runnable, "crm-object-query"));
    private final ExecutorService detailQueryExecutor = Executors.newFixedThreadPool(7, runnable -> daemonThread(runnable, "crm-detail-query"));

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
        String key = cacheKey(orgId, userId);
        Dataset dataset = availableDataset(orgId, userId, query.refresh());
        if (query.query() != null && !query.query().isBlank()) {
            Map<String, Object> result = searchQueue(orgId, userId, query, dataset);
            result.putAll(syncMetadata(key, dataset));
            return result;
        }
        if (dataset == null) return emptyQueue(query, key);
        List<Map<String, Object>> all = accountViews(orgId, userId, dataset);
        Map<String, Object> result = queueFromViews(all, query, dataset.loadedAt());
        result.putAll(syncMetadata(key, dataset));
        return result;
    }

    private Map<String, Object> searchQueue(String orgId, String userId, QueueQuery query, Dataset dataset) {
        int size = Math.max(1, Math.min(100, query.size()));
        int page = Math.max(1, query.page());
        String term = normalizedSearchTerm(query.query());
        PageRecords result = cloudcc.pageQueryRecords(orgId, userId, "Account",
                "id,name,ownerid,lastcontactdate,lastmodifydate,hangye,fenji,unconnecteddays",
                "name like '%" + escapeExpressionLiteral(term) + "%'", page, size);
        Map<String, Map<String, Object>> cachedAccounts = dataset == null ? Map.of() : dataset.accounts().stream()
                .collect(java.util.stream.Collectors.toMap(item -> text(item, "id"), item -> item, (left, right) -> left));
        Set<String> followed = followRepository.findByOrgIdAndUserId(orgId, userId).stream()
                .map(CustomerFollowSubscriptionEntity::getCrmAccountId).collect(java.util.stream.Collectors.toSet());
        Map<String, Long> pendingCounts = recommendationRepository.findByOrgIdOrderByUpdatedAtDesc(orgId).stream()
                .filter(item -> CustomerWorkbenchRecommendationEntity.STATUS_PENDING.equals(item.getStatus()))
                .collect(java.util.stream.Collectors.groupingBy(
                        CustomerWorkbenchRecommendationEntity::getCrmAccountId,
                        java.util.stream.Collectors.counting()));
        List<Map<String, Object>> items = result.records().stream().map(account -> {
            String accountId = text(account, "id");
            Map<String, Object> cached = cachedAccounts.get(accountId);
            return cached != null && dataset != null
                    ? accountView(cached, dataset, followed.contains(accountId), pendingCounts.getOrDefault(accountId, 0L))
                    : searchAccountView(account);
        }).sorted(queueComparator("interaction", "desc")).toList();
        return mapOf(
                "items", items,
                "page", result.pageNum(),
                "size", size,
                "totalElements", result.totalCount(),
                "totalPages", result.pageCount(),
                "filterCounts", mapOf("all", (long) result.totalCount()),
                "dataAsOf", dataset == null ? "" : dataset.loadedAt().toString(),
                "source", "CLOUDCC_SEARCH",
                "mode", "search",
                "searchScope", "ALL_VISIBLE_ACCOUNTS"
        );
    }

    private Map<String, Object> searchAccountView(Map<String, Object> account) {
        Instant last = firstInstant(account, "lastcontactdate", "lastmodifydate");
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (!text(account, "hangye").isBlank()) tags.add(text(account, "hangye"));
        if (!text(account, "fenji").isBlank()) tags.add(text(account, "fenji"));
        return mapOf(
                "accountId", text(account, "id"),
                "name", text(account, "name"),
                "owner", firstNonBlank(text(account, "owneridccname"), text(account, "ownerid"), "未分配"),
                "customerMode", "SEARCH",
                "segment", "SEARCH",
                "healthScore", 0,
                "progressScore", 0,
                "riskCount", 0,
                "nextActionCount", 0,
                "pendingRecommendationCount", 0,
                "opportunityCount", 0,
                "renewalDays", -1,
                "interactionCount", 0,
                "lastInteraction", last == null ? "暂无可见互动" : "CRM 客户档案最近更新",
                "lastInteractionType", "CRM",
                "stage", "客户档案",
                "tags", List.copyOf(tags),
                "followed", false,
                "updatedAt", last == null ? "" : last.toString(),
                "dataAsOf", Instant.now().toString(),
                "source", "CLOUDCC_SEARCH"
        );
    }

    private List<Map<String, Object>> accountViews(String orgId, String userId, Dataset dataset) {
        Set<String> followed = followRepository.findByOrgIdAndUserId(orgId, userId).stream()
                .map(CustomerFollowSubscriptionEntity::getCrmAccountId).collect(java.util.stream.Collectors.toSet());
        Map<String, Long> pendingCounts = recommendationRepository.findByOrgIdOrderByUpdatedAtDesc(orgId).stream()
                .filter(item -> CustomerWorkbenchRecommendationEntity.STATUS_PENDING.equals(item.getStatus()))
                .collect(java.util.stream.Collectors.groupingBy(
                        CustomerWorkbenchRecommendationEntity::getCrmAccountId,
                        java.util.stream.Collectors.counting()));
        return dataset.accounts().stream()
                .map(account -> accountView(account, dataset, followed.contains(text(account, "id")),
                        pendingCounts.getOrDefault(text(account, "id"), 0L)))
                .toList();
    }

    private Map<String, Object> queueFromViews(List<Map<String, Object>> allViews, QueueQuery query, Instant loadedAt) {
        List<Map<String, Object>> modeViews = allViews.stream().filter(item -> modeMatches(item, query.mode())).toList();
        Map<String, Long> filterCounts = filterCounts(allViews, query.mode());
        List<Map<String, Object>> filtered = modeViews.stream()
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
                "dataAsOf", loadedAt.toString(),
                "source", "CLOUDCC_LIVE",
                "mode", normalizeMode(query.mode())
        );
    }

    public Map<String, Object> detail(String orgId, String userId, String accountId, boolean refresh) {
        Dataset dataset = requireAccountDataset(orgId, userId, accountId, refresh);
        Map<String, Object> account = dataset.accounts().stream()
                .filter(item -> accountId.equals(text(item, "id"))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前用户无权访问该客户或客户不存在"));
        boolean followed = followRepository.findByOrgIdAndUserIdAndCrmAccountId(orgId, userId, accountId).isPresent();
        long pendingCount = recommendationRepository.countByOrgIdAndCrmAccountIdAndStatus(
                orgId, accountId, CustomerWorkbenchRecommendationEntity.STATUS_PENDING);
        Map<String, Object> view = new LinkedHashMap<>(accountView(account, dataset, followed, pendingCount));
        List<Map<String, Object>> opportunities = dataset.opportunitiesFor(accountId);
        List<Map<String, Object>> contacts = dataset.contactsFor(accountId);
        List<Map<String, Object>> tasks = dataset.tasksFor(accountId);
        List<Map<String, Object>> events = dataset.eventsFor(accountId);
        List<Map<String, Object>> cases = dataset.casesFor(accountId);
        List<Map<String, Object>> contracts = dataset.contractsFor(accountId);
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
        Dataset dataset = requireAccountDataset(orgId, userId, accountId, refresh);
        if (dataset.accounts().stream().noneMatch(item -> accountId.equals(text(item, "id")))) {
            throw new IllegalArgumentException("当前用户无权访问该客户或客户不存在");
        }
        return timeline(orgId, accountId, dataset.tasksFor(accountId), dataset.eventsFor(accountId));
    }

    public Map<String, Object> integrationStatus(String orgId, String userId) {
        String key = cacheKey(orgId, userId);
        Dataset value = availableDataset(orgId, userId, false);
        SyncState state = syncStates.get(key);
        if (value == null) {
            boolean failed = state != null && "FAILED".equals(state.status());
            Map<String, Object> status = mapOf(
                    "status", failed ? "SYNC_FAILED" : "SYNCING",
                    "ready", false,
                    "syncing", !failed,
                    "syncStatus", failed ? "FAILED" : "SYNCING",
                    "label", failed ? "CRM 数据同步失败" : "正在同步 CRM 数据",
                    "message", failed ? state.message() : "首次同步的数据量较大，您可以停留在当前页面，完成后会自动显示客户。",
                    "visibleAccounts", 0,
                    "recordLimitReached", false);
            return status;
        }
        Map<String, Object> status = mapOf("status", "CONNECTED", "ready", true, "label", "CloudCC CRM 已连接",
                "dataAsOf", value.loadedAt().toString(), "visibleAccounts", value.accounts().size());
        status.putAll(syncMetadata(key, value));
        return status;
    }

    @Transactional
    public Map<String, Object> follow(String orgId, String userId, String accountId, boolean followed) {
        Dataset dataset = requireAccountDataset(orgId, userId, accountId, false);
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
        Dataset dataset = availableDataset(orgId, userId, false);
        if (dataset == null) return List.of();
        List<Map<String, Object>> items = accountViews(orgId, userId, dataset);
        return items.stream().filter(item -> bool(item, "followed") || number(item, "riskCount") > 0)
                .sorted(queueComparator("risk", "desc"))
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

    public List<Map<String, Object>> visibleAccountViews(String orgId, String userId) {
        Dataset dataset = availableDataset(orgId, userId, false);
        return dataset == null ? List.of() : accountViews(orgId, userId, dataset);
    }

    private Dataset availableDataset(String orgId, String userId, boolean refresh) {
        String key = cacheKey(orgId, userId);
        CacheEntry current = cache.get(key);
        if (refresh || current == null || current.expiresAt().isBefore(Instant.now())) scheduleDatasetLoad(key, orgId, userId);
        return current == null ? null : current.dataset();
    }

    private Dataset requireDataset(String orgId, String userId, boolean refresh) {
        Dataset dataset = availableDataset(orgId, userId, refresh);
        if (dataset == null) throw new ConflictException("CRM 数据正在同步，请稍后重试");
        return dataset;
    }

    private Dataset requireAccountDataset(String orgId, String userId, String accountId, boolean refresh) {
        Dataset dataset = availableDataset(orgId, userId, refresh);
        if (dataset != null && dataset.accounts().stream().anyMatch(item -> accountId.equals(text(item, "id")))) return dataset;
        Dataset focused = loadFocusedDataset(orgId, userId, accountId);
        Dataset merged = dataset == null ? focused : dataset.merge(focused);
        cache.put(cacheKey(orgId, userId), new CacheEntry(merged, Instant.now().plus(CACHE_TTL)));
        return merged;
    }

    private void scheduleDatasetLoad(String key, String orgId, String userId) {
        if (activeLoads.containsKey(key)) return;
        SyncState previous = syncStates.get(key);
        if (previous != null && "FAILED".equals(previous.status()) && previous.completedAt() != null
                && previous.completedAt().plus(FAILED_RETRY_DELAY).isAfter(Instant.now())) return;
        CompletableFuture<Dataset> marker = new CompletableFuture<>();
        if (activeLoads.putIfAbsent(key, marker) != null) return;
        Instant startedAt = Instant.now();
        syncStates.put(key, new SyncState("SYNCING", startedAt, null, "正在同步 CRM 数据"));
        syncExecutor.execute(() -> {
            try {
                Dataset loaded = loadDataset(orgId, userId);
                cache.put(key, new CacheEntry(loaded, Instant.now().plus(CACHE_TTL)));
                syncStates.put(key, new SyncState("READY", startedAt, Instant.now(), "CRM 数据同步完成"));
                marker.complete(loaded);
                log.info("CloudCC dataset sync completed: org={}, user={}, accounts={}, limited={}, durationMs={}",
                        orgId, userId, loaded.accounts().size(), loaded.recordLimitReached(),
                        Duration.between(startedAt, Instant.now()).toMillis());
            } catch (RuntimeException ex) {
                String technicalMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                String userMessage = technicalMessage.contains("令牌") || technicalMessage.contains("登录")
                        ? "CRM 登录状态已失效，请重新进入工作台后重试。"
                        : "CRM 数据同步失败，请稍后重试。";
                syncStates.put(key, new SyncState("FAILED", startedAt, Instant.now(), userMessage));
                marker.completeExceptionally(ex);
                log.warn("CloudCC dataset sync failed: org={}, user={}, durationMs={}, message={}",
                        orgId, userId, Duration.between(startedAt, Instant.now()).toMillis(), technicalMessage);
            } finally {
                activeLoads.remove(key, marker);
            }
        });
    }

    private Dataset loadDataset(String orgId, String userId) {
        CompletableFuture<List<Map<String, Object>>> accounts = queryAsync(() -> queryRequired(orgId, userId, "Account", "id,name,ownerid,lastcontactdate,lastmodifydate,hangye,fenji,unconnecteddays"));
        CompletableFuture<List<Map<String, Object>>> contacts = queryAsync(() -> queryOptional(orgId, userId, "Contact", "id,name,khmc,ownerid,zhiwu,contactrole,scbclxbcrq"));
        CompletableFuture<List<Map<String, Object>>> opportunities = queryAsync(() -> queryOptional(orgId, userId, "Opportunity", "id,name,khmc,ownerid,jieduan,jine,jsrq,xyb,latestcontact,lastmodifydate"));
        CompletableFuture<List<Map<String, Object>>> tasks = queryAsync(() -> queryOptional(orgId, userId, "Task", "id,name,subject,relateid,relateobj,status,priority,expiredate,ownerid,remark,lastmodifydate"));
        CompletableFuture<List<Map<String, Object>>> events = queryAsync(() -> queryOptional(orgId, userId, "Event", "id,name,subject,relateid,relateobj,status,type,begintime,endtime,ownerid,remark,lastmodifydate"));
        CompletableFuture<List<Map<String, Object>>> cases = queryAsync(() -> queryOptional(orgId, userId, "cloudcccase", "id,name,khmc,ownerid,zhuangtai,yxj,duedate,zhuti,problemdescription,lastmodifydate"));
        CompletableFuture<List<Map<String, Object>>> contracts = queryAsync(() -> queryOptional(orgId, userId, "contract", "id,name,khmc,ownerid,zhuangtai,htksrq,htjsrq,htje,lastmodifydate"));
        CompletableFuture.allOf(accounts, contacts, opportunities, tasks, events, cases, contracts).join();
        return Dataset.create(accounts.join(), contacts.join(), opportunities.join(), tasks.join(), events.join(), cases.join(), contracts.join());
    }

    private Dataset loadFocusedDataset(String orgId, String userId, String accountId) {
        if (accountId == null || !accountId.matches("[A-Za-z0-9_-]{3,128}")) {
            throw new IllegalArgumentException("客户标识无效");
        }
        String accountExpression = "id = '" + accountId + "'";
        String relationExpression = "khmc = '" + accountId + "'";
        String activityExpression = "relateid = '" + accountId + "'";
        CompletableFuture<List<Map<String, Object>>> accounts = detailQueryAsync(() -> queryRequiredFiltered(orgId, userId,
                "Account", "id,name,ownerid,lastcontactdate,lastmodifydate,hangye,fenji,unconnecteddays", accountExpression));
        CompletableFuture<List<Map<String, Object>>> contacts = detailQueryAsync(() -> queryOptionalFiltered(orgId, userId,
                "Contact", "id,name,khmc,ownerid,zhiwu,contactrole,scbclxbcrq", relationExpression));
        CompletableFuture<List<Map<String, Object>>> opportunities = detailQueryAsync(() -> queryOptionalFiltered(orgId, userId,
                "Opportunity", "id,name,khmc,ownerid,jieduan,jine,jsrq,xyb,latestcontact,lastmodifydate", relationExpression));
        CompletableFuture<List<Map<String, Object>>> tasks = detailQueryAsync(() -> queryOptionalFiltered(orgId, userId,
                "Task", "id,name,subject,relateid,relateobj,status,priority,expiredate,ownerid,remark,lastmodifydate", activityExpression));
        CompletableFuture<List<Map<String, Object>>> events = detailQueryAsync(() -> queryOptionalFiltered(orgId, userId,
                "Event", "id,name,subject,relateid,relateobj,status,type,begintime,endtime,ownerid,remark,lastmodifydate", activityExpression));
        CompletableFuture<List<Map<String, Object>>> cases = detailQueryAsync(() -> queryOptionalFiltered(orgId, userId,
                "cloudcccase", "id,name,khmc,ownerid,zhuangtai,yxj,duedate,zhuti,problemdescription,lastmodifydate", relationExpression));
        CompletableFuture<List<Map<String, Object>>> contracts = detailQueryAsync(() -> queryOptionalFiltered(orgId, userId,
                "contract", "id,name,khmc,ownerid,zhuangtai,htksrq,htjsrq,htje,lastmodifydate", relationExpression));
        CompletableFuture.allOf(accounts, contacts, opportunities, tasks, events, cases, contracts).join();
        if (accounts.join().isEmpty()) throw new IllegalArgumentException("当前用户无权访问该客户或客户不存在");
        return Dataset.create(accounts.join(), contacts.join(), opportunities.join(), tasks.join(), events.join(), cases.join(), contracts.join());
    }

    private CompletableFuture<List<Map<String, Object>>> queryAsync(Supplier<List<Map<String, Object>>> query) {
        return CompletableFuture.supplyAsync(query, queryExecutor);
    }

    private CompletableFuture<List<Map<String, Object>>> detailQueryAsync(Supplier<List<Map<String, Object>>> query) {
        return CompletableFuture.supplyAsync(query, detailQueryExecutor);
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

    private List<Map<String, Object>> queryRequiredFiltered(String orgId, String userId, String objectApiName,
                                                            String fields, String expressions) {
        try {
            return cloudcc.queryAllRecords(orgId, userId, objectApiName, fields, expressions);
        } catch (RuntimeException ex) {
            log.warn("CloudCC filtered required query failed: org={}, user={}, object={}, message={}",
                    orgId, userId, objectApiName, ex.getMessage());
            return cloudcc.queryAllRecords(orgId, userId, objectApiName, "id,name", expressions);
        }
    }

    private List<Map<String, Object>> queryOptionalFiltered(String orgId, String userId, String objectApiName,
                                                            String fields, String expressions) {
        try {
            return cloudcc.queryAllRecords(orgId, userId, objectApiName, fields, expressions);
        } catch (RuntimeException ex) {
            log.info("CloudCC filtered optional object unavailable: org={}, user={}, object={}, message={}",
                    orgId, userId, objectApiName, ex.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> accountView(Map<String, Object> account, Dataset dataset, boolean followed, long pendingCount) {
        String accountId = text(account, "id");
        List<Map<String, Object>> opportunities = dataset.opportunitiesFor(accountId);
        List<Map<String, Object>> contacts = dataset.contactsFor(accountId);
        List<Map<String, Object>> tasks = dataset.tasksFor(accountId);
        List<Map<String, Object>> events = dataset.eventsFor(accountId);
        List<Map<String, Object>> cases = dataset.casesFor(accountId);
        List<Map<String, Object>> contracts = dataset.contractsFor(accountId);
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
                "pendingRecommendationCount", pendingCount,
                "opportunityCount", opportunities.size(),
                "renewalDays", renewalDays,
                "interactionCount", tasks.size() + events.size(),
                "lastInteraction", interactionSummary(latest),
                "lastInteractionType", latest.isEmpty() ? "CRM" : text(latest, "_source"),
                "stage", stage(opportunities, contracts),
                "tags", tags(account, riskCount, existing),
                "followed", followed,
                "updatedAt", last == null ? "" : last.toString(),
                "dataAsOf", dataset.loadedAt().toString(),
                "source", "CLOUDCC_LIVE"
        );
    }

    private Map<String, Object> emptyQueue(QueueQuery query, String key) {
        Map<String, Object> result = mapOf(
                "items", List.of(),
                "page", Math.max(1, query.page()),
                "size", Math.max(1, Math.min(100, query.size())),
                "totalElements", 0,
                "totalPages", 0,
                "filterCounts", mapOf("all", 0L, "focus", 0L, "follow", 0L, "risk", 0L, "pending", 0L),
                "dataAsOf", "",
                "source", "CLOUDCC_SYNCING",
                "mode", normalizeMode(query.mode()));
        result.putAll(syncMetadata(key, null));
        return result;
    }

    private Map<String, Object> syncMetadata(String key, Dataset dataset) {
        SyncState state = syncStates.get(key);
        boolean syncing = activeLoads.containsKey(key) || state != null && "SYNCING".equals(state.status());
        boolean failed = state != null && "FAILED".equals(state.status());
        boolean limited = dataset != null && dataset.recordLimitReached();
        return mapOf(
                "syncStatus", failed ? "FAILED" : syncing ? "SYNCING" : dataset == null ? "EMPTY" : "READY",
                "syncing", syncing,
                "syncMessage", failed ? state.message() : syncing ? "正在同步 CRM 数据" : limited
                        ? "当前可见客户已达到 10,000 条读取上限，完整增量同步能力待建设。" : "",
                "lastSuccessfulSyncAt", dataset == null ? "" : dataset.loadedAt().toString(),
                "recordLimitReached", limited,
                "recordLimit", RECORD_LIMIT);
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
                    "lifecycleArea", item.getLifecycleArea(), "sourceRecordId", item.getPublicId(),
                    "sourceBatchId", item.getSourceBatchId() == null ? "" : item.getSourceBatchId(),
                    "archiveAvailable", item.getSourceBatchId() != null && !item.getSourceBatchId().isBlank(),
                    "evidenceCount", item.getEvidenceCount(), "analysisVersion", item.getAnalysisVersion()));
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
            Instant now = Instant.now();
            signalRepository.upsertSignal(publicId, orgId, accountId, text(signal, "mode"), text(signal, "type"),
                    text(signal, "title"), text(signal, "detail"), text(signal, "severity"),
                    toJson(signal.get("evidence")), now, now);
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

    private String normalizedSearchTerm(String value) {
        String normalized = value == null ? "" : value.strip().replaceAll("[\\p{Cntrl}]", "");
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private String escapeExpressionLiteral(String value) {
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    private Comparator<Map<String, Object>> queueComparator(String sort, String direction) {
        Comparator<Map<String, Object>> comparator = switch (sort == null ? "interaction" : sort) {
            case "risk" -> Comparator.comparingInt(item -> number(item, "riskCount"));
            case "health" -> Comparator.comparingInt(item -> number(item, "healthScore"));
            case "interaction" -> Comparator.comparing(item -> instant(item, "updatedAt"), Comparator.nullsFirst(Comparator.naturalOrder()));
            case "renewal" -> Comparator.comparingInt(item -> number(item, "renewalDays"));
            case "priority" -> Comparator.comparingInt(item -> number(item, "progressScore") + number(item, "riskCount") * 10);
            default -> Comparator.comparing(item -> instant(item, "updatedAt"), Comparator.nullsFirst(Comparator.naturalOrder()));
        };
        Comparator<Map<String, Object>> ordered = "asc".equalsIgnoreCase(direction) ? comparator : comparator.reversed();
        return ordered.thenComparing(item -> text(item, "accountId"));
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

    private String normalizeMode(String mode) {
        if (mode == null) return "new";
        if ("existing".equalsIgnoreCase(mode)) return "existing";
        if ("all".equalsIgnoreCase(mode)) return "all";
        return "new";
    }

    private String cacheKey(String orgId, String userId) { return orgId + ":" + userId; }

    private static Thread daemonThread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    @PreDestroy
    void shutdownExecutors() {
        syncExecutor.shutdownNow();
        queryExecutor.shutdownNow();
        detailQueryExecutor.shutdownNow();
    }

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
    private record SyncState(String status, Instant startedAt, Instant completedAt, String message) {}

    private record Dataset(List<Map<String, Object>> accounts,
                           Map<String, List<Map<String, Object>>> contactsByAccount,
                           Map<String, List<Map<String, Object>>> opportunitiesByAccount,
                           Map<String, List<Map<String, Object>>> tasksByAccount,
                           Map<String, List<Map<String, Object>>> eventsByAccount,
                           Map<String, List<Map<String, Object>>> casesByAccount,
                           Map<String, List<Map<String, Object>>> contractsByAccount,
                           Instant loadedAt,
                           boolean recordLimitReached) {

        static Dataset create(List<Map<String, Object>> accounts,
                              List<Map<String, Object>> contacts,
                              List<Map<String, Object>> opportunities,
                              List<Map<String, Object>> tasks,
                              List<Map<String, Object>> events,
                              List<Map<String, Object>> cases,
                              List<Map<String, Object>> contracts) {
            return new Dataset(accounts, groupBy(contacts, "khmc"), groupBy(opportunities, "khmc"),
                    groupBy(tasks, "relateid"), groupBy(events, "relateid"), groupBy(cases, "khmc"),
                    groupBy(contracts, "khmc"), Instant.now(), accounts.size() >= RECORD_LIMIT);
        }

        List<Map<String, Object>> contactsFor(String accountId) { return values(contactsByAccount, accountId); }
        List<Map<String, Object>> opportunitiesFor(String accountId) { return values(opportunitiesByAccount, accountId); }
        List<Map<String, Object>> tasksFor(String accountId) { return values(tasksByAccount, accountId); }
        List<Map<String, Object>> eventsFor(String accountId) { return values(eventsByAccount, accountId); }
        List<Map<String, Object>> casesFor(String accountId) { return values(casesByAccount, accountId); }
        List<Map<String, Object>> contractsFor(String accountId) { return values(contractsByAccount, accountId); }

        Dataset merge(Dataset other) {
            LinkedHashMap<String, Map<String, Object>> mergedAccounts = new LinkedHashMap<>();
            accounts.forEach(item -> mergedAccounts.put(text(item, "id"), item));
            other.accounts.forEach(item -> mergedAccounts.put(text(item, "id"), item));
            return new Dataset(List.copyOf(mergedAccounts.values()),
                    mergeIndex(contactsByAccount, other.contactsByAccount),
                    mergeIndex(opportunitiesByAccount, other.opportunitiesByAccount),
                    mergeIndex(tasksByAccount, other.tasksByAccount),
                    mergeIndex(eventsByAccount, other.eventsByAccount),
                    mergeIndex(casesByAccount, other.casesByAccount),
                    mergeIndex(contractsByAccount, other.contractsByAccount),
                    loadedAt, recordLimitReached || other.recordLimitReached);
        }

        private static List<Map<String, Object>> values(Map<String, List<Map<String, Object>>> index, String accountId) {
            return index.getOrDefault(accountId, List.of());
        }

        private static Map<String, List<Map<String, Object>>> groupBy(List<Map<String, Object>> items, String field) {
            return items.stream().filter(item -> !text(item, field).isBlank())
                    .collect(java.util.stream.Collectors.groupingBy(item -> text(item, field)));
        }

        private static Map<String, List<Map<String, Object>>> mergeIndex(
                Map<String, List<Map<String, Object>>> left,
                Map<String, List<Map<String, Object>>> right) {
            Map<String, List<Map<String, Object>>> merged = new HashMap<>(left);
            right.forEach((key, values) -> merged.merge(key, values, (existing, added) -> {
                LinkedHashMap<String, Map<String, Object>> byId = new LinkedHashMap<>();
                existing.forEach(item -> byId.put(text(item, "id"), item));
                added.forEach(item -> byId.put(text(item, "id"), item));
                return List.copyOf(byId.values());
            }));
            return Map.copyOf(merged);
        }
    }
}
