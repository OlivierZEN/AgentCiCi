package com.codehouse.ciciassistant.tool.service;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Central place to normalize legacy tool aliases to the runtime tool ids used by chat orchestration.
 */
public final class ToolNameNormalizer {

    public static final String MCP_WORKFLOW_WILDCARD = "mcp-workflow";
    public static final String APPROVAL_FETCH_LEGACY = "approval-fetch";
    public static final String CRM_CUSTOMER_LEGACY = "crm-customer";
    public static final String QUOTE_GENERATOR_LEGACY = "quote-generator";
    public static final String GET_PENDING_APPROVALS = "get_pending_approvals";

    private ToolNameNormalizer() {
    }

    public static String canonicalize(String toolName) {
        if (toolName == null) {
            return null;
        }
        String normalized = toolName.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case APPROVAL_FETCH_LEGACY -> GET_PENDING_APPROVALS;
            case CRM_CUSTOMER_LEGACY -> CloudccOpenApiService.toolName();
            default -> normalized;
        };
    }

    public static List<String> canonicalizeAll(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String toolName : toolNames) {
            String canonical = canonicalize(toolName);
            if (canonical != null) {
                result.add(canonical);
            }
        }
        return List.copyOf(result);
    }

    public static boolean containsMcpWildcard(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return false;
        }
        for (String toolName : toolNames) {
            if (MCP_WORKFLOW_WILDCARD.equalsIgnoreCase(toolName)) {
                return true;
            }
        }
        return false;
    }
}
