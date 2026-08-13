---
task_id: TASK-297
feature_id: FEAT-179
status: review
priority: critical
owner_role: fullstack-agent
claimed_by: codex
---

# TASK-297 - 统一模型调用治理

## 范围

- 收敛 LLM、VLM、Embedding、ASR 和受管工具的模型、厂商与凭据解析。
- 要求所有模型调用使用平台场景路由，删除默认百炼回退与独立工具密钥。
- 迁移知识库 embedding、受管代码解释器、联网搜索/抓取及语音视觉调用，并补充定向测试。

## 验收

- 路由、厂商、模型或凭据缺失均失败关闭，不触发默认厂商调用。已完成本地实现。
- 业务生产路径不存在具体模型默认值或独立模型密钥。已对 LLM、VLM、Embedding、ASR 和受管工具路径扫描验证。
- 后端干净编译、定向 10 项测试、前端 production build 与 diff check 通过；本轮未发布 UAT/生产。

## 下一步

- 评审并按发布 Skill 在 UAT 为每个实际启用的场景配置已发布路由，随后验证上传、聊天、工具与语音业务路径。
