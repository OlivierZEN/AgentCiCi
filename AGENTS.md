# AGENTS

<!-- cc-aidev-guidelines-common:begin -->
## Required Skill

- 本项目遵循 `cc-aidev-guidelines-common`。
- 任何 AI agent 在项目分析、计划、编码、测试或维护前，必须先加载并遵循该技能。
- 如果当前环境未安装该技能，先从 GitHub 安装：<https://github.com/CloudCCAI/cloudcc-aidev-guidelines-common>
- 技能可用后，按其流程维护 `README.md`、`AGENTS.md`、`.claw/` 或 `.ai-dev/`、`docs/specs/`。
<!-- cc-aidev-guidelines-common:end -->

## 工作方式

- 默认先读 `.claw/current-status.md`；涉及实现、交接或排期时再读 `.claw/task-board.md` 与对应 `.claw/tasks/TASK-xxx.md`。
- 临时暂停开发身份门禁：自 2026-08-05 起，在项目经理明确宣布该流程正式可用之前，AI agent 不得启用或要求 DevAutopilot Developer SERVICE / `doctor` / 任务认领与设计审批门禁，也不得运行 `.claw` 的 `dev-login.py` / `check-assignment.py` SSH 持钥和任务范围门禁。现有 `.claw/developers/`、`.claw/assignments/` 与历史验证记录只作保留，不作为当前项目编辑的前置条件；功能规格、任务状态、测试证据、最小范围修改、Git 提交和其他安全治理规则继续执行。只有项目经理的后续明确指令可以重新启用这些门禁。
- 新功能、跨模块改动、API/数据结构变化、非平凡重构必须落到 `docs/specs/`；小修可只更新任务状态。
- 真实验证结果写入 `.claw/test-report.md`；热状态文件只保留当前快照，不堆历史。
- 功能设计、实现和测试默认不新增移动端兼容实现、移动端布局适配、移动端截图或移动端自动化测试；除非用户明确单独要求，验收只做桌面端产品质量门。
- 不回退用户或其他 agent 的改动；遇到无关脏工作区时只避开，遇到相关改动时先理解再协作。

## 反馈分配规范

- 处理 `功能需求` 或 `BUG反馈` 条目并准备分配 task 前，必须先检查反馈内容是否明确给出设计文档、需求文档、方案文档或其他可作为设计依据的链接。
- 如果反馈内容明确给出设计文档链接，必须先读取该设计文档，并把其中的目标、范围、流程、界面/API/数据设计、验收标准、约束和例外直接转写进对应 `docs/specs/` 文档；`spec` 可保留原始链接作为来源，但不得只放链接而缺少转写后的设计内容。
- 如果反馈内容没有明确设计文档链接，PM 或执行分配的 agent 必须先完成必要设计，并把设计内容补充到对应 `docs/specs/` 文档后，再创建或更新 `.claw/task-board.md`、`.claw/tasks/TASK-xxx.md` 与 `.claw/assignments/TASK-xxx.yaml` 进行任务分配。
- `功能需求` 或 `BUG反馈` 条目分配完成后，必须立即回写对应飞书文档：把原条目状态更新为 `已分配`，并在同一条记录中补充关联 `TASK-xxx`、`docs/specs/FEAT-xxx-*.md`、执行人/assignee；不得只更新本地 `.claw/` 而让飞书原始反馈停留在 `待分配`。

## 任务授权规范

- `.claw/assignments/TASK-xxx.yaml` 中的 `allowed_write_roots` 必须写成可递归匹配的 glob，例如 `frontend/src/**`、`docs/specs/**`、`.claw/tasks/**`；不得只写裸目录如 `frontend/src`，避免身份门禁把子目录文件误判为越界。
- PM 或执行分配的 agent 更新 assignment 后，必须用 `scripts/check-assignment.py` 或技能包自带 `check-assignment.py` 对任务预期会编辑的代表性文件做一次授权验证；验证失败时先修正 assignment，再让执行 agent 开始编码。
- PM 或执行分配的 agent 完成任务分配、授权验证和任务状态更新后，必须立即把任务分配内容提交并推送到 `origin/main`，提交范围限于该次分配相关的 `docs/specs/`、`.claw/tasks/`、`.claw/assignments/`、`.claw/task-board.md`、`.claw/current-status.md`、必要的 `.claw/developers/` 授权变更和项目规则文档；不得把执行 agent 的未验证实现代码混入该提交。
- 如果仓库未内置门禁脚本，仍按已加载的 `cc-aidev-guidelines-common` 技能包脚本执行同等检查，不能因为本地脚本缺失而跳过授权验证。

## 发布部署治理

- 生产发布、ACR 镜像推送、线上部署、回滚或发布验收必须以 `docs/production-release-runbook.md` 为长期事实源。
- 其他 agent 接手部署时，必须先读取 `docs/production-release-runbook.md` 与 `.claw/devops.md` 的当前环境摘要；不得沿用旧域名文档、临时任务记录或只推 `latest` 的历史命令。
- 统一发布入口是 `scripts/release-acr.sh`；真实发布前必须先运行 `./scripts/release-acr.sh --dry-run`，并确保镜像 tag、Git tag、`CICI_APP_VERSION`、`VITE_CICI_APP_VERSION`、`CICI_IMAGE_TAG` 使用同一个版本。

## PR 处理默认规则

- 用户要求完成或处理 PR 时，默认流程是：检查 PR、运行相关本地验证、修复发现的问题，本地验证通过后合并。
- 除非用户明确要求暂停，本地验证通过后不再额外等待“Ready for review”或人工确认。
- 合并冲突优先按代码、规格、测试和项目状态自行判断解决；只有无法安全判断时才升级给用户。
- PR 合并成功后，同步本地 `main` 到 `origin/main`；如状态文件有有意义变化，更新并推送 `.claw/current-status.md` 与 `.claw/test-report.md`。

## 文档职责

- 除非用户或任务说明明确要求使用其他语言，所有项目文档必须使用中文书写。
- `AGENTS.md`：项目执行规则、PR 默认流程、命名卫生和设计事实源入口。
- `PRODUCT.md`：产品定位、用户、首要场景、差异化和非目标。
- `DESIGN.md`：认证后产品界面的人工可读设计摘要。
- `DESIGN.json`：颜色、字号、组件规则、页面质量流程等结构化设计事实源。
- `docs/specs/`：功能级需求、例外、决策和交接记录。

避免把细粒度 UI 规则重复写进 `AGENTS.md` 或 `README.md`；需要细则时读 `DESIGN.md` 和 `DESIGN.json`。

## 命名卫生

- 实现层不得使用外部产品、厂商、竞品或参考项目名称作为标识符、CSS 类、组件、变量、路由、测试、产物或实现注释。
- 竞品或参考项目名称只可出现在确有必要的规格、调研或对比文档中。
- 在实现层发现借用外部产品命名时，视为缺陷，并在同次改动中改成 AgentCiCi 自有语义。

## 设计治理入口

- 页面分析、设计、改版、评审、润色或 UI 实现必须使用 `impeccable` 工作流。
- 所有前端页面、组件、弹窗和可视化 UI 的设计与实现必须同时遵循 `frontend-design` 技能要求：先明确用途、用户、语气、约束和差异化视觉方向，再落实到生产级布局、层级、动效、状态和细节。
- `frontend-design` 不覆盖项目品牌事实源：认证后产品页必须保留 `鎏金账房` 的暖象牙底、墨色文字、紧凑密度、香槟金结构线和克制企业工作台语气；不得引入与该风格冲突的紫色渐变、默认玻璃拟态、大面积深色命令中心或营销页式 hero。
- 编辑页面结构、样式、token 或组件视觉前，先读取 `PRODUCT.md` 与 `DESIGN.md`；需要细则时读取 `DESIGN.json`。
- `/`、`/admin/*`、`/platform/*` 默认都是 `product` register，继承 `鎏金账房`：暖象牙底、墨色文字、紧凑密度、香槟金结构线。
- 页面实现完成前必须按设计事实源完成本地运行、桌面端截图检查、交互状态检查和必要复测；阻塞项要写进交接。
- 改动如果改变视觉语言、token、组件语汇或跨页交互模式，必须同步更新 `DESIGN.json`，再按需更新 `DESIGN.md`。
- 新品牌页、营销页或路由级视觉偏离必须先完成 shape、得到用户确认，并写入 `docs/specs/` 后再实现。
