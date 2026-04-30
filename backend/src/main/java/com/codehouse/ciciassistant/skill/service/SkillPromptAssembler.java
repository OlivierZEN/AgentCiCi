package com.codehouse.ciciassistant.skill.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SkillPromptAssembler {

    public String assemble(String basePrompt, SkillResolverService.ResolvedSkillContext context) {
        if (context.skills().isEmpty()) {
            return composePromptWithoutSkills(basePrompt, context);
        }
        List<String> lines = new ArrayList<>();
        lines.add(composeBasePrompt(basePrompt, context.agentSystemPrompt()));
        lines.add("");
        appendPolicyBundle(lines, context);
        lines.add("Active business skills are attached below. Apply them as operating policy. When rules conflict, prefer the stricter safety rule.");
        if (!context.skillScopedToolNames().isEmpty()) {
            lines.add("Tool permission model: tools listed under agentDirectToolNames may be used as Agent-bound tools;"
                    + " tools only declared on Skills (skillScopedToolNames) are scoped to bound Skill execution and do not expand Agent static bindings.");
        }
        if (!context.skillCodes().isEmpty()) {
            lines.add("Active skill codes: " + String.join(", ", context.skillCodes()));
        }
        if (context.activeSkillCode() != null && !context.activeSkillCode().isBlank()) {
            lines.add("Current skill execution context (authorizes skill-scoped tools from this skill): " + context.activeSkillCode().trim());
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

    private String composePromptWithoutSkills(String basePrompt, SkillResolverService.ResolvedSkillContext context) {
        List<String> lines = new ArrayList<>();
        lines.add(composeBasePrompt(basePrompt, context.agentSystemPrompt()));
        lines.add("");
        appendPolicyBundle(lines, context);
        if (!context.handoffRules().isEmpty()) {
            lines.add("Handoff rules:");
            context.handoffRules().forEach(rule -> lines.add("- " + rule));
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

    private void appendPolicyBundle(List<String> lines, SkillResolverService.ResolvedSkillContext context) {
        if (context.policyBundle() == null || !context.policyBundle().hasContent()) {
            return;
        }
        lines.add("Platform core policy bundle is always on"
                + formatBundleVersion(context.policyBundle().bundleCode(), context.policyBundle().versionNo())
                + ".");
        if (context.policyBundle().promptFragment() != null && !context.policyBundle().promptFragment().isBlank()) {
            lines.add(context.policyBundle().promptFragment().trim());
        }
        lines.add("");
    }

    private String formatBundleVersion(String bundleCode, Integer versionNo) {
        if (bundleCode == null || bundleCode.isBlank() || versionNo == null) {
            return "";
        }
        return " (" + bundleCode + "@v" + versionNo + ")";
    }
}
