package com.codehouse.ciciassistant.memory.service;

import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Server-side scope for an external subject request. No controller binds this type: an authenticated
 * adapter must establish it around the Chat call and close it in the same execution thread.
 */
@Service
public class TrustedMemoryRuntimeContextService {
    private static final int PROMPT_BUDGET = 3_000;
    private final ThreadLocal<TrustedMemoryRequest> current = new ThreadLocal<>();
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
            return "";
        }
        ExternalMemoryContextService.MemoryContext structured = contexts.loadContext(
                request.context(), resolvedAgentId, request.domainNamespaces(), null);
        String prompt = assembler.build(structured, PROMPT_BUDGET);
        var semantic = retrieval.retrieve(request.context(), resolvedAgentId, request.domainNamespaces(), question, 4);
        if (semantic.isEmpty()) return prompt;
        StringBuilder output = new StringBuilder(prompt).append("- 相关历史：\n");
        semantic.forEach(record -> output.append("  - ").append(record.getContent()).append('\n'));
        return output.length() <= PROMPT_BUDGET ? output.toString() : output.substring(0, PROMPT_BUDGET - 1) + "…";
    }

    public record TrustedMemoryRequest(ExternalMemoryContextService.ExternalMemoryContext context,
                                       String agentId, Set<String> domainNamespaces) {
        public TrustedMemoryRequest { domainNamespaces = domainNamespaces == null ? Set.of() : Set.copyOf(domainNamespaces); }
    }
    @FunctionalInterface public interface Scope extends AutoCloseable { @Override void close(); }
}
