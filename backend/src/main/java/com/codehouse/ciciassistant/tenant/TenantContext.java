package com.codehouse.ciciassistant.tenant;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class TenantContext {

    private static final ThreadLocal<String> ORG_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> ROLES_HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setOrgId(String orgId) {
        ORG_ID_HOLDER.set(orgId);
    }

    public static Optional<String> getOrgId() {
        return Optional.ofNullable(ORG_ID_HOLDER.get());
    }

    public static String requireOrgId() {
        return getOrgId().orElseThrow(() -> new IllegalArgumentException("Missing organization context"));
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

    public static void clear() {
        ORG_ID_HOLDER.remove();
        USER_ID_HOLDER.remove();
        ROLES_HOLDER.remove();
    }
}
