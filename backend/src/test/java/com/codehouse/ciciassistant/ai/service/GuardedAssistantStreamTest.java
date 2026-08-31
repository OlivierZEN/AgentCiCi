package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuardedAssistantStreamTest {

    @Test
    void shouldReleaseProviderFramesBeforeTheModelFinishes() {
        List<String> deltas = new ArrayList<>();
        GuardedAssistantStream output = new GuardedAssistantStream(
                GuardedAssistantStream.Mode.STREAMING,
                true,
                GuardedAssistantStream.GuardDecision::allow,
                deltas::add);

        output.accept("第一段已经生成。\n");
        assertThat(deltas).containsExactly("第一段已经生成。\n");

        output.accept("第二段继续生成。\n");
        GuardedAssistantStream.Result result = output.finish();

        assertThat(deltas).containsExactly("第一段已经生成。\n", "第二段继续生成。\n");
        assertThat(result.text()).isEqualTo(String.join("", deltas));
        assertThat(result.outputMode()).isEqualTo("streaming");
        assertThat(result.firstProviderDeltaMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.firstClientDeltaMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldBeHonestWhenWholeResponseValidationRequiresBuffering() {
        List<String> deltas = new ArrayList<>();
        GuardedAssistantStream output = new GuardedAssistantStream(
                GuardedAssistantStream.Mode.BUFFERED,
                true,
                text -> GuardedAssistantStream.GuardDecision.allow(text.replace("secret", "[redacted]")),
                deltas::add);

        output.accept("first secret");
        output.accept(" second");
        assertThat(deltas).isEmpty();

        GuardedAssistantStream.Result result = output.finish();

        assertThat(deltas).containsExactly("first [redacted] second");
        assertThat(result.outputMode()).isEqualTo("buffered");
        assertThat(result.text()).isEqualTo("first [redacted] second");
    }

    @Test
    void shouldSuppressVisibleThinkingAndResumeAtTheAnswerHeading() {
        List<String> deltas = new ArrayList<>();
        GuardedAssistantStream output = new GuardedAssistantStream(
                GuardedAssistantStream.Mode.STREAMING,
                true,
                GuardedAssistantStream.GuardDecision::allow,
                deltas::add);

        output.accept("## 思考过程\n");
        output.accept("这里是不能展示的推理。\n");
        output.accept("## 回答\n");
        output.accept("这是最终答案。\n");
        GuardedAssistantStream.Result result = output.finish();

        assertThat(result.text()).isEqualTo("## 回答\n这是最终答案。\n");
        assertThat(result.text()).doesNotContain("思考过程", "不能展示的推理");
    }

    @Test
    void shouldHoldAMultilinePrivateKeyUntilOneGuardedFrameIsComplete() {
        List<String> guardedFrames = new ArrayList<>();
        List<String> deltas = new ArrayList<>();
        GuardedAssistantStream output = new GuardedAssistantStream(
                GuardedAssistantStream.Mode.STREAMING,
                true,
                text -> {
                    guardedFrames.add(text);
                    return GuardedAssistantStream.GuardDecision.allow("[private-key]\n");
                },
                deltas::add);

        output.accept("-----BEGIN PRIVATE KEY-----\nabc\n");
        assertThat(deltas).isEmpty();
        output.accept("-----END PRIVATE KEY-----\n");
        output.finish();

        assertThat(guardedFrames).containsExactly(
                "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----\n");
        assertThat(deltas).containsExactly("[private-key]\n");
    }

    @Test
    void shouldStopAfterAGuardBlocksAFrame() {
        List<String> deltas = new ArrayList<>();
        GuardedAssistantStream output = new GuardedAssistantStream(
                GuardedAssistantStream.Mode.STREAMING,
                true,
                text -> text.contains("blocked")
                        ? GuardedAssistantStream.GuardDecision.stop("内容已拦截。")
                        : GuardedAssistantStream.GuardDecision.allow(text),
                deltas::add);

        output.accept("安全内容。\n");
        output.accept("blocked 内容。\n");
        output.accept("不得继续显示。\n");
        GuardedAssistantStream.Result result = output.finish();

        assertThat(deltas).containsExactly("安全内容。\n", "内容已拦截。");
        assertThat(result.text()).doesNotContain("不得继续显示");
    }
}
