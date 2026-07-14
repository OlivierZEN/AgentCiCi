package com.codehouse.ciciassistant.openapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.codehouse.ciciassistant.openapi.config.AgentOpenApiProperties;
import com.codehouse.ciciassistant.openapi.domain.AgentApiFeedbackRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiFileRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiMessageRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiSessionMapRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiTaskEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AgentOpenApiConversationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSanitizeInternalCrmEventsBeforePublishingAgentThoughtObservation() throws Exception {
        Map<String, Object> rawPayload = Map.of(
                "toolName", "crm_product_sales_rank",
                "payload", "{\"status\":\"SUCCESS\",\"productId\":\"p-secret\",\"ownerId\":\"005-secret\"}",
                "arguments", Map.of("userId", "u-secret", "accessToken", "token-secret"));

        Object sanitized = AgentOpenApiConversationService.safeAgentThoughtObservation("tool_result", rawPayload);
        String serialized = objectMapper.writeValueAsString(sanitized);

        assertThat(serialized)
                .contains("工具处理完成")
                .doesNotContain("crm_product_sales_rank", "SUCCESS", "productId", "p-secret",
                        "ownerId", "005-secret", "arguments", "userId", "u-secret",
                        "accessToken", "token-secret", "{\"");
    }

    @Test
    void shouldNotForwardUnknownInternalEventPayloadIntoAgentThoughtObservation() throws Exception {
        Object sanitized = AgentOpenApiConversationService.safeAgentThoughtObservation(
                "internal_runtime_event", Map.of(
                "credentials", "secret-value",
                "internalId", "id-secret"));
        String serialized = objectMapper.writeValueAsString(sanitized);

        assertThat(serialized)
                .contains("运行状态已更新")
                .doesNotContain("credentials", "secret-value", "internalId", "id-secret");
    }

    @Test
    void shouldPublishStableSafeThoughtsForActualToolBridgeEvents() throws Exception {
        AgentOpenApiConversationService service = new AgentOpenApiConversationService(
                mock(AgentOpenApiAuthService.class),
                mock(AgentOpenApiRunService.class),
                mock(AgentApiMessageRepository.class),
                mock(AgentApiTaskRepository.class),
                mock(AgentApiFeedbackRepository.class),
                mock(AgentApiFileRepository.class),
                mock(AgentApiSessionMapRepository.class),
                new AgentOpenApiProperties(),
                objectMapper);
        CapturingEmitter clientEmitter = new CapturingEmitter();
        AgentApiTaskEntity task = new AgentApiTaskEntity(
                "task-safe", "request-safe", "demo-org", 1L, "agent-safe", "external-safe", "session-safe");
        SseEmitter bridge = newOpenApiStreamBridge(service, clientEmitter, task);
        Map<String, Object> rawPayload = Map.of(
                "toolName", "crm_product_sales_rank",
                "payload", "{\"status\":\"SUCCESS\",\"productId\":\"p-secret\",\"ownerId\":\"005-secret\"}",
                "arguments", Map.of("accessToken", "token-secret"));

        bridge.send(SseEmitter.event().name("tool_call").data(rawPayload));
        bridge.send(SseEmitter.event().name("tool_result").data(rawPayload));

        assertThat(clientEmitter.eventNames()).containsExactly("agent_thought", "agent_thought");
        assertThat(clientEmitter.thoughts()).containsExactly("工具处理中", "工具处理完成");
        assertThat(clientEmitter.observations()).containsExactly("工具处理中", "工具处理完成");
        assertThat(objectMapper.writeValueAsString(clientEmitter.eventData()))
                .doesNotContain("tool_call", "tool_result", "crm_product_sales_rank", "SUCCESS",
                        "productId", "p-secret", "ownerId", "005-secret", "arguments",
                        "accessToken", "token-secret", "{\"status\"");
    }

    private static SseEmitter newOpenApiStreamBridge(AgentOpenApiConversationService service,
                                                     SseEmitter clientEmitter,
                                                     AgentApiTaskEntity task) throws Exception {
        Class<?> bridgeType = Arrays.stream(AgentOpenApiConversationService.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("OpenApiStreamBridge"))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = bridgeType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (SseEmitter) constructor.newInstance(
                service, clientEmitter, task, "message-safe", null, null, "");
    }

    private static final class CapturingEmitter extends SseEmitter {
        private final List<String> eventNames = new ArrayList<>();
        private final List<Object> eventData = new ArrayList<>();

        private CapturingEmitter() {
            super(60_000L);
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            Set<ResponseBodyEmitter.DataWithMediaType> items = builder.build();
            String eventName = "";
            Object data = null;
            for (ResponseBodyEmitter.DataWithMediaType item : items) {
                Object value = item.getData();
                if (value instanceof String text && text.startsWith("event:")) {
                    String framed = text.substring("event:".length());
                    int lineEnd = framed.indexOf('\n');
                    eventName = (lineEnd >= 0 ? framed.substring(0, lineEnd) : framed).trim();
                } else if (!(value instanceof String)) {
                    data = value;
                }
            }
            eventNames.add(eventName);
            eventData.add(data);
        }

        private List<String> eventNames() {
            return List.copyOf(eventNames);
        }

        private List<Object> eventData() {
            return List.copyOf(eventData);
        }

        private List<String> thoughts() {
            return textValues("thought");
        }

        private List<String> observations() {
            return textValues("observation");
        }

        private List<String> textValues(String key) {
            return eventData.stream()
                    .map(data -> data instanceof Map<?, ?> map ? String.valueOf(map.get(key)) : "")
                    .toList();
        }
    }
}
