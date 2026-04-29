package com.codehouse.ciciassistant.skill.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SkillPromptAssembler {

    public String assemble(String basePrompt, SkillResolverService.ResolvedSkillContext context) {
        if (context.skills().isEmpty()) {
            return composeBasePrompt(basePrompt, context.agentSystemPrompt());
        }
        List<String> lines = new ArrayList<>();
        lines.add(composeBasePrompt(basePrompt, context.agentSystemPrompt()));
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

    private String composeBasePrompt(String basePrompt, String agentSystemPrompt) {
        if (agentSystemPrompt == null || agentSystemPrompt.isBlank()) {
            return basePrompt;
        }
        return String.join(
                "\n\n",
                basePrompt,
                "Agent-specific operating policy:",
                agentSystemPrompt.trim()
        );
    }
}
