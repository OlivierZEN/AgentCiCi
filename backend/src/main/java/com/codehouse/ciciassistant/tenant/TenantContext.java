package com.codehouse.ciciassistant.tenant;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class TenantContext {

    private static final ThreadLocal<String> COMPANY_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> ROLES_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> TOKEN_TYPE_HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCompanyId(String companyId) {
        COMPANY_ID_HOLDER.set(companyId);
    }

    public static Optional<String> getCompanyId() {
        return Optional.ofNullable(COMPANY_ID_HOLDER.get());
    }

    public static String requireCompanyId() {
        return getCompanyId().orElseThrow(() -> new IllegalArgumentException("Missing company context"));
    }

    public static void setUserId(String userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Optional<String> getUserId() {
        return Optional.ofNullable(USER_ID_HOLDER.get());
    }

    public static void setRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            ROLES_HOLDER.remove();
        } else {
            ROLES_HOLDER.set(List.copyOf(roles));
        }
    }

    public static List<String> getRoles() {
        List<String> r = ROLES_HOLDER.get();
        return r == null ? List.of() : r;
    }

    public static void setTokenType(String tokenType) {
        if (tokenType == null || tokenType.isBlank()) {
            TOKEN_TYPE_HOLDER.remove();
        } else {
            TOKEN_TYPE_HOLDER.set(tokenType);
        }
    }

    public static Optional<String> getTokenType() {
        return Optional.ofNullable(TOKEN_TYPE_HOLDER.get());
    }

    public static void clear() {
        COMPANY_ID_HOLDER.remove();
        USER_ID_HOLDER.remove();
        ROLES_HOLDER.remove();
        TOKEN_TYPE_HOLDER.remove();
    }
}
