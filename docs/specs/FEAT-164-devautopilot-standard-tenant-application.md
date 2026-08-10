---
kind: feature-spec
feature_id: FEAT-164
title: DevAutopilot standard tenant application
status: in_implementation
owner_role: integration-agent
task_ids: TASK-275
related_decisions: ADR-006, ADR-007, ADR-008
related_issues: none
updated_at: 2026-08-10T09:20:00Z
updated_by: codex
---

# FEAT-164 - DevAutopilot 标准多租户应用模板

## 背景与目标

AgentCiCi 的平台租户应用页已经将 AgentCiCi 和 Semattice 作为独立应用展示和开通。DevAutopilot 需要成为第三个可开通的组合型租户应用：共享无状态运行时，但每个租户必须拥有独立的 Agent、SERVICE Principal、机器凭据、Semattice Principal 投影、业务数据和审计链。

本功能以 `devautopilot-standard@1.0.0` 为平台签名、版本化模板。模板定义逻辑角色和最小权限，租户管理者定义实际名称和账号数量。`大乔`、`悟空`等仅是历史租户显示名，不能作为固定账号、全局 Client ID 或跨租户授权依据。

## 范围

### In Scope

- 平台应用目录中增加 `devautopilot` activation 及其操作、资源和审计记录。
- 开通、重试、暂停、恢复、状态查询；所有操作使用稳定幂等键与关联 ID。
- 平台开通时自动创建标准研发产品经理 Agent、其 PM SERVICE Principal、受控 Tool binding 与执行绑定，并以该租户最早的 active `OWNER` 或 `ORG_ADMIN` 作为初始 HUMAN owner。
- 租户 ORG_ADMIN 在 AgentCiCi `/admin/service-principals` 的独立新增弹窗中，按需新增自定义名称的 developer SERVICE Principal；产品经理 Agent 与机器主体的显示名称和负责人可由该租户后续调整。
- 租户 ORG_ADMIN 可在机器主体详情的独立编辑弹窗中变更显示名称和 HUMAN 负责人；编辑不轮换 Client Secret、不改变 Client ID 或最小权限范围。
- 受控调用 Semattice 标准基线应用，校验回执、模板版本和资源映射。
- 平台租户应用页展示依赖、初始化步骤、失败原因、模板版本和生命周期动作。
- 平台卡片统一使用 `租户标识 = company_id`，不展示 Semattice 内部 tenant UUID、产品经理或机器主体名称。
- 暂停应用时关闭运行时授权并暂停本应用拥有的 SERVICE Principal；不删除业务数据。

### Out Of Scope

- 自动创建带可分发 Secret 的默认开发者账号。
- 在浏览器、模板配置、日志或审计文本中保存 Client Secret、OACT 或私钥。
- 通过关闭应用删除 Semattice 业务记录或永久撤销身份。
- 将租户的 Agent、Principal、Client ID、会话或内存资源共享给另一租户。

## 模板与资源模型

模板只声明固定逻辑角色：

| logical_role | 基数 | 租户可配置项 | 运行职责 |
|---|---:|---|---|
| `product_manager` | 至少 1，且仅 1 个 primary | `display_name`、`resource_alias`、HUMAN owner | 查询、受控创建、设计与验收评审 |
| `developer` | 0..N | `display_name`、`resource_alias`、HUMAN owner、executor type | Coding Agent/CI 领取和交付任务 |
| `observer` | 0..N | 显示名称、HUMAN owner | 只读访问 |

每个资源均使用不可变 `principal_id`、内部 `agent_id` 和系统生成的 `client_id` 作为技术身份；`display_name` 和租户内唯一的 `resource_alias` 可变。任务归属、权限、审计、密钥轮换和跨系统关联只使用不可变标识。历史审计保留名称快照，改名不改变历史 actor。

## 控制面数据

本功能不得复用 `integration_app`。该表服务于外部连接配置，不包含模板版本、长事务 operation 或资源清单语义。新增 AgentCiCi 控制面实体：

```text
tenant_application_activation
  company_id + app_code (unique), template_code, template_version, template_digest,
  desired_state, actual_state, operation_id, idempotency_key, semattice_tenant_id,
  revision, activated_by, activated_at, last_error_code, last_error_step

tenant_application_resource
  activation_id, logical_role, resource_type, resource_alias, display_name,
  external_id, lifecycle_state, expected_version, actual_version, is_primary

tenant_application_operation
  operation_id, activation_id, step, state, attempt, correlation_id,
  request_digest, result_digest, started_at, completed_at
```

这些记录是 AgentCiCi 的租户应用控制面事实，不是 DevAutopilot 项目、任务、工时或交付事件的副本。

## 生命周期与编排

状态：`NOT_ENABLED → PROVISIONING → AWAITING_APPROVAL? → ACTIVE`；失败为 `FAILED`。暂停和恢复为 `ACTIVE → SUSPENDING → SUSPENDED → RESUMING → ACTIVE`。失败或中断保留操作与资源回执，可使用相同幂等键重试，不得重复创建资源。

开通步骤：

1. 验证 company active、调用者为平台管理员、Semattice 已 `PROVISIONED`、模板版本受支持。
2. 创建 activation/operation，锁定 `company_id + app_code`。
3. 请求 Semattice 应用 `devautopilot.standard.v1` 标准基线；已有非模板 metadata 时失败关闭，不覆盖已有模型。
4. 创建标准 `product_manager` 资源对：租户独立的研发产品经理 Agent、PM SERVICE Principal、最小 Tool binding 与执行绑定。产品经理 Agent 必须绑定 `web` 入口、以平台签名标准 Spec 编译并发布；只有 `published_version_id` 回读成功才允许初始化继续。系统生成 Client ID，仅本次返回的 Secret 不写入页面、审计或控制面。
5. 写入资源清单并以该租户初始 OWNER/ORG_ADMIN 作为 HUMAN owner；不创建默认 developer，租户可按需创建任意数量的开发者。
6. 通过 AgentCiCi 签发的短时、仅服务端使用的 OACT，先投影 HUMAN owner，再逐个调用 Semattice `identity.principal.sync` 投影 PM/developer SERVICE；投影必须携带 AgentCiCi 权威生命周期，不能把 suspended/disabled 主体写成 active。
7. 请求 DevAutopilot 健康/entitlement 探针；metadata、Agent、Principal、执行绑定和 Principal 投影全部回执一致后置为 `ACTIVE`。

早期模板版本只创建 Semattice 基线的已开通租户，平台卡片必须显示“待补齐”并提供受平台管理员授权的 `initializations` 动作。该动作只幂等补齐缺失的标准 PM 资源，不能在 GET、浏览器启动或普通租户读取时隐式创建机器主体。

暂停以 activation 门禁为先：先将 desired state 置为 suspended，使运行时立即 fail closed；随后暂停本 activation 资源清单中的 PM 和 developer SERVICE Principal 并同步 Semattice Principal 状态。任何后续步骤失败时保持 `SUSPENDING` 且入口持续关闭。恢复按反向顺序执行并重新验证。

`tenant_application_resource.lifecycle_state` 只保留编排快照，团队/activation 对外读取 SERVICE 资源时必须联表读取 AgentCiCi `principal.lifecycle_status` 作为当前权威状态；Principal 缺失按不可用处理，禁止用快照 ACTIVE 兜底。机器主体独立暂停、恢复、撤销、改名或负责人变更时，同步更新控制面快照并触发 Semattice 投影；远端短暂失败不重新开放主体，后续 `initializations` 必须可幂等补偿。

## API 契约

提供方为 AgentCiCi；详细版本化 HTTP 契约由本规格实现后在 AgentCiCi API 文档维护。

| 方法 | 路径 | 语义 |
|---|---|---|
| POST | `/api/platform/tenants/{companyId}/applications/devautopilot/activations` | 创建或幂等重放应用与数据基线；输入仅为 `Idempotency-Key` |
| POST | `/api/platform/tenants/{companyId}/applications/devautopilot/initializations` | 仅补齐早期 activation 缺失的标准 PM Agent/SERVICE/binding |
| GET | `/api/platform/tenants/{companyId}/applications/devautopilot` | 返回 activation、依赖、资源摘要、最近操作与安全错误码 |
| POST | `/.../suspensions` | 请求暂停，不删除数据 |
| POST | `/.../resumptions` | 请求恢复 |
| GET | `/api/admin/devautopilot/team` | 当前租户读取应用状态与团队资源；公司只能从已认证会话推导 |
| POST | `/api/admin/devautopilot/team/product-managers` | 当前 ORG_ADMIN 创建唯一 PM；输入为显示名称和同租户有效 HUMAN `ownerMemberId`，Secret 仅一次返回 |
| POST | `/api/admin/devautopilot/team/developers` | 当前 ORG_ADMIN 创建一个自定义名称的 developer SERVICE Principal；输入为显示名称和同租户有效 HUMAN `ownerMemberId`，Secret 仅一次返回 |
| PUT | `/api/admin/service-principals/{principalId}` | 当前 ORG_ADMIN 更新当前租户机器主体的显示名称和 HUMAN `ownerMemberId`；不改变 Client ID、Secret 或 scopes |

平台写接口要求 `Idempotency-Key` 与平台管理员授权；租户团队写接口要求当前租户 `ORG_ADMIN` 会话。平台接口不接受调用方指定 tenant、负责人、principal、scope、Semattice tenant ID 或 Client Secret。租户团队接口只接受同租户 HUMAN `ownerMemberId`，并由服务端校验该成员有效；公司、技术 principal、Client ID 与模板最小 scope 均由服务端推导。

## 安全与隔离

- 每个 PM Agent、SERVICE Principal、Client 和 Secret 都属于且仅属于一个 `company_id`。
- PM SERVICE 仅具备标准模板的 Semattice 最小 scope；developer 仅具有 developer scope。租户不能通过改显示名改变角色。
- DevAutopilot 以可信 OACT 解析 company/tenant/principal，并使用短时、按租户的 activation 快照缓存避免控制面短暂抖动阻塞业务；未开通、暂停、身份不一致和缓存过期后无法复核仍一律拒绝。
- Semattice 必须从可信 OACT 推导 tenant/company，并以 Principal、RBAC、RLS、PDP 作为资源端最终门禁。
- 关闭应用只暂停资源；永久撤销、导出和数据清理走独立保留期/审批流程。

## 用户应用入口与会话交接

AgentCiCi 前台是租户用户进入 DevAutopilot 的唯一浏览器入口。点击应用时，当前已验证的 AgentCiCi 会话只可创建一次性、60 秒有效的 opaque handoff ticket；浏览器跳转到同源 `/devautopilot/?handoff=...`，不得在 URL、localStorage、日志或页面状态中传递 OACT。

DevAutopilot 服务端调用 AgentCiCi 的 ticket exchange，并仅在自身短期 HttpOnly 会话内保存新签发的 OACT。AgentCiCi 的 ticket 状态只含 `company_id` 与成员 ID，不保存任何 bearer token；兑换一次即删除。兑换时重新核验 activation 为 `ACTIVE`，并按该成员签发 60-600 秒的最小 OACT。平台账号没有租户 `company_id`，不能创建 handoff。

## 跨项目契约

- `INT-008`：本规格为 AgentCiCi 控制面所有者。
- Semattice 子规格：`cc-semattice/docs/specs/FEAT-059-devautopilot-standard-tenant-baseline.md`。
- DevAutopilot 子规格：`cc-dev-autopilot/docs/specs/FEAT-010-tenant-activation-runtime-gate.md`。
- 版本/兼容：模板 `1.x` 只新增向后兼容资源和字段；破坏性模板升级必须新建主版本、显式迁移和租户确认。

## 验收标准

- 两个 UAT 测试租户可用不同 PM 和 developer 显示名开通，技术资源与数据完全不同。
- 租户 A 的 OACT、PM Tool、developer CLI 和 DevAutopilot Web/API 均不能读写租户 B 数据。
- 同一激活 key 重试不重复创建 Agent、Principal、metadata 基线或操作记录。
- 暂停后所有入口 fail closed，恢复后仅恢复对应租户的原资源。
- 新增 developer 账号不会影响其他账号；密钥只一次显示、轮换和暂停均限于本租户。
- UAT 验证包含 AgentCiCi、Semattice、DevAutopilot 三侧关联 ID、回滚和负向隔离证据。
- 开通或补齐初始化完成后，产品经理 Agent 必须出现在该租户员工首页的智能体列表，并可从首页创建会话；仅创建未发布草稿不算初始化成功。

## 风险与回滚

- Semattice 基线失败：activation 保持 `FAILED`，已存在的预置资源通过资源清单补偿暂停，不删除数据。
- Agent 创建失败：不标记 active；恢复操作沿资源清单幂等重试。
- DevAutopilot 新门禁故障：关闭该模板版本的 enforcement 开关并回到 `SUSPENDED`，不得开放未知租户。
- 任一子仓回滚时，其他项目不回滚；activation 保留失败原因与最后成功步骤。

## 实现进展

- [x] 控制面模型与 migration。
- [x] 将团队身份管理迁入 AgentCiCi 租户管理端，移除运营端人员字段。
- [x] 将机器主体新增和编辑收敛为独立 modal；创建时可选择同租户有效 HUMAN 负责人，编辑时可更新显示名称与负责人。
- [x] DevAutopilot activation 快照短时缓存实现与定向测试。
- [x] Semattice 标准基线契约与实现。
- [x] DevAutopilot runtime gate。
- [x] 独立 DevAutopilot 缓存的 UAT 发布与运行态验证（使用独立 beta 发布入口）。
- [x] 修复同源租户 handoff 与卡片字段事实，并发布 UAT AgentCiCi `2.8.57-beta.3 / 1b07df5c6f40`。
- [x] 将标准 PM Agent/SERVICE/Tool/execution binding 纳入新开通与既有租户显式补齐初始化。
- [x] 标准 PM Agent 初始化补齐 `web` 渠道、标准 Spec 编译与幂等发布；既有未发布 Agent 可由 `initializations` 补偿。
- [x] activation resolve 使用专用 RS256 OACT 验签边界，不再把 OACT 误交给 AgentCiCi 会话 JWT 解析器。
- [x] Semattice 浏览器控制台使用独立公网 base URL；UAT 同源 `/console/` 不再返回或校验内部 `192.168.*` 地址。
- [x] Principal 权威生命周期与 activation 资源读模型统一；停用开发者不得在 DevAutopilot 显示为可派单。
- [x] 开通/补齐初始化自动投影 HUMAN owner、PM SERVICE 和 developer SERVICE 到 Semattice，并可补偿既有 activation。
- [ ] 用正常租户用户回读 Semattice 当前项目数据，并完成双租户隔离验收。
- [ ] 双租户 UAT E2E（需正常租户 ORG_ADMIN 业务会话，不能由平台运营账号替代）。

目标租户的正常用户读取已于 `2026-08-09` 完成：handoff、consume、workspace、team 与 Semattice console 均为 200；Semattice 当前项目数真实为 0，DevAutopilot 以空工作台呈现，不注入演示项目。上述第一项仅剩双租户隔离部分。

## UAT 发布事实

- `2026-08-09` 已发布 AgentCiCi `2.8.57-beta.1 / e5c097adda5f`；backend/frontend ACR index digest 分别为 `sha256:3b642bf91ee54b9e6d36783ca958b032a88b0a1b8667961190d23bafc1c9d091` 与 `sha256:6f87671503319c8dc06be405fc137d3d6edb6fba90e258918500c6ac90b5bb3c`。
- `2026-08-09` 已发布 AgentCiCi `2.8.57-beta.2 / 2753d268acd9`；backend/frontend ACR index digest 分别为 `sha256:aa50caecfe55aaa8ac6c0b0e1f8494578a21966dda7f8fa0f20dec2303a92cdc` 与 `sha256:7da4fa653ff8b1de55ea183ea29a09b669708c7419eccd8100699f08179a6a37`。租户管理端已使用独立新增/编辑 modal，owner 仅可为同租户有效 HUMAN 成员；运行时 `health=UP`、版本一致，匿名团队 API=401。
- AgentCiCi UAT `/system/version` 同时返回 Git commit、`version` 和 `imageTag` 为上述 beta 版本；V108 成功，匿名 `/api/admin/devautopilot/team` 为预期 `401`。独立 DevAutopilot 已通过其独立 beta 发布入口上线 UAT `1.0.2-beta.1 / 1204ab74d375`，运行健康为 integrated/ok；未使用其生产发布脚本。
- 平台卡片不再含新增开发者、显示名称、负责人或技术别名输入；租户团队入口固定在“组织架构 → 机器主体”。未持有业务 ORG_ADMIN 会话，故不创建账号、Secret 或业务数据来伪造最终验收。
- `2026-08-09` 已发布 AgentCiCi `2.8.58-beta.1 / 4ffab5c43c0e`。该版本按生产 `2.8.58` 基线发布，修复测试版本不得回退到旧 Git production tag 的生成规则，并使用版本化 UAT Compose 覆盖层只重建 backend/frontend。备份为 `/data/apps/agentcici/backups/20260809T050558Z-before-2.8.58-beta.1`；两个容器 healthy，backend `health=UP`、`/system/version` 的 version/imageTag/commit 一致。匿名 `POST /api/platform/.../initializations`=401，证明真实前端 API 路由到后端；正常平台管理员补齐与租户 E2E 未伪造执行。
- `2026-08-09` 用户真实 UAT 请求证明 handoff issue/exchange 均为 200，但 activation resolve 为 401 并被 DevAutopilot 映射成 workspace 503；同一时段 Semattice console ticket API 为 200，但响应含内部 HTTP 地址而被浏览器安全校验拒绝。代码修复与发布目标为生产 `2.8.58` 的下一候选 `2.8.59-beta.1`。
- `2026-08-09` 已发布 `2.8.59-beta.1 / 94ceb612bd71`。专用 RS256 activation filter、Semattice console public base URL 与下一生产版本 beta 规则上线；目标租户 team 回读 `天工产品经理` AGENT/SERVICE、`墨子开发者`、`鲁班` 四项 ACTIVE 资源。配合 DevAutopilot `1.0.3-beta.2`，真实 handoff/consume/workspace/team/console 全部 200。
- `2026-08-10` 已发布 `2.8.59-beta.5 / 0edfc3567f85`。标准 PM Agent 初始化新增 `web` 渠道、标准 Spec 编译、生产就绪发布与 `published_version_id` 回读门禁；定向测试和后端 package 通过。发布后目标租户只读回读仍显示 `天工产品经理 / devautopilot-pm-09653ab9` 未发布且无 channel binding，因此该既有 activation 必须再由平台管理员执行一次正式 `initializations` 幂等补偿。版本部署本身不隐式修改租户业务资源，也不以数据库直写替代受治理初始化。
- `2026-08-10` 已发布 `2.8.59-beta.7 / 7e309a39394d`。控制面 API 新增只读 `initializationReady`，其完成条件为主 PM Agent 已发布、`web` 渠道启用且主 SERVICE 主体有效；前端保留旧响应兼容，但优先使用服务端事实。UAT 页面已从错误“已完成”修正为“待补齐”。真实补齐请求因平台模型目录为空而失败关闭；标准模板不会绕过模型运行依赖发布一个无法对话的 Agent，平台需先配置有效聊天模型和路由。
- `2026-08-10` 已发布 `2.8.59-beta.8 / 8213646b4fa3`。OneKeyToken 真实检测返回 `qwen3.5-flash`，平台管理员显式加入目录并绑定 chat 路由后，正式 `initializations` 返回成功，应用卡片回读“已完成”。只读回读为：`agent_definition.enabled=true,published_version_id=2`，工作流 v1=`PUBLISHED`，`web` channel enabled，主 SERVICE binding enabled 且 owner ACTIVE。平台管理员会话无法进入员工工作台，首页智能体可见和创建会话仍由正常租户用户完成最终视觉验收；不以平台身份替代。
