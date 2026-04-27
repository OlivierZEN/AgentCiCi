# 工作台状态机方案落地设计

## 1. 目标

将当前 `思思工作台` 从“静态任务卡 + 对话窗口”升级为“多智能体协同工作台”，重点增强以下体验：

- 顶部显示可切换的智能体列表。
- 主对话区域保持单一对话入口，不被系统过程日志打断。
- 在对话区上方增加一个受控边界内的“小型状态机”区域，展示当前智能体正在做什么。
- 在右侧侧栏继续展示今日工作概览，并增加紧凑的会话历史列表。
- 页面视觉保持轻量，不引入复杂业务逻辑即可先落地前端原型，再逐步接入真实运行态数据。

## 2. 当前系统接入点

### 2.1 现有工作台入口

- React 入口：`frontend/src/assistant/AssistantApp.tsx`
- 样式文件：`frontend/src/assistant/cici-ui.css`
- 当前工作台切换条件：`workspaceTab === "workbench"`

### 2.2 当前工作台 DOM 结构

当前结构大致为：

1. `cici-workbench__hero`
2. `cici-workbench__chat-shell`
3. `cici-workbench__side`

其中：

- `cici-workbench__chat-shell` 承载主对话区
- `cici-workbench__side` 承载飞书配对、今日任务、待处理审批、快捷动作

本方案建议保留“主对话 + 右侧栏”的总骨架，但把顶部 hero 重构为“智能体 Dock + 状态机条”。

## 3. 目标页面结构

建议最终页面分为三块：

### 3.1 左侧主区

- 顶部：智能体横向切换条
- 顶部右侧：当前智能体状态机条
- 中部：主对话消息流
- 底部：输入框

### 3.2 右侧侧栏

- 当前智能体缩略信息
- 今日工作概览
- 会话历史列表

### 3.3 状态机条

状态机条不做“长日志列表”，而是固定高度的摘要式流程区，显示：

- 当前智能体名
- 当前状态：处理中 / 检索中 / 等待确认 / 已完成
- 上一任务
- 当前任务
- 下一任务
- 思考流摘要轮播

## 4. 状态机模块设计

### 4.1 信息模型

推荐使用如下前端结构：

```ts
type AgentWorkbenchStateMachine = {
  status: "处理中" | "检索中" | "等待确认" | "已完成" | "待命中";
  previousTask: string;
  currentTask: string;
  nextTask: string;
  thoughts: string[];
};

type AgentWorkbenchViewModel = {
  key: string;
  name: string;
  short: string;
  color: string;
  status: "busy" | "idle";
  statusText: string;
  stateMachine: AgentWorkbenchStateMachine;
  messages: {
    role: "assistant" | "user";
    name: string;
    time: string;
    text: string;
  }[];
};
```

### 4.2 视觉状态

状态机条应固定在主区上方，严格不超边界。

推荐布局：

1. 左侧 32px 当前智能体图标
2. 中间三行任务轨道
3. 右侧思考流摘要

三段任务轨道：

- `上一项`：低对比度
- `当前`：高亮 + 动态状态点
- `下一项`：低对比度

思考流：

- 不展示完整推理
- 只展示安全、业务可读的过程摘要
- 每 1.5 到 2 秒轮播一次

### 4.3 动效规范

只建议保留三类轻动效：

1. 状态点呼吸
2. 思考流轮播
3. 智能体头像 Dock hover 放大

不要在状态机条内加入复杂路径动画、进度环或大面积闪烁。

## 5. 会话历史模块设计

右侧下半部分改为紧凑历史列表，而不是卡片流。

建议展示规则：

- 每条历史只显示一行摘要
- 左侧是内容截断文本
- 右侧是时间
- 使用细分隔线，不使用大卡片

示例：

```text
早上好，今天我先帮你把待办拆成三块...   09:32
其中客户摘要已经交给销售助手整理...     09:33
先把最优先的审批和需要补的材料告诉我... 09:34
最优先的是华东区折扣审批...             09:35
```

## 6. 与当前代码的映射方式

### 6.1 AssistantApp.tsx 改造建议

在 `workspaceTab === "workbench"` 视图中：

1. 删除当前 `hero` 大卡片里的静态概述文案
2. 增加 `WORKBENCH_AGENTS` 视图模型
3. 在主区顶部插入：
   - `WorkbenchAgentDock`
   - `WorkbenchStateMachineBar`
4. 将原 `workbenchMessages` 继续作为主对话消息源
5. 从当前 agent 的消息摘要生成右侧 `会话历史`

推荐拆组件：

- `WorkbenchAgentDock`
- `WorkbenchStateMachineBar`
- `WorkbenchConversationHistory`
- `WorkbenchOverviewSidebar`

### 6.2 cici-ui.css 改造建议

建议新增以下样式块：

- `.cici-workbench__dock`
- `.cici-workbench__state-machine`
- `.cici-workbench__state-lane`
- `.cici-workbench__state-thought`
- `.cici-workbench__history-list`
- `.cici-workbench__history-row`

原则：

- 继续沿用当前工作台浅色基底
- 维持现有圆角语言
- 不额外引入新设计体系

## 7. 数据来源分阶段方案

### Phase 1：纯前端静态映射

目标：快速落 UI。

做法：

- 在前端定义 `WORKBENCH_AGENTS`
- 每个 agent 维护：
  - `messages`
  - `stateMachine`
  - `status`
- 切换智能体时同步更新状态机和历史列表

优点：

- 无需后端改动
- 适合先把设计落到真实系统中

### Phase 2：会话驱动的半动态状态

目标：让状态机随对话演进。

做法：

- 每次发送 prompt 后，根据 prompt 类型切换当前任务
- 在本地 reducer 中推进：
  - `previousTask`
  - `currentTask`
  - `nextTask`
- 聊天完成后将状态调整为 `已完成` 或 `等待确认`

### Phase 3：真实后端编排状态接入

目标：让状态机反映真实工具调用与智能体编排。

建议后端提供聚合接口：

```ts
GET /workbench/agents/{agentId}/runtime-state
```

返回：

```json
{
  "agentId": "approval-agent",
  "status": "busy",
  "statusText": "处理中",
  "previousTask": "流程节点校验",
  "currentTask": "附件缺失识别",
  "nextTask": "生成补件话术",
  "thoughts": [
    "正在识别报价依据截图",
    "正在检查商务让利说明",
    "正在评估截止时间风险"
  ],
  "updatedAt": "2026-04-21T09:37:00Z"
}
```

## 8. 推荐实施顺序

### Step 1

前端工作台重构：

- 引入智能体 Dock
- 引入状态机条
- 引入右侧会话历史

### Step 2

前端状态机本地驱动：

- 选中 agent 时切换状态机
- 输入消息时推进任务状态

### Step 3

接后端真实运行态：

- 聚合 agent 运行状态
- 将工具调用/知识检索摘要映射为 `thoughts`

### Step 4

联动飞书和审批流程：

- 审批助手进入处理状态时同步更新状态机
- 飞书消息进入时将摘要同步到会话历史

## 9. 风险与约束

### 9.1 不应暴露完整推理

状态机条中的“思考过程”只能是过程摘要，不能展示模型原始 chain-of-thought。

### 9.2 动效边界必须受控

Dock 和状态机动画都必须在容器边界内完成，不能破坏主布局。

### 9.3 不应阻塞主对话

状态机是辅助信息，不应与消息流抢主视觉。

## 10. 验收标准

满足以下条件即可认为可落地：

1. 顶部智能体可切换，切换后状态机和历史同步变化
2. 状态机条能看到：
   - 上一任务
   - 当前任务
   - 下一任务
   - 思考流摘要轮播
3. 右侧历史为紧凑列表，不是卡片
4. 页面在桌面尺寸下不出现整体滚动条
5. `frontend` 构建通过

## 11. 本次建议的直接落地范围

建议先在当前系统中只落 Phase 1：

- 不改后端 API
- 只重构 `AssistantApp.tsx` 的 workbench 视图
- 只增加前端状态机数据和样式

这样可以先把“多智能体工作台”的交互骨架落到真实系统，再决定是否推进 Phase 2/3。
