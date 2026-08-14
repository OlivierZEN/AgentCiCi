package com.codehouse.ciciassistant.tool.service;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.crmanalysis.service.CrmProductSalesAnalysisToolService;
import com.codehouse.ciciassistant.email.service.EmailToolService;
import com.codehouse.ciciassistant.semattice.SematticeProjectDeliveryDeleteToolService;
import com.codehouse.ciciassistant.semattice.SematticeProjectDeliveryToolService;
import com.codehouse.ciciassistant.semattice.SematticeProjectDeliveryUpdateToolService;
import com.codehouse.ciciassistant.semattice.SematticeProjectDeliveryTransferToolService;
import com.codehouse.ciciassistant.semattice.SematticeProjectDeliveryReviewToolService;
import com.codehouse.ciciassistant.semattice.SematticeProjectDeliveryWriteToolService;
import com.codehouse.ciciassistant.tool.codeinterpreter.SandboxCodeInterpreterService;
import com.codehouse.ciciassistant.tool.managedweb.ManagedWebToolService;
import com.codehouse.ciciassistant.tool.tavily.TavilyToolService;
import com.codehouse.ciciassistant.userworkflow.service.AssistantScheduleToolService;
import java.util.List;

/**
 * Shared builtin tool catalog used by admin listings and hidden authoring flows.
 */
public final class BuiltinToolCatalog {

    private static final List<ToolCatalogItem> BUILTIN_TOOLS = List.of(
            new ToolCatalogItem("rag-search", "企业知识检索", "从已授权知识库检索答案片段。", "低风险", "knowledge"),
            new ToolCatalogItem(CloudccOpenApiService.toolName(), "CloudCC 分页查询",
                    CloudccOpenApiService.toolDescription(), "中风险", "crm"),
            new ToolCatalogItem(CloudccOpenApiService.toolNameGetStandardObjects(), "CloudCC 标准对象",
                    CloudccOpenApiService.toolDescriptionGetStandardObjects(), "低风险", "crm"),
            new ToolCatalogItem(CloudccOpenApiService.toolNameGetCustomObjects(), "CloudCC 自定义对象",
                    CloudccOpenApiService.toolDescriptionGetCustomObjects(), "低风险", "crm"),
            new ToolCatalogItem(CloudccOpenApiService.toolNameGetObjectFields(), "CloudCC 对象字段",
                    CloudccOpenApiService.toolDescriptionGetObjectFields(), "低风险", "crm"),
            new ToolCatalogItem(CrmProductSalesAnalysisToolService.TOOL_NAME, "CRM 产品销售排行",
                    CrmProductSalesAnalysisToolService.toolDescription(), "低风险", "crm"),
            new ToolCatalogItem(SematticeProjectDeliveryToolService.TOOL_NAME, "研发交付数据查询",
                    SematticeProjectDeliveryToolService.toolDescription(), "低风险", "project_delivery"),
            new ToolCatalogItem(SematticeProjectDeliveryWriteToolService.TOOL_NAME, "研发交付记录创建",
                    "由研发交付产品经理主动识别并专业整理用户描述，经用户确认后创建同租户项目、需求、缺陷或变更，并保留完整受理记录。", "中风险", "project_delivery"),
            new ToolCatalogItem(SematticeProjectDeliveryDeleteToolService.TOOL_NAME, "研发交付记录删除",
                    "仅由研发交付产品经理在用户明确确认后调用，将记录移入回收站（30天可恢复）。", "中风险", "project_delivery"),
            new ToolCatalogItem(SematticeProjectDeliveryUpdateToolService.TOOL_NAME, "研发交付记录修改",
                    "仅由研发交付产品经理在用户明确确认后调用，修改项目/需求/任务的负责人、状态、优先级、工时、描述等业务字段。", "中风险", "project_delivery"),
            new ToolCatalogItem(SematticeProjectDeliveryTransferToolService.TOOL_NAME, "开发任务转派",
                    "自动按 Developer Profile 花名识别转出和转入主体；只在用户确认后转派排队任务，并同步真实记录所有权。", "中风险", "project_delivery"),
            new ToolCatalogItem(SematticeProjectDeliveryReviewToolService.TOOL_NAME, "研发交付设计与验收评审",
                    SematticeProjectDeliveryReviewToolService.toolDescription(), "高风险", "project_delivery"),
            new ToolCatalogItem("get_pending_approvals", "审批待办拉取", "读取 CloudCC / OA 当前待审批项目。", "中风险", "approval"),
            new ToolCatalogItem(EmailToolService.TOOL_LIST_INBOX, "邮件收件箱",
                    "读取当前用户邮箱最近邮件摘要。", "低风险", "email"),
            new ToolCatalogItem(EmailToolService.TOOL_SEARCH, "邮件搜索",
                    "按关键字 / 发件人过滤最近邮件。", "低风险", "email"),
            new ToolCatalogItem(EmailToolService.TOOL_GET_MESSAGE, "邮件读正文",
                    "按 messageId 读取一封邮件正文。", "低风险", "email"),
            new ToolCatalogItem(EmailToolService.TOOL_SEND, "邮件发送",
                    "以当前用户身份发送新邮件，二次确认开关由账号配置决定。", "高风险", "email"),
            new ToolCatalogItem(EmailToolService.TOOL_REPLY, "邮件回复",
                    "对指定 messageId 回复一封邮件。", "高风险", "email"),
            new ToolCatalogItem(AssistantScheduleToolService.TOOL_NAME, "创建个人定时任务",
                    "为当前用户和当前智能体创建可执行的个人工作流定时任务，必须提供明确周期。", "中风险", "workflow"),
            new ToolCatalogItem(TavilyToolService.TOOL_SEARCH, "Tavily 网页搜索",
                    "调用 Tavily API 做面向 LLM 的网页搜索，返回命中 URL、摘要与相关度。", "低风险", "web"),
            new ToolCatalogItem(TavilyToolService.TOOL_EXTRACT, "Tavily 正文抽取",
                    "调用 Tavily API 抓取指定 URL 的正文，返回清洗后的 markdown/text。", "低风险", "web"),
            new ToolCatalogItem(SandboxCodeInterpreterService.TOOL_NAME, "受管代码解释器",
                    "在阿里云受管 Python 沙箱中完成精确计算、数据分析与代码验证；不在 AgentCiCi 宿主机执行代码。", "中风险", "analysis"),
            new ToolCatalogItem(ManagedWebToolService.TOOL_SEARCH, "受管联网搜索",
                    "调用百炼联网搜索获取时效信息；兼容 Responses 协议不提供可验证来源列表。", "低风险", "web"),
            new ToolCatalogItem(ManagedWebToolService.TOOL_EXTRACT, "受管网页抓取",
                    "调用百炼读取公开网页并按任务提取内容；一次请求同时使用搜索与抓取能力。", "中风险", "web")
    );

    private BuiltinToolCatalog() {
    }

    public static List<ToolCatalogItem> list() {
        return BUILTIN_TOOLS;
    }

    public record ToolCatalogItem(String toolName, String displayName, String description, String riskLevel, String category) {
    }
}
