package com.codehouse.ciciassistant.ai.domain;

import java.io.Serializable;
import java.util.Objects;

public class ChatSessionStateId implements Serializable {

    private String sessionId;
    private String orgId;

    public ChatSessionStateId() {
    }

    public ChatSessionStateId(String sessionId, String orgId) {
        this.sessionId = sessionId;
        this.orgId = orgId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getOrgId() {
        return orgId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatSessionStateId that)) {
            return false;
        }
        return Objects.equals(sessionId, that.sessionId) && Objects.equals(orgId, that.orgId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, orgId);
    }
}
