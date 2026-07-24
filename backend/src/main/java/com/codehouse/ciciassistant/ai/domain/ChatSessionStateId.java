package com.codehouse.ciciassistant.ai.domain;

import java.io.Serializable;
import java.util.Objects;

public class ChatSessionStateId implements Serializable {

    private String sessionId;
    private String companyId;

    public ChatSessionStateId() {
    }

    public ChatSessionStateId(String sessionId, String companyId) {
        this.sessionId = sessionId;
        this.companyId = companyId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCompanyId() {
        return companyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatSessionStateId that)) {
            return false;
        }
        return Objects.equals(sessionId, that.sessionId) && Objects.equals(companyId, that.companyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, companyId);
    }
}
