package com.codehouse.ciciassistant.auth;

public final class RoleCodes {

    public static final String ORG_ADMIN = "ORG_ADMIN";
    public static final String ORG_USER = "ORG_USER";

    private RoleCodes() {
    }

    public static boolean isValidRole(String code) {
        return ORG_ADMIN.equals(code) || ORG_USER.equals(code);
    }
}
