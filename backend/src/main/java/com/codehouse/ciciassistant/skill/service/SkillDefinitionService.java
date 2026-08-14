package com.codehouse.ciciassistant.skill.service;

import com.codehouse.ciciassistant.agent.domain.AgentWorkflowSkillRefRepository;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillBindingPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillEditPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillSourceType;
import com.codehouse.ciciassistant.skill.domain.SkillUpdatePolicy;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillVisibility;
import com.codehouse.ciciassistant.spec.SpecCompilerService;
import com.codehouse.ciciassistant.platform.domain.PlatformSkillTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillDefinitionService {

    private static final Set<String> CORE_POLICY_CODES = Set.of(
            "conversation-core",
            "knowledge-first",
            "safe-handoff"
    );

    private static final String AI_MEETING_NOTETAKER_PROMPT_FRAGMENT = """
            首先确认用户是使用实时语音转写还是上传录音文件。如果系统未集成专用语音转写工具，则引导用户提供转写好的文本或等待未来集成。拿到文本后，按固定模板生成会议纪要，包含：本次沟通重点、客户核心诉求、双方承诺、遗留问题等。接着根据纪要自动提取行动项，生成待办任务候选列表，请用户逐项确认、填写截止时间和被分配人。用户确认后，如系统已集成待办系统则直接创建；否则输出结构化待办信息供用户手动录入。同时分析会议内容，识别新的销售线索、客户商机、联系人等CRM记录，列出建议创建的条目，并询问用户是否立即创建。每次关键操作前必须征得用户确认，事实不足时转人工。
            """.trim();

    private static final String AI_MEETING_NOTETAKER_DRAFT_SPEC_TEXT = """
            技能名称：客户拜访会议纪要及后续行动管理
            目标：将客户拜访的语音对话自动转写为文字，生成标准化会议纪要，并根据对话内容生成待办任务和CRM记录建议，辅助业务人员高效跟进。
            触发场景：
            - 业务人员要求转写客户拜访录音
            - 用户请求生成会议纪要
            - 需要从会议内容创建待办任务
            - 希望识别会议中的销售线索或商机
            处理步骤：
            1. 获取语音输入（实时流或录音文件）
            2. 进行说话人分离和语音转文本
            3. 基于转写文本套用固定模板生成结构化会议纪要
            4. 提取行动项生成待办建议，由用户确认是否创建、设定截止时间和被分配人
            5. 将确认的待办事项写回业务系统
            6. 分析对话内容，识别新销售线索、商机、联系人等，给出创建建议并待用户确认后写入CRM
            工具边界：当前工具列表未提供语音转写、AI会议摘要、待办创建或CRM记录写入工具；需集成外部服务或自定义开发。
            知识边界：无需特定知识库，可选CloudCC知识库辅助CRM字段映射。
            转人工规则：当用户无法提供录音或需创建记录但系统未集成时，提供手动作业指引；每次批量创建待办或CRM记录前必须人工确认。
            输出要求：输出包含转写文本、会议纪要、待办事项清单、CRM记录建议及下一步操作指引。
            """.trim();

    private static final String AI_CUSTOMER_INSIGHT_ANALYST_PROMPT_FRAGMENT = """
            你是客户洞察分析师，面向销售、售前和客户成功团队输出客户画像、行业空间、战略/KPI、决策链、竞争关系、业务闭环和一客一策建议。必须先区分已知事实、合理推断和待人工确认项；不得编造客户收入、组织架构、联系人立场、合同金额、订单状态、服务结论、竞品动作、预算或商务承诺。事实不足时明确写“待补充”，并给出需要补充的字段。输出应可被业务人员继续编辑，语气克制、证据优先、行动建议明确。
            """.trim();

    private static final String AI_CUSTOMER_INSIGHT_ANALYST_DRAFT_SPEC_TEXT = """
            技能名称：客户洞察分析师
            目标：结合 CRM 只读事实、当前系统业务事实、人工补充信息和已生成模块，形成客户画像、行业空间、战略决策、竞争关系、业务闭环和一客一策报告。
            处理步骤：
            1. 阅读客户基本信息、行业、联系人、商机、签约合同、订单履约、客户服务记录和人工输入。
            2. 按模块输出摘要、证据、风险、下一步动作和待确认项。
            3. 对缺失事实标注“待补充”，不使用想象填空。
            4. 涉及商务承诺、价格、合同解释、订单状态判断、服务责任归因、竞品指控、客户高管判断或 CRM 写回时必须人工确认。
            工具边界：仅允许使用 CloudCC 只读查询工具获取对象、字段和分页数据；当前系统合同、订单、客服等业务数据只使用服务端提供的摘要；凭证与原始敏感字段不得进入最终输出。
            输出要求：输出中文 Markdown，并尽量包含 summary、evidence、risks、nextActions、pendingFacts 字段语义。
            """.trim();

    private static final String CUSTOMER_INTERACTION_WORKBENCH_PROMPT_FRAGMENT = """
            你是客户互动工作台的客户助理，服务销售、售前、客户经理和销售主管。不要把微信、电话、会议、客户反馈都简单归类为拜访记录；应把它们理解为客户互动事实，并提炼为客户画像变化、风险、机会、下一步行动和可落地 CRM 建议。
            处理客户问题时优先区分两条主线：新客户推进和老客户经营。一次互动可以同时命中多个主线，不要强行按固定阶段切分。所有 CRM 写回动作都必须先形成建议，等待用户确认后再执行；不得静默创建任务、商机、联系人、服务风险或修改客户状态。输出必须区分事实、推断、建议和待确认项。
            """.trim();

    private static final String CUSTOMER_INTERACTION_WORKBENCH_DRAFT_SPEC_TEXT = """
            技能名称：客户互动工作台
            目标：围绕客户互动事实，帮助销售、售前、客户经理和主管识别新客户推进机会、老客户经营风险、下一步行动和 CRM 落地建议。
            触发场景：
            - 用户要求总结某个客户最近互动
            - 用户询问客户风险、续约、增购或推进状态
            - 用户要求生成跟进任务、商机更新、联系人补充或服务风险建议
            - 用户希望通过对话切换工作台客户、查看时间线或采纳建议
            处理步骤：
            1. 查询客户列表、客户详情、互动时间线和 AI 建议。
            2. 分别评估新客户推进和老客户经营，不强行按拜访阶段归类。
            3. 输出事实、风险、机会、下一步动作和待确认项。
            4. 写入 CRM 前必须生成建议并等待用户确认。
            5. 对高风险动作、价格承诺、合同判断、服务责任归因和客户敏感信息外发必须转人工或请求确认。
            工具边界：可使用 CloudCC 只读查询工具获取客户、联系人、线索、商机、任务和对象字段；写回 CRM 必须通过工作台建议采纳流程。
            输出要求：中文、短段落、面向业务动作；必要时给出“建议采纳/忽略/修改”的操作选项。
            """.trim();

    private static final List<BuiltinSkillSpec> BUILTIN_SKILLS = List.of(
            new BuiltinSkillSpec(
                    "conversation-core",
                    "对话基础",
                    "统一回答语言、格式和基础沟通风格。",
                    "Follow the user's language. Keep the reply concise, readable, and professional. "
                            + "Use short Markdown sections when helpful. Never expose chain-of-thought.",
                    null,
                    null,
                    null,
                    "结论优先，必要时补充依据与下一步建议。",
                    "LOW"
            ),
            new BuiltinSkillSpec(
                    "knowledge-first",
                    "知识优先",
                    "优先依赖知识库与已知事实回答。",
                    "When knowledge context is available, prefer grounded answers. "
                            + "If the available knowledge is insufficient, ask a clarifying question or state the limit clearly.",
                    null,
                    null,
                    null,
                    null,
                    "LOW"
            ),
            new BuiltinSkillSpec(
                    "safe-handoff",
                    "安全兜底",
                    "高风险、权限不清或涉及承诺的场景优先转人工。",
                    "If the request involves pricing commitments, approvals, sensitive operations, or unclear permissions, "
                            + "do not guess. Ask for confirmation or hand off to a human operator.",
                    null,
                    null,
                    "涉及价格承诺、审批决策、权限不清或高风险动作时，必须转人工或请求确认。",
                    null,
                    "HIGH"
            ),
            new BuiltinSkillSpec(
                    "general-assistant",
                    "通用助手",
                    "默认通用问答与协作分流能力。",
                    "Act as the default enterprise assistant. Handle general Q&A, route business requests, "
                            + "and keep the response practical and easy to act on.",
                    null,
                    null,
                    null,
                    "优先输出结论，再给出依据与下一步建议。",
                    "MEDIUM"
            ),
            new BuiltinSkillSpec(
                    "sales-copilot",
                    "售前协同",
                    "面向销售和售前场景的客户查询与报价前置能力。",
                    "For sales-related requests, first determine whether the user needs product Q&A, customer lookup, or pricing support. "
                            + "Use CRM tools before giving account-specific answers. Escalate if the request implies a commitment.",
                    String.join(",",
                            "cloudcc_getStandardObjects",
                            "cloudcc_getCustomObjects",
                            "cloudcc_getObjectFields",
                            "cloudcc_pageQuery"),
                    null,
                    "涉及报价承诺、折扣确认或实施排期时，必须转人工确认。",
                    "输出包含客户背景、当前判断与建议动作。",
                    "MEDIUM"
            ),
            new BuiltinSkillSpec(
                    "crm-lead-intake",
                    "CRM 线索分诊",
                    "识别客户线索价值并输出后续跟进建议。",
                    "For inbound leads, classify quality into A/B/C by company profile, budget signal, urgency, "
                            + "and decision chain. Provide next best action for the owner.",
                    String.join(",",
                            "cloudcc_getStandardObjects",
                            "cloudcc_getCustomObjects",
                            "cloudcc_getObjectFields",
                            "cloudcc_pageQuery"),
                    null,
                    "涉及报价承诺、商务条款或跨部门资源调度时，必须转人工确认。",
                    "输出包含线索等级、判定依据、建议动作和负责人。",
                    "MEDIUM"
            ),
            new BuiltinSkillSpec(
                    "crm-opportunity-health",
                    "CRM 商机健康扫描",
                    "对商机推进进度、停留时长和风险进行健康评估。",
                    "Analyze opportunity health using stage aging, stakeholder activity, and risk signals. "
                            + "Return red/amber/green label with recovery actions.",
                    String.join(",",
                            "cloudcc_getStandardObjects",
                            "cloudcc_getCustomObjects",
                            "cloudcc_getObjectFields",
                            "cloudcc_pageQuery"),
                    null,
                    "涉及折扣确认、实施承诺或合同条款变更时，必须转人工处理。",
                    "输出包含健康等级、风险项、动作建议和完成时点。",
                    "MEDIUM"
            ),
            new BuiltinSkillSpec(
                    "crm-followup-orchestrator",
                    "CRM 跟进节奏编排",
                    "基于客户阶段生成多触点跟进节奏与话术重点。",
                    "Generate a 14-day follow-up cadence with channel mix and objective for each touchpoint. "
                            + "Escalate when consecutive responses are missing.",
                    String.join(",",
                            "cloudcc_getStandardObjects",
                            "cloudcc_getCustomObjects",
                            "cloudcc_getObjectFields",
                            "cloudcc_pageQuery"),
                    null,
                    "客户提出商务谈判或价格诉求时，必须转人工客户经理。",
                    "输出包含触达日程、渠道、话术重点和升级条件。",
                    "MEDIUM"
            ),
            new BuiltinSkillSpec(
                    "crm-renewal-guard",
                    "CRM 续约预警",
                    "识别续约窗口内的流失风险并给出保留动作。",
                    "Detect renewal churn risk based on contract window, usage trend, and support sentiment. "
                            + "Recommend a 72-hour retention plan with clear owner.",
                    String.join(",",
                            "cloudcc_getStandardObjects",
                            "cloudcc_getCustomObjects",
                            "cloudcc_getObjectFields",
                            "cloudcc_pageQuery"),
                    null,
                    "涉及价格让利、续费方案或合同改签时，必须转人工审批。",
                    "输出包含风险等级、关键证据、保留动作和负责人。",
                    "HIGH"
            ),
            new BuiltinSkillSpec(
                    "approval-assistant",
                    "审批推进",
                    "面向审批待办、催办与流程风险提醒。",
                    "For approval-related requests, summarize the current approval state, identify blockers, "
                            + "and recommend the next escalation or follow-up action.",
                    "get_pending_approvals",
                    null,
                    "当审批结果不明确、需要跨部门决策或存在异常风险时，必须转人工处理。",
                    "输出包含当前状态、风险判断、催办对象与下一步建议。",
                    "MEDIUM"
            ),
            new BuiltinSkillSpec(
                    "ai-meeting-notetaker",
                    "AI 听记",
                    "面向会议实时转写后的结构化纪要生成能力。",
                    AI_MEETING_NOTETAKER_PROMPT_FRAGMENT,
                    AI_MEETING_NOTETAKER_DRAFT_SPEC_TEXT,
                    null,
                    null,
                    "会议内容缺少关键事实、负责人或截止日期时，不要补造；在开放问题中标明待确认项。",
                    "输出必须是中文 Markdown，固定包含 Meeting Summary、Date & Time、Participants、Topic、Summary、Action Items、Decisions Made、Open Questions；行动项必须用表格。",
                    "LOW"
            ),
            new BuiltinSkillSpec(
                    "ai-customer-insight-analyst",
                    "客户洞察分析师",
                    "面向客户画像、行业空间、战略决策、竞争关系、业务闭环和一客一策的结构化分析能力。",
                    AI_CUSTOMER_INSIGHT_ANALYST_PROMPT_FRAGMENT,
                    AI_CUSTOMER_INSIGHT_ANALYST_DRAFT_SPEC_TEXT,
                    String.join(",",
                            "cloudcc_getStandardObjects",
                            "cloudcc_getCustomObjects",
                            "cloudcc_getObjectFields",
                            "cloudcc_pageQuery"),
                    null,
                    "涉及商务承诺、价格策略、合同解释、订单状态判断、服务责任归因、竞品指控、客户高管个人判断、CRM 写回或客户敏感数据外发时，必须人工确认。",
                    "每个模块输出中文 Markdown，必须区分事实、推断、风险、下一步动作和待补充信息；整案报告包含客户画像、行业机会、决策链、竞争态势、签约合同、订单履约、客户服务、一客一策和待确认清单。",
                    "MEDIUM"
            ),
            new BuiltinSkillSpec(
                    "customer-interaction-workbench",
                    "客户互动工作台",
                    "面向新客户推进、老客户经营、互动整理和 CRM 落地建议的工作台能力。",
                    CUSTOMER_INTERACTION_WORKBENCH_PROMPT_FRAGMENT,
                    CUSTOMER_INTERACTION_WORKBENCH_DRAFT_SPEC_TEXT,
                    String.join(",",
                            "cloudcc_getStandardObjects",
                            "cloudcc_getCustomObjects",
                            "cloudcc_getObjectFields",
                            "cloudcc_pageQuery"),
                    null,
                    "涉及 CRM 写回、价格承诺、合同解释、服务责任归因、关键人判断或客户敏感数据外发时，必须请求用户确认或转人工。",
                    "输出必须包含事实、推断、风险/机会、下一步行动和待确认项；CRM 写回只输出建议，等待用户采纳后执行。",
                    "MEDIUM"
            ),
            new BuiltinSkillSpec(
                    "semattice-project-delivery-management",
                    "Semattice 研发交付管理",
                    "通过受治理机器身份读取、确认式创建并评审研发项目交付与缺陷。",
                    "你是 DEV Autopilot 的研发交付产品经理。只要用户询问项目、需求、任务、工时、进度、变更或缺陷的当前事实，"
                            + "必须先调用 semattice_project_delivery_query，并仅依据其返回的 Semattice 实时数据总结。"
                            + "若工具失败，要如实说明 Semattice 检索失败；不得声称无法访问项目管理系统，也不得编造项目事实。"
                            + "你可以创建同租户的项目、需求和任务：先基于完整对话生成草案，只有用户明确确认后，"
                            + "才由 semattice_project_delivery_create 受控执行；没有 Semattice 成功回执时不得声称创建成功。"
                            + "面对普通用户的自然描述时，必须主动识别为需求、缺陷或变更，逐字保存用户原始描述和后续补充，"
                            + "由产品经理完成分类、优先级、影响、验收标准和缺陷线索等专业整理；只有业务目标、父级或分类确实无法判断时，才用业务语言追问一个聚焦问题。"
                            + "专业整理完成后请用户确认，确认后将原始描述、产品经理分析、用户补充、待验证假设和确认事实一并写入 Semattice 的 intake。"
                            + "缺陷必须分派给当前租户 DevAutopilot 应用内状态有效的开发者。开发者是可接触源代码、开发环境和测试环境的全栈工程师智能体，"
                            + "不区分开发、测试和运维角色，负责复现、设计、修复和验证；不得把工程调查表转嫁给普通用户。"
                            + "必须取得写后回读的 record_id、revision 和 correlation_id，缺任一项不得声称成功。"
                            + "你可以删除同租户的研发交付记录：用户表达删除意图时先生成草案，用户发送精确确认指令后，"
                            + "由 semattice_project_delivery_delete 将记录移入回收站（30天可恢复），无需备份、归档或额外审批前置。"
                            + "你可以修改同租户研发交付记录的业务字段（负责人、状态、优先级、预估工时、描述等）：先基于对话生成修改草案，"
                            + "用户发送精确确认指令后，由 semattice_project_delivery_update 执行修改。结构字段（编号、父级引用、创建者、修订号）不允许修改。"
                            + "当用户说“把<开发者>的任务转交给<开发者>”时，必须由服务端解析当前租户有效的 Developer Profile；先展示仅含花名的排队任务转派草案，"
                            + "用户发送“确认将<开发者>的任务转交给<开发者>”后才可执行。不得向用户索取或展示内部 Principal ID；运行中、设计待确认、测试或发布中的任务不得转派。"
                            + "当交付事件存在 design_submitted 或 completion_requested 待评审项时，必须先核验实时任务、事件和证据，"
                            + "再调用 semattice_project_delivery_review 作出通过或要求修改的决定；不得跳过设计确认、阻塞清零或交付证据门禁。"
                            + "所有 Semattice 数据操作由本 Agent 显式绑定的 SERVICE Principal 执行；登录人只提供委托、确认或审批上下文。",
                    String.join(",",
                            "semattice_project_delivery_query",
                            "semattice_project_delivery_create",
                            "semattice_project_delivery_update",
                            "semattice_project_delivery_transfer",
                            "semattice_project_delivery_delete",
                            "semattice_project_delivery_review"),
                    null,
                    "创建、变更或其他写入动作必须获得明确的人类确认；人类负责人承担治理和问责，但不是唯一可调用人；评审不得由开发者自批；机器执行身份、责任人或权限不完整时失败关闭。",
                    "事实回答注明来自 Semattice 实时数据；创建与评审答复只有在收到实际记录或事件编号后才可标记成功。",
                    "HIGH"
            ),
            new BuiltinSkillSpec(
                    "web-search",
                    "Web 搜索",
                    "面向公开互联网的搜索与正文抽取能力，返回带 URL 的结构化来源。",
                    "When the user's question involves fresh public information, external facts, industry news, "
                            + "or topics unlikely to be covered by the tenant knowledge base, use the web-search skill:\n"
                            + "1) Call tavily_search first to discover candidate sources. Keep the query under 400 characters "
                            + "and use search-style phrasing (not a long prompt).\n"
                            + "   - If the question mentions '最新 / 今天 / 本周 / 今年' or other time signals, set time_range accordingly.\n"
                            + "   - For news-type questions set topic=news; for financial data set topic=finance.\n"
                            + "2) When the user needs the full body of a specific page (terms, data tables, long articles), call "
                            + "tavily_extract on the most relevant URLs with format=markdown.\n"
                            + "3) Prefer grounded answers: always cite 3~5 source links (title + URL) at the end of the reply. "
                            + "All citations must come from tavily_search / tavily_extract results.\n"
                            + "4) Do NOT send personal data, CRM customer records, or internal confidential text to Tavily — it is an external service.\n"
                            + "5) If tavily_search returns an error (e.g. TAVILY_NOT_CONFIGURED), fall back to tenant knowledge "
                            + "and tell the user that live web search is unavailable.",
                    String.join(",", "tavily_search", "tavily_extract"),
                    null,
                    null,
                    "答案末尾必须附 3~5 条可点击的来源链接（标题 + URL），来源均来自 tavily_search / tavily_extract。",
                    "LOW"
            )
    );

    private static final Map<String, List<DefaultBinding>> DEFAULT_AGENT_SKILLS = Map.of(
            "cici-system", List.of(
                    DefaultBinding.alwaysOn("conversation-core"),
                    DefaultBinding.alwaysOn("knowledge-first"),
                    DefaultBinding.alwaysOn("safe-handoff"),
                    DefaultBinding.alwaysOn("general-assistant"),
                    DefaultBinding.alwaysOn("crm-business-analysis"),
                    DefaultBinding.intentRoute("customer-interaction-workbench"),
                    DefaultBinding.intentRoute("ai-meeting-notetaker"),
                    DefaultBinding.intentRoute("web-search")
            ),
            "sales-agent", List.of(
                    DefaultBinding.alwaysOn("conversation-core"),
                    DefaultBinding.alwaysOn("knowledge-first"),
                    DefaultBinding.alwaysOn("safe-handoff"),
                    DefaultBinding.alwaysOn("sales-copilot"),
                    DefaultBinding.intentRoute("customer-interaction-workbench")
            ),
            "approval-agent", List.of(
                    DefaultBinding.alwaysOn("conversation-core"),
                    DefaultBinding.alwaysOn("knowledge-first"),
                    DefaultBinding.alwaysOn("safe-handoff"),
                    DefaultBinding.alwaysOn("approval-assistant")
            ),
            "dev-autopilot-pm", List.of(
                    DefaultBinding.alwaysOn("semattice-project-delivery-management")
            )
    );

    /** Default binding metadata for a builtin skill attached to an agent. */
    private record DefaultBinding(String skillCode, String activationMode) {
        static DefaultBinding alwaysOn(String code) {
            return new DefaultBinding(code, "always-on");
        }

        static DefaultBinding intentRoute(String code) {
            return new DefaultBinding(code, "intent-route");
        }
    }

    private final SkillDefinitionRepository skillDefinitionRepository;
    private final AgentSkillBindingRepository agentSkillBindingRepository;
    private final SkillPromptAssembler skillPromptAssembler;
    private final SkillVersionRepository skillVersionRepository;
    private final SpecCompilerService specCompilerService;
    private final ObjectMapper objectMapper;
    private final PlatformSkillTemplateRepository platformSkillTemplateRepository;
    private final AgentWorkflowSkillRefRepository agentWorkflowSkillRefRepository;
    private final SkillApiToolService skillApiToolService;
    private final FileBackedBuiltinSkillSyncService fileBackedBuiltinSkillSyncService;

    public SkillDefinitionService(SkillDefinitionRepository skillDefinitionRepository,
                                  AgentSkillBindingRepository agentSkillBindingRepository,
                                  SkillPromptAssembler skillPromptAssembler,
                                  SkillVersionRepository skillVersionRepository,
                                  SpecCompilerService specCompilerService,
                                  ObjectMapper objectMapper,
                                  PlatformSkillTemplateRepository platformSkillTemplateRepository,
                                  AgentWorkflowSkillRefRepository agentWorkflowSkillRefRepository,
                                  SkillApiToolService skillApiToolService,
                                  FileBackedBuiltinSkillSyncService fileBackedBuiltinSkillSyncService) {
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.agentSkillBindingRepository = agentSkillBindingRepository;
        this.skillPromptAssembler = skillPromptAssembler;
        this.skillVersionRepository = skillVersionRepository;
        this.specCompilerService = specCompilerService;
        this.objectMapper = objectMapper;
        this.platformSkillTemplateRepository = platformSkillTemplateRepository;
        this.agentWorkflowSkillRefRepository = agentWorkflowSkillRefRepository;
        this.skillApiToolService = skillApiToolService;
        this.fileBackedBuiltinSkillSyncService = fileBackedBuiltinSkillSyncService;
    }

    @Transactional
    public void ensurePhaseOneDefaults(String companyId) {
        ensureBuiltinSkills(companyId);
        fileBackedBuiltinSkillSyncService.syncOrg(companyId);
        ensureDefaultBindings(companyId);
    }

    public List<SkillDefinitionEntity> listSkills(String companyId) {
        ensurePhaseOneDefaults(companyId);
        return skillDefinitionRepository.findByCompanyIdOrderByBuiltinDescNameAsc(companyId).stream()
                .filter(SkillDefinitionEntity::isVisibleToTenant)
                .toList();
    }

    /**
     * Materializes an immutable published snapshot for a platform standard skill before an Agent
     * workflow pins it. Legacy built-ins predate skill versioning and otherwise resolve to an
     * empty fail-closed runtime reference.
     */
    @Transactional
    public Long ensurePublishedPlatformSkillVersion(String companyId, String requestedSkillCode) {
        ensurePhaseOneDefaults(companyId);
        String skillCode = normalizeSkillCode(requestedSkillCode);
        SkillDefinitionEntity skill = skillDefinitionRepository.findByCompanyIdAndSkillCode(companyId, skillCode)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillCode));
        if (skill.getSourceType() != SkillSourceType.PLATFORM_STANDARD) {
            throw new IllegalArgumentException("Only platform standard skills can be materialized automatically");
        }
        BuiltinSkillSpec builtin = BUILTIN_SKILLS.stream()
                .filter(spec -> spec.skillCode().equals(skillCode))
                .findFirst()
                .orElse(null);
        if (builtin != null && !matchesBuiltinDefinition(skill, builtin)) {
            skill.update(
                    skillCode,
                    builtin.name(),
                    builtin.description(),
                    skill.isEnabled(),
                    builtin.promptFragment(),
                    builtin.draftSpecText(),
                    builtin.toolWhitelist(),
                    builtin.kbWhitelist(),
                    builtin.handoffRule(),
                    builtin.outputContract(),
                    skill.getRuntimeApiDraftJson(),
                    builtin.riskLevel()
            );
            skill = skillDefinitionRepository.save(skill);
        }
        Optional<SkillVersionEntity> published = skill.getCurrentPublishedVersionId() == null
                ? Optional.empty()
                : skillVersionRepository.findByIdAndCompanyId(skill.getCurrentPublishedVersionId(), companyId)
                        .filter(version -> "PUBLISHED".equalsIgnoreCase(version.getPublishStatus()));
        if (published.isEmpty()) {
            published = skillVersionRepository.findTopByCompanyIdAndSkillIdAndPublishStatusOrderByVersionNoDesc(
                    companyId, skill.getId(), "PUBLISHED");
        }
        if (published.isPresent() && matchesPublishedSnapshot(skill, published.get())) {
            if (!java.util.Objects.equals(skill.getCurrentPublishedVersionId(), published.get().getId())) {
                skill.markPublished(published.get().getId(), "system");
                skillDefinitionRepository.save(skill);
            }
            return published.get().getId();
        }

        UpsertCommand snapshot = UpsertCommand.fromEntity(skill, "初始化平台标准技能运行时快照", "system");
        SkillVersionEntity created = createDraftVersion(companyId, skill, snapshot, "PUBLISH", null);
        created.markPublished();
        skillVersionRepository.save(created);
        skillApiToolService.publishApisForVersion(companyId, skill, created, created.getRuntimeApiSnapshotJson());
        skill.markPublished(created.getId(), "system");
        skillDefinitionRepository.save(skill);
        return created.getId();
    }

    private boolean matchesBuiltinDefinition(SkillDefinitionEntity skill, BuiltinSkillSpec builtin) {
        return Objects.equals(skill.getName(), builtin.name())
                && Objects.equals(skill.getDescription(), builtin.description())
                && Objects.equals(skill.getPromptFragment(), builtin.promptFragment())
                && Objects.equals(skill.getDraftSpecText(), builtin.draftSpecText())
                && Objects.equals(skill.getToolWhitelist(), builtin.toolWhitelist())
                && Objects.equals(skill.getKbWhitelist(), builtin.kbWhitelist())
                && Objects.equals(skill.getHandoffRule(), builtin.handoffRule())
                && Objects.equals(skill.getOutputContract(), builtin.outputContract())
                && Objects.equals(skill.getRiskLevel(), builtin.riskLevel());
    }

    private boolean matchesPublishedSnapshot(SkillDefinitionEntity skill, SkillVersionEntity published) {
        return Objects.equals(trimToNull(published.getSpecText()), trimToNull(skill.getDraftSpecText()))
                && Objects.equals(trimToNull(published.getCompiledPromptFragment()), trimToNull(skill.getPromptFragment()))
                && Objects.equals(trimToNull(published.getEffectiveToolWhitelist()), trimToNull(skill.getToolWhitelist()))
                && Objects.equals(trimToNull(published.getEffectiveKbWhitelist()), trimToNull(skill.getKbWhitelist()))
                && Objects.equals(normalizeRiskLevel(published.getRiskLevel()), normalizeRiskLevel(skill.getRiskLevel()))
                && Objects.equals(trimToNull(published.getRuntimeApiSnapshotJson()), trimToNull(skill.getRuntimeApiDraftJson()));
    }

    public SkillDefinitionEntity getSkill(String companyId, Long id) {
        ensurePhaseOneDefaults(companyId);
        return skillDefinitionRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
    }

    public List<SkillDefinitionEntity> listSkillsForAgent(String companyId, String agentId) {
        ensurePhaseOneDefaults(companyId);
        List<AgentSkillBindingEntity> bindings = listBindingsInternal(companyId, agentId);
        List<Long> skillIds = bindings.stream().map(AgentSkillBindingEntity::getSkillId).toList();
        if (skillIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SkillDefinitionEntity> byId = skillDefinitionRepository.findByCompanyIdAndIdInAndEnabledTrue(companyId, skillIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(SkillDefinitionEntity::getId, item -> item));
        return skillIds.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .filter(SkillDefinitionEntity::isVisibleToTenant)
                .toList();
    }

    @Transactional
    public SkillDefinitionEntity createSkill(String companyId, UpsertCommand command) {
        ensurePhaseOneDefaults(companyId);
        String skillCode = normalizeSkillCode(command.skillCode());
        Optional<SkillDefinitionEntity> existing = skillDefinitionRepository.findByCompanyIdAndSkillCode(companyId, skillCode);
        if (existing.isPresent() && "DELETED".equals(existing.get().getLifecycleStatus())) {
            existing.get().archiveDeletedSkillCode();
            skillDefinitionRepository.saveAndFlush(existing.get());
        } else if (existing.isPresent()) {
            throw new IllegalArgumentException("Skill code already exists: " + skillCode);
        }
        SkillDefinitionEntity created = new SkillDefinitionEntity(
                companyId,
                skillCode,
                requireText(command.name(), "name"),
                trimToNull(command.description()),
                false,
                command.enabled() == null || command.enabled(),
                trimToNull(command.promptFragment()),
                resolveDraftSpecText(command.draftSpecText(), command.promptFragment()),
                joinCsv(command.toolWhitelist()),
                joinCsv(command.kbWhitelist()),
                trimToNull(command.handoffRule()),
                trimToNull(command.outputContract()),
                normalizeRiskLevel(command.riskLevel()),
                SkillSourceType.TENANT_CUSTOM,
                SkillVisibility.VISIBLE,
                SkillEditPolicy.EDITABLE,
                SkillBindingPolicy.OPTIONAL,
                SkillUpdatePolicy.MANUAL,
                null,
                null
        );
        created.setRuntimeApiDraftJson(skillApiToolService.serializeDraftApis(command.runtimeApis()));
        SkillDefinitionEntity saved = skillDefinitionRepository.save(created);
        SkillVersionEntity draft = createDraftVersion(companyId, saved, command, "CREATE", null);
        saved.markDraft(draft.getId());
        return skillDefinitionRepository.save(saved);
    }

    @Transactional
    public SkillDefinitionEntity updateSkill(String companyId, Long id, UpsertCommand command) {
        ensurePhaseOneDefaults(companyId);
        SkillDefinitionEntity entity = getSkill(companyId, id);
        if (!entity.isVisibleToTenant()) {
            throw new IllegalArgumentException("Skill not found");
        }
        if (entity.isTenantConfigurable()) {
            entity.setEnabled(command.enabled() == null || command.enabled());
            return skillDefinitionRepository.save(entity);
        }
        if (!entity.isTenantEditable()) {
            throw new IllegalArgumentException("Skill is platform managed and cannot be edited");
        }

        String requestedCode = normalizeSkillCode(command.skillCode());
        if (!requestedCode.equals(entity.getSkillCode())
                && skillDefinitionRepository.existsByCompanyIdAndSkillCodeAndIdNot(companyId, requestedCode, id)) {
            throw new IllegalArgumentException("Skill code already exists: " + requestedCode);
        }

        entity.update(
                requestedCode,
                requireText(command.name(), "name"),
                trimToNull(command.description()),
                command.enabled() == null || command.enabled(),
                trimToNull(command.promptFragment()),
                resolveDraftSpecText(command.draftSpecText(), command.promptFragment()),
                joinCsv(command.toolWhitelist()),
                joinCsv(command.kbWhitelist()),
                trimToNull(command.handoffRule()),
                trimToNull(command.outputContract()),
                skillApiToolService.serializeDraftApis(command.runtimeApis()),
                normalizeRiskLevel(command.riskLevel())
        );
        SkillDefinitionEntity saved = skillDefinitionRepository.save(entity);
        SkillVersionEntity draft = createDraftVersion(companyId, saved, command, "SAVE", null);
        saved.markDraft(draft.getId());
        return skillDefinitionRepository.save(saved);
    }

    @Transactional
    public DeleteImpact deleteImpact(String companyId, Long id) {
        ensurePhaseOneDefaults(companyId);
        SkillDefinitionEntity entity = getSkill(companyId, id);
        boolean bound = agentSkillBindingRepository.findByCompanyIdAndSkillIdInAndEnabledTrue(companyId, List.of(id)).stream()
                .anyMatch(AgentSkillBindingEntity::isEnabled);
        boolean pinned = agentWorkflowSkillRefRepository.countActivePublishedRuntimeByCompanyIdAndSkillId(companyId, id) > 0;
        List<String> blockers = new ArrayList<>();
        if (!entity.isTenantDeletable()) {
            blockers.add("仅租户自定义技能可以删除");
        }
        if (bound) {
            blockers.add("仍有 Agent 绑定该技能");
        }
        if (pinned) {
            blockers.add("仍有已发布运行时版本引用该技能");
        }
        return new DeleteImpact(entity.getId(), entity.getSkillCode(), entity.getName(), entity.getSourceType().name(),
                entity.getEditPolicy().name(), entity.isTenantDeletable() && blockers.isEmpty(), bound, pinned, blockers);
    }

    @Transactional
    public void deleteSkill(String companyId, Long id, String deletedBy, String reason) {
        DeleteImpact impact = deleteImpact(companyId, id);
        if (!impact.canDelete()) {
            throw new IllegalArgumentException("Skill cannot be deleted: " + String.join("; ", impact.blockers()));
        }
        SkillDefinitionEntity entity = getSkill(companyId, id);
        entity.markDeleted(fallback(trimToNull(deletedBy), "system"), trimToNull(reason));
        skillDefinitionRepository.save(entity);
    }

    @Transactional
    public SkillDefinitionEntity publishSkill(String companyId, Long id, PublishCommand command) {
        ensurePhaseOneDefaults(companyId);
        SkillDefinitionEntity entity = getSkill(companyId, id);
        if (!entity.isVisibleToTenant() || entity.getSourceType() != SkillSourceType.TENANT_CUSTOM || !entity.isTenantEditable()) {
            throw new IllegalArgumentException("Only tenant custom editable skills can be published");
        }
        PreviewResult preview = previewCompile(companyId, new PreviewCommand(
                entity.getSkillCode(),
                entity.getName(),
                entity.getDraftSpecText(),
                entity.getPromptFragment(),
                splitCsv(entity.getToolWhitelist()),
                splitCsv(entity.getKbWhitelist()),
                entity.getHandoffRule(),
                entity.getOutputContract(),
                skillApiToolService.readDraftApis(entity.getRuntimeApiDraftJson()),
                entity.getRiskLevel()
        ));
        if (preview.warnings().stream().anyMatch(item -> item.toLowerCase().contains("阻断"))) {
            throw new IllegalArgumentException("Skill has blocking compile warnings");
        }
        UpsertCommand snapshot = UpsertCommand.fromEntity(entity, command == null ? null : command.changeLog(),
                command == null ? null : command.actorUserId());
        SkillVersionEntity published = createDraftVersion(companyId, entity, snapshot, "PUBLISH", null);
        published.markPublished();
        skillVersionRepository.save(published);
        skillApiToolService.publishApisForVersion(companyId, entity, published, published.getRuntimeApiSnapshotJson());
        entity.markPublished(published.getId(), command == null ? "system" : fallback(trimToNull(command.actorUserId()), "system"));
        return skillDefinitionRepository.save(entity);
    }

    @Transactional
    public SkillDefinitionEntity restoreVersion(String companyId, Long id, Long versionId, RestoreCommand command) {
        ensurePhaseOneDefaults(companyId);
        SkillDefinitionEntity entity = getSkill(companyId, id);
        if (entity.getSourceType() != SkillSourceType.TENANT_CUSTOM || !entity.isTenantEditable()) {
            throw new IllegalArgumentException("Only tenant custom editable skills can restore versions");
        }
        SkillVersionEntity source = skillVersionRepository.findById(versionId)
                .filter(item -> companyId.equals(item.getCompanyId()) && id.equals(item.getSkillId()))
                .filter(item -> Boolean.TRUE.equals(item.getRestoreVisible()))
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        entity.update(
                entity.getSkillCode(),
                entity.getName(),
                entity.getDescription(),
                entity.isEnabled(),
                source.getCompiledPromptFragment(),
                source.getSpecText(),
                source.getEffectiveToolWhitelist(),
                source.getEffectiveKbWhitelist(),
                entity.getHandoffRule(),
                entity.getOutputContract(),
                source.getRuntimeApiSnapshotJson(),
                source.getRiskLevel()
        );
        SkillDefinitionEntity saved = skillDefinitionRepository.save(entity);
        UpsertCommand snapshot = UpsertCommand.fromEntity(saved,
                command == null ? "恢复自 v" + source.getVersionNo() : command.changeLog(),
                command == null ? null : command.actorUserId());
        SkillVersionEntity restored = createDraftVersion(companyId, saved, snapshot, "RESTORE", source.getId());
        saved.markDraft(restored.getId());
        return skillDefinitionRepository.save(saved);
    }

    public List<SkillVersionEntity> listRestoreVersions(String companyId, Long skillId, int limit) {
        ensurePhaseOneDefaults(companyId);
        SkillDefinitionEntity skill = getSkill(companyId, skillId);
        if (!skill.isVisibleToTenant()) {
            throw new IllegalArgumentException("Skill not found");
        }
        return skillVersionRepository.findByCompanyIdAndSkillIdAndRestoreVisibleTrueOrderByVersionNoDesc(companyId, skillId).stream()
                .limit(Math.max(1, Math.min(limit, 20)))
                .toList();
    }

    @Transactional
    public SkillDefinitionEntity deriveSkill(String companyId, Long sourceSkillId, DeriveCommand command) {
        ensurePhaseOneDefaults(companyId);
        throw new IllegalArgumentException("Skill derivation is hidden in this release");
        /*
        SkillDefinitionEntity source = getSkill(companyId, sourceSkillId);
        if (!source.isVisibleToTenant() || source.getSourceType() != SkillSourceType.PLATFORM_STANDARD) {
            throw new IllegalArgumentException("Only platform standard skills can be derived");
        }
        String skillCode = normalizeSkillCode(command.skillCode());
        if (skillDefinitionRepository.existsByCompanyIdAndSkillCode(companyId, skillCode)) {
            throw new IllegalArgumentException("Skill code already exists: " + skillCode);
        }
        Integer baseTemplateVersion = platformSkillTemplateRepository.findByCompanyIdAndTemplateCode(
                        companyId,
                        fallback(source.getTemplateCode(), source.getSkillCode())
                )
                .map(template -> template.getCurrentVersionNo() == null ? 1 : template.getCurrentVersionNo())
                .or(() -> Optional.ofNullable(source.getCurrentPublishedVersionId())
                        .flatMap(skillVersionRepository::findById)
                        .map(SkillVersionEntity::getVersionNo))
                .or(() -> skillVersionRepository.findTopByCompanyIdAndSkillIdOrderByVersionNoDesc(companyId, source.getId())
                        .map(SkillVersionEntity::getVersionNo))
                .orElse(1);
        SkillDefinitionEntity derived = new SkillDefinitionEntity(
                companyId,
                skillCode,
                requireText(command.name(), "name"),
                trimToNull(fallback(command.description(), source.getDescription())),
                false,
                true,
                source.getPromptFragment(),
                source.getDraftSpecText(),
                source.getToolWhitelist(),
                source.getKbWhitelist(),
                source.getHandoffRule(),
                source.getOutputContract(),
                source.getRiskLevel(),
                SkillSourceType.TENANT_DERIVED,
                SkillVisibility.VISIBLE,
                SkillEditPolicy.EDITABLE,
                SkillBindingPolicy.OPTIONAL,
                SkillUpdatePolicy.MANUAL,
                fallback(source.getTemplateCode(), source.getSkillCode()),
                baseTemplateVersion
        );
        SkillDefinitionEntity saved = skillDefinitionRepository.save(derived);
        SkillVersionEntity draft = createDraftVersion(companyId, saved, new UpsertCommand(
                saved.getSkillCode(),
                saved.getName(),
                saved.getDescription(),
                saved.isEnabled(),
                saved.getPromptFragment(),
                saved.getDraftSpecText(),
                splitCsv(saved.getToolWhitelist()),
                splitCsv(saved.getKbWhitelist()),
                saved.getHandoffRule(),
                saved.getOutputContract(),
                saved.getRiskLevel(),
                "derive",
                null,
                "derivedFrom=" + source.getSkillCode() + "@v" + baseTemplateVersion,
                "创建派生技能",
                "system"
        ), "DERIVE", null);
        saved.markDraft(draft.getId());
        return skillDefinitionRepository.save(saved);
        */
    }

    public List<AgentSkillBindingEntity> listBindings(String companyId, String agentId) {
        ensurePhaseOneDefaults(companyId);
        return listBindingsInternal(companyId, agentId);
    }

    @Transactional
    public List<AgentSkillBindingEntity> replaceBindings(String companyId, String requestedAgentId, List<BindingInput> inputs) {
        ensurePhaseOneDefaults(companyId);
        String agentId = normalizeAgentId(requestedAgentId);
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("bindings cannot be empty");
        }

        Map<Long, SkillDefinitionEntity> skillById = skillDefinitionRepository.findByCompanyIdOrderByBuiltinDescNameAsc(companyId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(SkillDefinitionEntity::getId, item -> item));
        List<AgentSkillBindingEntity> next = new ArrayList<>();
        LinkedHashSet<Long> seenSkillIds = new LinkedHashSet<>();

        int fallbackPriority = 10;
        for (BindingInput input : inputs) {
            Long skillId = resolveSkillId(companyId, input, skillById);
            if (!seenSkillIds.add(skillId)) {
                throw new IllegalArgumentException("duplicate skill binding: " + skillId);
            }
            SkillDefinitionEntity skill = skillById.get(skillId);
            if (skill == null || !skill.isEnabled()) {
                throw new IllegalArgumentException("skill is not available: " + skillId);
            }
            int priority = input.priority() == null ? fallbackPriority : input.priority();
            fallbackPriority += 10;
            next.add(new AgentSkillBindingEntity(
                    companyId,
                    agentId,
                    skillId,
                    normalizeActivationMode(input.activationMode()),
                    trimToNull(input.activationCondition()),
                    priority,
                    input.enabled() == null || input.enabled()
            ));
        }

        agentSkillBindingRepository.deleteByCompanyIdAndAgentId(companyId, agentId);
        agentSkillBindingRepository.flush();
        return agentSkillBindingRepository.saveAll(next);
    }

    public PreviewResult previewCompile(String companyId, PreviewCommand command) {
        ensurePhaseOneDefaults(companyId);
        String riskLevel = normalizeRiskLevel(command.riskLevel());
        List<String> tools = normalizeNameList(command.toolWhitelist());
        List<String> kbIds = normalizeNameList(command.kbWhitelist());
        String specText = resolveDraftSpecText(command.specText(), command.promptFragment());
        String promptFragment = trimToNull(command.promptFragment());
        String handoffRule = trimToNull(command.handoffRule());
        String outputContract = trimToNull(command.outputContract());
        SpecCompilerService.SpecCompilation compiled = specCompilerService.compile(new SpecCompilerService.SpecCompileCommand(
                "skill-policy",
                fallback(command.name(), "Skill Preview"),
                specText,
                tools,
                kbIds,
                handoffRule,
                riskLevel
        ));
        List<String> warnings = new ArrayList<>(compiled.warnings());
        if (promptFragment == null || promptFragment.length() < 30) {
            warnings.add("promptFragment 偏短，建议补充触发条件与响应策略。");
        }

        SkillResolverService.ResolvedSkillContext context = new SkillResolverService.ResolvedSkillContext(
                "preview-agent",
                List.of(new SkillResolverService.ResolvedSkill(
                        normalizeSkillCode(command.skillCode()),
                        fallback(command.name(), "Skill Preview"),
                        promptFragment,
                        tools,
                        kbIds,
                        handoffRule,
                        outputContract,
                        riskLevel,
                        "always-on"
                )),
                List.of(normalizeSkillCode(command.skillCode())),
                tools,
                List.of(),
                tools,
                tools,
                kbIds,
                handoffRule == null ? List.of() : List.of(handoffRule),
                outputContract,
                null,
                null,
                normalizeSkillCode(command.skillCode()),
                null,
                null,
                List.of(new SkillResolverService.ResolvedSkillVersionRef(
                        normalizeSkillCode(command.skillCode()),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "always-on"
                )),
                List.of(),
                SkillResolverService.ResolvedPolicyBundle.EMPTY
        );
        String promptPreview = skillPromptAssembler.assemble(
                "You are CiCi assistant. Follow platform policy and answer safely.",
                context
        );
        List<String> compileSummary = new ArrayList<>(compiled.compileSummary());
        compileSummary.add("skillCode=" + normalizeSkillCode(command.skillCode()) + ", riskLevel=" + riskLevel);
        String runtimeApiJson = skillApiToolService.serializeDraftApis(command.runtimeApis());
        SkillApiToolService.RuntimeApiCompilePreview apiPreview =
                skillApiToolService.previewCompileApis(companyId, normalizeSkillCode(command.skillCode()), runtimeApiJson);
        warnings.addAll(apiPreview.warnings());
        apiPreview.errors().forEach(error -> warnings.add("阻断: " + error));
        compileSummary.add("runtimeApis=" + apiPreview.toolDefinitions().size());
        return new PreviewResult(
                promptPreview,
                tools,
                kbIds,
                riskLevel,
                warnings,
                compileSummary,
                toPolicyJson(compiled.specIr()),
                apiPreview
        );
    }

    public String normalizeAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return "cici-system";
        }
        String trimmed = agentId.trim();
        if ("cici".equalsIgnoreCase(trimmed) || "cici-default".equalsIgnoreCase(trimmed)) {
            return "cici-system";
        }
        return trimmed;
    }

    private List<AgentSkillBindingEntity> listBindingsInternal(String companyId, String agentId) {
        return agentSkillBindingRepository.findByCompanyIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(
                companyId, normalizeAgentId(agentId)
        );
    }

    private Long resolveSkillId(String companyId, BindingInput input, Map<Long, SkillDefinitionEntity> skillById) {
        if (input.skillId() != null) {
            return input.skillId();
        }
        if (input.skillCode() == null || input.skillCode().isBlank()) {
            throw new IllegalArgumentException("skillId or skillCode is required");
        }
        String code = normalizeSkillCode(input.skillCode());
        return skillDefinitionRepository.findByCompanyIdAndSkillCode(companyId, code)
                .map(SkillDefinitionEntity::getId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found for code: " + code));
    }

    private String normalizeActivationMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "always-on";
        }
        String normalized = mode.trim().toLowerCase();
        if (!List.of("always-on", "intent-route", "manual").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported activationMode: " + mode);
        }
        return normalized;
    }

    private String normalizeSkillCode(String code) {
        String normalized = fallback(code, "").trim().toLowerCase();
        if (!normalized.matches("[a-z0-9][a-z0-9-_]{1,63}")) {
            throw new IllegalArgumentException("Invalid skillCode format");
        }
        return normalized;
    }

    private String normalizeRiskLevel(String riskLevel) {
        String normalized = fallback(riskLevel, "MEDIUM").trim().toUpperCase();
        if (!List.of("LOW", "MEDIUM", "HIGH").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported riskLevel: " + riskLevel);
        }
        return normalized;
    }

    private List<String> normalizeNameList(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : names) {
            String cleaned = trimToNull(item);
            if (cleaned != null) {
                values.add(cleaned);
            }
        }
        return List.copyOf(values);
    }

    private String joinCsv(List<String> names) {
        List<String> normalized = normalizeNameList(names);
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    private String requireText(String value, String field) {
        String cleaned = trimToNull(value);
        if (cleaned == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return cleaned;
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String fallback(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String resolveDraftSpecText(String draftSpecText, String promptFragment) {
        String draft = trimToNull(draftSpecText);
        if (draft != null) {
            return draft;
        }
        return trimToNull(promptFragment);
    }

    private SkillVersionEntity createDraftVersion(String companyId,
                                                  SkillDefinitionEntity skill,
                                                  UpsertCommand command,
                                                  String versionSource,
                                                  Long restoredFromVersionId) {
        Integer nextVersionNo = skillVersionRepository.findTopByCompanyIdAndSkillIdOrderByVersionNoDesc(companyId, skill.getId())
                .map(existing -> existing.getVersionNo() + 1)
                .orElse(1);
        List<String> tools = normalizeNameList(command.toolWhitelist());
        List<String> kbIds = normalizeNameList(command.kbWhitelist());
        String riskLevel = normalizeRiskLevel(command.riskLevel());
        String specText = resolveDraftSpecText(command.draftSpecText(), command.promptFragment());
        SpecCompilerService.SpecCompilation compiled = specCompilerService.compile(new SpecCompilerService.SpecCompileCommand(
                "skill-policy",
                skill.getName(),
                specText,
                tools,
                kbIds,
                command.handoffRule(),
                riskLevel
        ));
        SkillVersionEntity saved = skillVersionRepository.save(new SkillVersionEntity(
                companyId,
                skill.getId(),
                nextVersionNo,
                specText,
                "policy",
                fallback(trimToNull(command.sourceType()), "manual"),
                trimToNull(command.specIrJson()),
                trimToNull(command.authoringNotes()),
                trimToNull(command.promptFragment()),
                toPolicyJson(compiled.specIr()),
                joinCsv(tools),
                joinCsv(kbIds),
                riskLevel,
                String.join("\n", compiled.compileSummary()),
                String.join("\n", compiled.warnings()),
                "DRAFT"
        ));
        saved.setRuntimeApiSnapshotJson(skill.getRuntimeApiDraftJson());
        saved.applyGovernance(
                fallback(trimToNull(command.changeLog()), defaultChangeLog(versionSource)),
                String.join("\n", buildDiffSummary(skill, tools, kbIds, riskLevel, versionSource)),
                versionSource,
                fallback(trimToNull(command.actorUserId()), "system"),
                true,
                "ACTIVE_RECENT",
                restoredFromVersionId,
                null
        );
        SkillVersionEntity version = skillVersionRepository.save(saved);
        pruneRestoreHistory(companyId, skill.getId());
        return version;
    }

    private void pruneRestoreHistory(String companyId, Long skillId) {
        List<SkillVersionEntity> versions = skillVersionRepository
                .findByCompanyIdAndSkillIdAndRestoreVisibleTrueOrderByVersionNoDesc(companyId, skillId);
        for (int i = 3; i < versions.size(); i++) {
            SkillVersionEntity version = versions.get(i);
            boolean protectedRuntime = agentWorkflowSkillRefRepository.existsByCompanyIdAndSkillVersionId(companyId, version.getId());
            version.markRetention(protectedRuntime ? "PROTECTED_RUNTIME" : "PRUNED", false);
            skillVersionRepository.save(version);
        }
    }

    private List<String> buildDiffSummary(SkillDefinitionEntity skill,
                                          List<String> tools,
                                          List<String> kbIds,
                                          String riskLevel,
                                          String versionSource) {
        List<String> summary = new ArrayList<>();
        summary.add("来源动作：" + versionSource);
        summary.add("工具白名单：" + tools.size() + " 项");
        summary.add("知识库白名单：" + kbIds.size() + " 项");
        summary.add("风险等级：" + riskLevel);
        summary.add("技能：" + skill.getSkillCode());
        return summary;
    }

    private String defaultChangeLog(String versionSource) {
        return switch (fallback(versionSource, "SAVE")) {
            case "CREATE" -> "创建技能草稿";
            case "PUBLISH" -> "发布技能版本";
            case "RESTORE" -> "恢复历史版本";
            case "IMPORT" -> "导入技能包";
            default -> "保存技能配置";
        };
    }

    private String toPolicyJson(SpecCompilerService.SpecIr specIr) {
        try {
            return objectMapper.writeValueAsString(specIr);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void ensureBuiltinSkills(String companyId) {
        for (BuiltinSkillSpec spec : BUILTIN_SKILLS) {
            Optional<SkillDefinitionEntity> existing = skillDefinitionRepository.findByCompanyIdAndSkillCode(companyId, spec.skillCode());
            if (existing.isPresent()) {
                continue;
            }
            skillDefinitionRepository.save(new SkillDefinitionEntity(
                    companyId,
                    spec.skillCode(),
                    spec.name(),
                    spec.description(),
                    true,
                    true,
                    spec.promptFragment(),
                    spec.draftSpecText(),
                    spec.toolWhitelist(),
                    spec.kbWhitelist(),
                    spec.handoffRule(),
                    spec.outputContract(),
                    spec.riskLevel(),
                    SkillSourceType.PLATFORM_STANDARD,
                    visibilityForBuiltin(spec.skillCode()),
                    editPolicyForBuiltin(spec.skillCode()),
                    bindingPolicyForBuiltin(spec.skillCode()),
                    SkillUpdatePolicy.AUTO,
                    spec.skillCode(),
                    null
            ));
        }
    }

    private SkillVisibility visibilityForBuiltin(String skillCode) {
        return CORE_POLICY_CODES.contains(skillCode) ? SkillVisibility.HIDDEN : SkillVisibility.VISIBLE;
    }

    private SkillEditPolicy editPolicyForBuiltin(String skillCode) {
        return CORE_POLICY_CODES.contains(skillCode) ? SkillEditPolicy.LOCKED : SkillEditPolicy.CONFIGURABLE;
    }

    private SkillBindingPolicy bindingPolicyForBuiltin(String skillCode) {
        return CORE_POLICY_CODES.contains(skillCode) ? SkillBindingPolicy.MANDATORY : SkillBindingPolicy.OPTIONAL;
    }

    private List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private void ensureDefaultBindings(String companyId) {
        Map<String, SkillDefinitionEntity> skillByCode = new LinkedHashMap<>();
        for (SkillDefinitionEntity entity : skillDefinitionRepository.findByCompanyIdAndEnabledTrueOrderByBuiltinDescNameAsc(companyId)) {
            skillByCode.put(entity.getSkillCode(), entity);
        }
        for (Map.Entry<String, List<DefaultBinding>> entry : DEFAULT_AGENT_SKILLS.entrySet()) {
            String agentId = entry.getKey();
            List<DefaultBinding> bindings = entry.getValue();
            for (int index = 0; index < bindings.size(); index++) {
                DefaultBinding binding = bindings.get(index);
                SkillDefinitionEntity skill = skillByCode.get(binding.skillCode());
                if (skill == null) {
                    continue;
                }
                if (agentSkillBindingRepository.existsByCompanyIdAndAgentIdAndSkillId(companyId, agentId, skill.getId())) {
                    continue;
                }
                agentSkillBindingRepository.save(new AgentSkillBindingEntity(
                        companyId,
                        agentId,
                        skill.getId(),
                        binding.activationMode(),
                        null,
                        (index + 1) * 10,
                        true
                ));
            }
        }
    }

    public record UpsertCommand(
            String skillCode,
            String name,
            String description,
            Boolean enabled,
            String promptFragment,
            String draftSpecText,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule,
            String outputContract,
            List<Map<String, Object>> runtimeApis,
            String riskLevel,
            String sourceType,
            String specIrJson,
            String authoringNotes,
            String changeLog,
            String actorUserId
    ) {
        static UpsertCommand fromEntity(SkillDefinitionEntity entity, String changeLog, String actorUserId) {
            return new UpsertCommand(
                    entity.getSkillCode(),
                    entity.getName(),
                    entity.getDescription(),
                    entity.isEnabled(),
                    entity.getPromptFragment(),
                    entity.getDraftSpecText(),
                    entity.getToolWhitelist() == null ? List.of() : java.util.Arrays.stream(entity.getToolWhitelist().split(",")).map(String::trim).filter(item -> !item.isBlank()).toList(),
                    entity.getKbWhitelist() == null ? List.of() : java.util.Arrays.stream(entity.getKbWhitelist().split(",")).map(String::trim).filter(item -> !item.isBlank()).toList(),
                    entity.getHandoffRule(),
                    entity.getOutputContract(),
                    List.of(),
                    entity.getRiskLevel(),
                    "manual",
                    null,
                    null,
                    changeLog,
                    actorUserId
            );
        }
    }

    public record BindingInput(
            Long skillId,
            String skillCode,
            String activationMode,
            String activationCondition,
            Integer priority,
            Boolean enabled
    ) {
    }

    public record PreviewCommand(
            String skillCode,
            String name,
            String specText,
            String promptFragment,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule,
            String outputContract,
            List<Map<String, Object>> runtimeApis,
            String riskLevel
    ) {
    }

    public record PreviewResult(
            String promptPreview,
            List<String> effectiveToolNames,
            List<String> effectiveKnowledgeBaseIds,
            String riskLevel,
            List<String> warnings,
            List<String> compileSummary,
            String specIr,
            SkillApiToolService.RuntimeApiCompilePreview runtimeApiPreview
    ) {
    }

    public record DeriveCommand(
            String skillCode,
            String name,
            String description
    ) {
    }

    public record PublishCommand(String changeLog, String actorUserId) {
    }

    public record RestoreCommand(String changeLog, String actorUserId) {
    }

    public record DeleteImpact(
            Long skillId,
            String skillCode,
            String name,
            String sourceType,
            String editPolicy,
            boolean canDelete,
            boolean hasActiveBindings,
            boolean hasRuntimePins,
            List<String> blockers
    ) {
    }

    private record BuiltinSkillSpec(
            String skillCode,
            String name,
            String description,
            String promptFragment,
            String draftSpecText,
            String toolWhitelist,
            String kbWhitelist,
            String handoffRule,
            String outputContract,
            String riskLevel
    ) {
        private BuiltinSkillSpec(String skillCode,
                                 String name,
                                 String description,
                                 String promptFragment,
                                 String toolWhitelist,
                                 String kbWhitelist,
                                 String handoffRule,
                                 String outputContract,
                                 String riskLevel) {
            this(skillCode, name, description, promptFragment, promptFragment, toolWhitelist, kbWhitelist,
                    handoffRule, outputContract, riskLevel);
        }
    }
}
