package com.codehouse.ciciassistant.customer.service;

import com.codehouse.ciciassistant.ai.service.AliyunAsrService;
import com.codehouse.ciciassistant.ai.service.AliyunBailianClient;
import com.codehouse.ciciassistant.ai.service.ModelRouterService;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionAssetEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionAssetRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionBatchEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionBatchRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventRepository;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CustomerInteractionIngestionService {

    private static final int MAX_FILES = 12;
    private static final long MAX_FILE_BYTES = 50L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 200L * 1024L * 1024L;
    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_COMBINED_TEXT = 100_000;
    private static final Set<String> SOURCES = Set.of("WECHAT", "PHONE", "MEETING", "EMAIL", "CUSTOMER_FEEDBACK");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "wav", "m4a", "aac", "ogg", "webm", "mp4");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("txt", "md", "markdown", "docx", "pdf");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final CustomerInteractionBatchRepository batchRepository;
    private final CustomerInteractionAssetRepository assetRepository;
    private final CustomerInteractionEventRepository eventRepository;
    private final CustomerWorkbenchService workbenchService;
    private final CustomerMemoryService customerMemoryService;
    private final CustomerDynamicScoringService dynamicScoringService;
    private final CustomerInteractionActionService interactionActionService;
    private final AliyunAsrService asrService;
    private final AliyunBailianClient modelClient;
    private final ModelRouterService modelRouterService;
    private final ModelProviderService modelProviderService;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final Path storageRoot;

    public CustomerInteractionIngestionService(CustomerInteractionBatchRepository batchRepository,
                                               CustomerInteractionAssetRepository assetRepository,
                                               CustomerInteractionEventRepository eventRepository,
                                               CustomerWorkbenchService workbenchService,
                                               CustomerMemoryService customerMemoryService,
                                               CustomerDynamicScoringService dynamicScoringService,
                                               CustomerInteractionActionService interactionActionService,
                                               AliyunAsrService asrService,
                                               AliyunBailianClient modelClient,
                                               ModelRouterService modelRouterService,
                                               ModelProviderService modelProviderService,
                                               ObjectMapper objectMapper,
                                               @Qualifier("agentRuntimeExecutor") Executor executor,
                                               @Value("${app.customer-interaction.storage-dir:./data/kb-files/customer-interactions}") String storageDir) {
        this.batchRepository = batchRepository;
        this.assetRepository = assetRepository;
        this.eventRepository = eventRepository;
        this.workbenchService = workbenchService;
        this.customerMemoryService = customerMemoryService;
        this.dynamicScoringService = dynamicScoringService;
        this.interactionActionService = interactionActionService;
        this.asrService = asrService;
        this.modelClient = modelClient;
        this.modelRouterService = modelRouterService;
        this.modelProviderService = modelProviderService;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @Transactional
    public Map<String, Object> createBatch(String companyId, String userId, String accountId,
                                           String sourceType, String occurredAt, String subject,
                                           String narrationText, String pastedText,
                                           List<MultipartFile> files) {
        workbenchService.accountDetail(companyId, userId, accountId);
        String source = normalizeSource(sourceType);
        Instant occurred = parseInstant(occurredAt);
        String safeSubject = clip(blank(subject), 256);
        String narration = clip(blank(narrationText), 20_000);
        String pasted = clip(blank(pastedText), 50_000);
        List<MultipartFile> uploads = files == null ? List.of() : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        validateUploads(uploads, narration, pasted);

        String batchPublicId = "cib_" + UUID.randomUUID().toString().replace("-", "");
        CustomerInteractionBatchEntity batch = batchRepository.save(new CustomerInteractionBatchEntity(
                batchPublicId, companyId, accountId, userId, source, occurred, safeSubject, narration, pasted));
        Path batchDir = storageRoot.resolve(safeSegment(companyId)).resolve(safeSegment(accountId)).resolve(batchPublicId).normalize();
        ensureUnderStorage(batchDir);
        try {
            Files.createDirectories(batchDir);
            Set<String> uploadHashes = new HashSet<>();
            int order = 0;
            for (MultipartFile upload : uploads) {
                byte[] bytes = upload.getBytes();
                String hash = sha256(bytes);
                if (!uploadHashes.add(hash)) continue;
                String extension = extension(upload.getOriginalFilename());
                String inputType = classify(extension);
                String assetPublicId = "cia_" + UUID.randomUUID().toString().replace("-", "");
                Path path = batchDir.resolve(assetPublicId + (extension.isBlank() ? "" : "." + extension)).normalize();
                ensureUnderStorage(path);
                Files.write(path, bytes);
                assetRepository.save(new CustomerInteractionAssetEntity(
                        assetPublicId, batch.getId(), companyId, inputType,
                        safeFilename(upload.getOriginalFilename(), assetPublicId),
                        clip(blank(upload.getContentType()), 128), bytes.length, hash, path.toString(), order++));
            }
        } catch (IOException ex) {
            batch.markFailed("原始材料保存失败：" + clip(ex.getMessage(), 700));
            batchRepository.save(batch);
            throw new IllegalArgumentException("原始材料保存失败，请重试");
        }
        scheduleAfterCommit(batchPublicId);
        return batchView(batch, assetRepository.findByBatchIdOrderBySortOrderAsc(batch.getId()));
    }

    public List<Map<String, Object>> listBatches(String companyId, String userId, String accountId) {
        workbenchService.accountDetail(companyId, userId, accountId);
        return batchRepository.findTop20ByCompanyIdAndCrmAccountIdOrderByCreatedAtDesc(companyId, accountId).stream()
                .filter(batch -> batch.getCreatedBy().equals(userId))
                .map(batch -> batchView(batch, assetRepository.findByBatchIdOrderBySortOrderAsc(batch.getId())))
                .toList();
    }

    public Map<String, Object> getBatch(String companyId, String userId, String publicId) {
        CustomerInteractionBatchEntity batch = requireOwnedBatch(companyId, userId, publicId);
        return batchView(batch, assetRepository.findByBatchIdOrderBySortOrderAsc(batch.getId()));
    }

    public AssetDownload asset(String companyId, String userId, String batchPublicId, String assetPublicId) {
        CustomerInteractionBatchEntity batch = requireVisibleBatch(companyId, userId, batchPublicId);
        CustomerInteractionAssetEntity asset = assetRepository.findByCompanyIdAndPublicId(companyId, assetPublicId)
                .filter(item -> item.getBatchId().equals(batch.getId()))
                .orElseThrow(() -> new IllegalArgumentException("原始材料不存在"));
        Path path = Path.of(asset.getStoragePath()).toAbsolutePath().normalize();
        ensureUnderStorage(path);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("原始材料文件不存在");
        return new AssetDownload(new FileSystemResource(path), asset.getOriginalName(), asset.getContentType());
    }

    @Transactional
    public Map<String, Object> retry(String companyId, String userId, String publicId) {
        CustomerInteractionBatchEntity batch = requireOwnedBatch(companyId, userId, publicId);
        if (CustomerInteractionBatchEntity.STATUS_CONFIRMED.equals(batch.getStatus())) {
            throw new IllegalArgumentException("已归集批次不能重试");
        }
        assetRepository.findByBatchIdOrderBySortOrderAsc(batch.getId()).stream()
                .filter(asset -> CustomerInteractionAssetEntity.STATUS_FAILED.equals(asset.getStatus()))
                .forEach(CustomerInteractionAssetEntity::resetForRetry);
        batch.queueForRetry();
        batchRepository.save(batch);
        scheduleAfterCommit(publicId);
        return batchView(batch, assetRepository.findByBatchIdOrderBySortOrderAsc(batch.getId()));
    }

    @Transactional
    public Map<String, Object> confirm(String companyId, String userId, String publicId, ConfirmationCommand command) {
        CustomerInteractionBatchEntity batch = requireOwnedBatch(companyId, userId, publicId);
        if (CustomerInteractionBatchEntity.STATUS_CONFIRMED.equals(batch.getStatus())) {
            Map<String, Object> existing = new LinkedHashMap<>(batchView(batch, assetRepository.findByBatchIdOrderBySortOrderAsc(batch.getId())));
            existing.put("deduplicated", true);
            return existing;
        }
        if (!List.of(CustomerInteractionBatchEntity.STATUS_READY, CustomerInteractionBatchEntity.STATUS_PARTIAL).contains(batch.getStatus())) {
            throw new IllegalArgumentException("批次尚未完成整理，不能归集");
        }
        String content = command == null ? "" : blank(command.content());
        if (content.length() < 10 || content.length() > 10_000) {
            throw new IllegalArgumentException("确认稿长度需在 10 到 10000 个字符之间");
        }
        String source = normalizeSource(command == null || blank(command.sourceType()).isBlank() ? batch.getSourceType() : command.sourceType());
        String subject = command == null || blank(command.subject()).isBlank() ? batch.getSubject() : command.subject();
        String occurredAt = command == null || blank(command.occurredAt()).isBlank() ? batch.getOccurredAt().toString() : command.occurredAt();
        Map<String, Object> saved = workbenchService.saveInteraction(companyId, userId, batch.getCrmAccountId(),
                new CustomerWorkbenchService.InteractionCommand(source, subject, content, occurredAt));
        String eventId = String.valueOf(saved.getOrDefault("eventId", ""));
        List<CustomerInteractionAssetEntity> assets = assetRepository.findByBatchIdOrderBySortOrderAsc(batch.getId());
        workbenchService.attachInteractionArchive(companyId, userId, eventId, batch.getPublicId(),
                batch.getAnalysisJson(), assets.size());
        customerMemoryService.replaceForEvent(companyId, batch.getCrmAccountId(), eventId, batch.getPublicId(),
                batch.getOccurredAt(), batch.getAnalysisJson(), assets.stream().map(CustomerInteractionAssetEntity::getPublicId).toList());
        dynamicScoringService.recordAnalysis(companyId, batch.getCrmAccountId(), eventId, batch.getPublicId(),
                source, batch.getOccurredAt(), batch.getAnalysisJson());
        Map<String, Object> actionResult = interactionActionService.recordActions(
                companyId, batch.getCrmAccountId(), eventId, batch.getPublicId(), batch.getOccurredAt(), batch.getAnalysisJson());
        batch.markConfirmed(eventId);
        batchRepository.save(batch);
        Map<String, Object> result = new LinkedHashMap<>(batchView(batch, assets));
        result.put("event", saved);
        result.put("actionResult", actionResult);
        result.put("deduplicated", Boolean.TRUE.equals(saved.get("deduplicated")));
        return result;
    }

    public List<Map<String, Object>> interactionArchive(String companyId, String userId, String accountId) {
        workbenchService.accountDetail(companyId, userId, accountId);
        return eventRepository.findByCompanyIdAndCrmAccountIdOrderByOccurredAtDesc(companyId, accountId).stream()
                .filter(item -> item.getSourceBatchId() != null && !item.getSourceBatchId().isBlank())
                .map(this::archiveSummary).toList();
    }

    public Map<String, Object> interactionArchiveDetail(String companyId, String userId, String eventId) {
        CustomerInteractionEventEntity event = eventRepository.findByCompanyIdAndPublicId(companyId, eventId)
                .orElseThrow(() -> new IllegalArgumentException("互动档案不存在"));
        workbenchService.accountDetail(companyId, userId, event.getCrmAccountId());
        CustomerInteractionBatchEntity batch = batchRepository.findByCompanyIdAndPublicId(companyId, event.getSourceBatchId())
                .orElseThrow(() -> new IllegalArgumentException("互动档案缺少来源批次"));
        List<CustomerInteractionAssetEntity> assets = assetRepository.findByBatchIdOrderBySortOrderAsc(batch.getId());
        Map<String, Object> view = new LinkedHashMap<>(archiveSummary(event));
        view.put("confirmedText", event.getRawSummary());
        view.put("combinedText", batch.getCombinedText());
        view.put("analysis", parseAnalysis(event.getAnalysisJson()));
        view.put("assets", assets.stream().map(this::assetView).toList());
        view.put("memory", customerMemoryService.activeMemory(companyId, event.getCrmAccountId()).stream()
                .filter(item -> eventId.equals(String.valueOf(item.get("sourceEventId")))).toList());
        return view;
    }

    void processBatch(String publicId) {
        CustomerInteractionBatchEntity batch = batchRepository.findByPublicId(publicId).orElse(null);
        if (batch == null || CustomerInteractionBatchEntity.STATUS_CONFIRMED.equals(batch.getStatus())) return;
        try {
            batch.markProcessing();
            batchRepository.save(batch);
            List<CustomerInteractionAssetEntity> assets = assetRepository.findByBatchIdOrderBySortOrderAsc(batch.getId());
            List<String> errors = new ArrayList<>();
            for (CustomerInteractionAssetEntity asset : assets) {
                if (CustomerInteractionAssetEntity.STATUS_READY.equals(asset.getStatus())) continue;
                try {
                    asset.markProcessing();
                    assetRepository.save(asset);
                    String extracted = extract(batch.getCompanyId(), asset);
                    if (extracted.isBlank()) throw new IllegalArgumentException("未提取到有效内容");
                    asset.markReady(clip(extracted, MAX_COMBINED_TEXT));
                } catch (Exception ex) {
                    String message = clip(rootMessage(ex), 700);
                    asset.markFailed(message);
                    errors.add(asset.getOriginalName() + "：" + message);
                }
                assetRepository.save(asset);
            }
            String combined = combinedText(batch, assetRepository.findByBatchIdOrderBySortOrderAsc(batch.getId()));
            if (combined.length() < 10) {
                batch.markFailed(errors.isEmpty() ? "没有可用于整理的有效内容" : String.join("；", errors));
                batchRepository.save(batch);
                return;
            }
            AnalysisResult analysis = analyze(batch.getCompanyId(), combined, customerContext(batch));
            boolean partial = !errors.isEmpty() || analysis.degraded();
            String errorMessage = String.join("；", errors);
            if (analysis.degraded()) errorMessage = joinError(errorMessage, analysis.message());
            batch.markProcessed(combined, analysis.json(), partial, clip(errorMessage, 1000));
            batchRepository.save(batch);
        } catch (Exception ex) {
            batch.markFailed(clip(rootMessage(ex), 1000));
            batchRepository.save(batch);
        }
    }

    private void schedule(String publicId) {
        CompletableFuture.runAsync(() -> processBatch(publicId), executor);
    }

    private void scheduleAfterCommit(String publicId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            schedule(publicId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                schedule(publicId);
            }
        });
    }

    @Scheduled(
            fixedDelayString = "${app.customer-interaction.recovery-delay-ms:60000}",
            initialDelayString = "${app.customer-interaction.recovery-initial-delay-ms:20000}")
    public void recoverStalledBatches() {
        Instant now = Instant.now();
        List<CustomerInteractionBatchEntity> stalled = new ArrayList<>();
        stalled.addAll(batchRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                CustomerInteractionBatchEntity.STATUS_QUEUED, now.minusSeconds(120)));
        stalled.addAll(batchRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                CustomerInteractionBatchEntity.STATUS_PROCESSING, now.minusSeconds(900)));
        stalled.stream().map(CustomerInteractionBatchEntity::getPublicId).distinct().forEach(this::schedule);
    }

    private String extract(String companyId, CustomerInteractionAssetEntity asset) throws Exception {
        Path path = Path.of(asset.getStoragePath()).toAbsolutePath().normalize();
        ensureUnderStorage(path);
        byte[] bytes = Files.readAllBytes(path);
        return switch (asset.getInputType()) {
            case "AUDIO" -> transcribeAudio(bytes, asset);
            case "IMAGE" -> ocrImage(companyId, bytes, asset.getContentType());
            case "DOCUMENT" -> readDocument(bytes, extension(asset.getOriginalName()));
            default -> throw new IllegalArgumentException("不支持的材料类型");
        };
    }

    private String transcribeAudio(byte[] bytes, CustomerInteractionAssetEntity asset) {
        var result = asrService.transcribeMeetingFile(bytes, asset.getOriginalName(), asset.getContentType());
        StringBuilder transcript = new StringBuilder();
        result.transcript().forEach(segment -> {
            String speaker = blank(segment.speakerName());
            if (speaker.isBlank()) speaker = "发言人 " + (blank(segment.speakerId()).isBlank() ? "1" : segment.speakerId());
            if (!blank(segment.text()).isBlank()) transcript.append(speaker).append("：").append(segment.text().trim()).append('\n');
        });
        return transcript.toString().trim();
    }

    private String ocrImage(String companyId, byte[] bytes, String contentType) {
        if (bytes.length > MAX_IMAGE_BYTES) throw new IllegalArgumentException("单张截图不能超过 10MB");
        Map<String, String> credentials = modelProviderService.credentialsForProvider(companyId, ModelProviderService.PROVIDER_ALIYUN);
        if (!Boolean.parseBoolean(credentials.getOrDefault("enabled", "false"))) {
            throw new IllegalArgumentException("图片 OCR 所需的阿里云视觉模型未启用");
        }
        String mime = blank(contentType).startsWith("image/") ? contentType : "image/png";
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        List<Map<String, Object>> content = List.of(
                Map.of("type", "text", "text", "请逐行提取这张客户沟通截图中的可见文字。保留发送人、时间和消息顺序；不要总结、推断或补造。只输出 OCR 文本。"),
                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
        var response = modelClient.chatCompletionWithCredentials("qwen-vl-plus", List.of(
                Map.of("role", "user", "content", content)
        ), null, true, credentials.get("apiBaseUrl"), credentials.get("apiKey"));
        String text = blank(response.content());
        if (isModelFailure(text)) throw new IllegalArgumentException(text);
        return text;
    }

    private String readDocument(byte[] bytes, String extension) throws Exception {
        return switch (extension) {
            case "txt", "md", "markdown" -> new String(bytes, StandardCharsets.UTF_8).replace("\u0000", "").trim();
            case "pdf" -> readPdf(bytes);
            case "docx" -> readDocx(bytes);
            default -> throw new IllegalArgumentException("不支持的文档类型");
        };
    }

    private String readPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) throw new IllegalArgumentException("PDF 已加密，无法解析");
            String text = new PDFTextStripper().getText(document);
            String normalized = blank(text).replace('\u0000', ' ').trim();
            if (normalized.isBlank()) throw new IllegalArgumentException("扫描型 PDF 暂未提取到文本，请改为上传页面截图");
            return normalized;
        }
    }

    private String readDocx(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && ("word/document.xml".equals(entry.getName())
                        || entry.getName().matches("word/(header|footer|footnotes|endnotes)\\d*\\.xml"))) {
                    appendDocxXml(zip.readAllBytes(), text);
                }
                zip.closeEntry();
            }
        }
        String normalized = text.toString().replaceAll("[\\t ]+\\n", "\n").trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("DOCX 中没有可读取文本");
        return normalized;
    }

    private void appendDocxXml(byte[] xml, StringBuilder output) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        try (InputStream input = new ByteArrayInputStream(xml)) {
            XMLStreamReader reader = factory.createXMLStreamReader(input, StandardCharsets.UTF_8.name());
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) output.append(reader.getText());
                else if (event == XMLStreamConstants.START_ELEMENT && "tab".equals(reader.getLocalName())) output.append('\t');
                else if (event == XMLStreamConstants.END_ELEMENT && List.of("p", "br", "tr").contains(reader.getLocalName())) output.append('\n');
            }
            reader.close();
        }
    }

    private AnalysisResult analyze(String companyId, String combined, Map<String, Object> customerContext) {
        String crmContext = json(customerContext);
        String prompt = """
                请分析下面的客户原始沟通材料，并且只输出一个 JSON 对象，不要输出 Markdown 或代码围栏。
                禁止补造材料中没有的人名、日期、金额、承诺和结论。不确定内容必须放入 pendingQuestions。

                JSON 字段固定为：
                summary: string
                facts: string[]
                customerNeeds: string[]
                risks: string[]
                opportunities: string[]
                commitments: string[]
                nextActions: string[]
                pendingQuestions: string[]
                sentiment: POSITIVE | NEUTRAL | NEGATIVE
                scoringSignals: object[]，每一项字段固定为：
                  dimension: HEALTH | EXPANSION | RENEWAL | RELATIONSHIP | RISK
                  direction: POSITIVE | NEGATIVE
                  impact: 1 到 10 的整数
                  confidence: 0 到 1 的小数
                  title: string
                  evidence: string，必须是原始材料中可核验的原句或紧邻事实
                  reason: string，说明为什么影响该维度
                  validDays: 7 到 365 的整数
                actionCandidates: object[]，每一项字段固定为：
                  actionType: CREATE_TASK | CREATE_OPPORTUNITY | UPDATE_OPPORTUNITY
                  businessKey: string，稳定业务键，例如 expansion:mobile-inspection 或 followup:budget-approver
                  title: string，可直接作为 CRM 任务主题或商机名称
                  reason: string，说明为什么现在需要执行
                  evidence: string，必须是本次原始材料中的可核验原句
                  confidence: 0 到 1 的小数
                  dueInDays: 1 到 90 的整数
                  validDays: 7 到 180 的整数
                  targetRecordId: string，仅 UPDATE_OPPORTUNITY 必填，必须来自下面 CRM 上下文中的商机 id

                评分信号规则：
                1. 只依据本次材料识别，不得因联系人、合同或工单数量机械加减分。
                2. HEALTH 表示总体使用与合作健康，EXPANSION 表示增购可能，RENEWAL 表示续约质量，RELATIONSHIP 表示关键关系，RISK 表示风险程度。
                3. 对 HEALTH/EXPANSION/RENEWAL/RELATIONSHIP，POSITIVE 表示改善；对 RISK，NEGATIVE 表示风险上升，POSITIVE 表示风险缓解。
                4. 没有明确证据时不要生成信号；不确定事项仍放入 pendingQuestions。

                经营动作规则：
                1. 只生成销售人员可以执行且值得写入 CRM 的动作，纯信息摘要不要生成动作。
                2. 明确的新采购、增购或独立项目需求可生成 CREATE_OPPORTUNITY；已有商机的阶段、预算、方案或下一步发生变化时生成 UPDATE_OPPORTUNITY。
                3. 回访、材料补充、关系维护、服务闭环、续约准备和待确认事项生成 CREATE_TASK。
                4. 不确定是否为新商机时不要创建商机，应放入 pendingQuestions 或生成核实任务。
                5. actionCandidates 的证据只能来自本次原始材料；CRM 上下文只用于识别已有商机及 targetRecordId。

                当前 CRM 客户上下文：
                %s

                原始材料：
                %s
                """.formatted(clip(crmContext, 12_000), clip(combined, 60_000));
        Map<String, String> route = modelRouterService.route(companyId, "customer-insight");
        Map<String, String> credentials = modelProviderService.credentialsForProvider(companyId, route.get("provider"));
        if (!Boolean.parseBoolean(credentials.getOrDefault("enabled", "false"))) {
            return fallbackAnalysis(combined, "客户洞察模型未启用，已保留统一草稿");
        }
        var response = modelClient.chatCompletionWithCredentials(route.get("modelName"), List.of(
                Map.of("role", "system", "content", "你是客户沟通事实整理助手。只返回严格 JSON，不得改写或删除原始材料。"),
                Map.of("role", "user", "content", prompt)
        ), null, true, credentials.get("apiBaseUrl"), credentials.get("apiKey"));
        String content = stripCodeFence(blank(response.content()));
        if (isModelFailure(content)) return fallbackAnalysis(combined, clip(content, 500));
        try {
            Map<String, Object> parsed = objectMapper.readValue(content, MAP_TYPE);
            Map<String, Object> normalized = normalizeAnalysis(parsed, combined, false);
            return new AnalysisResult(objectMapper.writeValueAsString(normalized), false, "");
        } catch (Exception ex) {
            return fallbackAnalysis(combined, "模型分析格式异常，已保留统一草稿");
        }
    }

    private AnalysisResult fallbackAnalysis(String combined, String message) {
        try {
            return new AnalysisResult(objectMapper.writeValueAsString(normalizeAnalysis(Map.of(), combined, true)), true, message);
        } catch (Exception ex) {
            return new AnalysisResult("{}", true, message);
        }
    }

    private Map<String, Object> normalizeAnalysis(Map<String, Object> value, String combined, boolean degraded) {
        Map<String, Object> result = new LinkedHashMap<>();
        String summary = blank(value.get("summary"));
        result.put("summary", summary.isBlank() ? clip(combined.replace('\n', ' '), 300) : clip(summary, 2000));
        for (String key : List.of("facts", "customerNeeds", "risks", "opportunities", "commitments", "nextActions", "pendingQuestions")) {
            result.put(key, stringList(value.get(key), 30));
        }
        result.put("scoringSignals", scoringSignals(value.get("scoringSignals")));
        result.put("actionCandidates", actionCandidates(value.get("actionCandidates"), combined));
        String sentiment = blank(value.get("sentiment")).toUpperCase(Locale.ROOT);
        result.put("sentiment", List.of("POSITIVE", "NEUTRAL", "NEGATIVE").contains(sentiment) ? sentiment : "NEUTRAL");
        result.put("degraded", degraded);
        return result;
    }

    private List<Map<String, Object>> scoringSignals(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            String dimension = blank(raw.get("dimension")).toUpperCase(Locale.ROOT);
            String direction = blank(raw.get("direction")).toUpperCase(Locale.ROOT);
            String title = clip(blank(raw.get("title")), 256);
            String evidence = clip(blank(raw.get("evidence")), 2000);
            if (!Set.of("HEALTH", "EXPANSION", "RENEWAL", "RELATIONSHIP", "RISK").contains(dimension)
                    || !Set.of("POSITIVE", "NEGATIVE").contains(direction) || title.isBlank() || evidence.isBlank()) continue;
            Map<String, Object> signal = new LinkedHashMap<>();
            signal.put("dimension", dimension);
            signal.put("direction", direction);
            signal.put("impact", boundedInteger(raw.get("impact"), 1, 10, 5));
            signal.put("confidence", boundedDecimal(raw.get("confidence"), 0, 1, 0.5));
            signal.put("title", title);
            signal.put("evidence", evidence);
            signal.put("reason", clip(blank(raw.get("reason")), 2000));
            signal.put("validDays", boundedInteger(raw.get("validDays"), 7, 365, 90));
            result.add(signal);
            if (result.size() >= 30) break;
        }
        return result;
    }

    private List<Map<String, Object>> actionCandidates(Object value, String combined) {
        if (!(value instanceof List<?> list)) return List.of();
        String sourceText = normalizedEvidence(combined);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            String actionType = blank(raw.get("actionType")).toUpperCase(Locale.ROOT);
            String businessKey = clip(blank(raw.get("businessKey")), 128);
            String title = clip(blank(raw.get("title")), 256);
            String reason = clip(blank(raw.get("reason")), 2000);
            String evidence = clip(blank(raw.get("evidence")), 2000);
            String targetRecordId = clip(blank(raw.get("targetRecordId")), 128);
            if (!Set.of("CREATE_TASK", "CREATE_OPPORTUNITY", "UPDATE_OPPORTUNITY").contains(actionType)
                    || businessKey.isBlank() || title.isBlank() || reason.isBlank() || evidence.isBlank()
                    || !sourceText.contains(normalizedEvidence(evidence))) continue;
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("actionType", actionType);
            action.put("businessKey", businessKey);
            action.put("title", title);
            action.put("reason", reason);
            action.put("evidence", evidence);
            action.put("confidence", boundedDecimal(raw.get("confidence"), 0, 1, 0.5));
            action.put("dueInDays", boundedInteger(raw.get("dueInDays"), 1, 90, 7));
            action.put("validDays", boundedInteger(raw.get("validDays"), 7, 180, 30));
            action.put("targetRecordId", targetRecordId);
            result.add(action);
            if (result.size() >= 20) break;
        }
        return result;
    }

    private static String normalizedEvidence(String value) {
        return blank(value).replaceAll("\\s+", " ");
    }

    private Map<String, Object> customerContext(CustomerInteractionBatchEntity batch) {
        try {
            Map<String, Object> detail = workbenchService.accountDetail(
                    batch.getCompanyId(), batch.getCreatedBy(), batch.getCrmAccountId());
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("accountId", batch.getCrmAccountId());
            context.put("name", detail.getOrDefault("name", ""));
            context.put("customerMode", detail.getOrDefault("customerMode", ""));
            context.put("opportunityCount", detail.getOrDefault("opportunityCount", 0));
            Object opportunities = detail.get("opportunities");
            context.put("opportunities", opportunities instanceof List<?> list ? list.stream().limit(10).toList() : List.of());
            return context;
        } catch (RuntimeException ex) {
            return Map.of("accountId", batch.getCrmAccountId(), "crmContextAvailable", false);
        }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { return "{}"; }
    }

    private static int boundedInteger(Object value, int min, int max, int fallback) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(blank(value)))); }
        catch (Exception ex) { return fallback; }
    }

    private static double boundedDecimal(Object value, double min, double max, double fallback) {
        try { return Math.max(min, Math.min(max, Double.parseDouble(blank(value)))); }
        catch (Exception ex) { return fallback; }
    }

    private List<String> stringList(Object value, int limit) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(CustomerInteractionIngestionService::blank).filter(text -> !text.isBlank())
                .map(text -> clip(text, 1000)).limit(limit).toList();
    }

    private String combinedText(CustomerInteractionBatchEntity batch, List<CustomerInteractionAssetEntity> assets) {
        StringBuilder text = new StringBuilder();
        appendSection(text, "销售人员口述", batch.getNarrationText());
        appendSection(text, "手工粘贴内容", batch.getPastedText());
        for (CustomerInteractionAssetEntity asset : assets) {
            if (CustomerInteractionAssetEntity.STATUS_READY.equals(asset.getStatus())) {
                appendSection(text, asset.getInputType() + " · " + asset.getOriginalName(), asset.getExtractedText());
            }
        }
        return clip(text.toString().trim(), MAX_COMBINED_TEXT);
    }

    private void appendSection(StringBuilder target, String title, String content) {
        if (blank(content).isBlank()) return;
        if (!target.isEmpty()) target.append("\n\n");
        target.append("【").append(title).append("】\n").append(content.trim());
    }

    private Map<String, Object> batchView(CustomerInteractionBatchEntity batch, List<CustomerInteractionAssetEntity> assets) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("batchId", batch.getPublicId());
        view.put("accountId", batch.getCrmAccountId());
        view.put("sourceType", batch.getSourceType());
        view.put("occurredAt", batch.getOccurredAt().toString());
        view.put("subject", batch.getSubject());
        view.put("narrationText", batch.getNarrationText());
        view.put("pastedText", batch.getPastedText());
        view.put("status", batch.getStatus());
        view.put("combinedText", batch.getCombinedText());
        view.put("analysis", parseAnalysis(batch.getAnalysisJson()));
        view.put("errorMessage", batch.getErrorMessage());
        view.put("confirmedEventId", blank(batch.getConfirmedEventId()));
        view.put("createdAt", batch.getCreatedAt().toString());
        view.put("updatedAt", batch.getUpdatedAt().toString());
        view.put("assets", assets.stream().map(this::assetView).toList());
        return view;
    }

    private Map<String, Object> assetView(CustomerInteractionAssetEntity asset) {
        return Map.ofEntries(
                Map.entry("assetId", asset.getPublicId()),
                Map.entry("inputType", asset.getInputType()),
                Map.entry("name", asset.getOriginalName()),
                Map.entry("contentType", asset.getContentType()),
                Map.entry("size", asset.getFileSize()),
                Map.entry("sha256", asset.getSha256()),
                Map.entry("sortOrder", asset.getSortOrder()),
                Map.entry("status", asset.getStatus()),
                Map.entry("extractedText", asset.getExtractedText()),
                Map.entry("errorMessage", asset.getErrorMessage()));
    }

    private Map<String, Object> archiveSummary(CustomerInteractionEventEntity event) {
        return Map.ofEntries(
                Map.entry("eventId", event.getPublicId()),
                Map.entry("accountId", event.getCrmAccountId()),
                Map.entry("batchId", event.getSourceBatchId() == null ? "" : event.getSourceBatchId()),
                Map.entry("sourceType", event.getSourceType()),
                Map.entry("occurredAt", event.getOccurredAt().toString()),
                Map.entry("subject", event.getSubject()),
                Map.entry("summary", event.getAiSummary()),
                Map.entry("sentiment", event.getSentiment()),
                Map.entry("intentTags", parseList(event.getIntentTags())),
                Map.entry("analysisVersion", event.getAnalysisVersion()),
                Map.entry("evidenceCount", event.getEvidenceCount()),
                Map.entry("archiveAvailable", true));
    }

    private Object parseAnalysis(String json) {
        try { return objectMapper.readValue(blank(json).isBlank() ? "{}" : json, MAP_TYPE); }
        catch (Exception ex) { return Map.of(); }
    }

    private CustomerInteractionBatchEntity requireOwnedBatch(String companyId, String userId, String publicId) {
        return batchRepository.findByCompanyIdAndPublicId(companyId, publicId)
                .filter(batch -> batch.getCreatedBy().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("互动采集批次不存在或无权访问"));
    }

    private CustomerInteractionBatchEntity requireVisibleBatch(String companyId, String userId, String publicId) {
        CustomerInteractionBatchEntity batch = batchRepository.findByCompanyIdAndPublicId(companyId, publicId)
                .orElseThrow(() -> new IllegalArgumentException("互动采集批次不存在或无权访问"));
        workbenchService.accountDetail(companyId, userId, batch.getCrmAccountId());
        return batch;
    }

    private List<Object> parseList(String json) {
        try { return objectMapper.readValue(blank(json).isBlank() ? "[]" : json, new TypeReference<>() {}); }
        catch (Exception ex) { return List.of(); }
    }

    private void validateUploads(List<MultipartFile> files, String narration, String pasted) {
        if (files.size() > MAX_FILES) throw new IllegalArgumentException("一次最多上传 12 个文件");
        long total = 0;
        for (MultipartFile file : files) {
            if (file.getSize() > MAX_FILE_BYTES) throw new IllegalArgumentException("单个文件不能超过 50MB");
            total += file.getSize();
            classify(extension(file.getOriginalFilename()));
        }
        if (total > MAX_TOTAL_BYTES) throw new IllegalArgumentException("单次上传总量不能超过 200MB");
        if (files.isEmpty() && blank(narration).isBlank() && blank(pasted).length() < 10) {
            throw new IllegalArgumentException("请至少提供语音描述、文件或 10 个字符的文本内容");
        }
    }

    private String classify(String extension) {
        if (IMAGE_EXTENSIONS.contains(extension)) return "IMAGE";
        if (AUDIO_EXTENSIONS.contains(extension)) return "AUDIO";
        if (DOCUMENT_EXTENSIONS.contains(extension)) return "DOCUMENT";
        throw new IllegalArgumentException("不支持的文件类型：" + (extension.isBlank() ? "未知" : extension));
    }

    private String normalizeSource(String sourceType) {
        String value = blank(sourceType).toUpperCase(Locale.ROOT);
        if (!SOURCES.contains(value)) throw new IllegalArgumentException("互动来源不受支持");
        return value;
    }

    private Instant parseInstant(String value) {
        try { return blank(value).isBlank() ? Instant.now() : Instant.parse(value); }
        catch (Exception ex) { throw new IllegalArgumentException("发生时间格式不正确"); }
    }

    private void ensureUnderStorage(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(storageRoot)) throw new IllegalArgumentException("非法存储路径");
    }

    private static String safeSegment(String value) {
        return blank(value).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String safeFilename(String value, String fallback) {
        String name = blank(value).replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\r\\n\\t]", " ").trim();
        return clip(name.isBlank() ? fallback : name, 255);
    }

    private static String extension(String filename) {
        String name = blank(filename).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).replaceAll("[^a-z0-9]", "");
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算材料哈希", ex);
        }
    }

    private static boolean isModelFailure(String content) {
        return content.isBlank() || content.startsWith("Model call failed:") || content.startsWith("Aliyun API key is not configured.")
                || content.startsWith("Empty response.") || content.startsWith("No choices in response.");
    }

    private static String stripCodeFence(String value) {
        String text = blank(value).trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) text = text.substring(firstNewline + 1, lastFence).trim();
        }
        return text;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return blank(current.getMessage()).isBlank() ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String joinError(String first, String second) {
        if (blank(first).isBlank()) return blank(second);
        if (blank(second).isBlank()) return blank(first);
        return first + "；" + second;
    }

    private static String blank(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static String clip(String value, int max) {
        String text = blank(value);
        return text.length() <= max ? text : text.substring(0, max);
    }

    public record ConfirmationCommand(String sourceType, String subject, String content, String occurredAt) {}
    public record AssetDownload(Resource resource, String filename, String contentType) {}
    private record AnalysisResult(String json, boolean degraded, String message) {}
}
