package com.codehouse.ciciassistant.tool.service;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.email.service.EmailToolService;
import com.codehouse.ciciassistant.tool.tavily.TavilyToolService;
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
            new ToolCatalogItem(TavilyToolService.TOOL_SEARCH, "Tavily 网页搜索",
                    "调用 Tavily API 做面向 LLM 的网页搜索，返回命中 URL、摘要与相关度。", "低风险", "web"),
            new ToolCatalogItem(TavilyToolService.TOOL_EXTRACT, "Tavily 正文抽取",
                    "调用 Tavily API 抓取指定 URL 的正文，返回清洗后的 markdown/text。", "低风险", "web")
    );

    private BuiltinToolCatalog() {
    }

    public static List<ToolCatalogItem> list() {
        return BUILTIN_TOOLS;
    }

    public record ToolCatalogItem(String toolName, String displayName, String description, String riskLevel, String category) {
    }
}
