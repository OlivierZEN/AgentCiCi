package com.codehouse.ciciassistant.email.service;

import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.email.domain.EmailAccountEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.mail.Address;
import jakarta.mail.Authenticator;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runtime email tool: POP3 (read) + SMTP (send) via Jakarta Mail. All operations act on behalf of
 * the currently logged in user's default email account (resolved via {@link EmailAccountService}).
 *
 * <p>Phase 1 notes:
 * <ul>
 *   <li>POP3 is not a server-side search protocol; {@code email_search} filters locally.</li>
 *   <li>POP3 does not expose {@code \Seen}, so {@code unreadOnly} is intentionally not exposed.</li>
 *   <li>messageId stable key is taken from the standard {@code Message-ID} header when present.</li>
 *   <li>No message deletion / mailbox mutation is performed.</li>
 * </ul>
 */
@Service
public class EmailToolService {

    private static final Logger log = LoggerFactory.getLogger(EmailToolService.class);

    public static final String TOOL_LIST_INBOX = "email_list_inbox";
    public static final String TOOL_SEARCH = "email_search";
    public static final String TOOL_GET_MESSAGE = "email_get_message";
    public static final String TOOL_SEND = "email_send";
    public static final String TOOL_REPLY = "email_reply";

    public static final List<String> ALL_TOOL_NAMES = List.of(
            TOOL_LIST_INBOX, TOOL_SEARCH, TOOL_GET_MESSAGE, TOOL_SEND, TOOL_REPLY);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_BODY_PREVIEW = 200;
    private static final int MAX_BODY_BYTES = 200_000;
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final EmailAccountService emailAccountService;
    private final SecretCipherService secretCipherService;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.ConcurrentMap<String, RateWindow> rateMap = new java.util.concurrent.ConcurrentHashMap<>();

    public EmailToolService(EmailAccountService emailAccountService,
                            SecretCipherService secretCipherService,
                            ObjectMapper objectMapper) {
        this.emailAccountService = emailAccountService;
        this.secretCipherService = secretCipherService;
        this.objectMapper = objectMapper;
    }

    private static final int RATE_LIMIT_READ_PER_MIN = 20;
    private static final int RATE_LIMIT_WRITE_PER_MIN = 10;

    private record RateWindow(long windowStart, int count) {
    }

    // =============================================================================================
    // Tool definition (function-calling schema) for ToolOrchestratorService
    // =============================================================================================

    public List<Map<String, Object>> toolDefinitions() {
        List<Map<String, Object>> all = new ArrayList<>();
        all.add(functionTool(TOOL_LIST_INBOX, description(TOOL_LIST_INBOX), listInboxSchema()));
        all.add(functionTool(TOOL_SEARCH, description(TOOL_SEARCH), searchSchema()));
        all.add(functionTool(TOOL_GET_MESSAGE, description(TOOL_GET_MESSAGE), getMessageSchema()));
        all.add(functionTool(TOOL_SEND, description(TOOL_SEND), sendSchema()));
        all.add(functionTool(TOOL_REPLY, description(TOOL_REPLY), replySchema()));
        return all;
    }

    public Map<String, Object> toolDefinition(String toolName) {
        return switch (toolName) {
            case TOOL_LIST_INBOX -> functionTool(TOOL_LIST_INBOX, description(TOOL_LIST_INBOX), listInboxSchema());
            case TOOL_SEARCH -> functionTool(TOOL_SEARCH, description(TOOL_SEARCH), searchSchema());
            case TOOL_GET_MESSAGE -> functionTool(TOOL_GET_MESSAGE, description(TOOL_GET_MESSAGE), getMessageSchema());
            case TOOL_SEND -> functionTool(TOOL_SEND, description(TOOL_SEND), sendSchema());
            case TOOL_REPLY -> functionTool(TOOL_REPLY, description(TOOL_REPLY), replySchema());
            default -> throw new IllegalArgumentException("未知邮件工具名: " + toolName);
        };
    }

    public String description(String toolName) {
        return switch (toolName) {
            case TOOL_LIST_INBOX -> "列出当前用户邮箱收件箱最近的邮件摘要（POP3 协议下不支持未读过滤）。";
            case TOOL_SEARCH -> "在最近若干封邮件内按关键字 / 发件人过滤，POP3 下为本地过滤。";
            case TOOL_GET_MESSAGE -> "按 messageId 读取一封邮件的完整正文（纯文本摘要）。";
            case TOOL_SEND -> "以当前用户身份发送新邮件。若账号开启了二次确认，模型须先请用户确认后再带 confirmed=true 调用。";
            case TOOL_REPLY -> "对指定 messageId 回复一封邮件（通过信头 In-Reply-To / References 拼接线程）。同样遵循二次确认开关。";
            default -> "email tool";
        };
    }

    // =============================================================================================
    // Execution entry point called by ToolOrchestratorService
    // =============================================================================================

    public String dispatch(String orgId, String userId, String toolName, String argumentsJson) {
        EmailAccountEntity account = emailAccountService.findDefaultAccount(orgId, userId).orElse(null);
        if (account == null) {
            return "❌ 当前用户尚未配置邮箱，请先在「个人信息 → 我的邮箱」中绑定。";
        }
        String rateDenyMessage = checkRateLimit(orgId, userId, toolName);
        if (rateDenyMessage != null) {
            return rateDenyMessage;
        }
        JsonNode args = readArgs(argumentsJson);
        try {
            return switch (toolName) {
                case TOOL_LIST_INBOX -> listInbox(account, args);
                case TOOL_SEARCH -> search(account, args);
                case TOOL_GET_MESSAGE -> getMessage(account, args);
                case TOOL_SEND -> send(account, args);
                case TOOL_REPLY -> reply(account, args);
                default -> "❌ 未知邮件工具: " + toolName;
            };
        } catch (Exception ex) {
            log.warn("Email tool {} failed for org={} user={}: {}", toolName, orgId, userId, ex.toString());
            return failure(ex);
        }
    }

    // =============================================================================================
    // Verify connection (used by /me/email-accounts/{id}/verify)
    // =============================================================================================

    /**
     * Attempts a POP3 login and an SMTP handshake without sending a message. Throws on failure.
     */
    public void verifyConnection(EmailAccountEntity account) throws MessagingException {
        String password = secretCipherService.decryptUtf8(account.getSecretCipher(), account.getSecretIv());

        Store store = openPop3Store(account, password);
        store.close();

        Session smtpSession = smtpSession(account, password);
        try (Transport transport = smtpSession.getTransport(smtpTransportProtocol(account))) {
            transport.connect(account.getSmtpHost(), account.getSmtpPort(), account.getLoginUsername(), password);
        }
    }

    // =============================================================================================
    // POP3 read operations
    // =============================================================================================

    private String listInbox(EmailAccountEntity account, JsonNode args) throws MessagingException, IOException {
        int limit = clampLimit(args.path("limit").asInt(DEFAULT_LIMIT));
        String password = decrypt(account);
        Store store = openPop3Store(account, password);
        try {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            try {
                int total = inbox.getMessageCount();
                if (total <= 0) {
                    return "📭 收件箱为空。";
                }
                int start = Math.max(1, total - limit + 1);
                Message[] recent = inbox.getMessages(start, total);
                List<MessageSummary> summaries = new ArrayList<>();
                for (int i = recent.length - 1; i >= 0; i--) {
                    summaries.add(summarize(recent[i]));
                }
                return renderInbox(account, summaries);
            } finally {
                inbox.close(false);
            }
        } finally {
            store.close();
        }
    }

    private String search(EmailAccountEntity account, JsonNode args) throws MessagingException, IOException {
        int limit = clampLimit(args.path("limit").asInt(DEFAULT_LIMIT));
        int scanLimit = clampLimit(args.path("scanLimit").asInt(MAX_LIMIT));
        if (scanLimit < limit) {
            scanLimit = limit;
        }
        String keyword = safeText(args.path("keyword").asText(""));
        String fromFilter = safeText(args.path("from").asText(""));

        String password = decrypt(account);
        Store store = openPop3Store(account, password);
        try {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            try {
                int total = inbox.getMessageCount();
                if (total <= 0) {
                    return "📭 收件箱为空，无法过滤。";
                }
                int start = Math.max(1, total - scanLimit + 1);
                Message[] scanned = inbox.getMessages(start, total);
                List<MessageSummary> hits = new ArrayList<>();
                for (int i = scanned.length - 1; i >= 0; i--) {
                    MessageSummary summary = summarize(scanned[i]);
                    if (matches(summary, keyword, fromFilter)) {
                        hits.add(summary);
                    }
                    if (hits.size() >= limit) {
                        break;
                    }
                }
                if (hits.isEmpty()) {
                    return "🔎 在最近 " + scanned.length + " 封邮件中未命中过滤条件 "
                            + "(keyword='" + keyword + "', from='" + fromFilter + "')。";
                }
                return renderSearch(account, hits, keyword, fromFilter, scanned.length);
            } finally {
                inbox.close(false);
            }
        } finally {
            store.close();
        }
    }

    private String getMessage(EmailAccountEntity account, JsonNode args) throws MessagingException, IOException {
        String targetId = safeText(args.path("messageId").asText(""));
        if (targetId.isBlank()) {
            return "❌ messageId 不能为空。";
        }
        int scanLimit = clampLimit(args.path("scanLimit").asInt(MAX_LIMIT));
        String password = decrypt(account);
        Store store = openPop3Store(account, password);
        try {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            try {
                int total = inbox.getMessageCount();
                if (total <= 0) {
                    return "📭 收件箱为空。";
                }
                int start = Math.max(1, total - scanLimit + 1);
                Message[] scanned = inbox.getMessages(start, total);
                for (int i = scanned.length - 1; i >= 0; i--) {
                    Message message = scanned[i];
                    String messageId = readMessageId(message);
                    if (targetId.equals(messageId) || targetId.equals("#" + (i + start))) {
                        return renderMessage(account, message, messageId);
                    }
                }
                return "❌ 在最近 " + scanned.length + " 封邮件中没有找到 messageId=" + targetId
                        + "。POP3 下 messageId 仅在服务器可见范围内有效，建议先用 email_list_inbox 获取最新 id。";
            } finally {
                inbox.close(false);
            }
        } finally {
            store.close();
        }
    }

    // =============================================================================================
    // SMTP write operations
    // =============================================================================================

    private String send(EmailAccountEntity account, JsonNode args) throws MessagingException, java.io.UnsupportedEncodingException {
        if (account.isRequireSendConfirm() && !args.path("confirmed").asBoolean(false)) {
            return "NEEDS_CONFIRMATION：当前邮箱开启了发送二次确认。请先向用户回显 收件人/抄送/主题/正文要点，"
                    + "获得明确确认后，带 confirmed=true 重新调用 email_send。";
        }
        MimeMessage message = buildOutgoing(account, args, null);
        return doSend(account, message, "邮件已发送");
    }

    private String reply(EmailAccountEntity account, JsonNode args) throws MessagingException, IOException, java.io.UnsupportedEncodingException {
        if (account.isRequireSendConfirm() && !args.path("confirmed").asBoolean(false)) {
            return "NEEDS_CONFIRMATION：当前邮箱开启了发送二次确认。请先向用户回显 回复对象/正文要点，"
                    + "获得明确确认后，带 confirmed=true 重新调用 email_reply。";
        }
        String inReplyTo = safeText(args.path("messageId").asText(""));
        if (inReplyTo.isBlank()) {
            return "❌ messageId 不能为空（需要被回复邮件的 Message-ID）。";
        }
        ReplyContext context = loadReplyContext(account, inReplyTo);
        MimeMessage message = buildOutgoing(account, args, context);
        return doSend(account, message, "回复已发送");
    }

    private ReplyContext loadReplyContext(EmailAccountEntity account, String targetId)
            throws MessagingException, IOException {
        String password = decrypt(account);
        Store store = openPop3Store(account, password);
        try {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            try {
                int total = inbox.getMessageCount();
                if (total <= 0) {
                    return null;
                }
                int start = Math.max(1, total - MAX_LIMIT + 1);
                Message[] scanned = inbox.getMessages(start, total);
                for (int i = scanned.length - 1; i >= 0; i--) {
                    String id = readMessageId(scanned[i]);
                    if (targetId.equals(id)) {
                        String subject = nullSafe(scanned[i].getSubject());
                        String references = header(scanned[i], "References");
                        return new ReplyContext(id, subject, references);
                    }
                }
                return new ReplyContext(targetId, null, null);
            } finally {
                inbox.close(false);
            }
        } finally {
            store.close();
        }
    }

    private MimeMessage buildOutgoing(EmailAccountEntity account, JsonNode args, ReplyContext replyContext)
            throws MessagingException, java.io.UnsupportedEncodingException {
        List<InternetAddress> to = parseAddresses(args.path("to"));
        List<InternetAddress> cc = parseAddresses(args.path("cc"));
        if (to.isEmpty()) {
            throw new IllegalArgumentException("to 不能为空");
        }
        String subject = safeText(args.path("subject").asText(""));
        String body = safeText(args.path("body").asText(""));
        String bodyFormat = args.path("bodyFormat").asText("text");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject 不能为空");
        }
        if (body.isBlank()) {
            throw new IllegalArgumentException("body 不能为空");
        }
        if (replyContext != null && (subject == null || subject.isBlank() || !subject.toLowerCase(Locale.ROOT).startsWith("re:"))) {
            String baseSubject = replyContext.subject() == null || replyContext.subject().isBlank()
                    ? subject : replyContext.subject();
            subject = baseSubject.toLowerCase(Locale.ROOT).startsWith("re:") ? baseSubject : "Re: " + baseSubject;
        }

        String password = decrypt(account);
        Session session = smtpSession(account, password);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(account.getEmailAddress(),
                account.getDisplayName() == null ? account.getEmailAddress() : account.getDisplayName()));
        message.setRecipients(Message.RecipientType.TO, to.toArray(new Address[0]));
        if (!cc.isEmpty()) {
            message.setRecipients(Message.RecipientType.CC, cc.toArray(new Address[0]));
        }
        message.setSubject(subject, "UTF-8");
        if ("html".equalsIgnoreCase(bodyFormat)) {
            message.setContent(body, "text/html; charset=UTF-8");
        } else {
            message.setText(body, "UTF-8");
        }
        message.setSentDate(new Date());
        if (replyContext != null && replyContext.messageId() != null) {
            message.setHeader("In-Reply-To", "<" + stripAngles(replyContext.messageId()) + ">");
            String references = replyContext.references() == null
                    ? ""
                    : replyContext.references().trim();
            String combined = references.isBlank()
                    ? "<" + stripAngles(replyContext.messageId()) + ">"
                    : references + " <" + stripAngles(replyContext.messageId()) + ">";
            message.setHeader("References", combined);
        }
        return message;
    }

    private String doSend(EmailAccountEntity account, MimeMessage message, String successPrefix) throws MessagingException {
        String password = decrypt(account);
        try (Transport transport = message.getSession().getTransport(smtpTransportProtocol(account))) {
            transport.connect(account.getSmtpHost(), account.getSmtpPort(), account.getLoginUsername(), password);
            transport.sendMessage(message, message.getAllRecipients());
        }
        Address[] to = message.getRecipients(Message.RecipientType.TO);
        String toLabel = to == null ? "" : Arrays.stream(to).map(Address::toString).reduce((a, b) -> a + ", " + b).orElse("");
        return String.format("✅ %s。收件人: %s；主题: %s。", successPrefix, toLabel, safeText(message.getSubject()));
    }

    // =============================================================================================
    // POP3 / SMTP plumbing
    // =============================================================================================

    private Store openPop3Store(EmailAccountEntity account, String password) throws MessagingException {
        Properties props = new Properties();
        String protocol = account.isPop3Ssl() ? "pop3s" : "pop3";
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", account.getPop3Host());
        props.put("mail." + protocol + ".port", String.valueOf(account.getPop3Port()));
        props.put("mail." + protocol + ".connectiontimeout", "10000");
        props.put("mail." + protocol + ".timeout", "15000");
        if (account.isPop3Ssl()) {
            props.put("mail.pop3s.ssl.enable", "true");
            props.put("mail.pop3s.ssl.checkserveridentity", "true");
        }
        Session session = Session.getInstance(props, authenticator(account.getLoginUsername(), password));
        Store store = session.getStore(protocol);
        store.connect(account.getPop3Host(), account.getPop3Port(), account.getLoginUsername(), password);
        return store;
    }

    private Session smtpSession(EmailAccountEntity account, String password) {
        Properties props = new Properties();
        String protocol = smtpTransportProtocol(account);
        props.put("mail.transport.protocol", protocol);
        props.put("mail." + protocol + ".host", account.getSmtpHost());
        props.put("mail." + protocol + ".port", String.valueOf(account.getSmtpPort()));
        props.put("mail." + protocol + ".auth", "true");
        props.put("mail." + protocol + ".connectiontimeout", "10000");
        props.put("mail." + protocol + ".timeout", "15000");
        switch (account.getSmtpSslMode()) {
            case EmailProviderRegistry.SSL_MODE_STARTTLS -> {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
            case EmailProviderRegistry.SSL_MODE_SSL -> {
                props.put("mail.smtps.ssl.enable", "true");
                props.put("mail.smtps.ssl.checkserveridentity", "true");
            }
            default -> {
            }
        }
        return Session.getInstance(props, authenticator(account.getLoginUsername(), password));
    }

    private String smtpTransportProtocol(EmailAccountEntity account) {
        return EmailProviderRegistry.SSL_MODE_SSL.equals(account.getSmtpSslMode()) ? "smtps" : "smtp";
    }

    private Authenticator authenticator(String user, String password) {
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, password);
            }
        };
    }

    // =============================================================================================
    // Rendering & parsing helpers
    // =============================================================================================

    private String renderInbox(EmailAccountEntity account, List<MessageSummary> summaries) {
        StringBuilder sb = new StringBuilder();
        sb.append("📬 ").append(account.getEmailAddress()).append(" 最近 ").append(summaries.size()).append(" 封邮件：\n");
        for (MessageSummary item : summaries) {
            sb.append("- [").append(item.receivedAt()).append("] ")
                    .append(item.from()).append(" · ").append(item.subject())
                    .append(" · id=").append(item.messageId())
                    .append("\n");
        }
        sb.append("\n可用 `email_get_message` 传入 messageId 读取正文。");
        return sb.toString();
    }

    private String renderSearch(EmailAccountEntity account, List<MessageSummary> hits,
                                String keyword, String fromFilter, int scannedCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔎 在最近 ").append(scannedCount).append(" 封邮件中匹配到 ")
                .append(hits.size()).append(" 封 (keyword='")
                .append(keyword).append("', from='").append(fromFilter).append("')：\n");
        for (MessageSummary item : hits) {
            sb.append("- [").append(item.receivedAt()).append("] ")
                    .append(item.from()).append(" · ").append(item.subject())
                    .append(" · id=").append(item.messageId())
                    .append("\n");
        }
        return sb.toString();
    }

    private String renderMessage(EmailAccountEntity account, Message message, String messageId)
            throws MessagingException, IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("📨 ").append(account.getEmailAddress()).append(" 邮件正文\n");
        sb.append("- 主题: ").append(nullSafe(message.getSubject())).append('\n');
        sb.append("- 发件人: ").append(formatAddresses(message.getFrom())).append('\n');
        sb.append("- 收件人: ").append(formatAddresses(message.getRecipients(Message.RecipientType.TO))).append('\n');
        Address[] cc = message.getRecipients(Message.RecipientType.CC);
        if (cc != null && cc.length > 0) {
            sb.append("- 抄送: ").append(formatAddresses(cc)).append('\n');
        }
        Date received = message.getReceivedDate() != null ? message.getReceivedDate() : message.getSentDate();
        sb.append("- 时间: ").append(received == null ? "" : DATE_FORMATTER.format(received.toInstant())).append('\n');
        sb.append("- messageId: ").append(messageId).append('\n');
        sb.append("\n---\n");
        sb.append(extractText(message));
        return sb.toString();
    }

    private boolean matches(MessageSummary summary, String keyword, String from) {
        if (!keyword.isBlank()) {
            String lower = (summary.subject() + " " + summary.preview()).toLowerCase(Locale.ROOT);
            if (!lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (!from.isBlank()) {
            if (summary.from() == null || !summary.from().toLowerCase(Locale.ROOT).contains(from.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private MessageSummary summarize(Message message) throws MessagingException, IOException {
        Address[] fromAddresses = message.getFrom();
        String from = fromAddresses == null || fromAddresses.length == 0 ? "" : formatAddress(fromAddresses[0]);
        Date received = message.getReceivedDate() != null ? message.getReceivedDate() : message.getSentDate();
        String date = received == null ? "" : DATE_FORMATTER.format(received.toInstant());
        String messageId = readMessageId(message);
        String preview = previewText(message);
        return new MessageSummary(messageId, from, nullSafe(message.getSubject()), date, preview);
    }

    private String readMessageId(Message message) throws MessagingException {
        String id = header(message, "Message-ID");
        if (id != null && !id.isBlank()) {
            return stripAngles(id.trim());
        }
        return "#" + message.getMessageNumber();
    }

    private String header(Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }

    private String previewText(Message message) {
        try {
            String text = extractText(message);
            if (text == null) {
                return "";
            }
            String compact = text.replaceAll("\\s+", " ").trim();
            if (compact.length() <= DEFAULT_BODY_PREVIEW) {
                return compact;
            }
            return compact.substring(0, DEFAULT_BODY_PREVIEW) + "...";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String extractText(Part part) throws MessagingException, IOException {
        if (part.getSize() > MAX_BODY_BYTES) {
            // still try, but cap output later
        }
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            return content == null ? "" : truncate(content.toString());
        }
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            return content == null ? "" : truncate(stripHtml(content.toString()));
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                Part sub = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(sub.getDisposition())) {
                    continue;
                }
                String text = extractText(sub);
                if (text != null && !text.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(text);
                }
            }
            return truncate(sb.toString());
        }
        return "";
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= MAX_BODY_BYTES) {
            return value;
        }
        return value.substring(0, MAX_BODY_BYTES) + "\n...(正文已截断)";
    }

    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    private List<InternetAddress> parseAddresses(JsonNode node) throws AddressException {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        List<InternetAddress> list = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (!value.isEmpty()) {
                    list.addAll(Arrays.asList(InternetAddress.parse(value)));
                }
            }
        } else if (node.isTextual()) {
            String value = node.asText("").trim();
            if (!value.isEmpty()) {
                list.addAll(Arrays.asList(InternetAddress.parse(value)));
            }
        }
        return list;
    }

    private String formatAddresses(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Address address : addresses) {
            parts.add(formatAddress(address));
        }
        return String.join(", ", parts);
    }

    private String formatAddress(Address address) {
        if (address instanceof InternetAddress internet) {
            try {
                String personal = internet.getPersonal();
                return personal == null || personal.isBlank()
                        ? internet.getAddress()
                        : MimeUtility.decodeText(personal) + " <" + internet.getAddress() + ">";
            } catch (Exception e) {
                return internet.toUnicodeString();
            }
        }
        return address.toString();
    }

    private JsonNode readArgs(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String decrypt(EmailAccountEntity account) {
        return secretCipherService.decryptUtf8(account.getSecretCipher(), account.getSecretIv());
    }

    private static int clampLimit(int value) {
        if (value <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String stripAngles(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String failure(Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return "❌ 邮件工具执行失败：" + message;
    }

    private String checkRateLimit(String orgId, String userId, String toolName) {
        boolean writeOperation = TOOL_SEND.equals(toolName) || TOOL_REPLY.equals(toolName);
        int limit = writeOperation ? RATE_LIMIT_WRITE_PER_MIN : RATE_LIMIT_READ_PER_MIN;
        String bucket = writeOperation ? "write" : "read";
        String key = orgId + ":" + userId + ":email:" + bucket;
        long nowMinute = System.currentTimeMillis() / 60_000L;
        RateWindow updated = rateMap.compute(key, (k, current) -> {
            if (current == null || current.windowStart() != nowMinute) {
                return new RateWindow(nowMinute, 1);
            }
            return new RateWindow(current.windowStart(), current.count() + 1);
        });
        if (updated.count() > limit) {
            return "❌ 邮件工具每分钟调用已超限（" + (writeOperation ? "发送/回复" : "读取") + " ≤ " + limit + "/min）。请稍后再试。";
        }
        return null;
    }

    // =============================================================================================
    // Schema builders
    // =============================================================================================

    private Map<String, Object> functionTool(String name, String description, JsonNode schema) {
        Map<String, Object> parameters;
        try {
            parameters = objectMapper.convertValue(schema, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            parameters = Map.of("type", "object", "properties", Map.of());
        }
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", description);
        fn.put("parameters", parameters);
        return Map.of(
                "type", "function",
                "function", fn);
    }

    private JsonNode listInboxSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("limit").put("type", "integer").put("minimum", 1).put("maximum", MAX_LIMIT).put("default", DEFAULT_LIMIT);
        return root;
    }

    private JsonNode searchSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("keyword").put("type", "string");
        props.putObject("from").put("type", "string");
        props.putObject("limit").put("type", "integer").put("minimum", 1).put("maximum", MAX_LIMIT).put("default", DEFAULT_LIMIT);
        props.putObject("scanLimit").put("type", "integer").put("minimum", 1).put("maximum", MAX_LIMIT).put("default", MAX_LIMIT);
        return root;
    }

    private JsonNode getMessageSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.putArray("required").add("messageId");
        ObjectNode props = root.putObject("properties");
        props.putObject("messageId").put("type", "string");
        props.putObject("scanLimit").put("type", "integer").put("minimum", 1).put("maximum", MAX_LIMIT).put("default", MAX_LIMIT);
        return root;
    }

    private JsonNode sendSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.putArray("required").add("to").add("subject").add("body");
        ObjectNode props = root.putObject("properties");
        ObjectNode to = props.putObject("to");
        to.put("type", "array");
        to.putObject("items").put("type", "string");
        ObjectNode cc = props.putObject("cc");
        cc.put("type", "array");
        cc.putObject("items").put("type", "string");
        props.putObject("subject").put("type", "string").put("maxLength", 256);
        props.putObject("body").put("type", "string").put("maxLength", 20000);
        ObjectNode bodyFormat = props.putObject("bodyFormat");
        bodyFormat.put("type", "string");
        bodyFormat.putArray("enum").add("text").add("html");
        bodyFormat.put("default", "text");
        ObjectNode attachments = props.putObject("attachmentUrls");
        attachments.put("type", "array").put("maxItems", 5);
        attachments.putObject("items").put("type", "string");
        props.putObject("confirmed").put("type", "boolean").put("default", false);
        return root;
    }

    private JsonNode replySchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.putArray("required").add("messageId").add("body");
        ObjectNode props = root.putObject("properties");
        props.putObject("messageId").put("type", "string");
        ObjectNode cc = props.putObject("cc");
        cc.put("type", "array");
        cc.putObject("items").put("type", "string");
        ObjectNode to = props.putObject("to");
        to.put("type", "array");
        to.putObject("items").put("type", "string");
        props.putObject("subject").put("type", "string").put("maxLength", 256);
        props.putObject("body").put("type", "string").put("maxLength", 20000);
        ObjectNode bodyFormat = props.putObject("bodyFormat");
        bodyFormat.put("type", "string");
        bodyFormat.putArray("enum").add("text").add("html");
        bodyFormat.put("default", "text");
        props.putObject("confirmed").put("type", "boolean").put("default", false);
        return root;
    }

    // =============================================================================================
    // Local types
    // =============================================================================================

    private record MessageSummary(String messageId, String from, String subject, String receivedAt, String preview) {
    }

    private record ReplyContext(String messageId, String subject, String references) {
    }

    @SuppressWarnings("unused")
    private static List<String> unused(List<String> anything) {
        return anything == null ? Collections.emptyList() : anything;
    }
}
