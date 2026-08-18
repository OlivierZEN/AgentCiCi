---
task_id: TASK-318
feature_id: FEAT-179
status: in_progress
priority: high
owner_role: backend-agent
claimed_by: codex
---

# TASK-318 - OneKeyToken 按 Key 枚举可用模型

## 范围

- 使用平台已保存的 OneKeyToken API Key，以 Bearer 鉴权调用配置地址下的 `GET /v1/models`。
- 解析 OpenAI-compatible `data[].id/name` 模型列表，供现有“全部模型”目录选择流程使用。
- 401 明确提示检查 Key 是否正确或已轮换；403 明确提示检查 Key、账号或应用状态以及 `model:invoke` scope。
- 不回显 Key，不自动推断模型能力，不修改聊天调用和场景路由失败关闭规则。

## 验收

- 真实无效 Key 负例返回 `401 unauthorized`，证明目标路由存在且鉴权语义符合说明。
- 聚焦测试覆盖成功列表、Bearer Key、GET/JSON 请求、401、403 与错误不泄露 Key。
- 从 AgentCiCi 本地 `main` 构建 backend `:local`，完成健康、版本指纹和已保存 Key 的真实模型目录回读。
- UAT、生产、远端仓库及厂商配置不修改。

## 下一步

- 提交本地 `main`，更新本地 backend 后通过受权平台页面执行真实“全部模型”回读。
