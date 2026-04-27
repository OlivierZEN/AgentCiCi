# CloudCC 触发器使用总结

> 基于 25 个生产环境触发器实例分析  
> 文档版本：v1.0  
> 更新时间：2026-03-24

---

## 一、什么是触发器

CloudCC 触发器是**完全符合 Java 语法规范的代码**，在特定业务事件发生时自动执行，实现业务逻辑的自动化处理。

### 触发器本质
- 一段 Java 代码，运行在 CloudCC 平台沙箱环境中
- 使用 CCService、CCObject 等平台提供的 API 类
- 在数据变更的特定时刻（前/后）自动触发执行

---

## 二、触发器能干什么

### 2.1 核心能力

| 能力 | 说明 | 示例 |
|------|------|------|
| **自动赋值** | 在记录保存前自动计算并填充字段 | 个人业绩/部门业绩自动赋值 |
| **数据校验** | 阻止不符合业务规则的数据保存 | 预算金额校验、审批金额验证 |
| **关联更新** | 修改一条记录时自动更新关联记录 | 回款明细汇总到收款计划 |
| **自动生成** | 根据业务规则自动创建新记录 | 审批通过后自动生成回款记录 |
| **权限控制** | 自动设置数据共享权限 | 按部门/人员自动共享 |
| **状态同步** | 审批状态变更后同步更新相关字段 | 预算审批状态回写 |
| **汇总计算** | 实时汇总明细数据到主记录 | 部门业绩字段汇总 |

### 2.2 技术能力（基于 CCService API）

```java
// 1. 新增记录
CCObject opp = new CCObject("Opportunity");
opp.put("name", "value");
cs.insert(opp);

// 2. 查询记录
// 查询指定字段
List<CCObject> opps = cs.cqueryByFields("Opportunity", 
    "khmc__c='" + record_new.get("id") + "'",
    "id,name,createdate");

// 查询全部字段
List<CCObject> opps = cs.cquery("Opportunity", 
    "khmc__c='" + record_new.get("id") + "'");

// 复杂 SQL 查询
List<CCObject> ccobjs = cs.cqlQuery("Opportunity", 
    "select sum(amount) as sumAmount from Opportunity where khmc = '0012001238ytwuiw'");

// 3. 修改记录
cs.update(opp);

// 4. 删除记录
cs.delete(opp);

// 5. 发送邮件通知
SendEmail sendEmail = new SendEmail(userInfo);
sendEmail.sendMailFromSystem(toAddress, ccAddress, bccAddress, subject, content, isText);
```

---

## 三、触发器的 4 种触发时机

### 3.1 beforeUpsert（创建或更新前）

**执行时机**：记录保存之前，数据尚未写入数据库

**典型用途**：
- 自动计算字段值
- 数据格式校验
- 阻止非法数据保存（抛出异常）
- 自动填充默认值

**生产实例**：
| 触发器名称 | 对象 | 作用 |
|-----------|------|------|
| 个人业绩/部门业绩赋值 | 对外付款 | 自动赋值业绩字段 |
| 计算产研成本/净业绩 | 预算 | 自动计算成本、净业绩 |
| 生成部门/个人业绩 | 净业绩拆分 | 自动创建业绩记录 |
| 编辑更新部门业绩 | 净业绩拆分回款 | 更新前校验和计算 |

**代码示例**：
```java
// beforeUpsert 触发器示例
CCObject record = (CCObject) data.get("record");

// 自动计算净业绩
BigDecimal income = record.getBigDecimal("ysr");
BigDecimal cost = record.getBigDecimal("cycb");
BigDecimal net = income.subtract(cost);
record.put("jyj", net);

// 校验：毛利不能为负
if (net.compareTo(BigDecimal.ZERO) < 0) {
    throw new Exception("毛利不能为负数！");
}
```

---

### 3.2 afterInsert（创建后）

**执行时机**：记录已成功创建，ID 已生成

**典型用途**：
- 基于新记录 ID 创建关联记录
- 发送创建通知
- 初始化关联数据

**生产实例**：
| 触发器名称 | 对象 | 作用 |
|-----------|------|------|
| 根据净业绩拆分生成净业绩拆分回款 | 回款明细 | 新建回款明细后自动生成回款记录 |

**代码示例**：
```java
// afterInsert 触发器示例
CCObject newRecord = (CCObject) data.get("record");
String recordId = newRecord.getString("id");

// 创建关联的回款记录
CCObject hkRecord = new CCObject("Huikuan");
hkRecord.put("mingxi__c", recordId);  // 关联明细 ID
hkRecord.put("je", newRecord.get("je"));
cs.insert(hkRecord);

// 发送通知
UserInfo userInfo = UserInfo.getUserInfo();
SendEmail email = new SendEmail(userInfo);
email.sendMailFromSystem(...);
```

---

### 3.3 afterUpsert（创建或更新后）

**执行时机**：记录已成功保存（新增或修改）

**典型用途**：
- 汇总数据到父记录
- 触发下游业务流程
- 更新关联记录状态
- 创建审计日志

**生产实例**：
| 触发器名称 | 对象 | 作用 |
|-----------|------|------|
| 金额汇总到收款计划 | 回款明细 | 汇总回款金额到收款计划 |
| 更新部门业绩字段 | 部门业绩 | 汇总各维度业绩数据 |
| 更新回款明细 | 回款明细 | 重建净业绩拆分回款记录 |
| (新) 新建自动拆分/审批通过自动生成回款 | 预算 | 审批后自动生成回款 |

**代码示例**：
```java
// afterUpsert 触发器示例 - 汇总金额到父记录
CCObject detail = (CCObject) data.get("record");

// 查询父记录（收款计划）
String planId = detail.getString("skjh__c");
List<CCObject> plans = cs.cqueryByFields("ShoukuanJihua", 
    "id='" + planId + "'", "id,shyjje,yishouje");

if (!plans.isEmpty()) {
    CCObject plan = plans.get(0);
    BigDecimal current = plan.getBigDecimal("yishouje");
    BigDecimal detailAmount = detail.getBigDecimal("je");
    
    // 累加已收金额
    plan.put("yishouje", current.add(detailAmount));
    
    // 判断是否完成
    if (current.add(detailAmount).compareTo(plan.getBigDecimal("shyjje")) >= 0) {
        plan.put("sfwc", "true");
    }
    
    cs.update(plan);
}
```

---

### 3.4 afterDelete（删除后）

**执行时机**：记录已成功删除

**典型用途**：
- 清理关联数据
- 反向汇总更新
- 记录删除日志

**生产实例**：
| 触发器名称 | 对象 | 作用 |
|-----------|------|------|
| 回款明细删除 | 回款明细 | 删除后重新计算回款金额 |

**代码示例**：
```java
// afterDelete 触发器示例
CCObject deletedRecord = (CCObject) data.get("oldRecord");
String planId = deletedRecord.getString("skjh__c");

// 重新汇总剩余明细金额
List<CCObject> details = cs.cquery("HuikuanMingxi", 
    "skjh__c='" + planId + "'");

BigDecimal total = BigDecimal.ZERO;
for (CCObject d : details) {
    total = total.add(d.getBigDecimal("je"));
}

// 更新收款计划
CCObject plan = cs.cqueryByFields("ShoukuanJihua", 
    "id='" + planId + "'", "id,yishouje").get(0);
plan.put("yishouje", total);
cs.update(plan);
```

---

### 3.5 approval（审批时）

**执行时机**：记录提交审批或审批通过时

**典型用途**：
- 审批前校验业务规则
- 审批通过后执行业务操作
- 更新审批状态

**生产实例**：
| 触发器名称 | 对象 | 作用 |
|-----------|------|------|
| 更新拆分审批状态 | 预算 | 审批通过后回写状态 |
| 订单审批判断 | 订单 | 审批时校验收款计划 |
| 提交审批验证金额 | 对外付款 | 审批前验证金额 |
| 还款记录审批通过更新借款单 | 还款记录 | 审批后更新借款单 |
| 审批通过计算剩余欠款 | 费用报销 | 审批后计算欠款 |

**代码示例**：
```java
// approval 触发器示例 - 审批前校验
CCObject record = (CCObject) data.get("record");
String action = (String) data.get("action"); // "submit" | "approve" | "reject"

if ("submit".equals(action)) {
    // 提交前校验
    BigDecimal amount = record.getBigDecimal("je");
    BigDecimal budget = record.getBigDecimal("ysye");
    
    if (amount.compareTo(budget) > 0) {
        throw new Exception("报销金额超出预算余额！");
    }
}

if ("approve".equals(action)) {
    // 审批通过后执行
    record.put("spzt", "approved");
    record.put("spdate", new Date());
    cs.update(record);
    
    // 创建回款记录
    CCObject hk = new CCObject("Huikuan");
    hk.put("dd__c", record.get("id"));
    cs.insert(hk);
}
```

---

## 四、为什么要用触发器

### 4.1 解决的核心问题

| 问题 | 传统方案 | 触发器方案 |
|------|---------|-----------|
| **数据一致性** | 依赖用户手动操作，容易遗漏 | 自动执行，保证数据准确 |
| **业务规则执行** | 前端校验可绕过，后端校验分散 | 统一在平台层执行，无法绕过 |
| **跨对象联动** | 需要多次手动操作 | 自动关联更新 |
| **审批流程复杂逻辑** | 审批流配置无法实现复杂逻辑 | Java 代码实现任意业务逻辑 |
| **实时性要求** | 定时任务有延迟 | 实时触发，立即执行 |

### 4.2 对比其他方案

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|---------|
| **触发器** | 实时、自动、无法绕过 | 需要 Java 开发能力 | 核心业务逻辑、数据一致性要求高 |
| **审批流** | 配置简单、可视化 | 逻辑能力有限 | 简单审批流程 |
| **工作流** | 可视化编排 | 复杂逻辑实现困难 | 跨系统流程编排 |
| **定时任务** | 适合批量处理 | 有延迟、不是实时 | 数据同步、报表生成 |
| **前端校验** | 用户体验好 | 可绕过、不安全 | 辅助校验，不能替代触发器 |

---

## 五、典型业务场景

### 5.1 财务业绩管理（核心场景）

**业务背景**：公司需要实时统计部门业绩、个人业绩，涉及多个对象的数据联动。

**触发器矩阵**：

```
┌─────────────────────────────────────────────────────────────────┐
│                      业绩管理触发器链路                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  订单 (Order)                                                    │
│    │ approval 触发器：审批判断                                   │
│    ▼                                                            │
│  预算 (Budget)                                                   │
│    │ beforeUpsert: 计算产研成本/净业绩                            │
│    │ afterUpsert: 自动拆分/生成回款                               │
│    ▼                                                            │
│  净业绩拆分 (jyjcf)                                              │
│    │ beforeUpsert: 生成部门/个人业绩                              │
│    │ afterUpsert: 校验金额累计是否超预算                          │
│    ▼                                                            │
│  部门业绩 (bmyj)                                                 │
│    │ afterUpsert: 更新部门业绩字段                                │
│    │ afterUpdate: 汇总部门资金池                                  │
│                                                                 │
│  回款明细 (cloudccproceeddetail)                                 │
│    │ afterInsert: 生成净业绩拆分回款                              │
│    │ afterUpsert: 金额汇总到收款计划                              │
│    │ afterUpsert: 更新回款明细                                    │
│    │ afterDelete: 回款明细删除（反向更新）                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**关键触发器**：

1. **计算产研成本/净业绩**（预算 - beforeUpsert）
   - 自动计算预算收入、产研成本、净业绩
   - 支持两种成本计算模式
   - 审批控制：毛利<0 阻止提交

2. **生成部门/个人业绩**（净业绩拆分 - beforeUpsert）
   - 根据拆分规则自动创建业绩记录
   - 按 owner 部门与年份关联业绩主数据

3. **金额汇总到收款计划**（回款明细 - afterUpsert）
   - 汇总同一回款单下的明细金额
   - 回填至收款计划（实际收款、余额、是否完成）
   - 按负责人 + 年份/月度汇总销售业绩

4. **更新部门业绩字段**（部门业绩 - afterUpsert）
   - 按「类别」+「业绩计算方式」多维度汇总
   - 计算订阅返款、产品返款、实施外包等累计
   - 计算目标完成率

---

### 5.2 费用报销管理

**业务背景**：员工费用报销需要预算校验、审批流程、欠款计算。

**触发器链路**：

```
费用报销提交
    │
    ▼
[approval 触发器] 验证审批时校验预算
    │ 校验：报销金额 ≤ 预算余额
    │ 阻止：超出预算的提交
    ▼
审批通过
    │
    ▼
[approval 触发器] 审批通过计算剩余欠款
    │ 计算：剩余欠款 = 借款 - 报销
    │ 更新：员工欠款状态
    ▼
[beforeUpsert 触发器] 个人业绩/部门业绩赋值
    │ 自动赋值业绩相关字段
```

---

### 5.3 借款还款管理

**业务背景**：借款单、还款记录需要联动更新。

**触发器**：

| 触发器 | 对象 | 时机 | 作用 |
|--------|------|------|------|
| 借款单触发器 | 借款单 | beforeUpsert | 借款前校验、自动计算 |
| 还款记录审批通过更新借款单 | 还款记录 | approval | 审批后更新借款单状态和剩余欠款 |

---

### 5.4 订单与收款管理

**业务背景**：订单审批时需要校验收款计划，审批通过后自动生成回款。

**触发器**：

| 触发器 | 对象 | 时机 | 作用 |
|--------|------|------|------|
| 订单审批判断 | 订单 | approval | 判断当前是否存在收款计划 |
| (新) 新建自动拆分/审批通过自动生成回款 | 预算 | afterUpsert | 审批通过自动生成回款记录 |

---

## 六、触发器开发最佳实践

### 6.1 代码规范

```java
// ✅ 推荐：使用 try-catch 处理异常
try {
    // 业务逻辑
    CCObject record = (CCObject) data.get("record");
    cs.update(record);
} catch (Exception e) {
    // 记录日志
    System.out.println("触发器执行失败：" + e.getMessage());
    // 抛出业务异常，阻止操作
    throw new Exception("业务校验失败：" + e.getMessage());
}

// ❌ 避免：不处理异常，导致触发器静默失败
CCObject record = (CCObject) data.get("record");
cs.update(record); // 如果失败，没有任何提示
```

### 6.2 性能优化

```java
// ✅ 推荐：使用字段过滤，只查询需要的字段
List<CCObject> records = cs.cqueryByFields("Object__c", 
    "status__c='active'", 
    "id,name,amount__c"); // 只查 3 个字段

// ❌ 避免：查询全部字段
List<CCObject> records = cs.cquery("Object__c", 
    "status__c='active'"); // 查询所有字段，性能差
```

```java
// ✅ 推荐：批量操作使用 updateLt（带 Lt 禁止执行其他触发器，避免递归）
List<CCObject> recordsToUpdate = new ArrayList<>();
// ... 准备数据
cs.updateLt(recordsToUpdate);

// ❌ 避免：循环中逐条更新，可能触发递归
for (CCObject r : records) {
    cs.update(r); // 可能再次触发触发器，导致死循环
}
```

### 6.3 避免递归触发

```java
// ✅ 推荐：使用 updateLt 或添加标志位
// 方法 1：使用 updateLt
cs.updateLt(records);

// 方法 2：添加标志位
if ("true".equals(System.getProperty("trigger.running"))) {
    return; // 避免递归
}
System.setProperty("trigger.running", "true");
// 业务逻辑
cs.update(record);
System.setProperty("trigger.running", "false");
```

### 6.4 错误处理

```java
// ✅ 推荐：明确的错误信息
if (amount.compareTo(budget) > 0) {
    throw new Exception(String.format(
        "报销金额 (%.2f) 超出预算余额 (%.2f)", 
        amount, budget));
}

// ❌ 避免：模糊的错误信息
if (amount.compareTo(budget) > 0) {
    throw new Exception("金额错误");
}
```

---

## 七、触发器调试与监控

### 7.1 日志记录

```java
// 记录关键信息
System.out.println("=== 触发器开始执行 ===");
System.out.println("对象：" + record.getSObjectName());
System.out.println("操作：" + data.get("action"));
System.out.println("记录 ID: " + record.get("id"));

// 记录计算过程
System.out.println("收入：" + income + ", 成本：" + cost + ", 净业绩：" + net);
```

### 7.2 触发器状态管理

| 状态 | 说明 | 操作 |
|------|------|------|
| **激活** | 触发器正常运行 | 无需操作 |
| **停用** | 触发器被禁用 | 检查是否需重新激活 |
| **编译失败** | 代码有语法错误 | 修复代码后重新编译 |

### 7.3 常见问题排查

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| 触发器不执行 | 未激活、触发时机不对 | 检查状态和触发时机配置 |
| 递归触发 | 更新操作再次触发触发器 | 使用 updateLt 或添加标志位 |
| 性能问题 | 查询数据量过大 | 添加查询条件、使用字段过滤 |
| 数据不一致 | 触发器执行失败但事务未回滚 | 添加异常处理，确保事务一致性 |

---

## 八、总结

### 8.1 触发器的价值

1. **自动化**：减少人工操作，降低出错率
2. **一致性**：保证数据准确性和业务规则执行
3. **实时性**：业务变更立即响应，无需等待
4. **灵活性**：Java 代码实现任意复杂业务逻辑

### 8.2 何时使用触发器

✅ **适合使用触发器的场景**：
- 需要实时数据联动
- 核心业务规则校验
- 跨对象数据同步
- 审批流程中的复杂逻辑
- 自动创建/更新关联记录

❌ **不适合使用触发器的场景**：
- 简单的字段默认值（用字段默认值功能）
- 大批量数据处理（用定时任务）
- 跨系统集成（用 Webhook 或集成平台）
- 用户界面交互（用前端逻辑）

### 8.3 触发器开发 checklist

- [ ] 明确触发时机（before/after/审批）
- [ ] 考虑递归触发风险
- [ ] 添加异常处理
- [ ] 记录关键日志
- [ ] 测试各种边界情况
- [ ] 性能测试（大数据量场景）
- [ ] 文档说明触发器作用

---

## 附录：25 个生产触发器清单

| # | 名称 | 对象 | 时机 | 状态 |
|---|------|------|------|------|
| 1 | 个人业绩/部门业绩赋值 | 对外付款 | beforeUpsert | ✅ |
| 2 | 个人业绩/部门业绩赋值 | 费用报销 | beforeUpsert | ❌ |
| 3 | 本部业绩统计 | 本部业绩统计 | beforeUpsert | ✅ |
| 4 | 更新拆分审批状态 | 预算 | approval | ✅ |
| 5 | 订单审批判断 | 订单 | approval | ✅ |
| 6 | 根据净业绩拆分生成净业绩拆分回款 | 回款明细 | afterInsert | ✅ |
| 7 | 金额汇总到收款计划 | 回款明细 | afterUpsert | ✅ |
| 8 | 回款明细删除 | 回款明细 | afterDelete | ❌ |
| 9 | 计算产研成本/净业绩 | 预算 | beforeUpsert | ✅ |
| 10 | 保证金回款记录 | 概算 | afterUpdate | ✅ |
| 11 | 校验金额累计是否超预算 | 净业绩拆分 | afterUpsert | ✅ |
| 12 | 更新部门业绩字段 | 部门业绩 | afterUpsert | ✅ |
| 13 | 汇总部门资金池 | 部门业绩 | afterUpdate | ✅ |
| 14 | 编辑更新部门业绩 | 净业绩拆分回款 | beforeUpsert | ✅ |
| 15 | 生成部门/个人业绩 | 净业绩拆分 | beforeUpsert | ✅ |
| 16 | 更新回款明细 | 回款明细 | afterUpsert | ✅ |
| 17 | (新) 新建自动拆分/审批通过自动生成回款 | 预算 | afterUpsert | ✅ |
| 18 | 提交审批验证金额 | 对外付款 | approval | ❌ |
| 19 | 还款记录审批通过更新借款单 | 还款记录 | approval | ✅ |
| 20 | 审批通过计算剩余欠款 | 费用报销 | approval | ✅ |
| 21 | 借款单触发器 | 借款单 | beforeUpsert | ✅ |
| 22 | 创建编辑项目任务时判断 | 项目任务 | beforeUpsert | ✅ |
| 23 | 任务派工工时限制 | 项目任务 | afterUpsert | ✅ |
| 24 | 测试 AI 代码生成 | 客户 | beforeInsert | ❌ |
| 25 | 验证审批时校验预算 | 费用报销 | approval | ❌ |

---

*文档生成时间：2026-03-24*  
*基于 CloudCC 生产环境 25 个触发器实例分析*
