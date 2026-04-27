# 智能体产品功能对比打钩表

更新时间：2026-04-15  
对比对象：`Cherry Studio`、`Open WebUI`、`LibreChat`、`AnythingLLM`

## 说明

- 本文只统计各产品在官方文档、官网或官方仓库中已明确说明的能力。
- `✅`：官方已明确支持，且是当前公开能力。
- `◐`：支持，但有版本/部署/付费/企业版限制，或能力形态明显弱于其他产品。
- `—`：当前未在官方公开资料中看到明确支持，不代表绝对没有。
- 对 `Cherry Studio`，凡是企业版专属能力，会在备注中单独写明。

## 总体判断

按“功能覆盖面”而不是“单点体验”来看，当前结论是：

1. `Open WebUI` 最全面，覆盖个人使用、Agent、知识库、团队治理、运维部署几乎全栈。
2. `LibreChat` 和 `AnythingLLM` 属于第二梯队，前者更像多模型 Agent 门户，后者更像文档/RAG/Agent 一体化工作台。
3. `Cherry Studio` 在桌面端里很强，但团队治理和服务端运维能力主要依赖企业版，因此整体覆盖面略弱于前三者。

## 一、基础对话与模型能力

| 功能项 | Cherry Studio | Open WebUI | LibreChat | AnythingLLM | 备注 |
|---|---|---|---|---|---|
| 多模型 / 多 Provider 接入 | ✅ | ✅ | ✅ | ✅ | 四者都支持多家云模型服务接入 |
| 本地模型接入 | ✅ | ✅ | ✅ | ✅ | Cherry 明确支持 `Ollama`、`LM Studio`；AnythingLLM 也支持本地与云模型混用 |
| 自定义助手 / Agent 预设 | ✅ | ✅ | ✅ | ✅ | Cherry 有 `300+` 预设助手；其余三者都有自定义 Agent 能力 |
| 多模型对比 / 同时对话 | ✅ | ✅ | — | — | Cherry 文档写明 `Multi-model Simultaneous Conversations`；Open WebUI 有 side-by-side compare |
| 文件 / 图片上传分析 | ✅ | ✅ | ✅ | ✅ | 四者都支持把文件作为上下文或分析对象 |
| 会话组织能力 | ✅ | ✅ | ✅ | ◐ | Cherry 有 Topic；Open WebUI 有 folders/tags/pins；LibreChat 有 fork/share/import；AnythingLLM 更偏 workspace 组织 |
| 分享 / 外链协作 | ◐ | ◐ | ✅ | ◐ | LibreChat 官方明确有 shareable links；其余产品公开资料里不是同等强项 |

## 二、知识库与 RAG 能力

| 功能项 | Cherry Studio | Open WebUI | LibreChat | AnythingLLM | 备注 |
|---|---|---|---|---|---|
| 内置知识库 / RAG | ✅ | ✅ | ✅ | ✅ | 四者都不是纯聊天壳，均支持文档问答 |
| 文件向量化索引 | ✅ | ✅ | ✅ | ✅ | Cherry 知识库教程明确写了 vectorization |
| 多来源导入 | ✅ | ◐ | ◐ | ◐ | Cherry 明确支持文件、文件夹、URL、sitemap、纯文本；其他三者公开资料更强调文件/RAG，不像 Cherry 写得这么细 |
| 知识库检索结果回引 / 引用 | ✅ | ✅ | ✅ | ✅ | Cherry 会把引用数据源附在回答下方；Open WebUI / LibreChat / AnythingLLM 也都强调来源或 RAG 引用 |
| 多向量数据库后端 | — | ✅ | ◐ | ✅ | Open WebUI 明确支持 9 种向量库；AnythingLLM 支持 LanceDB/PGVector/Pinecone/Qdrant 等；LibreChat 为 RAG API 方案，产品层显式程度略弱 |
| OCR / 文档抽取增强 | — | ✅ | ✅ | ◐ | Cherry 当前 OCR 还在 roadmap；Open WebUI 明确有多 extraction engine；LibreChat 有 OCR；AnythingLLM 文档中有转录与文档处理能力，但公开页不如前两者明确 |
| 查询模式控制 | — | ◐ | ◐ | ✅ | AnythingLLM 明确区分 `Automatic / Chat / Query` 模式，这一项最清晰 |
| Notes / 非向量全文上下文工作区 | — | ✅ | ◐ | — | Open WebUI 的 Notes 很完整；LibreChat 有 File Context，但不是独立 Notes 工作区 |

## 三、Agent、工具扩展与自动化

| 功能项 | Cherry Studio | Open WebUI | LibreChat | AnythingLLM | 备注 |
|---|---|---|---|---|---|
| MCP 支持 | ✅ | ✅ | ✅ | ✅ | 四者都已明确支持 MCP |
| MCP 生态 / 管理深度 | ◐ | ✅ | ✅ | ◐ | Open WebUI 和 LibreChat 的文档化程度更高；Cherry 有 MCP 配置和同步能力；AnythingLLM 有兼容页但平台化程度略弱 |
| 内置工具 / Skills 生态 | ◐ | ✅ | ✅ | ✅ | AnythingLLM 的 built-in skills 很丰富；Open WebUI 有 Tools/Functions/Skills；LibreChat 有 built-in tools；Cherry 公开资料更多是 MCP + Code Tools |
| 无代码 Agent 构建 | ◐ | ✅ | ✅ | ✅ | LibreChat 和 AnythingLLM 对“no-code agent builder”表述更直接；Open WebUI 也可通过 presets/tools/knowledge 组合构建 |
| OpenAPI / 外部服务工具导入 | — | ✅ | ✅ | — | Open WebUI 支持 OpenAPI servers；LibreChat 支持 Actions 从 OpenAPI specs 动态建工具 |
| 代码执行 / Code Interpreter | ◐ | ✅ | ✅ | — | Cherry 的 Code Tools 更像“调起外部编码代理”，不是内建 interpreter；AnythingLLM 未见同级官方描述 |
| 真实终端 / 计算环境接入 | ◐ | ✅ | — | ◐ | Cherry 会调起系统 Terminal 跑 Claude Code / Codex；Open WebUI 有 Open Terminal；AnythingLLM 有 `AI Computer use` 预览能力 |
| Agent 工作流 / Flow 编排 | — | ◐ | — | ✅ | AnythingLLM 明确有 Agent Flows；Open WebUI 有 pipelines，但偏平台扩展，不是同形态可视化 Flow |
| Agent API / 开发者调用 | — | ◐ | ✅ | ✅ | LibreChat 有 Agents API；AnythingLLM 有完整 developer API；Open WebUI 有 API keys 和平台 API，但“Agent API”表述不如前两者聚焦 |

## 四、团队治理、权限与部署

| 功能项 | Cherry Studio | Open WebUI | LibreChat | AnythingLLM | 备注 |
|---|---|---|---|---|---|
| 桌面客户端 | ✅ | — | — | ✅ | Cherry 和 AnythingLLM 都有桌面版 |
| 自托管 Web / 服务端形态 | ◐ | ✅ | ✅ | ✅ | Cherry 主要依赖企业版私有部署；后三者天然是服务端产品 |
| 多用户 / 角色权限 | ◐ | ✅ | ◐ | ✅ | Cherry 为企业版；Open WebUI 有完整 RBAC；LibreChat 有管理与权限，但官方公开页的颗粒度弱于 Open WebUI；AnythingLLM Docker 版支持多用户角色 |
| 细粒度 RBAC / Group ACL | ◐ | ✅ | ◐ | ◐ | Open WebUI 这一项最强；Cherry 企业版支持 model / KB / feature 权限；AnythingLLM 角色更偏 Admin/Manager/Default |
| SSO / LDAP / SCIM | — | ✅ | ✅ | — | Open WebUI 明确有 `SSO/OIDC/LDAP/SCIM`；LibreChat 明确有 `OAuth2/SAML/LDAP/2FA` |
| 管理后台 / Analytics | ◐ | ✅ | ◐ | ✅ | Cherry 企业版有 admin backend；Open WebUI 有 analytics；AnythingLLM 多用户管理员可看 logs/analytics |
| API Keys / 外部 API 能力 | — | ✅ | ✅ | ✅ | AnythingLLM 和 LibreChat 都明确可供外部程序调用；Open WebUI 也有 API keys |
| 云原生部署能力 | ◐ | ✅ | ◐ | ✅ | Open WebUI 明确支持 Docker/K8s/Helm/scale；AnythingLLM 支持 Docker/Cloud VM/云部署；Cherry 只在企业版语境下提到私有部署 |
| 私有化 / 本地数据控制 | ✅ | ✅ | ✅ | ✅ | 四者都可走私有化或自控数据路径，但 Cherry 的完整团队能力主要在企业版 |
| 可嵌入聊天组件 | — | — | — | ✅ | AnythingLLM 明确有 embeddable chat widget |

## 结论

如果按“功能覆盖面”做选择，可以直接这样看：

1. `Open WebUI`
   最像完整 AI 操作系统，聊天、知识库、Agent、终端、权限、分析、部署都很齐。
2. `LibreChat`
   最像多模型 Agent 门户，MCP、工具、Agent、认证体系都成熟，适合做团队统一入口。
3. `AnythingLLM`
   最像文档/RAG/Agent 一体化平台，知识库、workspace、flows、API、embed 很均衡。
4. `Cherry Studio`
   最像强桌面生产力客户端，个人使用和本地工作流很顺手，但企业治理能力主要在企业版。

如果只看“桌面端功能丰富度”：

1. `Cherry Studio`
2. `AnythingLLM Desktop`

如果只看“服务端/Web 形态的功能完整度”：

1. `Open WebUI`
2. `LibreChat`
3. `AnythingLLM`

## 适合的使用场景

- 个人高频使用、偏桌面体验：`Cherry Studio`
- 团队统一 AI 门户、强调权限和扩展：`Open WebUI`
- 多模型 Agent 平台、偏开发者和工具链：`LibreChat`
- 文档问答、RAG、Agent、对外嵌入一体化：`AnythingLLM`

## 主要依据

- Cherry Studio GitHub README: <https://github.com/CherryHQ/cherry-studio>
- Cherry Studio Quick Assistant: <https://docs.cherry-ai.com/docs/en-us/cherry-studio/preview/kuai-jie-zhu-shou>
- Cherry Studio Knowledge Base: <https://docs.cherry-ai.com/docs/en-us/knowledge-base/knowledge-base>
- Cherry Studio WebDAV Backup: <https://docs.cherry-ai.com/docs/en-us/pre-basic/data-settings/webdav>
- Cherry Studio MCP: <https://docs.cherry-ai.com/advanced-basic/mcp/config>
- Cherry Studio Code Tools: <https://docs.cherry-ai.com/docs/en-us/advanced-basic/code-tools-shi-yong-jiao-cheng>
- Open WebUI Features: <https://docs.openwebui.com/features/>
- Open WebUI RBAC: <https://docs.openwebui.com/features/access-security/rbac/>
- Open WebUI MCP: <https://docs.openwebui.com/features/extensibility/mcp/>
- LibreChat Features Overview: <https://www.librechat.ai/docs/features>
- LibreChat Agents: <https://www.librechat.ai/docs/features/plugins>
- LibreChat MCP: <https://www.librechat.ai/docs/features/mcp>
- LibreChat RAG API: <https://www.librechat.ai/docs/features/rag_api>
- LibreChat Authentication: <https://www.librechat.ai/docs/features/authentication>
- AnythingLLM Docs: <https://docs.anythingllm.com/>
- AnythingLLM All Features: <https://docs.anythingllm.com/features/all-features>
- AnythingLLM AI Agents: <https://docs.anythingllm.com/features/ai-agents>
- AnythingLLM API Access: <https://docs.anythingllm.com/features/api>
- AnythingLLM Security & Access: <https://docs.anythingllm.com/features/security-and-access>
- AnythingLLM Chat Modes: <https://docs.anythingllm.com/features/chat-modes>
- AnythingLLM GitHub README: <https://github.com/Mintplex-Labs/anything-llm>
