package com.codehouse.ciciassistant.auth;

import java.util.Locale;
import java.util.Set;

public final class ProductThemeCodes {

    public static final String DEFAULT = "gilded";

    private static final Set<String> ALLOWED = Set.of(
            DEFAULT,
            "crm-blue",
            "ocean",
            "sakura",
            "lavender",
            "avocado",
            "wine",
            "galaxy"
    );

    private ProductThemeCodes() {
    }

    public static String requireAllowed(String themeCode) {
        String normalized = normalize(themeCode);
        if (!ALLOWED.contains(normalized)) {
            throw new IllegalArgumentException("不支持的界面主题");
        }
        return normalized;
    }

    public static String normalizeStored(String themeCode) {
        String normalized = normalize(themeCode);
        return ALLOWED.contains(normalized) ? normalized : DEFAULT;
    }

    private static String normalize(String themeCode) {
        return themeCode == null ? "" : themeCode.trim().toLowerCase(Locale.ROOT);
    }
}
