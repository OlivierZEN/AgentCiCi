package com.codehouse.ciciassistant.skill.service;

import java.util.List;

/**
 * Structured intermediate representation for skill authoring.
 * This IR bridges natural-language intent and persisted skill assets.
 */
public record SkillSpecIr(
        String skillCode,
        String name,
        String description,
        String skillKind,
        List<String> triggerHints,
        List<String> userIntentExamples,
        String promptFragment,
        List<String> toolWhitelist,
        List<String> kbWhitelist,
        String handoffRule,
        String outputContract,
        String riskLevel,
        List<String> clarificationQuestions,
        List<String> warnings
) {
}
