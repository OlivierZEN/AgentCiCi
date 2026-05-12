package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ChatCompletionResult;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.codehouse.ciciassistant.skill.domain.SkillBindingPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillEditPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillSourceType;
import com.codehouse.ciciassistant.skill.domain.SkillUpdatePolicy;
import com.codehouse.ciciassistant.skill.domain.SkillVisibility;
import com.codehouse.ciciassistant.skill.service.SkillDefinitionService;
import com.codehouse.ciciassistant.skill.service.SkillPromptAssembler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MeetingMinutesServiceTest {

    @Test
    void shouldExplicitlyApplyAiMeetingNotetakerSkillWhenSummarizing() {
        AliyunBailianClient client = mock(AliyunBailianClient.class);
        ModelRouterService modelRouterService = mock(ModelRouterService.class);
        ModelProviderService modelProviderService = mock(ModelProviderService.class);
        SkillDefinitionService skillDefinitionService = mock(SkillDefinitionService.class);
        SkillPromptAssembler skillPromptAssembler = new SkillPromptAssembler();
        MeetingMinutesService service = new MeetingMinutesService(
                client,
                modelRouterService,
                modelProviderService,
                skillDefinitionService,
                skillPromptAssembler);

        SkillDefinitionEntity skill = new SkillDefinitionEntity(
                "demo-org",
                "ai-meeting-notetaker",
                "AI 听记",
                "面向会议实时转写后的结构化纪要生成能力。",
                true,
                true,
                "AI meeting notetaker skill prompt: faithfully summarize meeting transcripts.",
                "AI meeting notetaker skill prompt: faithfully summarize meeting transcripts.",
                null,
                null,
                "不要补造缺失的负责人或日期。",
                "输出中文 Markdown，并包含行动项表格。",
                "LOW",
                SkillSourceType.PLATFORM_STANDARD,
                SkillVisibility.VISIBLE,
                SkillEditPolicy.CONFIGURABLE,
                SkillBindingPolicy.OPTIONAL,
                SkillUpdatePolicy.AUTO,
                "ai-meeting-notetaker",
                null
        );
        when(skillDefinitionService.listSkills("demo-org")).thenReturn(List.of(skill));
        when(modelRouterService.route("demo-org", "chat"))
                .thenReturn(Map.of("provider", "aliyun-bailian", "modelName", "deepseek-v4-pro"));
        when(modelProviderService.credentialsForProvider("demo-org", "aliyun-bailian"))
                .thenReturn(Map.of(
                        "apiBaseUrl", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                        "apiKey", "configured-api-key",
                        "providerCode", "aliyun-bailian",
                        "enabled", "true"
                ));
        when(client.chatCompletionWithCredentials(
                eq("deepseek-v4-pro"),
                anyList(),
                isNull(),
                eq(true),
                eq("https://dashscope.aliyuncs.com/compatible-mode/v1"),
                eq("configured-api-key")))
                .thenReturn(new ChatCompletionResult("assistant", "## Meeting Summary\n- 已整理", List.of(), "stop", 10, 5));

        MeetingMinutesService.MeetingMinutesResult result = service.summarize(
                "demo-org",
                "周会",
                List.of(new MeetingMinutesService.TranscriptSegment("1", "张三", "我们下周发布。", 0L, 1000L))
        );

        assertThat(result.summary()).contains("Meeting Summary");
        assertThat(result.skillCode()).isEqualTo("ai-meeting-notetaker");
        assertThat(result.skillName()).isEqualTo("AI 听记");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(client).chatCompletionWithCredentials(
                eq("deepseek-v4-pro"),
                messagesCaptor.capture(),
                isNull(),
                eq(true),
                eq("https://dashscope.aliyuncs.com/compatible-mode/v1"),
                eq("configured-api-key"));
        String systemPrompt = messagesCaptor.getValue().get(0).get("content").toString();
        String userPrompt = messagesCaptor.getValue().get(1).get("content").toString();

        assertThat(systemPrompt)
                .contains("Active skill codes: ai-meeting-notetaker")
                .contains("Current skill execution context")
                .contains("AI meeting notetaker skill prompt")
                .contains("Preferred output contract: 输出中文 Markdown，并包含行动项表格。");
        assertThat(userPrompt)
                .contains("张三：我们下周发布。")
                .contains("Topic**: 周会");
    }
}
