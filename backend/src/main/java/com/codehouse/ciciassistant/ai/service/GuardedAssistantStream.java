package com.codehouse.ciciassistant.ai.service;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts provider deltas into safe user-visible frames without pretending that a completed
 * response is still being generated. Incremental mode releases complete lines or sentences after
 * their guard passes. Buffered mode performs one whole-response guard and emits one delta.
 */
final class GuardedAssistantStream {

    private static final Pattern THINKING_HEADING = Pattern.compile(
            "(?is)^\\s*#+\\s*(Thinking\\s*Process|思考过程|Chain[- ]?of[- ]?Thought|思维链|分析过程)[:：]?.*$");
    private static final Pattern THINKING_BOLD = Pattern.compile(
            "(?is)^\\s*\\*\\*Thinking\\s*Process\\*\\*[:：]?.*$");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?s)^\\s*#+\\s+.+$");
    private static final Pattern PRIVATE_KEY_BEGIN = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----");
    private static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
            "(?s)^.*?-----END [A-Z ]*PRIVATE KEY-----\\r?\\n?");
    private static final int MIN_SENTENCE_FRAME_CHARS = 32;

    private final Mode mode;
    private final boolean stripThinking;
    private final TextGuard guard;
    private final Consumer<String> sink;
    private final long startedNanos;
    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder buffered = new StringBuilder();
    private final StringBuilder visible = new StringBuilder();
    private boolean suppressThinking;
    private boolean privateKeyBlock;
    private boolean terminal;
    private long firstProviderDeltaNanos = -1L;
    private long firstClientDeltaNanos = -1L;

    GuardedAssistantStream(Mode mode,
                           boolean stripThinking,
                           TextGuard guard,
                           Consumer<String> sink) {
        this.mode = mode == null ? Mode.BUFFERED : mode;
        this.stripThinking = stripThinking;
        this.guard = guard;
        this.sink = sink;
        this.startedNanos = System.nanoTime();
    }

    void accept(String piece) {
        if (piece == null || piece.isEmpty() || terminal) {
            return;
        }
        if (firstProviderDeltaNanos < 0) {
            firstProviderDeltaNanos = System.nanoTime();
        }
        if (mode == Mode.BUFFERED) {
            buffered.append(piece);
            return;
        }
        pending.append(piece);
        drain(false);
    }

    Result finish() {
        if (terminal) {
            return result();
        }
        if (mode == Mode.BUFFERED) {
            String candidate = stripThinking
                    ? AssistantContentSanitizer.stripThinkingSections(buffered.toString())
                    : buffered.toString();
            emitGuarded(candidate);
            return result();
        }
        drain(true);
        return result();
    }

    private void drain(boolean finishing) {
        while (!terminal && !pending.isEmpty()) {
            int boundary = nextBoundary(finishing);
            if (boundary <= 0) {
                return;
            }
            String frame = pending.substring(0, boundary);
            pending.delete(0, boundary);
            if (stripThinking && shouldSuppress(frame)) {
                continue;
            }
            emitGuarded(frame);
        }
    }

    private int nextBoundary(boolean finishing) {
        if (privateKeyBlock) {
            Matcher block = PRIVATE_KEY_BLOCK.matcher(pending);
            if (block.find()) {
                privateKeyBlock = false;
                return block.end();
            }
            return finishing ? pending.length() : -1;
        }

        int lineEnd = pending.indexOf("\n");
        int candidateEnd = lineEnd >= 0 ? lineEnd + 1 : -1;
        String candidate = candidateEnd > 0 ? pending.substring(0, candidateEnd) : pending.toString();
        if (PRIVATE_KEY_BEGIN.matcher(candidate).find()) {
            privateKeyBlock = true;
            Matcher block = PRIVATE_KEY_BLOCK.matcher(pending);
            if (block.find()) {
                privateKeyBlock = false;
                return block.end();
            }
            return finishing ? pending.length() : -1;
        }
        if (candidateEnd > 0) {
            return candidateEnd;
        }
        if (finishing) {
            return pending.length();
        }
        return sentenceBoundary(pending);
    }

    private static int sentenceBoundary(CharSequence text) {
        if (text.length() < MIN_SENTENCE_FRAME_CHARS) {
            return -1;
        }
        for (int index = MIN_SENTENCE_FRAME_CHARS - 1; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '。' || value == '！' || value == '？'
                    || value == '!' || value == '?' || value == '；' || value == ';') {
                return index + 1;
            }
        }
        return -1;
    }

    private boolean shouldSuppress(String frame) {
        String trimmed = frame.trim();
        if (suppressThinking) {
            if (MARKDOWN_HEADING.matcher(trimmed).matches()
                    && !THINKING_HEADING.matcher(trimmed).matches()) {
                suppressThinking = false;
                return false;
            }
            return true;
        }
        if (THINKING_HEADING.matcher(trimmed).matches()
                || THINKING_BOLD.matcher(trimmed).matches()) {
            suppressThinking = true;
            return true;
        }
        return false;
    }

    private void emitGuarded(String frame) {
        if (frame == null || frame.isEmpty() || terminal) {
            return;
        }
        GuardDecision decision = frame.isBlank()
                ? GuardDecision.allow(frame)
                : guard.apply(frame);
        String safe = decision == null || decision.text() == null ? "" : decision.text();
        if (!safe.isEmpty()) {
            if (firstClientDeltaNanos < 0) {
                firstClientDeltaNanos = System.nanoTime();
            }
            sink.accept(safe);
            visible.append(safe);
        }
        terminal = decision != null && decision.terminal();
    }

    private Result result() {
        return new Result(
                visible.toString(),
                elapsedMs(firstProviderDeltaNanos),
                elapsedMs(firstClientDeltaNanos),
                mode.name().toLowerCase(Locale.ROOT));
    }

    private int elapsedMs(long eventNanos) {
        if (eventNanos < 0) {
            return -1;
        }
        return (int) Math.max(0L, (eventNanos - startedNanos) / 1_000_000L);
    }

    enum Mode { STREAMING, BUFFERED }

    @FunctionalInterface
    interface TextGuard {
        GuardDecision apply(String text);
    }

    record GuardDecision(String text, boolean terminal) {
        static GuardDecision allow(String text) {
            return new GuardDecision(text, false);
        }

        static GuardDecision stop(String text) {
            return new GuardDecision(text, true);
        }
    }

    record Result(String text, int firstProviderDeltaMs, int firstClientDeltaMs, String outputMode) {
    }
}
