---
kind: task-status
task_id: TASK-112
assignee: DEV-fengchu
status: review
branch: codex/TASK-112-agent-openapi-dify-parity
pr_url: n/a
updated_at: 2026-05-19T16:05:00Z
updated_by: MANAGER-001
---

# TASK-112 - Agent Open API conversation service enhancement

## 当前状态

- 状态：`review`
- 分配人：`MANAGER-001`
- 负责人：`DEV-fengchu`
- 授权文件：`.claw/assignments/TASK-112.yaml`
- 规格文件：`docs/specs/FEAT-036-agent-open-api-dify-parity.md`
- 写入范围：见授权文件 `scope_files`

## 目标

- 参考成熟会话服务 API 的常用 Agent/OpenAPI 能力，增强当前 FEAT-021 Agent Open API。
- 开发阶段不保留旧公开 Open API 入口，统一对外暴露会话服务接口。
- 提供参数发现、`chat-messages` 会话调用、停止生成、会话/消息列表、反馈、建议问题、文件上传和必要 scopes/文档。

## 已完成

- 2026-05-18T04:04:58Z：`MANAGER-001` 已创建规格和 assignment，将任务分配给 `DEV-fengchu`。
- 2026-05-18T07:11:02Z：`DEV-fengchu` 已完成 FEAT-036 首版实现。
  - 新增 `V57__agent_open_api_dify_parity.sql`，落地 `agent_api_task`、`agent_api_message`、`agent_api_feedback`、`agent_api_file`，并为会话映射补会话名和软删除字段。
  - 新增会话服务端点：`parameters`、`chat-messages`、`chat-messages/{taskId}/stop`、`conversations`、`messages`、`messages/{messageId}/feedbacks`、`messages/{messageId}/suggested`、`files/upload`。
  - 补 `knowledgeBaseIds` / `activeSkillCode` 绑定校验。
  - API Key 创建/更新支持 `scopes`，默认包含 `chat`、`files`、`feedback`、`history`。
  - Open API 文档弹窗、Key 管理弹窗、生产 deploy env 示例已同步更新。
- 2026-05-18T08:38:25Z：修复 OpenAPI 聊天在组织级 chat 路由仍为初始化占位 `mock/cici-default` 时继续调用不可用默认模型的问题。
  - OpenAPI 调用前只在占位路由场景按当前组织已启用的 Agent 基础模型补齐 `org_model_config`。
  - 已用本地 `cici-system` Key 验证 `/chat-messages` 不再返回 `cici-default model_not_found`，trace 模型为 `qwen3.6-plus`。
- 2026-05-18T08:51:04Z：按产品语义调整，把任务、前端文档、部署开关和后端错误码统一改为“会话服务 API / Open API 增强”表达。
- 2026-05-18T09:30:42Z：按 FEAT-036 验收差距继续补齐会话服务 API。
  - `chat-messages` 请求支持 `response_mode`、`conversation_id`、`session_id`、`external_user`、`auto_generate_name`、`knowledge_base_ids`、`active_skill_code` 等 snake_case 别名。
  - `chat-messages` streaming 分支修复为真实 `SseEmitter` 返回，覆盖 `agent_thought`、`message`、`message_end`。
  - `GET /messages` 支持 `first_id` / `limit` 滚动分页，并回显消息 feedback、query 和存储 metadata。
  - `files/upload` 增加文档/图片 MIME 与扩展名校验，非允许类型返回 `file_type_not_allowed`。
  - Open API 运行层补 `default-timeout-ms` 超时保护、`maxResponseChars` 响应长度保护；`activeSkillCode` 校验要求 Skill 启用且 Agent 绑定启用。
- 2026-05-18T09:48:26Z：按产品决策删除旧公开 Open API 入口 `health`、`chat`、`chat/stream`。
  - `AgentOpenApiController` 只保留会话服务路由。
  - API Key 默认 scopes 移除 `health`，参数发现改用 `chat` scope。
  - 前端 Open API 文档删除旧入口章节，Key scope 选择器删除健康 scope。
  - 集成测试全部迁移到 `/chat-messages`。
- 2026-05-18T11:15:32Z：通过 `http://127.0.0.1:9187/openapi-chat-test.html` 跑通会话服务接口全链路。
  - HTML 测试页覆盖 `parameters`、`files/upload`、`chat-messages` blocking/streaming、`messages`、`feedbacks`、`suggested`、`conversations`、重命名、`stop`、删除会话。
  - 浏览器测试发现后端 Open API CORS 未允许 `DELETE`，导致删除会话被 `Invalid CORS request` 拦截；已补充 `DELETE` 并加预检测试。
- 2026-05-19T16:05:00Z：合并前集成检查发现 `main` 已包含 `V56__organization_profile.sql`，原 `V53__agent_open_api_dify_parity.sql` 会在已应用 V56 的环境触发 Flyway out-of-order 校验失败；已由 `MANAGER-001` 将迁移重编号为 `V57__agent_open_api_dify_parity.sql`。

## 修改范围

- `backend/src/main/java/com/codehouse/ciciassistant/openapi/**`
- `backend/src/main/java/com/codehouse/ciciassistant/skill/domain/AgentSkillBindingRepository.java`
- `backend/src/test/java/com/codehouse/ciciassistant/openapi/AgentOpenApiIntegrationTest.java`
- `backend/src/main/resources/db/migration/V57__agent_open_api_dify_parity.sql`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-local.yml`
- `deploy/acr.env.example`
- `deploy/docker-compose.acr.yml`
- `frontend/src/assistant/AgentOpenApiDocsDialog.tsx`
- `frontend/src/assistant/AgentOpenApiKeysDialog.tsx`
- `frontend/src/assistant/cici-ui.css`
- `docs/specs/FEAT-036-agent-open-api-dify-parity.md`

## 验证记录

- 状态：`passed`
- 命令：`python3 /Users/xuhm/.codex/skills/cc-aidev-guidelines-common/scripts/dev-login.py .claw --ssh-key /Users/xuhm/.ssh/id_ed25519_agentcici_fengchu --developer DEV-fengchu --git-username Bimo --task TASK-112 --branch codex/TASK-112-agent-openapi-dify-parity --no-cache --json`
- 结果：`allowed`
- 命令：`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`
- 结果：`passed`
- 命令：`npm run build`
- 结果：`passed`（保留既有 Vite chunk-size warning）
- 命令：`git diff --check`
- 结果：`passed`
- 命令：`mvn -q -Dtest=AgentOpenApiIntegrationTest test`
- 结果：`passed`
- 命令：`/Users/xuhm/Documents/apache-maven-3.9.9/bin/mvn -q -DskipTests compile`
- 结果：`passed`
- 命令：`/Users/xuhm/Documents/apache-maven-3.9.9/bin/mvn -q -Dtest=AgentOpenApiIntegrationTest test`
- 结果：`passed`
- 命令：`POST http://127.0.0.1:5174/openapi/v1/agents/cici-system/chat-messages`
- 结果：`passed`，返回 `event=message`，`trace_id=5b4e7ec3-cbc5-4b1f-9aea-3d09fd73da2f`，`agent_run_trace.model_name=qwen3.6-plus`
- 2026-05-18T08:51:04Z 语义命名调整后复测：
  - `/Users/xuhm/Documents/apache-maven-3.9.9/bin/mvn -q -DskipTests compile`：`passed`
  - `npm run build`：`passed`（保留既有 Vite chunk-size warning）
  - `/Users/xuhm/Documents/apache-maven-3.9.9/bin/mvn -q -Dtest=AgentOpenApiIntegrationTest test`：`passed`
  - `git diff --check`：`passed`
  - `python3 /Users/xuhm/.codex/skills/cc-aidev-guidelines-common/scripts/validate-state.py .claw`：`passed`
- 2026-05-18T09:30:42Z 设计验收补齐后复测：
  - `/Users/xuhm/Documents/apache-maven-3.9.9/bin/mvn -q -DskipTests compile`：`passed`
  - `/Users/xuhm/Documents/apache-maven-3.9.9/bin/mvn -q -Dtest=AgentOpenApiIntegrationTest test`：`passed`
  - `npm run build`：`passed`（保留既有 Vite chunk-size warning）
  - `git diff --check`：`passed`
  - `python3 /Users/xuhm/.codex/skills/cc-aidev-guidelines-common/scripts/validate-state.py .claw`：`passed`
- 2026-05-18T09:48:26Z 删除旧公开 Open API 入口后复测：
  - `/Users/xuhm/Documents/apache-maven-3.9.9/bin/mvn -q -DskipTests compile`：`passed`
  - `/Users/xuhm/Documents/apache-maven-3.9.9/bin/mvn -q -Dtest=AgentOpenApiIntegrationTest test`：`passed`
  - `/Users/xuhm/Documents/apache-maven-3.9.9/bin/mvn -q -Dtest=AgentOpenApiCorsConfigTest test`：`passed`
  - `npm run build`：`passed`（保留既有 Vite chunk-size warning）
  - `git diff --check`：`passed`
  - `python3 /Users/xuhm/.codex/skills/cc-aidev-guidelines-common/scripts/validate-state.py .claw`：`passed`
- 2026-05-18T11:15:32Z 9187 测试 HTML 浏览器复测：
  - `/Users/xuhm/Documents/apache-maven-3.9.9/bin/mvn -q -Dtest=AgentOpenApiCorsConfigTest test`：`passed`
  - `http://127.0.0.1:9187/openapi-chat-test.html` 页面点击 `Test All`：`passed`，页面状态 `All interface tests passed`
  - `OPTIONS http://127.0.0.1:8080/openapi/v1/agents/cici-system/conversations/test` with `Access-Control-Request-Method: DELETE`：`passed`，返回 `Access-Control-Allow-Methods: GET,POST,DELETE,OPTIONS`
  - 旧公开入口 `GET /health`、`POST /chat`、`POST /chat/stream`：均返回 `404`
- 备注：测试前通过 Docker PostgreSQL 容器创建缺失的 `cici_assistant_test` 测试库；首次使用 repo-local `.m2` 运行测试曾因 Maven Central TLS 握手中断缺少 Surefire/JUnit 依赖，改用用户默认 Maven 缓存后测试通过。

## 阻塞点

- 无当前阻塞。

## 交接说明

- 先读 `docs/specs/FEAT-036-agent-open-api-dify-parity.md` 和 `docs/specs/FEAT-021-agent-open-api.md`。
- 先补安全边界和转换层测试，再铺开文件/反馈/会话能力。
- 任意 UI 改动必须继续遵守 `DESIGN.md`、`DESIGN.json` 和 `AGENTS.md` 中的 `鎏金账房` 产品页规则。
