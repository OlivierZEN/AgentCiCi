package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AliyunAsrServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcceptRequestedAudioAndVideoExtensions() {
        List.of(
                "sample.aac",
                "sample.amr",
                "sample.avi",
                "sample.flac",
                "sample.flv",
                "sample.m4a",
                "sample.mkv",
                "sample.mov",
                "sample.mp3",
                "sample.mp4",
                "sample.mpeg",
                "sample.ogg",
                "sample.opus",
                "sample.wav",
                "sample.webm",
                "sample.wma",
                "sample.wmv"
        ).forEach(name -> assertThat(AliyunAsrService.validateSupportedFile(name, "application/octet-stream").extension())
                .isEqualTo(name.substring(name.lastIndexOf('.') + 1)));
    }

    @Test
    void shouldRejectUnsupportedFileExtensions() {
        assertThatThrownBy(() -> AliyunAsrService.validateSupportedFile("notes.txt", "text/plain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持以下音视频格式")
                .hasMessageContaining("mp3")
                .hasMessageContaining("wav");
    }

    @Test
    void shouldParseSpeakerDiarizationSentences() throws Exception {
        String json = """
                {
                  "transcripts": [
                    {
                      "sentences": [
                        {
                          "begin_time": 100,
                          "end_time": 3820,
                          "text": "你好，我们今天讨论项目进度。",
                          "speaker_id": 0
                        },
                        {
                          "begin_time": 3820,
                          "end_time": 6500,
                          "text": "好的，我先汇报一下。",
                          "speaker_id": 1
                        }
                      ]
                    }
                  ]
                }
                """;

        List<AliyunAsrService.MeetingFileTranscriptSegment> segments =
                AliyunAsrService.parseFileTranscript(objectMapper.readTree(json));

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).speakerId()).isEqualTo("1");
        assertThat(segments.get(0).speakerName()).isEqualTo("发言人 1");
        assertThat(segments.get(0).text()).isEqualTo("你好，我们今天讨论项目进度。");
        assertThat(segments.get(0).startMs()).isEqualTo(100L);
        assertThat(segments.get(0).endMs()).isEqualTo(3820L);
        assertThat(segments.get(1).speakerId()).isEqualTo("2");
        assertThat(segments.get(1).speakerName()).isEqualTo("发言人 2");
    }

    @Test
    void shouldOnlyAcceptFileAsrModelsWithSpeakerDiarization() {
        assertThat(AliyunAsrService.supportsSpeakerDiarizationFileAsr(
                "qwen-audio-3.0-asr-flash-filetrans")).isTrue();
        assertThat(AliyunAsrService.supportsSpeakerDiarizationFileAsr(
                "qwen-audio-3.0-asr-flash-filetrans-2026-08-01")).isTrue();
        assertThat(AliyunAsrService.supportsSpeakerDiarizationFileAsr("fun-asr")).isTrue();
        assertThat(AliyunAsrService.supportsSpeakerDiarizationFileAsr("fun-asr-2025-11-07")).isTrue();
        assertThat(AliyunAsrService.supportsSpeakerDiarizationFileAsr("fun-asr-mtl")).isTrue();
        assertThat(AliyunAsrService.supportsSpeakerDiarizationFileAsr("qwen-audio-3.0-asr-flash")).isFalse();
        assertThat(AliyunAsrService.supportsSpeakerDiarizationFileAsr("fun-asr-flash")).isFalse();
        assertThat(AliyunAsrService.supportsSpeakerDiarizationFileAsr("qwen3-asr-flash-filetrans")).isFalse();
    }
}
