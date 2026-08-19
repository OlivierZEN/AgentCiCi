---
kind: task-status
task_id: TASK-325
feature_id: FEAT-192
status: in_progress
priority: critical
owner_role: backend-agent
claimed_by: codex
updated_at: 2026-08-19T13:36:32Z
updated_by: codex
---

# TASK-325 - 产品经理项目改名确定性执行与可信回执

## 范围

- 修复产品经理展示项目修改草案并给出确认口令后，确认消息没有执行实际更新的问题。
- 将公开的精确修改确认协议解析成受白名单约束的对象、记录引用、字段和值，由服务端直接调用更新 Tool，不再交给模型自由规划。
- 更新只允许业务字段；用当前 revision 乐观锁写入，再通过 Semattice 实时查询核对 record ID、revision 和目标字段值。
- 返回统一 `SUCCESS / SEMATTICE_LIVE / record_id / revision / correlation_id / readback_verified` 回执，并由服务端渲染明确成功或失败结果。
- 不修改 Semattice 或 DevAutopilot 仓库，不代用户执行截图中的真实项目改名，不触及 UAT/生产。

## 完成条件

- `确认将项目 DAS-4F5ED86B 的名称修改为 AgentCiCi企业级智能体平台` 能被严格解析；普通自然语言、结构字段和非法数值不能绕过确认协议。
- 确认后直接进入 update Tool dispatcher；update Tool 不暴露给自由模型 Tool 列表，避免未确认写入。
- 只有写入响应和写后查询均核对成功时显示已修改；重复确认在目标值已存在时返回可验证 NOOP，不重复增加 revision。
- 写入失败、revision 冲突、目标不唯一或回读不一致均明确显示未修改，不再退化成“内部字段已隐藏”。
- 聚焦回归、后端 package、状态校验和本地正式入口运行指纹完成。

## 当前证据

- 旧 `confirmedIntent` 只接受 `确认修改项目：...`，与产品经理实际输出的 `确认将项目 ... 的名称修改为 ...` 不一致，而且只返回不可执行的 combined 文本。
- 阻塞与流式编排都只有创建、删除和转派的确定性确认路由，没有更新路由；`ToolOrchestratorService` 也未注册更新服务 dispatcher。
- 旧更新 Tool 仅信任一次 update 响应，返回小写 `succeeded/new_revision`，缺少 `source/correlation_id/readback_verified` 和写后查询，永远不能满足统一可信回执门禁。
- 通用结构化结果回退不会将该旧回执渲染成业务结果，因此截图显示受保护字段提示；随后实时查询显示旧名称和 revision 1，证明本轮没有完成写入。
- 已实现精确确认解析、阻塞/流式服务端强制路由、Tool dispatcher、字段与对象白名单、精确记录匹配、稳定幂等键、乐观锁、写入响应核对和写后实时查询；update Tool 保持不向自由模型暴露。
- 服务端成功输出必须核对 `SUCCESS / SEMATTICE_LIVE / record_id / revision / correlation_id / readback_verified / verified_values`；目标值已存在时返回带回读的幂等 NOOP，回读漂移时失败关闭。
- 10 个相关测试类共 97 项通过，0 failure/error/skipped；`mvn -q -DskipTests package` 与 `git diff --check` 通过。状态校验仍只报告既有历史债务，未报告 TASK-325/FEAT-192 新错误。
- 待提交并合并本地 `main`，再从该提交重建 `cici.localhost` backend；未执行截图中的真实项目改名。
