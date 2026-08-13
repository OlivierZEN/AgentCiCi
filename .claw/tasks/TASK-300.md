---
kind: task-status
task_id: TASK-300
feature_id: FEAT-181
integration_id: INT-018
status: review
priority: critical
owner_role: integration-agent
claimed_by: codex
---

# TASK-300 - 发布机器开发者实例上限控制面

- 新增资源级 `max_instances` 与乐观锁版本、管理员 API、审计和管理台交互。
- SERVICE OACT 交换从当前激活资源签入权威上限，客户端不能指定。
- 完成条件：后端目标测试、前端测试/构建、桌面浏览器、local main 镜像与跨仓并发验收通过。
