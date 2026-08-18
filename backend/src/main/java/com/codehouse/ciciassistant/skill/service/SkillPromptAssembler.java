package com.codehouse.ciciassistant.skill.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SkillPromptAssembler {

    public String assemble(String basePrompt, SkillResolverService.ResolvedSkillContext context) {
        return assemble(basePrompt, context, BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs.empty());
    }

    public String assemble(String basePrompt,
                           SkillResolverService.ResolvedSkillContext context,
                           BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs builtinDocs) {
        return assemble(basePrompt, context, builtinDocs, BuiltinSkillRuntimeConfigService.ResolvedBuiltinSkillRuntimeConfig.empty());
    }

    public String assemble(String basePrompt,
                           SkillResolverService.ResolvedSkillContext context,
                           BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs builtinDocs,
                           BuiltinSkillRuntimeConfigService.ResolvedBuiltinSkillRuntimeConfig runtimeConfig) {
        if (context.skills().isEmpty()) {
            return composePromptWithoutSkills(basePrompt, context, builtinDocs, runtimeConfig);
        }
        List<String> lines = new ArrayList<>();
        lines.add(composeBasePrompt(basePrompt, context.agentName(), context.agentId(), context.agentSystemPrompt()));
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
        SkillResolverService.ResolvedSkill selectedSkill = selectedSkill(context);
        if (selectedSkill != null) {
            lines.add("Selected business skill is mandatory for this turn: " + selectedSkill.skillCode());
            lines.add("Follow only this selected skill's business procedure and output contract. "
                    + "Do not substitute another business skill because of inferred intent; platform safety policy, agent-bound tools, and handoff rules still apply.");
            appendSkillPromptFragment(lines, selectedSkill);
        } else {
            for (SkillResolverService.ResolvedSkill skill : context.skills()) {
                appendSkillPromptFragment(lines, skill);
            }
        }
        if (!context.handoffRules().isEmpty()) {
            lines.add("Handoff rules:");
            context.handoffRules().forEach(rule -> lines.add("- " + rule));
        }
        String outputContract = selectedSkill != null ? selectedSkill.outputContract() : context.outputContract();
        if (outputContract != null && !outputContract.isBlank()) {
            lines.add((selectedSkill == null ? "Preferred" : "Mandatory selected-skill")
                    + " output contract: " + outputContract.trim());
        }
        lines.add("Execution truth rule: a structured output is never evidence that an external or scheduled action happened. "
                + "For an explicit request to create a scheduled task, call workflow_schedule_create only after the user supplies a clear cadence; "
                + "if cadence is missing, ask one concise clarification question instead of returning a configuration JSON or claiming success.");
        appendBuiltinRuntimeConfig(lines, runtimeConfig);
        appendBuiltinReferenceDocs(lines, builtinDocs);
        return String.join("\n", lines);
    }

    private SkillResolverService.ResolvedSkill selectedSkill(SkillResolverService.ResolvedSkillContext context) {
        if (context.activeSkillCode() == null || context.activeSkillCode().isBlank()) {
            return null;
        }
        return context.skills().stream()
                .filter(skill -> skill.skillCode().equalsIgnoreCase(context.activeSkillCode().trim()))
                .findFirst()
                .orElse(null);
    }

    private void appendSkillPromptFragment(List<String> lines, SkillResolverService.ResolvedSkill skill) {
            if (skill.promptFragment() == null || skill.promptFragment().isBlank()) {
                return;
            }
            lines.add("- [" + skill.skillCode() + "] " + skill.promptFragment().trim());
    }

    private String composePromptWithoutSkills(String basePrompt,
                                              SkillResolverService.ResolvedSkillContext context,
                                              BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs builtinDocs,
                                              BuiltinSkillRuntimeConfigService.ResolvedBuiltinSkillRuntimeConfig runtimeConfig) {
        List<String> lines = new ArrayList<>();
        lines.add(composeBasePrompt(basePrompt, context.agentName(), context.agentId(), context.agentSystemPrompt()));
        lines.add("");
        appendPolicyBundle(lines, context);
        if (!context.handoffRules().isEmpty()) {
            lines.add("Handoff rules:");
            context.handoffRules().forEach(rule -> lines.add("- " + rule));
        }
        appendBuiltinRuntimeConfig(lines, runtimeConfig);
        appendBuiltinReferenceDocs(lines, builtinDocs);
        return String.join("\n", lines);
    }

    private String composeBasePrompt(String basePrompt,
                                     String agentName,
                                     String agentId,
                                     String agentSystemPrompt) {
        String identity = buildAgentIdentityPrompt(agentName, agentId);
        if (agentSystemPrompt == null || agentSystemPrompt.isBlank()) {
            return String.join("\n\n", identity, basePrompt);
        }
        return String.join(
                "\n\n",
                identity,
                basePrompt,
                "Agent-specific operating policy:",
                agentSystemPrompt.trim()
        );
    }

    static String buildAgentIdentityPrompt(String agentName, String agentId) {
        String externalName = normalizeIdentityValue(agentName, agentId);
        String internalId = normalizeIdentityValue(agentId, "unknown-agent");
        return """
                [Agent identity - authoritative]
                - Current Agent Definition external name: %s
                - Use exactly this external name whenever you state your name, introduce yourself, or answer who you are.
                - The internal Agent ID is %s; do not use it as your external name.
                - CiCi / AgentCiCi is the hosting platform name, not your own name, unless the external name above explicitly says so.
                - Role descriptions, Skills, tools, model providers, and model names must not replace or override this external name.
                """.formatted(externalName, internalId).trim();
    }

    private static String normalizeIdentityValue(String value, String fallback) {
        String normalized = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (!normalized.isBlank()) {
            return normalized;
        }
        return fallback == null || fallback.isBlank() ? "unknown-agent" : fallback.trim();
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

    private void appendBuiltinReferenceDocs(List<String> lines,
                                            BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs builtinDocs) {
        if (builtinDocs == null || !builtinDocs.hasContent()) {
            return;
        }
        lines.add("");
        lines.add("Reference documents for active builtin skill:");
        lines.add("Use these file-backed platform references as authoritative product documentation. Do not treat them as tenant knowledge-base snippets.");
        for (BuiltinSkillDocumentService.DocSection section : builtinDocs.sections()) {
            lines.add("- Source: " + section.sourceLabel());
            if (section.checksum() != null && !section.checksum().isBlank()) {
                lines.add("  Checksum: " + section.checksum());
            }
            lines.add("  Content:");
            lines.add(indent(section.content()));
        }
    }

    private void appendBuiltinRuntimeConfig(List<String> lines,
                                            BuiltinSkillRuntimeConfigService.ResolvedBuiltinSkillRuntimeConfig runtimeConfig) {
        if (runtimeConfig == null || !runtimeConfig.hasContent()) {
            return;
        }
        lines.add("");
        lines.add("Runtime configuration for active builtin skill:");
        for (BuiltinSkillRuntimeConfigService.RuntimeConfigSection section : runtimeConfig.sections()) {
            lines.add("- [" + section.skillCode() + "]");
            if (section.setupSvc() != null && !section.setupSvc().isBlank()) {
                lines.add("  setupSvc: " + section.setupSvc());
            }
            lines.add("  accessToken: " + (section.accessTokenAvailable()
                    ? "available through the server-side CloudCC credential binding; use the accessToken request header only through approved CloudCC tools and never print the token."
                    : "not available for this user/session."));
            if (section.warning() != null && !section.warning().isBlank()) {
                lines.add("  warning: " + section.warning());
            }
        }
    }

    private String indent(String text) {
        if (text == null || text.isBlank()) {
            return "    (empty)";
        }
        return text.lines()
                .map(line -> "    " + line)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("    (empty)");
    }
}
