package com.codehouse.ciciassistant.memory.service;

import com.codehouse.ciciassistant.memory.domain.MemoryRecordEntity;
import java.util.List;

/** Builds a bounded, domain-neutral prompt fragment from already-authorized memory context. */
public class MemoryContextPromptAssembler {

    public String build(ExternalMemoryContextService.MemoryContext context, int characterBudget) {
        if (context == null || characterBudget <= 0) {
            return "";
        }
        StringBuilder output = new StringBuilder();
        appendWithinBudget(output, "## 当前主体上下文\n", characterBudget);
        appendWithinBudget(output, "- 会话摘要：" + emptyFallback(context.summary(), "无") + "\n", characterBudget);
        List<MemoryRecordEntity> records = context.records() == null ? List.of() : context.records();
        for (MemoryRecordEntity record : records) {
            if (output.length() >= characterBudget) {
                break;
            }
            String line = "- [" + record.getMemoryType() + "] " + record.getContent() + "\n";
            appendWithinBudget(output, line, characterBudget);
        }
        return output.toString();
    }

    private static void appendWithinBudget(StringBuilder output, String value, int budget) {
        int remaining = budget - output.length();
        if (remaining <= 0) {
            return;
        }
        if (value.length() <= remaining) {
            output.append(value);
            return;
        }
        if (remaining == 1) {
            output.append('…');
            return;
        }
        output.append(value, 0, remaining - 1).append('…');
    }

    private static String emptyFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
