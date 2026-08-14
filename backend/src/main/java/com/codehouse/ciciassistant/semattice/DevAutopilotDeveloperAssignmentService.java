package com.codehouse.ciciassistant.semattice;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Resolves a stable active full-stack developer from the current tenant application resources. */
@Service
public class DevAutopilotDeveloperAssignmentService {

    private final JdbcTemplate jdbcTemplate;

    public DevAutopilotDeveloperAssignmentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<DeveloperAssignment> select(String companyId, String routingKey) {
        List<DeveloperAssignment> developers = activeDevelopers(companyId);
        if (developers.isEmpty()) {
            return Optional.empty();
        }
        int index = Math.floorMod((routingKey == null ? "" : routingKey).hashCode(), developers.size());
        return Optional.of(developers.get(index));
    }

    /** Resolves a user-facing Developer Profile name without exposing its internal principal ID. */
    public Optional<DeveloperAssignment> resolveActiveByDisplayName(String companyId, String displayName) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (normalized.isBlank()) return Optional.empty();
        List<DeveloperAssignment> matches = activeDevelopers(companyId).stream()
                .filter(item -> item.displayName().equalsIgnoreCase(normalized))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private List<DeveloperAssignment> activeDevelopers(String companyId) {
        return jdbcTemplate.query("""
                SELECT resource.external_id, resource.display_name
                FROM tenant_application_activation activation
                JOIN tenant_application_resource resource
                  ON resource.activation_id = activation.id
                 AND resource.logical_role = 'developer'
                 AND resource.resource_type = 'SERVICE_PRINCIPAL'
                 AND resource.lifecycle_state = 'ACTIVE'
                JOIN principal principal
                  ON principal.id = resource.external_id
                 AND principal.principal_type = 'SERVICE'
                 AND principal.lifecycle_status = 'ACTIVE'
                WHERE activation.company_id = ?
                  AND activation.app_code = 'devautopilot'
                  AND activation.actual_state = 'ACTIVE'
                ORDER BY resource.external_id
                """, (rs, rowNum) -> new DeveloperAssignment(rs.getString(1), rs.getString(2)), companyId);
    }

    public record DeveloperAssignment(String principalId, String displayName) { }
}
