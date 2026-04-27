package com.codehouse.ciciassistant.ai.service;

import java.util.regex.Pattern;

/**
 * Removes visible chain-of-thought / "thinking process" sections some models emit inside the assistant message body.
 */
public final class AssistantContentSanitizer {

    /** Markdown heading style, e.g. ## Thinking Process */
    private static final Pattern THINKING_HEADING = Pattern.compile(
            "(?is)(^|\\n)#+\\s*(Thinking\\s*Process|思考过程|Chain[- ]?of[- ]?Thought|思维链|分析过程)[:：]?[^\\n]*\\n.*?(?=\\n#+\\s|\\z)");

    /** Bold line style, e.g. **Thinking Process:** — strip through the next markdown heading or EOF. */
    private static final Pattern THINKING_BOLD = Pattern.compile(
            "(?is)(^|\\n)\\*\\*Thinking\\s*Process\\*\\*[:：]?[^\\n]*\\n.*?(?=\\n#+\\s|\\z)");

    private AssistantContentSanitizer() {
    }

    public static String stripThinkingSections(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String cleaned = THINKING_HEADING.matcher(text).replaceAll("\n");
        cleaned = THINKING_BOLD.matcher(cleaned).replaceAll("\n");
        return cleaned.replaceAll("\n{3,}", "\n\n").trim();
    }
}
