---
kind: task-status
task_id: TASK-267
status: in_progress
updated_at: 2026-08-05T07:06:00Z
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
- 已发现并修复刷新回归：SPA 页面 `/admin/service-principals` 与后端接口曾共用同一路径，导致浏览器硬刷新绕过 React `AdminGuard` 并直接渲染后端 `401 Authentication required` JSON。浏览器端现改用 `/api/admin/service-principals`，Nginx 内部转发到原受保护接口；同样迁移了有相同冲突的用户管理浏览器调用至 `/api/admin/users`。页面路由不再被后端代理。

## Next Action

- 完成代码合并与生产发布后，使用无有效会话和有效组织管理员会话分别硬刷新“组织架构 → 机器主体”与“用户”，确认前者进入统一登录流程、后者正常显示页面和数据；不得出现后端 JSON。

## Verification

- `npm test -- AdminServicePrincipalsPage.test.ts`：通过（2 tests）。
- `npm run build`：通过（TypeScript + Vite）；仅保留既有 bundle size 提示。
- `mvn -q -Dtest=ServicePrincipalServiceTest test`：通过；覆盖秘密轮换不写入审计和跨企业访问拒绝。
- `git diff --check`：通过。
- `production-2.8.50`：ACR backend/frontend index digest 为 `sha256:affd6eb08e2b65c0a5d33c2ca59dbe29e72208444b618714eab31a1e478dd20c` / `sha256:59e52f78a72dc11197ed9aa976f0dd21e319dabe2bb393d6ae189b871b3e35c0`。发布前四类备份位于 `/opt/cici/backups/20260805-052058-before-2.8.50-machine-principals` 且非空；仅重建 backend/frontend，六容器 healthy，backend health=UP、版本为 `2.8.50 / 82e1c249e622`、Nginx 配置通过，`x.agentcici.com` HTTPS=200、HTTP=301，匿名机器主体接口为预期 401。
- `route-conflict-hotfix`：前端 API 路径定向测试 3/3、TypeScript/Vite 生产构建和 `git diff --check` 通过。Nginx 1.27 语法校验通过；挂载生产构建工件的临时 Nginx 实例对 `/admin/service-principals` 返回 `200 text/html` 与 SPA `#root`，证明不会再将该页面刷新代理至后端。生产发布与受权硬刷新验收待执行。
