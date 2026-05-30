export type HelpRole = "员工用户" | "组织管理员" | "平台运营" | "开发者";

export type HelpSection = {
  title: string;
  body?: string;
  bullets?: string[];
  steps?: string[];
  code?: string;
};

export type HelpDoc = {
  slug: string;
  title: string;
  category: string;
  role: HelpRole[];
  entry: string;
  summary: string;
  updatedAt: string;
  aliases: string[];
  prerequisites: string[];
  sections: HelpSection[];
  related: string[];
};

export type HelpCategory = {
  id: string;
  title: string;
  summary: string;
};

export const helpCategories: HelpCategory[] = [
  { id: "getting-started", title: "快速开始", summary: "入口、账号、角色和首次配置。" },
  { id: "user-workbench", title: "用户工作台", summary: "对话、知识库、会议纪要和个人设置。" },
  { id: "admin", title: "管理后台", summary: "知识库、技能、智能体、计费用量和运行观测。" },
  { id: "platform", title: "平台控制面", summary: "模型厂商、平台治理、审计和租户生命周期。" },
  { id: "openapi", title: "Open API 与嵌入", summary: "API Key、调用方式、错误码和嵌入应用。" },
  { id: "troubleshooting", title: "故障排查", summary: "常见现象、检查路径和修复方式。" },
  { id: "security", title: "安全与权限", summary: "组织隔离、密钥、审计和高风险动作。" },
  { id: "changelog", title: "更新日志", summary: "新功能、行为变更、已知限制。" },
];

export const helpDocs: HelpDoc[] = [
  {
    slug: "getting-started/what-is-agentcici",
    title: "AgentCiCi 是什么",
    category: "getting-started",
    role: ["员工用户", "组织管理员", "平台运营", "开发者"],
    entry: "/",
    summary: "理解 AgentCiCi 的产品边界、主要入口和适用任务。",
    updatedAt: "2026-05-19",
    aliases: ["产品定位", "智能体运行与治理平台", "入口区别"],
    prerequisites: ["你已经获得 AgentCiCi 的访问地址。"],
    sections: [
      {
        title: "你可以用它做什么",
        body: "AgentCiCi 是面向企业业务系统的智能体运行与治理平台。员工在工作台完成对话、知识库问答、会议纪要和客户洞察；组织管理员配置知识库、模型、技能、工具和智能体；平台运营人员治理平台技能、内置工具、租户生命周期和审计。",
      },
      {
        title: "主要入口",
        bullets: [
          "`/` 是员工用户工作台，用于日常对话和个人任务。",
          "`/admin/login` 是组织管理后台入口，用于组织级配置。",
          "`/platform/login` 是平台控制面入口，用于运营和治理。",
          "`/help` 或 `help.agentcici.com` 是公开帮助中心。",
        ],
      },
      {
        title: "结果验证",
        bullets: ["你能说明自己应进入哪个入口。", "你能区分员工用户、组织管理员和平台运营人员的职责。"],
      },
      {
        title: "常见错误",
        bullets: ["把 AgentCiCi 当成通用聊天机器人。它更关注企业智能体的配置、运行、观测和治理。", "把管理后台和平台控制面混用。组织配置通常在 `/admin/*`，平台治理通常在 `/platform/*`。"],
      },
    ],
    related: ["getting-started/accounts-roles", "user-workbench/overview", "admin/agent-builder/overview"],
  },
  {
    slug: "getting-started/accounts-roles",
    title: "账号、组织与角色",
    category: "getting-started",
    role: ["员工用户", "组织管理员", "平台运营"],
    entry: "/、/admin/login、/platform/login",
    summary: "了解登录标识、组织成员、管理员权限和平台账号边界。",
    updatedAt: "2026-05-19",
    aliases: ["登录", "权限", "ORG_ADMIN", "ORG_USER", "平台账号"],
    prerequisites: ["你已经由管理员创建账号，或已经获得测试环境账号。"],
    sections: [
      {
        title: "角色说明",
        bullets: [
          "`ORG_USER` 可以使用员工工作台中的对话、知识库选择、会议纪要和个人设置。",
          "`ORG_ADMIN` 可以进入组织管理后台，维护本组织的知识库、模型、技能、工具、智能体和用户。",
          "平台运营账号进入 `/platform/*`，处理跨租户的治理、审计和平台级配置。",
        ],
      },
      {
        title: "操作步骤",
        steps: ["打开对应入口。", "输入手机号或邮箱以及密码。", "登录后确认页面左上角的组织和当前入口。", "如果没有目标入口权限，联系组织管理员或平台运营人员调整角色。"],
      },
      {
        title: "权限与安全说明",
        bullets: ["组织管理员不能自动拥有平台控制面权限。", "API Key 不等于管理员登录态，也不能替代用户密码。", "涉及唯一管理员变更时，系统会保留必要限制，避免组织失去管理入口。"],
      },
      {
        title: "常见错误",
        bullets: ["登录成功但看不到管理后台，通常是账号不是 `ORG_ADMIN`。", "平台登录失败，通常是账号不是平台角色，或正在使用组织端密码尝试登录平台端。"],
      },
    ],
    related: ["troubleshooting/login", "security/roles", "admin/users"],
  },
  {
    slug: "user-workbench/overview",
    title: "用户工作台总览",
    category: "user-workbench",
    role: ["员工用户"],
    entry: "/",
    summary: "从工作台开始对话、查看历史、切换智能体和进入个人设置。",
    updatedAt: "2026-05-19",
    aliases: ["工作台", "对话", "会话历史", "切换智能体"],
    prerequisites: ["你已经登录员工工作台。"],
    sections: [
      {
        title: "你可以用它做什么",
        body: "工作台用于连续完成企业智能体任务。你可以发起对话、查看会话历史、选择知识库参与回答、启动会议纪要、生成客户洞察，也可以维护个人资料、绑定邮箱和调整个人记忆。",
      },
      {
        title: "操作步骤",
        steps: ["在输入框输入问题或任务。", "按需选择知识库或切换智能体。", "等待回答生成后检查引用、状态或后续建议。", "在左侧会话历史中回到之前的上下文。", "通过设置入口维护个人资料、邮箱账号和专属记忆。"],
      },
      {
        title: "结果验证",
        bullets: ["新消息出现在当前会话中。", "如果使用了知识库，回答区域会体现知识库参与和命中状态。", "切换智能体后，新对话会使用当前智能体的能力边界。"],
      },
      {
        title: "常见错误",
        bullets: ["没有选择知识库却期待文档问答结果。", "在个人设置中修改资料后没有保存。", "把组织级配置问题放到工作台解决，模型、技能和知识库发布需要管理员进入 `/admin/*`。"],
      },
    ],
    related: ["user-workbench/knowledge-selection", "user-workbench/meeting-minutes", "admin/agent-builder/overview"],
  },
  {
    slug: "user-workbench/knowledge-selection",
    title: "知识库使用与管理",
    category: "user-workbench",
    role: ["员工用户", "组织管理员"],
    entry: "/、/admin/kb",
    summary: "员工如何选择知识库，管理员如何创建、上传和发布知识库文档。",
    updatedAt: "2026-05-19",
    aliases: ["知识库", "RAG", "文档发布", "未命中", "上传文档"],
    prerequisites: ["员工需要可用知识库。", "管理员需要进入 `/admin/kb`。"],
    sections: [
      {
        title: "员工侧操作",
        steps: ["在工作台确认当前智能体支持知识库问答。", "选择需要参与回答的知识库。", "提出具体问题，尽量包含产品、客户、流程或文档关键词。", "阅读回答中的知识库状态和引用信息。"],
      },
      {
        title: "管理员侧操作",
        steps: ["进入 `/admin/kb`。", "创建知识库并填写名称、说明和适用范围。", "上传文档，等待解析和分段。", "检查分段结果，必要时手动维护。", "发布文档，使其参与检索。"],
      },
      {
        title: "结果验证",
        bullets: ["知识库状态为可用。", "已发布文档能在测试问题中被命中。", "运行日志中能看到知识库检索明细。"],
      },
      {
        title: "常见错误",
        bullets: ["只上传未发布，员工侧不会稳定命中。", "问题过于泛化，系统可能不触发知识库检索。", "文档格式或大小超出限制，需要管理员按当前上传规则处理。"],
      },
    ],
    related: ["troubleshooting/kb-not-hit", "admin/ops/run-logs", "security/knowledge-boundary"],
  },
  {
    slug: "platform/models/providers",
    title: "模型厂商治理",
    category: "platform",
    role: ["平台运营"],
    entry: "/platform/models",
    summary: "由运营平台统一配置模型供应商、API 地址、API Key 和可用模型目录。",
    updatedAt: "2026-05-30",
    aliases: ["模型供应商", "百炼", "Ollama", "LM Studio", "模型路由"],
    prerequisites: ["你拥有平台运营权限。", "你已经准备好供应商地址、模型名和必要密钥。"],
    sections: [
      {
        title: "操作步骤",
        steps: ["进入 `/platform/models`。", "选择模型供应商。", "填写 API 地址和必要 API Key。", "保存后检测连通性。", "拉取模型列表，并把允许运行的模型加入平台已选模型。"],
      },
      {
        title: "权限与安全说明",
        bullets: ["模型厂商凭据只由平台运营维护，组织管理员不能自行配置。", "本地模型供应商可能不需要 API Key，但仍需要由平台确认运行环境可访问。", "模型目录影响 credits 归因、平台代付资源和客户自有资源边界。"],
      },
      {
        title: "结果验证",
        bullets: ["供应商显示为可用。", "模型列表可以读取。", "Agent Builder 和知识库 embedding 只能选择平台已选模型。"],
      },
      {
        title: "常见错误",
        bullets: ["base URL 末尾路径不符合供应商要求。", "把本地模型的不可访问地址填成浏览器本机地址。", "只保存厂商但没有加入已选模型，导致组织侧没有可选模型。"],
      },
    ],
    related: ["troubleshooting/model-failure", "admin/ops/run-logs", "admin/agent-builder/overview"],
  },
  {
    slug: "admin/agent-builder/overview",
    title: "智能体构建器总览",
    category: "admin",
    role: ["组织管理员"],
    entry: "/admin/agent-builder",
    summary: "用自然语言 Spec、知识库、工具和技能构建可发布智能体。",
    updatedAt: "2026-05-19",
    aliases: ["智能体构建", "Agent Builder", "绑定知识库", "发布智能体"],
    prerequisites: ["至少有一个可用模型。", "如需知识问答，先准备知识库。"],
    sections: [
      {
        title: "你可以用它做什么",
        body: "智能体构建器把业务目标、知识库、工具、技能和运行策略组织到一个可发布智能体中。管理员可以先编写自然语言 Spec，再绑定能力，预览和调试后发布。",
      },
      {
        title: "操作步骤",
        steps: ["进入 `/admin/agent-builder`。", "创建或选择智能体。", "编写智能体目标、边界和回答风格。", "绑定需要的知识库、工具和技能。", "编译并预览运行结果。", "确认无误后发布。"],
      },
      {
        title: "结果验证",
        bullets: ["智能体处于已发布状态。", "员工工作台能切换到该智能体。", "Open API 文档入口能显示该智能体的调用说明。"],
      },
      {
        title: "常见错误",
        bullets: ["能力绑定完成但未发布，员工侧看不到新行为。", "Spec 太宽泛，导致智能体回答边界不稳定。", "高风险工具未配置确认策略，不建议直接给生产智能体使用。"],
      },
    ],
    related: ["admin/skills/create", "user-workbench/overview", "openapi/quickstart"],
  },
  {
    slug: "admin/skills/create",
    title: "技能创建与发布",
    category: "admin",
    role: ["组织管理员", "平台运营"],
    entry: "/admin/skills、/platform/skills",
    summary: "创建自定义技能、理解标准技能只读边界，并完成版本发布。",
    updatedAt: "2026-05-19",
    aliases: ["技能", "自定义技能", "标准技能", "Runtime API", "发布"],
    prerequisites: ["你拥有组织管理员权限。", "如果治理平台技能，需要平台运营权限。"],
    sections: [
      {
        title: "操作步骤",
        steps: ["进入 `/admin/skills`。", "新建自定义技能，填写名称、说明和适用场景。", "配置提示词、输入输出和可用 Runtime API。", "保存草稿并进行测试。", "确认结果后发布新版本。"],
      },
      {
        title: "权限与安全说明",
        bullets: ["标准技能通常只读，不能在组织侧直接编辑。", "高风险 Runtime API 应配置人工确认或明确限制。", "导入技能前应先预览差异，确认不会覆盖关键配置。"],
      },
      {
        title: "结果验证",
        bullets: ["技能列表显示新版本。", "绑定到智能体后，调试运行能触发预期行为。", "审计或运行日志中能看到关键动作。"],
      },
      {
        title: "常见错误",
        bullets: ["草稿未发布就绑定到生产流程。", "API 参数只写自然语言，缺少结构化字段约束。", "删除或恢复前没有确认影响范围。"],
      },
    ],
    related: ["admin/agent-builder/overview", "security/high-risk-actions", "troubleshooting/skill-publish"],
  },
  {
    slug: "openapi/quickstart",
    title: "Open API 快速开始",
    category: "openapi",
    role: ["开发者", "组织管理员"],
    entry: "/admin/agent-builder/:agentId/openapi-docs",
    summary: "创建 API Key 后完成第一次 AgentCiCi Chat API 调用。",
    updatedAt: "2026-05-19",
    aliases: ["Open API", "Chat API", "stream", "requestId", "traceId"],
    prerequisites: ["目标智能体已发布。", "管理员已经创建或准备创建 API Key。"],
    sections: [
      {
        title: "请求地址",
        code: "GET  /openapi/v1/parameters\nPOST /openapi/v1/chat-messages\nPOST /openapi/v1/files/upload",
      },
      {
        title: "鉴权",
        body: "在服务端请求中使用 API Key。完整 Key 只在创建或重新生成时显示一次，请放在服务端密钥管理中，不建议写入浏览器前端代码。",
      },
      {
        title: "请求示例",
        code: "curl -X POST https://autoservice.agentcici.com/openapi/v1/chat-messages \\\n  -H \"Authorization: Bearer <AGENTCICI_API_KEY>\" \\\n  -H \"Content-Type: application/json\" \\\n  -H \"Idempotency-Key: demo-request-001\" \\\n  -d '{\"user\":\"customer-001\",\"query\":\"保修政策是什么？\",\"responseMode\":\"blocking\"}'",
      },
      {
        title: "结果验证",
        bullets: ["HTTP 状态为 200。", "响应中包含回答文本。", "管理员能在运行日志中用 `requestId` 或 `traceId` 定位调用。"],
      },
      {
        title: "调用限制",
        bullets: ["API Key 只允许调用绑定的智能体。", "调用应保存 `requestId` 和 `traceId`。", "遇到 401、403 或 429 时，先检查 Key、权限和配额。"],
      },
    ],
    related: ["openapi/api-keys", "admin/ops/run-logs", "troubleshooting/openapi-errors"],
  },
  {
    slug: "openapi/api-keys",
    title: "API Key 管理",
    category: "openapi",
    role: ["组织管理员", "开发者"],
    entry: "/admin/agent-builder/:agentId/openapi-docs",
    summary: "创建、轮换、撤销 API Key，并理解它和管理员 JWT 的区别。",
    updatedAt: "2026-05-19",
    aliases: ["API Key", "密钥", "轮换", "撤销", "401"],
    prerequisites: ["目标智能体已创建。", "你拥有组织管理员权限。"],
    sections: [
      {
        title: "操作步骤",
        steps: ["进入智能体构建器。", "打开目标智能体的开放 API 文档入口。", "创建 API Key 并记录完整 Key。", "把 Key 放入服务端密钥管理或部署环境变量。", "需要更换时生成新 Key，验证后撤销旧 Key。"],
      },
      {
        title: "权限与安全说明",
        bullets: ["API Key 不等同于管理员 JWT。", "API Key 只允许调用绑定智能体，不应被赋予后台管理权限。", "完整 Key 只显示一次，泄露后应立即撤销并轮换。", "浏览器直传 Key 代表接受暴露风险，推荐服务端转发。"],
      },
      {
        title: "结果验证",
        bullets: ["新 Key 可以完成 health 或 chat 调用。", "撤销后的旧 Key 返回未授权错误。", "运行日志能定位到对应智能体和调用来源。"],
      },
      {
        title: "常见错误",
        bullets: ["把管理员登录 token 当成 Open API Key 使用。", "复制时遗漏前后字符。", "旧 Key 仍留在定时任务或第三方系统中。"],
      },
    ],
    related: ["openapi/quickstart", "security/api-key", "troubleshooting/openapi-errors"],
  },
  {
    slug: "admin/ops/run-logs",
    title: "运行日志与 Trace 排查",
    category: "admin",
    role: ["组织管理员", "平台运营", "开发者"],
    entry: "/admin/ops",
    summary: "通过运行日志定位模型、工具、知识库和 Open API 调用问题。",
    updatedAt: "2026-05-19",
    aliases: ["运行日志", "Trace", "requestId", "traceId", "观测"],
    prerequisites: ["你拥有管理后台或平台观测权限。", "你知道用户会话、时间范围、requestId 或 traceId 中至少一项。"],
    sections: [
      {
        title: "操作步骤",
        steps: ["进入 `/admin/ops`。", "按时间范围、智能体、状态或 traceId 筛选。", "打开链路详情。", "依次检查模型调用、工具调用、知识库命中和错误信息。", "把 traceId、requestId 和失败片段提供给实施或研发支持。"],
      },
      {
        title: "结果验证",
        bullets: ["能找到对应运行记录。", "能判断失败发生在模型、工具、知识库、鉴权还是配额阶段。", "能复制必要定位信息。"],
      },
      {
        title: "权限与安全说明",
        bullets: ["日志可能包含客户输入摘要，应按组织权限查看。", "对外沟通时不要复制真实密钥、token 或客户隐私字段。"],
      },
      {
        title: "常见错误",
        bullets: ["只有用户描述，没有时间范围或 traceId，定位会很慢。", "把模型失败和知识库未命中混在一起处理，应先看链路阶段。"],
      },
    ],
    related: ["troubleshooting/model-failure", "troubleshooting/kb-not-hit", "openapi/quickstart"],
  },
  {
    slug: "admin/wechat-kf/setup",
    title: "企业微信微信客服配置",
    category: "admin",
    role: ["组织管理员"],
    entry: "/admin/channels/wechat-kf",
    summary: "创建微信客服账号配置，复制回调 URL，并绑定售后 Agent。",
    updatedAt: "2026-05-19",
    aliases: ["微信客服", "企业微信", "回调 URL", "售后 Agent"],
    prerequisites: ["你拥有企业微信管理权限。", "组织内已有售后 Agent 或可用默认售后智能体。"],
    sections: [
      {
        title: "操作步骤",
        steps: ["进入 `/admin/channels/wechat-kf`。", "新增客服账号配置。", "填写企业 ID、Secret、Token 和 EncodingAESKey。", "复制系统生成的回调 URL 到企业微信后台。", "绑定售后 Agent 和运行身份。", "启用配置并发送一条测试消息。"],
      },
      {
        title: "结果验证",
        bullets: ["微信客服配置状态为启用。", "企业微信后台回调验证通过。", "测试消息能进入 AgentCiCi，并由售后 Agent 基于授权知识库回答。"],
      },
      {
        title: "权限与安全说明",
        bullets: ["Secret 和 EncodingAESKey 保存后不应明文展示。", "当前售后 Agent 默认以知识库问答为主，不应擅自查询或操作 CRM、订单、工单和物流。"],
      },
      {
        title: "常见错误",
        bullets: ["回调 URL 复制到错误环境。", "Token 或 EncodingAESKey 前后存在空格。", "绑定的 Agent 未发布或没有可用知识库。"],
      },
    ],
    related: ["troubleshooting/wechat-kf-callback", "user-workbench/knowledge-selection", "security/high-risk-actions"],
  },
  {
    slug: "user-workbench/meeting-minutes",
    title: "会议纪要使用",
    category: "user-workbench",
    role: ["员工用户", "开发者"],
    entry: "/、/embed/meeting-minutes",
    summary: "启动实时会议听记、编辑发言人并生成 AI 纪要。",
    updatedAt: "2026-05-19",
    aliases: ["会议纪要", "实时听记", "麦克风", "嵌入 SDK"],
    prerequisites: ["浏览器已允许麦克风权限。", "如使用嵌入页面，需要有效 embed token。"],
    sections: [
      {
        title: "操作步骤",
        steps: ["在工作台或嵌入页面打开会议纪要。", "允许浏览器访问麦克风。", "开始实时听记。", "会议中按需编辑发言人。", "结束后生成 AI 纪要。", "检查摘要、行动项和可写回内容。"],
      },
      {
        title: "结果验证",
        bullets: ["实时转写持续出现文本。", "发言人名称能被保存。", "AI 纪要包含主题、要点、决策和行动项。"],
      },
      {
        title: "常见错误",
        bullets: ["浏览器麦克风权限被拒绝。", "系统没有可用 ASR 配置。", "embed token 过期或来源不匹配。"],
      },
      {
        title: "相关开发入口",
        code: "GET /embed/meeting-minutes\nPOST /embed/v1/meeting-minutes/*",
      },
    ],
    related: ["troubleshooting/microphone", "openapi/quickstart", "admin/ops/run-logs"],
  },
  {
    slug: "troubleshooting/kb-not-hit",
    title: "文档发布后没有命中",
    category: "troubleshooting",
    role: ["员工用户", "组织管理员"],
    entry: "/、/admin/kb、/admin/ops",
    summary: "排查知识库已上传但问答没有引用文档的问题。",
    updatedAt: "2026-05-19",
    aliases: ["知识库未命中", "RAG 未命中", "文档没有引用"],
    prerequisites: ["你知道问题示例、知识库名称和大致测试时间。"],
    sections: [
      {
        title: "现象",
        body: "用户提问后，回答没有引用目标文档，或者运行日志中没有知识库命中记录。",
      },
      {
        title: "可能原因",
        bullets: ["员工侧没有选择目标知识库。", "文档只上传但没有发布。", "文档解析或分段失败。", "问题没有触发知识库检索。", "目标知识库不在当前智能体允许范围内。"],
      },
      {
        title: "检查步骤",
        steps: ["在工作台确认已选择目标知识库。", "进入 `/admin/kb` 检查文档发布状态。", "查看分段是否存在有效文本。", "进入 `/admin/ops`，用时间和用户问题定位运行记录。", "检查知识库检索阶段是否执行，以及命中分数是否过低。"],
      },
      {
        title: "修复方式",
        bullets: ["发布文档或重新解析失败文档。", "把问题改写为包含文档中的关键业务词。", "确认智能体绑定了目标知识库。", "仍未解决时，收集 traceId、知识库名称、文档名和问题示例。"],
      },
    ],
    related: ["user-workbench/knowledge-selection", "admin/ops/run-logs", "security/knowledge-boundary"],
  },
  {
    slug: "troubleshooting/openapi-errors",
    title: "Open API 401 / 403 / 429",
    category: "troubleshooting",
    role: ["开发者", "组织管理员"],
    entry: "/openapi/*、/admin/ops",
    summary: "定位 Open API 的鉴权、权限和配额错误。",
    updatedAt: "2026-05-19",
    aliases: ["401", "403", "429", "agent_api_key_missing", "限流"],
    prerequisites: ["你能看到调用方请求头、目标 URL、响应状态和 requestId。"],
    sections: [
      {
        title: "现象",
        body: "调用 Open API 返回 401、403 或 429，业务系统无法继续获得智能体回答。",
      },
      {
        title: "可能原因",
        bullets: ["401 通常表示缺少 Key、Key 错误、Key 已撤销或 Authorization 格式不正确。", "403 通常表示 Key 无权调用目标智能体。", "429 通常表示配额、限流或并发保护触发。"],
      },
      {
        title: "检查步骤",
        steps: ["确认请求头是 `Authorization: Bearer <AGENTCICI_API_KEY>`。", "确认 URL 中的 agentCode 与 Key 绑定智能体一致。", "确认 Key 未撤销且未复制错误。", "在 `/admin/ops` 用 requestId 或 traceId 查询运行记录。", "检查组织配额和调用频率。"],
      },
      {
        title: "修复方式",
        bullets: ["重新生成 Key 并更新服务端密钥。", "改用绑定目标智能体的 Key。", "降低调用频率或联系管理员调整配额。", "把 requestId、traceId、状态码和响应体交给管理员排查。"],
      },
    ],
    related: ["openapi/quickstart", "openapi/api-keys", "admin/ops/run-logs"],
  },
  {
    slug: "security/api-key",
    title: "API Key 安全",
    category: "security",
    role: ["组织管理员", "开发者"],
    entry: "/admin/agent-builder/:agentId/openapi-docs",
    summary: "把 API Key 作为生产密钥管理，避免泄露和越权调用。",
    updatedAt: "2026-05-19",
    aliases: ["API Key 安全", "密钥泄露", "浏览器调用", "轮换"],
    prerequisites: ["你正在接入 Open API，或负责管理智能体开放能力。"],
    sections: [
      {
        title: "安全边界",
        bullets: ["API Key 只服务 Open API 调用，不是用户登录密码。", "Key 应保存在服务端密钥管理或部署环境变量中。", "浏览器直传 Key 会暴露给用户和前端运行环境。", "发现泄露后应立即撤销旧 Key 并生成新 Key。"],
      },
      {
        title: "推荐做法",
        steps: ["为每个业务系统或环境使用独立 Key。", "在调用中设置 requestId，便于日志追踪。", "定期轮换 Key。", "把 Key 权限限定到必要智能体。", "上线前确认日志不会打印完整 Key。"],
      },
      {
        title: "结果验证",
        bullets: ["生产代码仓库中没有明文 Key。", "浏览器网络面板中看不到完整 Key。", "撤销旧 Key 后旧调用失败，新 Key 调用成功。"],
      },
    ],
    related: ["openapi/api-keys", "troubleshooting/openapi-errors", "admin/ops/run-logs"],
  },
  {
    slug: "changelog",
    title: "更新日志",
    category: "changelog",
    role: ["员工用户", "组织管理员", "平台运营", "开发者"],
    entry: "/help/changelog",
    summary: "面向使用者记录新功能、行为变更、修复说明和已知限制。",
    updatedAt: "2026-05-19",
    aliases: ["版本更新", "已知限制", "行为变更"],
    prerequisites: ["无。"],
    sections: [
      {
        title: "2026-05-19",
        bullets: [
          "新增 AgentCiCi 帮助中心 MVP，覆盖快速开始、工作台、管理后台、Open API、故障排查、安全和更新日志。",
          "帮助中心首批内容以产品任务为主，不直接复制研发规格。",
          "当前文档以中文为主，URL slug 使用英文，为后续英文版保留空间。",
        ],
      },
      {
        title: "记录规则",
        bullets: ["每条更新说明影响范围。", "如需用户操作，必须写明操作入口。", "已知限制应链接到对应帮助文档或排障文档。"],
      },
    ],
    related: ["getting-started/what-is-agentcici", "openapi/quickstart", "troubleshooting/openapi-errors"],
  },
];

export const featuredSlugs = [
  "getting-started/what-is-agentcici",
  "user-workbench/knowledge-selection",
  "platform/models/providers",
  "admin/agent-builder/overview",
  "openapi/quickstart",
  "admin/ops/run-logs",
];

export const roleEntrypoints = [
  {
    role: "员工用户",
    summary: "开始对话、选择知识库、生成会议纪要和维护个人设置。",
    slugs: ["user-workbench/overview", "user-workbench/knowledge-selection", "user-workbench/meeting-minutes"],
  },
  {
    role: "组织管理员",
    summary: "维护知识库、技能、智能体、渠道、计费用量和运行观测。",
    slugs: ["admin/skills/create", "admin/agent-builder/overview", "admin/wechat-kf/setup", "admin/ops/run-logs"],
  },
  {
    role: "平台运营",
    summary: "治理模型厂商、标准技能、内置工具、租户生命周期和平台审计。",
    slugs: ["platform/models/providers", "admin/ops/run-logs", "admin/skills/create", "security/high-risk-actions"],
  },
  {
    role: "开发者",
    summary: "创建 API Key，接入 Chat API、流式调用和嵌入式会议纪要。",
    slugs: ["openapi/quickstart", "openapi/api-keys", "troubleshooting/openapi-errors"],
  },
  {
    role: "平台运营",
    summary: "查看平台治理、安全边界、审计和变更说明。",
    slugs: ["admin/ops/run-logs", "security/api-key", "changelog"],
  },
];
