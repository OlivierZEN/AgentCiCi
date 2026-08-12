# FEAT-177 DevAutopilot 授权初始化编排

## 目标

AgentCiCi 在 DevAutopilot 新开通、标准模板同步和新增开发者时，自动调用 Semattice `devautopilot.authorization.v1`，将自身权威的 HUMAN/SERVICE 资源映射为 Semattice 系统角色。应用只有在元数据、智能体、执行绑定、Principal 投影和授权模板全部验证通过后才进入完成态。

关联集成：`INT-014`。消费方任务：`TASK-293`；提供方：Semattice `TASK-077 / FEAT-064`。

## 编排顺序

1. 验证租户、Semattice provisioning 和平台管理员授权。
2. 幂等应用 `devautopilot.standard.v1` 元数据。
3. 创建并发布产品经理 Agent 与 PM SERVICE；新增 developer 仍由租户管理员按需执行。
4. 投影初始 HUMAN owner、PM SERVICE 和所有 developer SERVICE。
5. 以 activation 资源清单调用 Semattice 固定授权模板：owner=`application_admin`、PM=`product_manager`、developer=`developer`。
6. 校验模板版本、摘要、4 个角色、4 个权限包、7 个对象、全部预期主体绑定和 `verified=true`。
7. 保存授权回执；只有所有条件满足才置 `ACTIVE` / `initializationReady=true`。

## 状态与补齐

- activation 保存授权模板版本、摘要、角色数、权限包数、绑定数和最近验证时间。
- `initializationReady` 必须同时验证原有 PM Agent/执行绑定以及已保存的完整授权回执；旧 activation 自动显示“待补齐”。
- `POST .../initializations` 按同一顺序幂等补偿当前及历史租户。
- 新增 developer 成功投影后必须同步授权模板；授权失败则本次新增失败，并保留后续 `initializations` 的补偿能力。
- GET、页面加载和 DevAutopilot 运行请求不得隐式创建授权资源。

## 安全边界

- AgentCiCi 不传权限明细、不读写 Semattice 数据库，也不获取 `authorization.manage`。
- 调用体中的主体只能来自当前 activation 的受管资源和初始有效 HUMAN 管理员。
- HUMAN 负责人承担治理问责；PM 与 developer SERVICE 是实际业务执行主体。
- OACT scope 只是 Capability 入口上限，Semattice 角色、字段权限、数据范围、Principal 状态和 RLS 继续作为最终授权。

## 验收

- 新开通租户无需手工配置即可完成四类系统角色/权限包初始化。
- 旧租户显示待补齐；正式同步后变为已完成。
- 暂停 developer 后 Semattice Principal 门禁立即拒绝；恢复后在既有角色范围内恢复。
- 新增 developer 自动获得全栈开发者角色，不能获得产品经理或删除权限。
- 当前 `https://cici.localhost/` 租户通过正式平台动作补齐，并从 Semattice 控制台/API 回读角色、权限包和绑定。
