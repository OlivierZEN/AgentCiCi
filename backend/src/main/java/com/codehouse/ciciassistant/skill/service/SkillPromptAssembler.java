package com.codehouse.ciciassistant.skill.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SkillPromptAssembler {

    public String assemble(String basePrompt, SkillResolverService.ResolvedSkillContext context) {
        // Agent-level system prompt takes precedence over the global default.
        String effectiveBase = (context.agentSystemPrompt() != null && !context.agentSystemPrompt().isBlank())
                ? context.agentSystemPrompt()
                : basePrompt;

        if (context.skills().isEmpty()) {
            return effectiveBase;
        }
        List<String> lines = new ArrayList<>();
        lines.add(effectiveBase);
        lines.add("");
        lines.add("Active business skills are attached below. Apply them as operating policy. When rules conflict, prefer the stricter safety rule.");
        if (!context.skillCodes().isEmpty()) {
            lines.add("Active skill codes: " + String.join(", ", context.skillCodes()));
        }
        for (SkillResolverService.ResolvedSkill skill : context.skills()) {
            if (skill.promptFragment() == null || skill.promptFragment().isBlank()) {
                continue;
            }
            lines.add("- [" + skill.skillCode() + "] " + skill.promptFragment().trim());
        }
        if (!context.handoffRules().isEmpty()) {
            lines.add("Handoff rules:");
            context.handoffRules().forEach(rule -> lines.add("- " + rule));
        }
        if (context.outputContract() != null && !context.outputContract().isBlank()) {
            lines.add("Preferred output contract: " + context.outputContract());
        }
        return String.join("\n", lines);
    }
}
