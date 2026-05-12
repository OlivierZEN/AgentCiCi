package com.codehouse.ciciassistant.ai.ws;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class IflytekAsrResultParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void tracksWordLevelSpeakerSwitchMarkers() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "action": "result",
                  "data": {
                    "cn": {
                      "st": {
                        "type": "0",
                        "rt": [
                          {
                            "ws": [
                              {"cw": [{"w": "今天", "rl": "1"}]},
                              {"cw": [{"w": "继续", "rl": "0"}]},
                              {"cw": [{"w": "你说", "rl": "2"}]},
                              {"cw": [{"w": "几句", "rl": "0"}]}
                            ]
                          }
                        ]
                      }
                    }
                  }
                }
                """);

        var payload = IflytekAsrResultParser.parsePayload(objectMapper, root);
        var result = IflytekAsrResultParser.extractPieces(payload, "");
        var pieces = result.pieces();

        assertThat(pieces).extracting(IflytekAsrResultParser.TranscriptPiece::speakerId)
                .containsExactly("1", "2");
        assertThat(pieces).extracting(IflytekAsrResultParser.TranscriptPiece::text)
                .containsExactly("今天继续", "你说几句");
        assertThat(result.activeSpeakerId()).isEqualTo("2");
        assertThat(IflytekAsrResultParser.speakerDisplayName("0")).isEqualTo("发言人 1");
        assertThat(IflytekAsrResultParser.speakerDisplayName("1")).isEqualTo("发言人 1");
        assertThat(IflytekAsrResultParser.speakerDisplayName("2")).isEqualTo("发言人 2");
    }

    @Test
    void carriesActiveSpeakerAcrossPayloadsWhenRoleMarkerIsZero() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "action": "result",
                  "data": {
                    "cn": {
                      "st": {
                        "type": "0",
                        "rt": [
                          {
                            "ws": [
                              {"cw": [{"w": "继续", "rl": "0"}]},
                              {"cw": [{"w": "上一位", "rl": "0"}]}
                            ]
                          }
                        ]
                      }
                    }
                  }
                }
                """);

        var payload = IflytekAsrResultParser.parsePayload(objectMapper, root);
        var result = IflytekAsrResultParser.extractPieces(payload, "2");

        assertThat(result.pieces()).hasSize(1);
        assertThat(result.pieces().get(0).speakerId()).isEqualTo("2");
        assertThat(result.pieces().get(0).text()).isEqualTo("继续上一位");
        assertThat(result.activeSpeakerId()).isEqualTo("2");
    }

    @Test
    void parsesTextualDataPayload() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "action": "result",
                  "data": "{\\"cn\\":{\\"st\\":{\\"type\\":\\"0\\",\\"rt\\":[{\\"ws\\":[{\\"cw\\":[{\\"w\\":\\"好\\",\\"rl\\":\\"2\\"}]}]}]}}}}"
                }
                """);

        var payload = IflytekAsrResultParser.parsePayload(objectMapper, root);
        var result = IflytekAsrResultParser.extractPieces(payload, "");
        var pieces = result.pieces();

        assertThat(pieces).hasSize(1);
        assertThat(pieces.get(0).speakerId()).isEqualTo("2");
        assertThat(pieces.get(0).text()).isEqualTo("好");
    }
}
