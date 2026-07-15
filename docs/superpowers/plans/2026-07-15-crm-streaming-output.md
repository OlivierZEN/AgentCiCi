# CRM True Streaming Output Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make deterministic CRM business-analysis answers arrive as genuine ordered SSE fragments while preserving the exact blocking, persisted, and OpenAPI answer body.

**Architecture:** Keep the existing deterministic CRM formatter as the only answer source. Route its completed safe text through the existing `safeSendDeltaInChunks` helper, then rely on the existing OpenAPI bridge to map each internal `delta` one-to-one to an external `message` before one terminal `message_end`.

**Tech Stack:** Java 21, Spring MVC `SseEmitter`, JUnit 5, AssertJ, Mockito, Maven, React/Vitest/Vite verification, Docker Compose, Alibaba Cloud ACR.

## Global Constraints

- The approved server-side chunk contract is at most 18 Java characters per non-empty fragment with about 18ms between adjacent fragments.
- `/ai/chat/stream` must emit `phase... -> delta x N -> done`, with `N > 1` for the non-empty long CRM answer and exactly one `done` after the last `delta`.
- Agent OpenAPI streaming must emit one `message` for every internal `delta`, followed by exactly one `message_end`; concatenated `message.answer` values must equal the blocking answer exactly.
- Blocking responses and persistence remain one complete deterministic body; no answer facts, ranking, formatter output, permissions, billing facts, or database schema change.
- Do not restore a final LLM call and do not expose `tool_call`, `tool_result`, tool names, raw JSON, internal record IDs, tokens, cookies, or credentials.
- Do not modify frontend production code, CRM sample data, CloudCC metadata, roles, profiles, sharing rules, runtime configuration, migrations, or TASK-210 implementation.
- `2.7.6` 已因 OpenAPI 分片边界空白丢失而验收失败并回滚；修复只允许从 merged、clean `main` 发布新的不可变版本 `2.7.7`，不得覆盖 `2.7.5` 或 `2.7.6`。
- Production rollback target is `2.7.5`; this task has no data or metadata rollback.

---

## File Map

- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`: switch the deterministic CRM streaming branch to the existing chunk sender.
- `backend/src/test/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorServiceModelIdentityTest.java`: enforce fragment count, size, order, exact concatenation, persistence equality, and no-LLM/no-leak behavior.
- `backend/src/test/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiConversationServiceTest.java`: enforce ordered one-to-one OpenAPI fragment mapping and a single terminal event.
- `backend/src/main/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiConversationService.java`: preserve every non-empty delta exactly, including leading/trailing whitespace.
- `.claw/tasks/TASK-211.md`, `.claw/current-status.md`, `.claw/task-board.md`, `.claw/issue-list.md`, `.claw/test-report.md`, `.claw/devops.md`, and `docs/specs/FEAT-114-crm-product-sales-analysis-hardening.md`: record only observed implementation, test, release, and production-acceptance facts.

### Task 1: Enforce genuine CRM fragments across internal SSE and OpenAPI

**Files:**
- Modify: `backend/src/test/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorServiceModelIdentityTest.java:704-744,888-931`
- Modify: `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java:671-674`
- Modify: `backend/src/test/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiConversationServiceTest.java:1-153`

**Interfaces:**
- Consumes: existing `private static void safeSendDeltaInChunks(SseEmitter emitter, String text)` with `chunkSize = 18` and `pauseMs = 18L`.
- Produces: internal ordered non-empty `delta` fragments, each no longer than 18 Java characters, whose concatenation is the deterministic formatter body.
- Produces: external ordered `message` events carrying the same fragments and one `message_end` after all fragments.

- [x] **Step 1: Add the failing internal SSE assertions and capture helpers**

In `shouldUseSameDeterministicCrmBodyForBlockingStreamingAndPersistenceWithoutFinalLlm`, immediately after the existing blocking and streaming invocations, add:

```java
List<String> deltaChunks = emitter.deltaChunks();

assertThat(deltaChunks).hasSizeGreaterThan(1);
assertThat(deltaChunks).allSatisfy(chunk -> {
    assertThat(chunk).isNotBlank();
    assertThat(chunk.length()).isLessThanOrEqualTo(18);
});
assertThat(deltaChunks.getFirst()).isNotEqualTo(expected);
assertThat(String.join("", deltaChunks)).isEqualTo(expected);
assertThat(emitter.eventNames().stream().filter("done"::equals).count()).isEqualTo(1L);
assertThat(emitter.lastIndexOf("delta")).isLessThan(emitter.firstIndexOf("done"));
```

Replace `CapturingEmitter.deltaText()` and add the two index helpers with:

```java
private List<String> deltaChunks() {
    List<String> chunks = new ArrayList<>();
    for (int index = 0; index < eventNames.size(); index++) {
        if (!"delta".equals(eventNames.get(index))) {
            continue;
        }
        Object data = eventData.get(index);
        if (data instanceof Map<?, ?> map && map.get("text") != null) {
            chunks.add(String.valueOf(map.get("text")));
        }
    }
    return List.copyOf(chunks);
}

private String deltaText() {
    return String.join("", deltaChunks());
}

private int firstIndexOf(String eventName) {
    return eventNames.indexOf(eventName);
}

private int lastIndexOf(String eventName) {
    return eventNames.lastIndexOf(eventName);
}
```

- [x] **Step 2: Run the internal regression and verify the RED state**

Run:

```bash
cd backend
mvn -q -Dmaven.repo.local=../.m2 -Dtest=ChatOrchestratorServiceModelIdentityTest#shouldUseSameDeterministicCrmBodyForBlockingStreamingAndPersistenceWithoutFinalLlm test
```

Expected: FAIL because the current deterministic CRM branch produces exactly one `delta`, so `hasSizeGreaterThan(1)` reports an actual size of 1. The existing exact-body, persistence, leak, and zero-LLM assertions must remain in the test.

- [x] **Step 3: Apply the minimal production fix**

Change only the deterministic CRM branch in `ChatOrchestratorService.chatStreamBlocking`:

```java
if (forcedCrmProductSalesAnswer.isPresent()) {
    finalText = forcedCrmProductSalesAnswer.get();
    safeSendDeltaInChunks(emitter, finalText);
} else {
```

Do not change the helper constants, formatter, blocking path, persistence path, OpenAPI production code, or generic LLM streaming path.

- [x] **Step 4: Re-run the internal regression and verify the GREEN state**

Run the same Maven command from Step 2.

Expected: PASS. The long deterministic answer has more than one fragment; every fragment is non-empty and at most 18 Java characters; the concatenation, blocking answer, and two persisted assistant bodies remain exactly equal; `done` is last and unique; final LLM interactions remain zero.

- [x] **Step 5: Add the OpenAPI bridge regression**

Add Mockito imports for `any`, `verify`, and `when`; imports for `AgentApiCredentialEntity`, `AgentApiMessageEntity`, `Instant`, `Optional`, and `ArgumentCaptor`; then add this test:

```java
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
            new AgentOpenApiProperties(),
            objectMapper);
    AgentApiCredentialEntity credential = mock(AgentApiCredentialEntity.class);
    when(credential.getId()).thenReturn(1L);
    when(credential.getOrgId()).thenReturn("demo-org");
    when(credential.getAgentId()).thenReturn("agent-safe");
    AgentOpenApiAuthService.AuthenticatedCredential auth =
            new AgentOpenApiAuthService.AuthenticatedCredential(credential, null, "127.0.0.1", null);
    AgentOpenApiSessionService.SessionResolution session =
            new AgentOpenApiSessionService.SessionResolution("conversation-safe", "internal-safe", true);
    AgentOpenApiRunService.ChatStreamExecution execution =
            new AgentOpenApiRunService.ChatStreamExecution(
                    auth, session, "request-safe", "", "external-safe", Instant.EPOCH, null);
    AgentApiTaskEntity task = new AgentApiTaskEntity(
            "task-safe", "request-safe", "demo-org", 1L,
            "agent-safe", "external-safe", "conversation-safe");
    Object input = newChatMessageInput();
    String fullAnswer = "第一段第二段";

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
    bridge.send(SseEmitter.event().name("delta").data(Map.of("text", "第一段")));
    bridge.send(SseEmitter.event().name("delta").data(Map.of("text", "第二段")));
    bridge.send(SseEmitter.event().name("done").data(Map.of()));

    assertThat(clientEmitter.eventNames())
            .containsExactly("agent_thought", "message", "message", "message_end");
    assertThat(clientEmitter.messageAnswers()).containsExactly("第一段", "第二段");
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
```

Keep the existing three-argument helper by delegating to a new overload, and add reflective construction for the private input record:

```java
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
```

Add this method to the OpenAPI test's `CapturingEmitter`:

```java
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
```

- [x] **Step 6: Run the OpenAPI and combined focused tests**

Run:

```bash
cd backend
mvn -q -Dmaven.repo.local=../.m2 \
  -Dtest=AgentOpenApiConversationServiceTest,ChatOrchestratorServiceModelIdentityTest test
```

Expected: both classes PASS; no `message_end` precedes a `message`, and the bridge persists the exact concatenated answer once.

- [x] **Step 7: Run the CRM regression set and static diff gate**

Run:

```bash
cd backend
mvn -q -Dmaven.repo.local=../.m2 \
  -Dtest=ChatOrchestratorServiceModelIdentityTest,AgentOpenApiConversationServiceTest,CrmProductSalesAnswerFormatterTest,CrmProductSalesAnalysisToolServiceTest,CrmProductSalesIntentRouterTest,CrmProductSalesAnalysisServiceTest,CrmAnalyticsDemoDatasetContractTest,FileBackedBuiltinSkillIntegrationTest test
cd ..
git diff --check
```

Expected: all selected Surefire reports have zero failures and zero errors; `git diff --check` prints nothing and exits 0.

- [x] **Step 8: Commit the reviewed implementation**

```bash
git add \
  backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java \
  backend/src/test/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorServiceModelIdentityTest.java \
  backend/src/test/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiConversationServiceTest.java
git commit -S -m "fix: stream deterministic CRM answers in chunks"
```

Expected: a signed commit containing only the approved backend source and test files.

---

### Task 2: 修复 OpenAPI 分片边界空白丢失

**Files:**
- Modify: `backend/src/test/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiConversationServiceTest.java`
- Modify: `backend/src/main/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiConversationService.java`

- [x] **Step 1: 生产验收定位根因**

`2.7.6` 真实 OpenAPI streaming 产生 133 个 `message`，但拼接后只有 2,342 字，blocking 为 2,383 字。逐片检查证明 `deltaText()` 通过通用 `text()` 调用了 `trim()`，并以 `isBlank()` 丢弃纯空白片段。

- [x] **Step 2: 添加空白敏感 RED 回归**

让桥接测试依次发送尾随空格片段、纯空白片段和前导换行片段；旧实现应把它们裁剪/丢弃，测试明确失败且不能完成预期完整正文。

- [x] **Step 3: 最小保真修复并转绿**

`deltaText()` 只做 null-to-empty，其他字符逐字保留；正文转发条件改为 `!piece.isEmpty()`。测试必须证明外部 `message` 拼接、运行完成入参和持久化答案都等于包含原始空白的完整正文。

- [x] **Step 4: 完成组合回归、评审和 2.7.7 发布验收**

- [x] 重复 8 类 CRM 回归与独立评审，PR #7 合并并从 clean main 发布不可变 `2.7.7`。
- [x] 完成 5 次 SalesA SSE、blocking、SalesB、OpenAPI streaming/blocking/history、空白保真、访问清理、防泄漏和干净日志验收。
- [x] 应用内 Browser 已恢复并完成 fresh SalesA 同一气泡验收：partial 为 50 字且 composer disabled，final 为 2,100 字且 composer enabled；console error/warning 0、无横向溢出、Top 5/五层分析完整且无内部结果泄漏。

---

## Controller Delivery Gates

- Run a fresh task-level spec and code-quality review from the task brief, implementer report, and generated review package. Both verdicts must be approved.
- Run a fresh whole-branch review against the pre-implementation base; resolve every Critical or Important finding and re-review.
- From the reviewed branch run the CRM regression set above, `npm test`, `npm run build`, Compose config validation, assignment validation, and `git diff --check`; record actual counts and outputs in `.claw/test-report.md`.
- Update FEAT-114 and TASK-211 with observed facts only, commit them with the implementation, push the branch, create a PR, run local verification on the PR head, merge, and synchronize a clean local `main` with `origin/main`.
- On clean merged `main`, run `./scripts/release-acr.sh --dry-run --version 2.7.7` before `./scripts/release-acr.sh --version 2.7.7`. Verify ACR manifests, annotated Git tag, application version, and Git commit all agree.
- Before deployment, create the four non-empty production backups required by `docs/production-release-runbook.md`: environment, PostgreSQL, knowledge-base files, and Qdrant. Record the backup directory and sizes.
- Set production `CICI_IMAGE_TAG=2.7.7` and `CICI_APP_VERSION=2.7.7`, pull and force-recreate backend/frontend only, preserve the four state-service container IDs, and verify six services, health `UP`, `/system/version`, Nginx, and public routes.
- Using SalesA, run five fresh `/ai/chat/stream` conversations and record event counts, maximum fragment length, first-fragment and final-fragment timestamps, concatenated-body hash, persisted-body equality, Top 5 facts, amount leader, and leak checks. Every long answer must have multiple `delta` events.
- Create one temporary SalesA-bound OpenAPI credential, verify multiple ordered `message` events plus one terminal `message_end`, then revoke the credential and restore the original channel set exactly.
- Run one SalesB control conversation and an authenticated desktop browser check that captures a visibly partial assistant bubble before completion and the final complete five-layer analysis; console errors must be zero.
- Scan a clean post-acceptance log window for backend errors, exact Nginx 5xx responses, CRM-analysis failures, and abnormal disconnects. Roll back only the application image to `2.7.5` if any Go/No-Go gate fails.
- Mark TASK-211 complete and update `.claw/current-status.md`, `.claw/task-board.md`, `.claw/issue-list.md`, `.claw/test-report.md`, `.claw/devops.md`, and FEAT-114 with the final tag, commit, image digests, backup, acceptance metrics, revoked temporary access, and rollback result.
