package com.codehouse.ciciassistant.email.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Built-in provider presets for Phase 1 (POP3 + SMTP, password / app-password only).
 * Values can still be overridden by user input in create/update requests.
 */
public final class EmailProviderRegistry {

    public static final String PROVIDER_ALIYUN = "aliyun_mail";
    public static final String PROVIDER_HOTMAIL = "hotmail";
    public static final String PROVIDER_GMAIL = "gmail";
    public static final String PROVIDER_CUSTOM = "custom";

    public static final String SSL_MODE_SSL = "ssl";
    public static final String SSL_MODE_STARTTLS = "starttls";
    public static final String SSL_MODE_PLAIN = "plain";

    public static final String AUTH_PASSWORD = "password";
    public static final String AUTH_APP_PASSWORD = "app_password";

    private static final Map<String, ProviderPreset> PRESETS;

    static {
        Map<String, ProviderPreset> map = new LinkedHashMap<>();
        map.put(PROVIDER_ALIYUN, new ProviderPreset(
                PROVIDER_ALIYUN,
                "阿里云企业邮箱",
                "pop.qiye.aliyun.com", 995, true,
                "smtp.qiye.aliyun.com", 465, SSL_MODE_SSL,
                AUTH_PASSWORD));
        map.put(PROVIDER_HOTMAIL, new ProviderPreset(
                PROVIDER_HOTMAIL,
                "Hotmail / Outlook",
                "outlook.office365.com", 995, true,
                "smtp-mail.outlook.com", 587, SSL_MODE_STARTTLS,
                AUTH_APP_PASSWORD));
        map.put(PROVIDER_GMAIL, new ProviderPreset(
                PROVIDER_GMAIL,
                "Gmail",
                "pop.gmail.com", 995, true,
                "smtp.gmail.com", 465, SSL_MODE_SSL,
                AUTH_APP_PASSWORD));
        map.put(PROVIDER_CUSTOM, new ProviderPreset(
                PROVIDER_CUSTOM,
                "通用 POP3/SMTP",
                null, 0, true,
                null, 0, SSL_MODE_SSL,
                AUTH_PASSWORD));
        PRESETS = Map.copyOf(map);
    }

    private EmailProviderRegistry() {
    }

    public static Map<String, ProviderPreset> all() {
        return PRESETS;
    }

    public static Optional<ProviderPreset> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(PRESETS.get(code.trim().toLowerCase()));
    }

    public static boolean isKnown(String code) {
        return find(code).isPresent();
    }

    public static boolean isValidSslMode(String mode) {
        return SSL_MODE_SSL.equals(mode) || SSL_MODE_STARTTLS.equals(mode) || SSL_MODE_PLAIN.equals(mode);
    }

    public static boolean isValidAuthType(String authType) {
        return AUTH_PASSWORD.equals(authType) || AUTH_APP_PASSWORD.equals(authType);
    }

    public record ProviderPreset(
            String code,
            String displayLabel,
            String pop3Host,
            int pop3Port,
            boolean pop3Ssl,
            String smtpHost,
            int smtpPort,
            String smtpSslMode,
            String defaultAuthType
    ) {
    }
}
