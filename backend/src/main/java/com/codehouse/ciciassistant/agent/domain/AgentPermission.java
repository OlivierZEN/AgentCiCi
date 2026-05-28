package com.codehouse.ciciassistant.agent.domain;

import java.util.Locale;

public enum AgentPermission {
    VIEW,
    RUN,
    DEBUG,
    EDIT,
    PUBLISH,
    MANAGE,
    OPENAPI,
    LOG_VIEW;

    public static AgentPermission from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("permission is required");
        }
        return AgentPermission.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
