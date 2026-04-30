package com.codehouse.ciciassistant.auth;

public final class RoleCodes {

    public static final String ORG_ADMIN = "ORG_ADMIN";
    public static final String ORG_USER = "ORG_USER";
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String PLATFORM_OPERATOR = "PLATFORM_OPERATOR";
    public static final String PLATFORM_SUPPORT = "PLATFORM_SUPPORT";
    public static final String PLATFORM_BILLING = "PLATFORM_BILLING";
    public static final String PLATFORM_AUDITOR = "PLATFORM_AUDITOR";

    private RoleCodes() {
    }

    public static boolean isValidRole(String code) {
        return ORG_ADMIN.equals(code)
                || ORG_USER.equals(code)
                || PLATFORM_ADMIN.equals(code)
                || PLATFORM_OPERATOR.equals(code)
                || PLATFORM_SUPPORT.equals(code)
                || PLATFORM_BILLING.equals(code)
                || PLATFORM_AUDITOR.equals(code);
    }

    public static boolean isPlatformRole(String code) {
        return PLATFORM_ADMIN.equals(code)
                || PLATFORM_OPERATOR.equals(code)
                || PLATFORM_SUPPORT.equals(code)
                || PLATFORM_BILLING.equals(code)
                || PLATFORM_AUDITOR.equals(code);
    }
}
