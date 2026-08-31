package com.codehouse.ciciassistant.openapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.openapi.config.AgentOpenApiProperties;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiFeedbackRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiFileRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiMessageEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiMessageRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiSessionMapRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiTaskEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
                mock(AgentOpenApiAttachmentService.class),
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

    @Test
    void shouldPublishEveryInternalDeltaBeforeSingleMessageEnd() throws Exception {
        AgentOpenApiRunService runService = mock(AgentOpenApiRunService.class);
        AgentApiMessageRepository messageRepository = mock(AgentApiMessageRepository.class);
        AgentApiTaskRepository taskRepository = mock(AgentApiTaskRepository.class);
        AgentApiFeedbackRepository feedbackRepository = mock(AgentApiFeedbackRepository.class);
        AgentOpenApiConversationService service = new AgentOpenApiConversationService(
                mock(AgentOpenApiAuthService.class),
                runService,
                messageRepository,
                taskRepository,
                feedbackRepository,
                mock(AgentApiFileRepository.class),
                mock(AgentApiSessionMapRepository.class),
                mock(AgentOpenApiAttachmentService.class),
                new AgentOpenApiProperties(),
                objectMapper);
        AgentApiCredentialEntity credential = mock(AgentApiCredentialEntity.class);
        when(credential.getId()).thenReturn(1L);
        when(credential.getCompanyId()).thenReturn("demo-org");
        when(credential.getAgentId()).thenReturn("agent-safe");
        AgentOpenApiAuthService.AuthenticatedCredential auth =
                new AgentOpenApiAuthService.AuthenticatedCredential(credential, null, "127.0.0.1", null);
        AgentOpenApiSessionService.SessionResolution session =
                new AgentOpenApiSessionService.SessionResolution("conversation-safe", "internal-safe", true);
        AgentOpenApiRunService.ChatStreamExecution execution =
                new AgentOpenApiRunService.ChatStreamExecution(
                        auth, session, "request-safe", "", "external-safe", Instant.EPOCH, null, List.of());
        AgentApiTaskEntity task = new AgentApiTaskEntity(
                "task-safe", "request-safe", "demo-org", 1L,
                "agent-safe", "external-safe", "conversation-safe");
        Object input = newChatMessageInput();
        String fullAnswer = "第一段    \n第二段";

        when(runService.completeChatStreamSuccess(execution, fullAnswer))
                .thenReturn(new AgentOpenApiRunService.StreamCompletion("trace-safe", 21));
        when(taskRepository.findById("task-safe")).thenReturn(Optional.of(task));
        when(messageRepository.save(any(AgentApiMessageEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(feedbackRepository.findByMessageIdOrderByCreatedAtDesc("message-safe"))
                .thenReturn(List.of());

        CapturingEmitter clientEmitter = new CapturingEmitter();
        SseEmitter bridge = newOpenApiStreamBridge(service, clientEmitter, task, execution, input);
        bridge.send(SseEmitter.event().name("phase").data(Map.of("internalId", "secret-id")));
        bridge.send(SseEmitter.event().name("delta").data(Map.of("text", "第一段 ")));
        bridge.send(SseEmitter.event().name("delta").data(Map.of("text", "   ")));
        bridge.send(SseEmitter.event().name("delta").data(Map.of("text", "\n第二段")));
        bridge.send(SseEmitter.event().name("done").data(Map.of()));

        assertThat(clientEmitter.eventNames())
                .containsExactly("agent_thought", "message", "message", "message", "message_end");
        assertThat(clientEmitter.messageAnswers()).containsExactly("第一段 ", "   ", "\n第二段");
        assertThat(String.join("", clientEmitter.messageAnswers())).isEqualTo(fullAnswer);
        assertThat(clientEmitter.eventNames().stream().filter("message_end"::equals).count())
                .isEqualTo(1L);
        assertThat(objectMapper.writeValueAsString(clientEmitter.eventData()))
                .contains("运行阶段已更新")
                .doesNotContain("secret-id", "internalId", "tool_call", "tool_result");
        verify(runService).completeChatStreamSuccess(execution, fullAnswer);
        ArgumentCaptor<AgentApiMessageEntity> persisted =
                ArgumentCaptor.forClass(AgentApiMessageEntity.class);
        verify(messageRepository).save(persisted.capture());
        assertThat(persisted.getValue().getAnswer()).isEqualTo(fullAnswer);
    }

    private static SseEmitter newOpenApiStreamBridge(AgentOpenApiConversationService service,
                                                     SseEmitter clientEmitter,
                                                     AgentApiTaskEntity task) throws Exception {
        return newOpenApiStreamBridge(service, clientEmitter, task, null, null);
    }

    private static SseEmitter newOpenApiStreamBridge(AgentOpenApiConversationService service,
                                                     SseEmitter clientEmitter,
                                                     AgentApiTaskEntity task,
                                                     AgentOpenApiRunService.ChatStreamExecution execution,
                                                     Object input) throws Exception {
        Class<?> bridgeType = Arrays.stream(AgentOpenApiConversationService.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("OpenApiStreamBridge"))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = bridgeType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (SseEmitter) constructor.newInstance(
                service, clientEmitter, task, "message-safe", execution, input, "");
    }

    private static Object newChatMessageInput() throws Exception {
        Class<?> inputType = Arrays.stream(AgentOpenApiConversationService.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("ChatMessageInput"))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = inputType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(
                "销量最好的产品有哪些？",
                "conversation-safe",
                "external-safe",
                Map.of(),
                List.of(),
                "crm-business-analysis",
                Map.of(),
                null,
                List.of());
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

        private List<String> messageAnswers() {
            List<String> answers = new ArrayList<>();
            for (int index = 0; index < eventNames.size(); index++) {
                if (!"message".equals(eventNames.get(index))) {
                    continue;
                }
                Object data = eventData.get(index);
                if (data instanceof Map<?, ?> map && map.get("answer") != null) {
                    answers.add(String.valueOf(map.get("answer")));
                }
            }
            return List.copyOf(answers);
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
