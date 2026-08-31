package com.codehouse.ciciassistant.ai.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Server-owned fast path for deciding whether a turn needs the model tool-planning round.
 * Having tool capability is not evidence that the current user request needs external facts.
 */
final class ToolPlanningIntentRouter {

    private static final Pattern LOOKUP_INTENT = Pattern.compile(
            "(?is).*(查询|查一下|查找|查看|读取|获取|列出|检索|搜索|统计|汇总|盘点|分析).{0,28}"
                    + "(订单|客户(?:记录|列表|档案)?|商机|销售机会|联系人|对象|字段|邮件|日程|待办|报价|库存|记录|数据|系统|CRM|CloudCC|Semattice).*"
                    + "|.*(订单|客户(?:记录|列表|档案)?|商机|销售机会|联系人|对象|字段|邮件|日程|待办|报价|库存|记录|数据)"
                    + ".{0,28}(查询|查一下|查找|查看|读取|获取|列出|检索|搜索|统计|汇总|盘点|分析).*"
    );
    private static final Pattern ACTION_INTENT = Pattern.compile(
            "(?is).*(创建|新增|更新|修改|删除|发送|提交|审批|保存|写入|登记|同步|导入|导出|生成).{0,28}"
                    + "(报价|订单|客户|商机|联系人|记录|邮件|日程|待办|任务|项目|缺陷|对象|字段|报表|文件).*"
    );
    private static final Pattern LIVE_METRIC_INTENT = Pattern.compile(
            "(?is).*(今天|本周|本月|当前|最近|实时|最新|我们|本公司|现有).{0,24}"
                    + "(销量|销售额|订单数|客户数|复购率|转化率|库存|排名|top|最高|最低|最好|最差).*"
                    + "|.*(销量|销售额|订单数|客户数|复购率|转化率|库存|排名|top|最高|最低|最好|最差)"
                    + ".{0,24}(今天|本周|本月|当前|最近|实时|最新|哪些|多少|情况).*"
    );
    private static final Pattern EXPLICIT_SYSTEM_INTENT = Pattern.compile(
            "(?is).*(从|在|到|通过|调用).{0,12}(CRM|CloudCC|Semattice|系统|数据库|知识库|邮箱|日历).*"
    );
    private static final Pattern DIRECT_ADVISORY_INTENT = Pattern.compile(
            "(?is).*(建议|方案|策略|规划|怎么做|如何|应该|适合|推荐|介绍|说明|解释|概述|总结|了解|讲讲|思路|方法|最佳实践).*"
                    + "|.*(我们是|我们做|我们卖|我需要|我想要|团队规模|业务场景|期望解决).*"
                    + "|^(你好|您好|嗨|hello|hi)[！!。.]?$"
    );

    private ToolPlanningIntentRouter() {
    }

    static Decision decide(String question, List<String> availableToolNames, boolean externalFactRequired) {
        List<String> tools = availableToolNames == null ? List.of() : availableToolNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (tools.isEmpty()) {
            return new Decision(false, "NO_TOOLS");
        }
        if (externalFactRequired) {
            return new Decision(true, "KNOWLEDGE_REQUIRED");
        }
        String normalized = question == null ? "" : question.trim();
        if (normalized.isBlank()) {
            return new Decision(false, "EMPTY_QUESTION");
        }
        if (LOOKUP_INTENT.matcher(normalized).matches()) {
            return new Decision(true, "EXPLICIT_LOOKUP");
        }
        if (ACTION_INTENT.matcher(normalized).matches()) {
            return new Decision(true, "EXPLICIT_ACTION");
        }
        if (LIVE_METRIC_INTENT.matcher(normalized).matches()) {
            return new Decision(true, "LIVE_METRIC");
        }
        if (EXPLICIT_SYSTEM_INTENT.matcher(normalized).matches()) {
            return new Decision(true, "EXPLICIT_SYSTEM");
        }
        if (DIRECT_ADVISORY_INTENT.matcher(normalized).matches()) {
            return new Decision(false, "DIRECT_ADVISORY");
        }
        return new Decision(true, "AMBIGUOUS_TOOL_INTENT");
    }

    record Decision(boolean shouldPlan, String reason) {
    }
}
