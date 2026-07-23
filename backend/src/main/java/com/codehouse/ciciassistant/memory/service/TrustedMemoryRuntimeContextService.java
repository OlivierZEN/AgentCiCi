package com.codehouse.ciciassistant.memory.service;

import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Server-side scope for an external subject request. No controller binds this type: an authenticated
 * adapter must establish it around the Chat call and close it in the same execution thread.
 */
@Service
public class TrustedMemoryRuntimeContextService {
    private static final int PROMPT_BUDGET = 3_000;
    private final ThreadLocal<TrustedMemoryRequest> current = new ThreadLocal<>();
    private final ThreadLocal<Resolution> lastResolution = new ThreadLocal<>();
    private final ExternalMemoryContextService contexts;
    private final MemorySemanticRetrievalService retrieval;
    private final MemoryContextPromptAssembler assembler = new MemoryContextPromptAssembler();

    public TrustedMemoryRuntimeContextService(ExternalMemoryContextService contexts,
                                              MemorySemanticRetrievalService retrieval) {
        this.contexts = contexts;
        this.retrieval = retrieval;
    }

    public Scope enter(TrustedMemoryRequest request) {
        if (request == null || request.context() == null || request.agentId() == null || request.agentId().isBlank()) {
            throw new IllegalArgumentException("trusted memory request is required");
        }
        TrustedMemoryRequest previous = current.get();
        current.set(request);
        return () -> { if (previous == null) current.remove(); else current.set(previous); };
    }

    public String buildPrompt(String orgId, String resolvedAgentId, String question) {
        TrustedMemoryRequest request = current.get();
        if (request == null || !request.context().orgId().equals(orgId)
                || !request.agentId().equals(resolvedAgentId)) {
            lastResolution.set(Resolution.none());
            return "";
        }
        ExternalMemoryContextService.MemoryContext structured;
        try {
            structured = contexts.loadContext(request.context(), resolvedAgentId, request.domainNamespaces(), null);
        } catch (IllegalArgumentException ignored) {
            lastResolution.set(Resolution.none());
            return "";
        }
        String prompt = assembler.build(structured, PROMPT_BUDGET);
        var semantic = retrieval.retrieve(request.context(), resolvedAgentId, request.domainNamespaces(), question, 4);
        if (semantic.isEmpty()) {
            lastResolution.set(new Resolution(true, structured.records().size(), 0, prompt.length() >= PROMPT_BUDGET));
            return prompt;
        }
        StringBuilder output = new StringBuilder(prompt).append("- 相关历史：\n");
        semantic.forEach(record -> output.append("  - ").append(record.getContent()).append('\n'));
        boolean truncated = output.length() > PROMPT_BUDGET;
        lastResolution.set(new Resolution(true, structured.records().size(), semantic.size(), truncated));
        return truncated ? output.substring(0, PROMPT_BUDGET - 1) + "…" : output.toString();
    }

    public Map<String, Object> traceMetadata() {
        Resolution resolution = lastResolution.get();
        if (resolution == null || !resolution.injected()) return Map.of("memoryInjected", false);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("memoryInjected", true);
        out.put("structuredRecordCount", resolution.structuredRecordCount());
        out.put("semanticHitCount", resolution.semanticHitCount());
        out.put("truncated", resolution.truncated());
        return out;
    }

    public record TrustedMemoryRequest(ExternalMemoryContextService.ExternalMemoryContext context,
                                       String agentId, Set<String> domainNamespaces) {
        public TrustedMemoryRequest { domainNamespaces = domainNamespaces == null ? Set.of() : Set.copyOf(domainNamespaces); }
    }
    private record Resolution(boolean injected, int structuredRecordCount, int semanticHitCount, boolean truncated) {
        static Resolution none() { return new Resolution(false, 0, 0, false); }
    }
    @FunctionalInterface public interface Scope extends AutoCloseable { @Override void close(); }
}
