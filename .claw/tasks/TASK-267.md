---
kind: task-status
task_id: TASK-267
status: review
updated_at: 2026-08-05T05:08:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-267.yaml
spec_path: docs/specs/FEAT-159-governed-service-principal-admin-ui.md
---

# TASK-267 - 机器主体管理页面

## Current State

- 已在组织架构下新增“机器主体”入口和 `/admin/service-principals` 路由，直接调用既有受 ORG_ADMIN 保护的 SERVICE Principal 管理 API。
- 页面按当前组织控制台的米白/金色视觉语言呈现主体清单、Client ID、受众、最小授权范围、人类负责人、生命周期和密钥轮换入口。
- 新 Client Secret 只保存于 React 内存，轮换后显示一次；切换主体、点击“我已安全保存”或离开页面后均无法再次回显。页面不读取、存储、记录或传输历史密钥。
- 定向前端测试、生产构建、既有 ServicePrincipalService 安全契约测试与 diff 检查均通过；尚未发布生产，等待用户验收/发布指令。

## Next Action

- 提交并推送 `main`；如获发布授权，按 AgentCiCi 生产发布 runbook 部署并使用受权组织管理员会话验收。

## Verification

- `npm test -- AdminServicePrincipalsPage.test.ts`：通过（2 tests）。
- `npm run build`：通过（TypeScript + Vite）；仅保留既有 bundle size 提示。
- `mvn -q -Dtest=ServicePrincipalServiceTest test`：通过；覆盖秘密轮换不写入审计和跨企业访问拒绝。
- `git diff --check`：通过。
