package com.codehouse.ciciassistant.common.util;

import java.util.Locale;

/**
 * Validates avatar payloads encoded as image data URLs.
 */
public final class AvatarDataUrlValidator {

    private static final int DEFAULT_MAX_LENGTH = 320_000;

    private AvatarDataUrlValidator() {
    }

    public static String normalizeNullableDataUrl(String rawValue, String fieldName) {
        return normalizeNullableDataUrl(rawValue, fieldName, DEFAULT_MAX_LENGTH);
    }

    public static String normalizeNullableDataUrl(String rawValue, String fieldName, int maxLength) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too large");
        }

        String lower = value.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("data:image/png;base64,")
                || lower.startsWith("data:image/jpeg;base64,")
                || lower.startsWith("data:image/jpg;base64,")
                || lower.startsWith("data:image/webp;base64,"))) {
            throw new IllegalArgumentException(fieldName + " must be a PNG/JPEG/WEBP data URL");
        }

        int commaIndex = value.indexOf(',');
        if (commaIndex <= 0 || commaIndex >= value.length() - 1) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
        String base64 = value.substring(commaIndex + 1);
        if (!base64.matches("^[A-Za-z0-9+/=]+$")) {
            throw new IllegalArgumentException(fieldName + " contains invalid base64 characters");
        }
        return value;
    }
}
