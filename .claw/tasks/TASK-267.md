---
kind: task-status
task_id: TASK-267
status: done
updated_at: 2026-08-05T05:22:30Z
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
- 定向前端测试、生产构建、既有 ServicePrincipalService 安全契约测试与 diff 检查均通过；已发布生产 `2.8.50 / 82e1c249e622`，六容器健康。

## Next Action

- 已完成。受权组织管理员可进入“组织架构 → 机器主体”实际查看租户 SERVICE 身份并执行受确认保护的治理操作。

## Verification

- `npm test -- AdminServicePrincipalsPage.test.ts`：通过（2 tests）。
- `npm run build`：通过（TypeScript + Vite）；仅保留既有 bundle size 提示。
- `mvn -q -Dtest=ServicePrincipalServiceTest test`：通过；覆盖秘密轮换不写入审计和跨企业访问拒绝。
- `git diff --check`：通过。
- `production-2.8.50`：ACR backend/frontend index digest 为 `sha256:affd6eb08e2b65c0a5d33c2ca59dbe29e72208444b618714eab31a1e478dd20c` / `sha256:59e52f78a72dc11197ed9aa976f0dd21e319dabe2bb393d6ae189b871b3e35c0`。发布前四类备份位于 `/opt/cici/backups/20260805-052058-before-2.8.50-machine-principals` 且非空；仅重建 backend/frontend，六容器 healthy，backend health=UP、版本为 `2.8.50 / 82e1c249e622`、Nginx 配置通过，`x.agentcici.com` HTTPS=200、HTTP=301，匿名机器主体接口为预期 401。
