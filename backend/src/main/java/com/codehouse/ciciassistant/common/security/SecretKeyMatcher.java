package com.codehouse.ciciassistant.common.security;

import java.util.List;
import java.util.Locale;

public final class SecretKeyMatcher {

    private static final List<String> SENSITIVE_KEY_HINTS = List.of(
            "secret",
            "password",
            "token",
            "credential",
            "encrypted",
            "apikey",
            "accesskey",
            "authorization",
            "cookie",
            "privatekey",
            "safetymark",
            "configjson");

    private SecretKeyMatcher() {
    }

    public static boolean matches(String key) {
        String normalized = normalize(key);
        return !normalized.isEmpty()
                && SENSITIVE_KEY_HINTS.stream().anyMatch(normalized::contains);
    }

    public static String normalize(String key) {
        if (key == null) {
            return "";
        }
        String lower = key.toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(lower.length());
        for (int index = 0; index < lower.length(); index++) {
            char character = lower.charAt(index);
            if ((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')) {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    public static List<String> sensitiveKeyHints() {
        return SENSITIVE_KEY_HINTS;
    }
}
