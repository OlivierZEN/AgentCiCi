package com.codehouse.ciciassistant.ontology.semattice;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Server-side capability gateway used by the ontology integration. Tenant, actor and scopes are
 * always derived from the authenticated AgentCiCi member and its short-lived OACT.
 */
public interface SematticeOntologyGateway {

    JsonNode invoke(
            String companyId,
            String userId,
            String capabilityId,
            Map<String, Object> input,
            String idempotencyKey);

    default JsonNode invokeRead(
            String companyId,
            String userId,
            String capabilityId,
            Map<String, Object> input) {
        return invoke(companyId, userId, capabilityId, input, null);
    }
}
