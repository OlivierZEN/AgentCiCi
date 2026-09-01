package com.codehouse.ciciassistant.embed.service;

import com.codehouse.ciciassistant.ai.domain.ChatMessageEntity;
import com.codehouse.ciciassistant.ai.domain.ChatMessageRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionEntity;
import com.codehouse.ciciassistant.ai.domain.ChatSessionRepository;
import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.embed.config.WebsitePresalesProperties;
import com.codehouse.ciciassistant.embed.domain.WebsitePresalesLeadEntity;
import com.codehouse.ciciassistant.embed.domain.WebsitePresalesLeadRepository;
import com.codehouse.ciciassistant.embed.domain.WebsiteVisitSessionEntity;
import com.codehouse.ciciassistant.embed.domain.WebsiteVisitSessionRepository;
import com.codehouse.ciciassistant.embed.domain.WebsiteVisitorProfileEntity;
import com.codehouse.ciciassistant.embed.domain.WebsiteVisitorProfileRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WebsitePresalesLifecycleService {

    private static final int CONTACT_REQUEST_TURN = 6;
    private static final int MAX_TURNS = 8;
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![A-Z0-9._%+-])");
    private static final Pattern SERVICE_INTENT = Pattern.compile(
            "已购买|已经购买|正在使用|使用中|无法|不能登录|登录不了|报错|故障|异常|退款|退费|发票|账单|工单|投诉|实施中|交付中|账号登录|重置密码|密码错误|续费|服务到期|售中问题|售后问题",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRESALES_SERVICE_QUESTION = Pattern.compile(
            "(售后服务|实施服务).*(怎么样|有哪些|如何|是否|包括)|是否.*(售后服务|实施服务)",
            Pattern.CASE_INSENSITIVE);

    private final WebsiteVisitorProfileRepository profileRepository;
    private final WebsiteVisitSessionRepository visitRepository;
    private final WebsitePresalesLeadRepository leadRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SecretCipherService secretCipherService;
    private final WebsitePresalesProperties properties;

    public WebsitePresalesLifecycleService(WebsiteVisitorProfileRepository profileRepository,
                                           WebsiteVisitSessionRepository visitRepository,
                                           WebsitePresalesLeadRepository leadRepository,
                                           ChatSessionRepository chatSessionRepository,
                                           ChatMessageRepository chatMessageRepository,
                                           SecretCipherService secretCipherService,
                                           WebsitePresalesProperties properties) {
        this.profileRepository = profileRepository;
        this.visitRepository = visitRepository;
        this.leadRepository = leadRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.secretCipherService = secretCipherService;
        this.properties = properties;
    }

    public boolean applies(EmbedTokenService.AuthenticatedEmbedToken token) {
        return properties.isEnabled() && "website".equals(token.source());
    }

    @Transactional
    public OpenDecision inspectOpen(EmbedTokenService.AuthenticatedEmbedToken token, String existingChatSessionId) {
        WebsiteVisitorProfileEntity profile = profileRepository
                .findByCompanyIdAndAgentIdAndExternalTenantIdAndExternalUserId(
                        token.companyId(), token.agentId(), token.externalTenantId(), token.externalUserId())
                .orElseGet(() -> profileRepository.saveAndFlush(new WebsiteVisitorProfileEntity(
                        token.companyId(), token.agentId(), token.externalTenantId(), token.externalUserId())));

        if (existingChatSessionId != null && !existingChatSessionId.isBlank()) {
            Optional<WebsiteVisitSessionEntity> current = visitRepository
                    .findByCompanyIdAndChatSessionId(token.companyId(), existingChatSessionId);
            String externalVisitId = text(token.context().get("visitId"));
            if (current.isPresent() && !shouldStartNew(current.get(), externalVisitId)) {
                profile.recordVisit(Instant.now());
                return new OpenDecision(profile.getId(), false, false, profile.getLastSummary(), view(current.get(), profile));
            }
            if (current.isPresent() && !current.get().isClosed()) {
                current.get().close(WebsiteVisitSessionEntity.COMPLETED);
            }
            String summary = current.map(this::visitSummary)
                    .filter(value -> !value.isBlank())
                    .orElseGet(() -> summarize(token.companyId(), existingChatSessionId));
            if (!summary.isBlank()) {
                profile.recordSummary(summary);
            }
        }

        boolean returning = profile.getLastVisitAt() != null || (profile.getLastSummary() != null && !profile.getLastSummary().isBlank());
        return new OpenDecision(profile.getId(), true, returning, profile.getLastSummary(), Map.of());
    }

    @Transactional
    public Map<String, Object> startVisit(String profileId,
                                          String companyId,
                                          String agentId,
                                          String chatSessionId,
                                          boolean returning,
                                          String priorSummary,
                                          String externalVisitId) {
        WebsiteVisitorProfileEntity profile = profileRepository.findById(profileId)
                .filter(item -> companyId.equals(item.getCompanyId()) && agentId.equals(item.getAgentId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Website visitor not found"));
        boolean choiceRequired = returning && priorSummary != null && !priorSummary.isBlank();
        WebsiteVisitSessionEntity visit = visitRepository.saveAndFlush(new WebsiteVisitSessionEntity(
                profileId, companyId, agentId, chatSessionId, externalVisitId, priorSummary, choiceRequired));
        profile.recordVisit(Instant.now());
        return view(visit, profile);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> view(String companyId, String chatSessionId) {
        WebsiteVisitSessionEntity visit = requireVisit(companyId, chatSessionId);
        WebsiteVisitorProfileEntity profile = requireProfile(visit);
        return view(visit, profile);
    }

    @Transactional
    public Map<String, Object> choose(String companyId, String chatSessionId, String rawChoice) {
        WebsiteVisitSessionEntity visit = requireVisit(companyId, chatSessionId);
        if (!WebsiteVisitSessionEntity.AWAITING_CHOICE.equals(visit.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This visit does not require a resume choice");
        }
        String choice = rawChoice == null ? "" : rawChoice.trim().toUpperCase(Locale.ROOT);
        if (!"CONTINUE".equals(choice) && !"START_NEW".equals(choice)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "choice must be CONTINUE or START_NEW");
        }
        visit.choose(choice);
        return view(visit, requireProfile(visit));
    }

    @Transactional
    public TurnDecision beforeTurn(String companyId, String userId, String chatSessionId, String question) {
        WebsiteVisitSessionEntity visit = requireVisit(companyId, chatSessionId);
        WebsiteVisitorProfileEntity profile = requireProfile(visit);
        if (WebsiteVisitSessionEntity.AWAITING_CHOICE.equals(visit.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Choose whether to continue the previous enquiry first");
        }
        if (visit.isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This website visit has ended");
        }

        String normalizedQuestion = question == null ? "" : question.trim();
        int turn = visit.nextTurn(isServiceIntent(normalizedQuestion) ? "SERVICE" : "PRESALES");
        appendSummary(visit, normalizedQuestion);

        if (isServiceIntent(normalizedQuestion)) {
            visit.close(WebsiteVisitSessionEntity.SERVICE_REDIRECTED);
            profile.recordSummary(visitSummary(visit));
            String answer = "这个问题属于售中或售后服务范围。为了保护您的账号与业务数据，我不会在公开页面查询或处理。请登录 CloudCC 系统后，在系统内提交在线工单，服务团队会根据您的身份和业务记录继续处理。";
            persistDirectAnswer(companyId, userId, chatSessionId, normalizedQuestion, visit.getAgentId(), answer);
            return TurnDecision.direct(answer, view(visit, profile));
        }

        Optional<Contact> contact = contact(normalizedQuestion);
        if (contact.isPresent()) {
            captureLead(profile, visit, contact.get());
            visit.close(WebsiteVisitSessionEntity.COMPLETED);
            profile.recordSummary(visitSummary(visit));
            String answer = "谢谢，您的联系方式已安全记录。我们的顾问会结合本次需求与您联系；本次售前咨询先到这里，祝您工作顺利。";
            // The structured lead owns the encrypted contact value. The chat transcript keeps only a
            // redacted copy so a later visit summary or routine history read cannot expose the contact.
            persistDirectAnswer(companyId, userId, chatSessionId, redact(normalizedQuestion), visit.getAgentId(), answer);
            return TurnDecision.direct(answer, view(visit, profile));
        }

        if (turn >= MAX_TURNS) {
            visit.close(WebsiteVisitSessionEntity.COMPLETED);
            profile.recordSummary(visitSummary(visit));
            String answer = profile.isHasLead()
                    ? "本次售前咨询先到这里。我们已经保留您的跟进信息，如需补充新需求，欢迎稍后重新发起咨询。"
                    : "为避免占用您更多时间，本次售前咨询先到这里。如希望顾问继续跟进，请下次咨询时留下手机号或邮箱。";
            persistDirectAnswer(companyId, userId, chatSessionId, normalizedQuestion, visit.getAgentId(), answer);
            return TurnDecision.direct(answer, view(visit, profile));
        }

        if (turn >= CONTACT_REQUEST_TURN && !profile.isHasLead()) {
            visit.requestContact();
            String answer = "为了让售前顾问更准确地继续跟进，请留下手机号或邮箱，并简单说明方便联系的时间。提交即表示您同意我们仅将该联系方式用于本次咨询跟进。";
            persistDirectAnswer(companyId, userId, chatSessionId, normalizedQuestion, visit.getAgentId(), answer);
            return TurnDecision.direct(answer, view(visit, profile));
        }

        visit.activate();
        Map<String, Object> trusted = new LinkedHashMap<>();
        trusted.put("publicPresalesPolicy", true);
        trusted.put("websiteVisitTurn", turn);
        trusted.put("contactAlreadyCaptured", profile.isHasLead());
        if ("CONTINUE".equals(visit.getResumeChoice())
                && visit.getInheritedSummary() != null
                && !visit.getInheritedSummary().isBlank()) {
            trusted.put("previousVisitSummary", visit.getInheritedSummary());
        }
        return TurnDecision.model(trusted, view(visit, profile));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ticketEntry(String companyId, String chatSessionId) {
        requireVisit(companyId, chatSessionId);
        Optional<String> url = validatedTicketUrl();
        return url.<Map<String, Object>>map(value -> Map.of("available", true, "url", value))
                .orElseGet(() -> Map.of("available", false));
    }

    public boolean ticketEntryAvailable() {
        return validatedTicketUrl().isPresent();
    }

    private boolean shouldStartNew(WebsiteVisitSessionEntity visit, String externalVisitId) {
        if (externalVisitId != null && !externalVisitId.isBlank()) {
            return !externalVisitId.equals(visit.getExternalVisitId());
        }
        if (visit.isClosed()) return true;
        return Duration.between(visit.getUpdatedAt(), Instant.now()).toMinutes() >= properties.getIdleMinutes();
    }

    private WebsiteVisitSessionEntity requireVisit(String companyId, String chatSessionId) {
        return visitRepository.findByCompanyIdAndChatSessionId(companyId, chatSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Website visit not found"));
    }

    private WebsiteVisitorProfileEntity requireProfile(WebsiteVisitSessionEntity visit) {
        return profileRepository.findById(visit.getProfileId())
                .filter(item -> visit.getCompanyId().equals(item.getCompanyId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Website visitor not found"));
    }

    private Map<String, Object> view(WebsiteVisitSessionEntity visit, WebsiteVisitorProfileEntity profile) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", visit.getStatus());
        data.put("returningVisitor", visit.getInheritedSummary() != null && !visit.getInheritedSummary().isBlank());
        data.put("resumeChoiceRequired", WebsiteVisitSessionEntity.AWAITING_CHOICE.equals(visit.getStatus()));
        data.put("priorSummary", visit.getInheritedSummary() == null ? "" : visit.getInheritedSummary());
        data.put("contactCaptured", profile.isHasLead());
        data.put("turnCount", visit.getTurnCount());
        data.put("canSend", WebsiteVisitSessionEntity.ACTIVE.equals(visit.getStatus())
                || WebsiteVisitSessionEntity.CONTACT_REQUESTED.equals(visit.getStatus()));
        data.put("ticketEntryAvailable", ticketEntryAvailable());
        return data;
    }

    private void persistDirectAnswer(String companyId,
                                     String userId,
                                     String chatSessionId,
                                     String question,
                                     String agentId,
                                     String answer) {
        ChatSessionEntity session = chatSessionRepository.findByIdAndCompanyIdAndUserId(chatSessionId, companyId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        session.touch(clip(question, 48), agentId);
        chatSessionRepository.save(session);
        chatMessageRepository.save(new ChatMessageEntity(chatSessionId, companyId, "user", question));
        chatMessageRepository.save(new ChatMessageEntity(chatSessionId, companyId, "assistant", answer));
        chatMessageRepository.flush();
    }

    private void captureLead(WebsiteVisitorProfileEntity profile,
                             WebsiteVisitSessionEntity visit,
                             Contact contact) {
        String fingerprint = sha256(String.join("\n",
                profile.getCompanyId(), profile.getAgentId(), profile.getId(), contact.normalized()));
        if (!leadRepository.existsByCompanyIdAndAgentIdAndProfileIdAndContactHash(
                profile.getCompanyId(), profile.getAgentId(), profile.getId(), fingerprint)) {
            SecretCipherService.EncryptedSecret encrypted = secretCipherService.encryptUtf8(contact.normalized());
            leadRepository.save(new WebsitePresalesLeadEntity(
                    profile.getId(), profile.getCompanyId(), profile.getAgentId(), visit.getChatSessionId(),
                    contact.type(), encrypted.cipherBase64(), encrypted.ivBase64(), fingerprint,
                    visitSummary(visit)));
        }
        profile.markLeadCaptured();
    }

    private void appendSummary(WebsiteVisitSessionEntity visit, String question) {
        String safe = redact(question);
        if (safe.isBlank()) return;
        String previous = visit.getCurrentSummary() == null ? "" : visit.getCurrentSummary().trim();
        String combined = previous.isBlank() ? "咨询主题：" + safe : previous + "；补充：" + safe;
        visit.recordCurrentSummary(clip(combined, 800));
    }

    private String visitSummary(WebsiteVisitSessionEntity visit) {
        String current = visit.getCurrentSummary() == null ? "" : visit.getCurrentSummary().trim();
        if (!"CONTINUE".equals(visit.getResumeChoice())) return current;
        String inherited = visit.getInheritedSummary() == null ? "" : visit.getInheritedSummary().trim();
        if (inherited.isBlank()) return current;
        if (current.isBlank()) return inherited;
        return clip(inherited + "；本次补充：" + current, 800);
    }

    private String summarize(String companyId, String chatSessionId) {
        List<ChatMessageEntity> latest = chatMessageRepository.findByCompanyIdAndSessionIdOrderByCreatedAtDesc(
                companyId, chatSessionId, PageRequest.of(0, 12));
        if (latest.isEmpty()) return "";
        List<ChatMessageEntity> ascending = new ArrayList<>(latest);
        Collections.reverse(ascending);
        List<String> topics = ascending.stream()
                .filter(item -> "user".equals(item.getRoleCode()))
                .map(ChatMessageEntity::getContent)
                .map(this::redact)
                .filter(item -> !item.isBlank())
                .map(item -> clip(item, 160))
                .toList();
        if (topics.isEmpty()) return "";
        return clip("上次咨询主题：" + String.join("；", topics), 800);
    }

    private boolean isServiceIntent(String question) {
        return SERVICE_INTENT.matcher(question).find() && !PRESALES_SERVICE_QUESTION.matcher(question).find();
    }

    private Optional<Contact> contact(String question) {
        Matcher mobile = MOBILE.matcher(question);
        if (mobile.find()) return Optional.of(new Contact("MOBILE", mobile.group()));
        Matcher email = EMAIL.matcher(question);
        if (email.find()) return Optional.of(new Contact("EMAIL", email.group().toLowerCase(Locale.ROOT)));
        return Optional.empty();
    }

    private String redact(String value) {
        String redacted = MOBILE.matcher(value == null ? "" : value).replaceAll("[手机号已隐藏]");
        return EMAIL.matcher(redacted).replaceAll("[邮箱已隐藏]").replace('\r', ' ').replace('\n', ' ').trim();
    }

    private Optional<String> validatedTicketUrl() {
        String configured = properties.getCloudccTicketEntryUrl();
        if (configured == null || configured.isBlank()) return Optional.empty();
        try {
            URI uri = URI.create(configured);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                return Optional.empty();
            }
            return Optional.of(uri.toString());
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint contact", exception);
        }
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record Contact(String type, String normalized) {
    }

    public record OpenDecision(String profileId,
                               boolean startNew,
                               boolean returning,
                               String priorSummary,
                               Map<String, Object> lifecycle) {
    }

    public record TurnDecision(boolean direct,
                               String answer,
                               Map<String, Object> trustedContext,
                               Map<String, Object> lifecycle) {
        static TurnDecision direct(String answer, Map<String, Object> lifecycle) {
            return new TurnDecision(true, answer, Map.of(), lifecycle);
        }

        static TurnDecision model(Map<String, Object> trustedContext, Map<String, Object> lifecycle) {
            return new TurnDecision(false, "", trustedContext, lifecycle);
        }
    }
}
