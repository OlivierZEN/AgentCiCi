package com.codehouse.ciciassistant.email.service;

import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.email.domain.EmailAccountEntity;
import com.codehouse.ciciassistant.email.domain.EmailAccountRepository;
import com.codehouse.ciciassistant.email.service.EmailProviderRegistry.ProviderPreset;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/write operations on {@code email_account}. Secrets are AES-GCM encrypted before persistence.
 * Secret plaintext never leaves this service except for the on-demand POP3/SMTP runtime (see
 * {@link EmailToolService}).
 */
@Service
public class EmailAccountService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final EmailAccountRepository repository;
    private final SecretCipherService secretCipherService;
    private final ObjectProvider<EmailToolService> emailToolServiceProvider;

    public EmailAccountService(EmailAccountRepository repository,
                               SecretCipherService secretCipherService,
                               ObjectProvider<EmailToolService> emailToolServiceProvider) {
        this.repository = repository;
        this.secretCipherService = secretCipherService;
        this.emailToolServiceProvider = emailToolServiceProvider;
    }

    public List<EmailAccountView> list(String companyId, String userId) {
        return repository.findByCompanyIdAndUserIdOrderByIdAsc(companyId, userId).stream()
                .map(EmailAccountService::toView)
                .toList();
    }

    public EmailAccountView get(String companyId, String userId, Long id) {
        return toView(findOrThrow(companyId, userId, id));
    }

    /**
     * @return the default enabled account for a user (smallest id), or empty when none configured.
     */
    public Optional<EmailAccountEntity> findDefaultAccount(String companyId, String userId) {
        return repository.findFirstByCompanyIdAndUserIdAndEnabledTrueOrderByIdAsc(companyId, userId);
    }

    public String decryptSecret(EmailAccountEntity entity) {
        return secretCipherService.decryptUtf8(entity.getSecretCipher(), entity.getSecretIv());
    }

    @Transactional
    public EmailAccountView create(String companyId, String userId, UpsertCommand command) {
        ProviderPreset preset = requireKnownProvider(command.providerCode());
        ResolvedConfig resolved = resolveConfig(preset, command, true);
        String email = requireValidEmail(resolved.emailAddress());

        if (repository.findByCompanyIdAndUserIdAndEmailAddress(companyId, userId, email).isPresent()) {
            throw new IllegalArgumentException("该邮箱已经绑定过了，不能重复添加: " + email);
        }

        SecretCipherService.EncryptedSecret secret = secretCipherService.encryptUtf8(requireSecret(command.secret()));
        EmailAccountEntity entity = new EmailAccountEntity(
                companyId,
                userId,
                resolved.providerCode(),
                resolved.displayName(),
                email,
                resolved.loginUsername(),
                resolved.authType(),
                secret.cipherBase64(),
                secret.ivBase64(),
                resolved.pop3Host(),
                resolved.pop3Port(),
                resolved.pop3Ssl(),
                resolved.smtpHost(),
                resolved.smtpPort(),
                resolved.smtpSslMode(),
                resolved.requireSendConfirm(),
                true
        );
        return toView(repository.save(entity));
    }

    @Transactional
    public EmailAccountView update(String companyId, String userId, Long id, UpsertCommand command) {
        EmailAccountEntity entity = findOrThrow(companyId, userId, id);
        ProviderPreset preset = requireKnownProvider(command.providerCode());
        ResolvedConfig resolved = resolveConfig(preset, command, false);
        String email = requireValidEmail(resolved.emailAddress());

        repository.findByCompanyIdAndUserIdAndEmailAddress(companyId, userId, email)
                .filter(other -> !other.getId().equals(entity.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("该邮箱已绑定到另一个账号: " + email);
                });

        entity.updateProfile(
                resolved.providerCode(),
                resolved.displayName(),
                email,
                resolved.loginUsername(),
                resolved.authType(),
                resolved.pop3Host(),
                resolved.pop3Port(),
                resolved.pop3Ssl(),
                resolved.smtpHost(),
                resolved.smtpPort(),
                resolved.smtpSslMode(),
                resolved.requireSendConfirm(),
                command.enabled() == null || command.enabled()
        );

        String newSecret = command.secret();
        if (newSecret != null && !newSecret.isBlank()) {
            SecretCipherService.EncryptedSecret secret = secretCipherService.encryptUtf8(newSecret);
            entity.updateSecret(secret.cipherBase64(), secret.ivBase64());
        }
        return toView(entity);
    }

    @Transactional
    public void delete(String companyId, String userId, Long id) {
        EmailAccountEntity entity = findOrThrow(companyId, userId, id);
        repository.delete(entity);
    }

    @Transactional
    public VerifyResult verify(String companyId, String userId, Long id) {
        EmailAccountEntity entity = findOrThrow(companyId, userId, id);
        EmailToolService toolService = emailToolServiceProvider.getObject();
        try {
            toolService.verifyConnection(entity);
            entity.markVerified(Instant.now());
            return new VerifyResult(true, "连接成功");
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            entity.markVerifyFailed(msg);
            return new VerifyResult(false, msg);
        }
    }

    public static EmailAccountView toView(EmailAccountEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("providerCode", item.getProviderCode());
        payload.put("displayName", item.getDisplayName());
        payload.put("emailAddress", item.getEmailAddress());
        payload.put("loginUsername", item.getLoginUsername());
        payload.put("authType", item.getAuthType());
        payload.put("secretMasked", "***");
        payload.put("pop3", Map.of(
                "host", item.getPop3Host(),
                "port", item.getPop3Port(),
                "ssl", item.isPop3Ssl()));
        payload.put("smtp", Map.of(
                "host", item.getSmtpHost(),
                "port", item.getSmtpPort(),
                "sslMode", item.getSmtpSslMode()));
        payload.put("requireSendConfirm", item.isRequireSendConfirm());
        payload.put("enabled", item.isEnabled());
        payload.put("lastVerifiedAt", item.getLastVerifiedAt() == null ? null : item.getLastVerifiedAt().toString());
        payload.put("lastVerifyError", item.getLastVerifyError());
        payload.put("createdAt", item.getCreatedAt().toString());
        payload.put("updatedAt", item.getUpdatedAt().toString());
        return new EmailAccountView(payload);
    }

    private EmailAccountEntity findOrThrow(String companyId, String userId, Long id) {
        return repository.findByIdAndCompanyIdAndUserId(id, companyId, userId)
                .orElseThrow(() -> new IllegalArgumentException("邮箱账号不存在或不属于当前用户"));
    }

    private ProviderPreset requireKnownProvider(String code) {
        return EmailProviderRegistry.find(code)
                .orElseThrow(() -> new IllegalArgumentException(
                        "不支持的 providerCode，允许值: aliyun_mail / hotmail / gmail / custom"));
    }

    private ResolvedConfig resolveConfig(ProviderPreset preset, UpsertCommand command, boolean create) {
        String email = normalize(command.emailAddress());
        String loginUsername = firstNonBlank(command.loginUsername(), email);
        String displayName = trimToNull(command.displayName());
        String authType = firstNonBlank(command.authType(), preset.defaultAuthType());
        if (!EmailProviderRegistry.isValidAuthType(authType)) {
            throw new IllegalArgumentException("authType 仅允许 password / app_password");
        }

        String pop3Host;
        int pop3Port;
        boolean pop3Ssl;
        String smtpHost;
        int smtpPort;
        String smtpSslMode;

        if (EmailProviderRegistry.PROVIDER_CUSTOM.equals(preset.code())) {
            pop3Host = requireText(command.pop3Host(), "pop3.host");
            pop3Port = requirePort(command.pop3Port(), "pop3.port");
            pop3Ssl = command.pop3Ssl() == null ? true : command.pop3Ssl();
            smtpHost = requireText(command.smtpHost(), "smtp.host");
            smtpPort = requirePort(command.smtpPort(), "smtp.port");
            smtpSslMode = firstNonBlank(command.smtpSslMode(), EmailProviderRegistry.SSL_MODE_SSL);
        } else {
            pop3Host = firstNonBlank(command.pop3Host(), preset.pop3Host());
            pop3Port = command.pop3Port() != null && command.pop3Port() > 0 ? command.pop3Port() : preset.pop3Port();
            pop3Ssl = command.pop3Ssl() == null ? preset.pop3Ssl() : command.pop3Ssl();
            smtpHost = firstNonBlank(command.smtpHost(), preset.smtpHost());
            smtpPort = command.smtpPort() != null && command.smtpPort() > 0 ? command.smtpPort() : preset.smtpPort();
            smtpSslMode = firstNonBlank(command.smtpSslMode(), preset.smtpSslMode());
        }

        if (!EmailProviderRegistry.isValidSslMode(smtpSslMode)) {
            throw new IllegalArgumentException("smtp.sslMode 仅允许 ssl / starttls / plain");
        }
        if (pop3Host == null || pop3Host.isBlank()) {
            throw new IllegalArgumentException("pop3.host 不能为空");
        }
        if (smtpHost == null || smtpHost.isBlank()) {
            throw new IllegalArgumentException("smtp.host 不能为空");
        }
        if (pop3Port <= 0 || smtpPort <= 0) {
            throw new IllegalArgumentException("pop3/smtp 端口必须为正整数");
        }
        boolean requireSendConfirm = command.requireSendConfirm() == null ? true : command.requireSendConfirm();

        if (create && (command.secret() == null || command.secret().isBlank())) {
            throw new IllegalArgumentException("创建邮箱时必须提供 secret（密码或 App Password）");
        }

        return new ResolvedConfig(
                preset.code(),
                displayName,
                email,
                loginUsername,
                authType,
                pop3Host, pop3Port, pop3Ssl,
                smtpHost, smtpPort, smtpSslMode,
                requireSendConfirm);
    }

    private static String normalize(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireText(String value, String field) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return trimmed;
    }

    private static int requirePort(Integer port, String field) {
        if (port == null || port <= 0 || port > 65535) {
            throw new IllegalArgumentException(field + " 必须是 1~65535 的整数");
        }
        return port;
    }

    private static String requireSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret 不能为空");
        }
        return secret;
    }

    private static String requireValidEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("邮箱地址格式不正确: " + email);
        }
        return email;
    }

    private record ResolvedConfig(
            String providerCode,
            String displayName,
            String emailAddress,
            String loginUsername,
            String authType,
            String pop3Host,
            int pop3Port,
            boolean pop3Ssl,
            String smtpHost,
            int smtpPort,
            String smtpSslMode,
            boolean requireSendConfirm
    ) {
    }

    public record UpsertCommand(
            String providerCode,
            String displayName,
            String emailAddress,
            String loginUsername,
            String authType,
            String secret,
            String pop3Host,
            Integer pop3Port,
            Boolean pop3Ssl,
            String smtpHost,
            Integer smtpPort,
            String smtpSslMode,
            Boolean requireSendConfirm,
            Boolean enabled
    ) {
    }

    public record EmailAccountView(Map<String, Object> payload) {
    }

    public record VerifyResult(boolean success, String message) {
    }
}
