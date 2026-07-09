#!/usr/bin/env python3
"""Seed AgentCiCi + CloudCC CRM customer-workbench demo data.

This script creates real simulated records in the bound CloudCC CRM tenant and
refreshes the AgentCiCi customer workbench aggregate tables for the demo org.
It intentionally prints only record counts and public IDs, never tokens or
secret-bearing configuration.
"""

from __future__ import annotations

import argparse
import json
import os
import shlex
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
CLOUDCC = Path("/Users/owenmacbook/.agents/skills/cc-customization-expert-msapi/tools/bin/cloudcc")
DEFAULT_SSH_KEY = Path("/Volumes/AISpace/datafiles/ecs-key/cc-cici-ecs.pem")
DEFAULT_REMOTE = "root@47.97.119.160"
AGENT_ORG_ID = "org2sva14i4udjmi2t4s"
CLOUDCC_ORG_ID = "org0720f814430017229"
BATCH = "TASK-172-DEMO-V1"


@dataclass(frozen=True)
class DemoAccount:
    key: str
    name: str
    segment: str
    owner: str
    industry: str
    contact_name: str
    contact_role: str
    phone: str
    email: str
    stage: str
    health: int
    progress: int
    summary: str
    risks: list[str]
    new_signals: list[str]
    existing_signals: list[str]
    next_actions: list[str]
    tags: list[str]
    interactions: list[str]
    opportunity_stage: str
    opportunity_amount: int


ACCOUNTS: list[DemoAccount] = [
    DemoAccount(
        "a01",
        "北京智造科技有限公司",
        "NEW",
        "张伟",
        "制造业",
        "李娜",
        "技术负责人",
        "13810001001",
        "lina.demo-a01@example.com",
        "方案评审中",
        72,
        88,
        "客户围绕 MES 集成、权限模型和实施周期推进评审，预算窗口已进入 7 月中旬。",
        ["决策链缺少采购负责人", "实施周期需要压缩到 8 周内"],
        ["MES 集成需求明确", "技术评审会已约定", "预算窗口明确"],
        ["暂无存量经营信号"],
        ["补齐采购负责人", "安排技术评审复盘", "创建商机产品明细"],
        ["重点推进", "MES 集成", "预算窗口"],
        [
            "微信沟通确认 MES 集成是首要关注点，客户要求补充接口清单。",
            "电话回访中客户询问实施周期和费用拆分，希望本周拿到确认。",
            "方案评审会约定由技术、采购和生产部门共同参加。",
        ],
        "需求分析",
        680000,
    ),
    DemoAccount(
        "a02",
        "深圳未来视界科技有限公司",
        "NEW",
        "陈晨",
        "高科技",
        "刘洋",
        "采购经理",
        "13810001002",
        "liuyang.demo-a02@example.com",
        "竞品比较",
        64,
        76,
        "客户正在比较竞品，核心关注总拥有成本、权限体系和交付风险。",
        ["竞品报价仍在评估", "采购关注价格多于业务价值"],
        ["权限治理需求强", "已有明确采购角色", "需要 TCO 对比材料"],
        ["暂无存量经营信号"],
        ["补充 TCO 对比", "安排权限治理演示", "确认业务决策人"],
        ["竞品对比", "权限关注", "待演示"],
        [
            "客户要求补充权限体系和审计说明。",
            "电话中明确正在比较两家竞品报价。",
            "采购希望本周拿到 TCO 对比和交付风险说明。",
        ],
        "价值验证",
        520000,
    ),
    DemoAccount(
        "a03",
        "宁波启明医疗器械有限公司",
        "NEW",
        "陈晨",
        "医疗器械",
        "郑琳",
        "信息主管",
        "13810001003",
        "zhenglin.demo-a03@example.com",
        "合规评估",
        68,
        73,
        "客户关注合规审计、权限分级和国产化部署，安全审批需要信息主管背书。",
        ["合规资料待补", "信息安全审批周期较长"],
        ["审计和权限需求明确", "部署窗口在 Q3", "信息主管已参与"],
        ["暂无存量经营信号"],
        ["补合规资料包", "安排安全架构评审", "确认 Q3 项目窗口"],
        ["合规关注", "安全评审", "Q3 窗口"],
        [
            "客户要求补充审计能力说明和权限分级案例。",
            "电话中提到国产化部署要求。",
            "安全审批需要信息主管出具评审意见。",
        ],
        "安全评审",
        460000,
    ),
    DemoAccount(
        "a04",
        "南京星河软件有限公司",
        "NEW",
        "张伟",
        "软件服务",
        "孙菲",
        "销售经理",
        "13810001004",
        "sunfei.demo-a04@example.com",
        "初步接触",
        60,
        62,
        "客户刚完成首次沟通，需求集中在销售过程管理和主管报表。",
        ["决策链未知", "预算尚未确认"],
        ["销售漏斗需求明确", "主管报表关注度高"],
        ["暂无存量经营信号"],
        ["安排需求澄清会", "确认预算和项目窗口", "输出主管报表样例"],
        ["首次接触", "需求澄清", "报表关注"],
        [
            "市场活动后完成首次沟通。",
            "客户关注销售漏斗和主管报表。",
            "客户尚未透露预算，需要下一次澄清。",
        ],
        "初步沟通",
        260000,
    ),
    DemoAccount(
        "a05",
        "上海云链信息技术有限公司",
        "RISK",
        "李娜",
        "软件服务",
        "陈峰",
        "客户经理",
        "13810001005",
        "chenfeng.demo-a05@example.com",
        "服务风险中",
        48,
        58,
        "客户连续反馈响应慢，续约窗口临近，需用整改计划和价值复盘挽回。",
        ["续约窗口临近", "服务响应满意度下降", "关键人态度转弱"],
        ["有增购数据治理模块兴趣"],
        ["续约窗口 45 天内", "服务问题需闭环", "客户成功拜访可挽回"],
        ["安排客户成功主管回访", "创建服务风险", "准备续约价值复盘"],
        ["续约风险", "服务反馈", "主管关注"],
        [
            "客户反馈上周问题未及时回复。",
            "电话中提到续约暂缓，需要先看到整改计划。",
            "售后会议记录显示数据同步问题已复现。",
        ],
        "续约挽回",
        390000,
    ),
    DemoAccount(
        "a06",
        "广州海创智联有限公司",
        "EXISTING",
        "王磊",
        "装备制造",
        "周倩",
        "信息化总监",
        "13810001006",
        "zhouqian.demo-a06@example.com",
        "增购识别",
        82,
        69,
        "一期系统稳定运行，客户开始讨论售后服务和移动巡检扩展。",
        ["移动端预算尚未确认"],
        ["售后场景有扩展机会"],
        ["使用满意度较高", "增购意向明确", "关键人关系稳定"],
        ["准备增购方案", "邀请客户参加成功案例交流", "维护信息化总监关系"],
        ["健康客户", "增购机会", "关系稳定"],
        [
            "微信中客户询问移动端巡检能力。",
            "季度回访确认一期上线效果稳定。",
            "客户希望看到同行案例和扩展报价。",
        ],
        "增购培育",
        720000,
    ),
    DemoAccount(
        "a07",
        "成都智云互联有限公司",
        "EXISTING",
        "周敏",
        "云服务",
        "马杰",
        "CIO",
        "13810001007",
        "majie.demo-a07@example.com",
        "健康经营",
        88,
        70,
        "客户使用稳定，对知识库和客服场景扩展接受度高，愿意共创案例。",
        ["预算需要 Q3 确认"],
        ["客服知识场景可推进"],
        ["健康度高", "关键人愿意共创", "续约风险低"],
        ["准备客服场景 PoC", "沉淀成功案例", "维护 CIO 关系"],
        ["健康客户", "扩展机会", "案例共创"],
        [
            "客户称当前系统稳定，业务部门反馈积极。",
            "会议中 CIO 希望探索客服知识场景。",
            "客户愿意提供内部案例素材用于联合复盘。",
        ],
        "扩展 PoC",
        610000,
    ),
    DemoAccount(
        "a08",
        "武汉联创节能科技有限公司",
        "EXISTING",
        "赵鹏",
        "能源",
        "何涛",
        "总经理",
        "13810001008",
        "hetao.demo-a08@example.com",
        "续约准备",
        78,
        66,
        "合同 60 天内到期，客户满意但希望降低运维成本。",
        ["续约价格敏感"],
        ["能源项目看板可增购"],
        ["续约窗口明确", "总经理关系稳定", "运维成本是谈判点"],
        ["准备续约价值报告", "列出运维降本证据", "约总经理复盘"],
        ["续约窗口", "价格敏感", "价值复盘"],
        [
            "电话确认续约窗口为 60 天内。",
            "客户提到运维成本压力，希望看到降本证据。",
            "总经理认可当前项目价值。",
        ],
        "续约报价",
        480000,
    ),
    DemoAccount(
        "a09",
        "苏州精密制造集团",
        "STRATEGIC",
        "李娜",
        "制造业",
        "许强",
        "副总裁",
        "13810001009",
        "xuqiang.demo-a09@example.com",
        "战略客户经营",
        84,
        78,
        "集团多部门并行推进，存在跨业务线协同和集团级平台机会。",
        ["集团采购流程复杂", "多部门需求口径不一"],
        ["集团级平台机会", "多业务线扩展"],
        ["战略客户", "关系多点覆盖", "可进入集团规划"],
        ["补齐权力地图", "组织集团级方案会", "拆分业务线机会"],
        ["战略客户", "集团机会", "权力地图"],
        [
            "副总裁提到集团统一平台规划。",
            "两个业务线分别提出不同诉求。",
            "采购流程需要集团审批，需提前准备材料。",
        ],
        "集团方案",
        1280000,
    ),
    DemoAccount(
        "a10",
        "青岛港航物流有限公司",
        "RISK",
        "王磊",
        "物流",
        "邓丽",
        "运营总监",
        "13810001010",
        "dengli.demo-a10@example.com",
        "服务改进",
        69,
        62,
        "客户认可派工效率，但投诉报表滞后影响运营团队满意度。",
        ["投诉报表滞后", "运营团队希望看到整改节奏"],
        ["服务看板增购机会"],
        ["核心流程使用稳定", "局部服务体验需改善"],
        ["创建服务改进任务", "准备服务看板方案", "每周同步整改节奏"],
        ["服务风险", "看板机会", "运营关注"],
        [
            "客户反馈投诉报表滞后。",
            "运营总监认可派工效率提升。",
            "客户希望每周看到整改节奏。",
        ],
        "服务整改",
        350000,
    ),
]


def run(cmd: list[str], *, input_text: str | None = None, timeout: int = 60) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        cmd,
        input=input_text,
        text=True,
        cwd=ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=False,
    )


def parse_json_output(text: str) -> dict[str, Any]:
    start = text.find("{")
    if start < 0:
        raise RuntimeError(f"CloudCC output did not contain JSON: {text[:200]}")
    return json.loads(text[start:])


def cloudcc(action: str, object_api: str, payload: dict[str, Any]) -> dict[str, Any]:
    body = json.dumps({"objectApiName": object_api, **payload}, ensure_ascii=False)
    proc = run([str(CLOUDCC), action, "openapi", ".", body], timeout=120)
    if proc.returncode != 0:
        raise RuntimeError(f"CloudCC {action} {object_api} failed: {proc.stderr.strip() or proc.stdout[:400]}")
    data = parse_json_output(proc.stdout)
    if data.get("result") is not True:
        raise RuntimeError(f"CloudCC {action} {object_api} returned failure: {data.get('returnInfo')}")
    return data


def page_query(object_api: str, fields: str, page_size: int = 500) -> list[dict[str, Any]]:
    result = cloudcc("pageQuery", object_api, {"fields": fields, "pageNum": 1, "pageSize": page_size})
    rows = result.get("data")
    return rows if isinstance(rows, list) else []


def existing_by_name(object_api: str, fields: str, names: set[str]) -> dict[str, str]:
    found: dict[str, str] = {}
    for row in page_query(object_api, fields):
        for key in ("name", "subject"):
            value = row.get(key)
            if value in names and row.get("id"):
                found[value] = str(row["id"])
    return found


def create_missing(object_api: str, fields: str, records: list[dict[str, Any]], name_key: str = "name") -> dict[str, str]:
    names = {str(item[name_key]) for item in records}
    found = existing_by_name(object_api, fields, names)
    missing = [item for item in records if str(item[name_key]) not in found]
    if not missing:
        return found
    result = cloudcc("create", object_api, {"data": missing})
    ids = result.get("data", {}).get("ids", [])
    if len(ids) != len(missing):
        raise RuntimeError(f"CloudCC create {object_api} returned unexpected id count")
    for record, item in zip(missing, ids):
        if item.get("success") is not True:
            raise RuntimeError(f"CloudCC create {object_api} failed for {record[name_key]}: {item.get('errors')}")
        found[str(record[name_key])] = str(item["id"])
    return found


def crm_records() -> dict[str, dict[str, str]]:
    account_records = [
        {
            "name": account.name,
            "hangye": account.industry,
            "fenji": "战略客户" if account.segment == "STRATEGIC" else ("重点客户" if account.segment in {"NEW", "EXISTING"} else "风险客户"),
            "dianhua": "010-" + account.phone[-8:],
            "beizhu": f"{BATCH} | AgentCiCi/CloudCC 双环境演示客户 | {account.summary}",
        }
        for account in ACCOUNTS
    ]
    account_ids = create_missing("Account", "id,name,beizhu", account_records)

    contact_records = [
        {
            "name": account.contact_name,
            "khmc": account_ids[account.name],
            "contactrole": account.contact_role,
            "shouji": account.phone,
            "email": account.email,
            "beizhu": f"{BATCH} | {account.name} 关键联系人 | {account.contact_role}",
        }
        for account in ACCOUNTS
    ]
    contact_ids = create_missing("Contact", "id,name,khmc,beizhu", contact_records)

    lead_records = [
        {
            "name": account.contact_name + " - " + account.name,
            "company": account.name,
            "phone": account.phone,
            "email": account.email,
            "qzkhly": "市场活动",
            "qzkhzt": "已联系",
            "beizhu": f"{BATCH} | 新客户推进线索 | {account.summary}",
        }
        for account in ACCOUNTS
        if account.segment in {"NEW", "RISK"}
    ]
    lead_ids = create_missing("cloudcclead", "id,name,company,beizhu", lead_records)

    opportunity_records = [
        {
            "name": account.name + " - " + account.stage,
            "khmc": account_ids[account.name],
            "jieduan": account.opportunity_stage,
            "jine": str(account.opportunity_amount),
            "description": f"{BATCH} | {account.summary}",
            "ywjhsm": "；".join(account.next_actions),
        }
        for account in ACCOUNTS
    ]
    opportunity_ids = create_missing("Opportunity", "id,name,khmc,description", opportunity_records)

    task_records = [
        {
            "name": account.name + " - " + account.next_actions[0],
            "subject": account.name + " - " + account.next_actions[0],
            "relateid": account_ids[account.name],
            "relateobj": "Account",
            "whoid": contact_ids[account.contact_name],
            "whoobj": "Contact",
            "status": "未开始",
            "priority": "高",
            "tasktype": "跟进",
            "expiredate": "2026-07-15",
            "remark": f"{BATCH} | AI 建议任务 | " + "；".join(account.next_actions),
        }
        for account in ACCOUNTS
    ]
    task_ids = create_missing("Task", "id,name,subject,relateid,remark", task_records)

    event_records = []
    for account in ACCOUNTS:
        for index, interaction in enumerate(account.interactions[:2], start=1):
            subject = f"{account.name} - 客户互动复盘 {index}"
            event_records.append(
                {
                    "name": subject,
                    "subject": subject,
                    "relateid": account_ids[account.name],
                    "relateobj": "Account",
                    "whoid": contact_ids[account.contact_name],
                    "whoobj": "Contact",
                    "begintime": f"2026-07-{9 + index:02d} 09:30:00",
                    "endtime": f"2026-07-{9 + index:02d} 10:00:00",
                    "status": "已完成",
                    "type": "客户沟通",
                    "remark": f"{BATCH} | {interaction}",
                }
            )
    event_ids = create_missing("Event", "id,name,subject,relateid,remark", event_records)

    return {
        "accounts": account_ids,
        "contacts": contact_ids,
        "leads": lead_ids,
        "opportunities": opportunity_ids,
        "tasks": task_ids,
        "events": event_ids,
    }


def sql_quote(value: Any) -> str:
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def json_text(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def build_agent_sql(ids: dict[str, dict[str, str]]) -> str:
    statements: list[str] = [
        "BEGIN;",
        f"DELETE FROM customer_workbench_recommendation WHERE org_id = {sql_quote(AGENT_ORG_ID)};",
        f"DELETE FROM customer_interaction_event WHERE org_id = {sql_quote(AGENT_ORG_ID)};",
        f"DELETE FROM customer_workbench_snapshot WHERE org_id = {sql_quote(AGENT_ORG_ID)};",
    ]
    for idx, account in enumerate(ACCOUNTS, start=1):
        account_id = ids["accounts"][account.name]
        contact_id = ids["contacts"][account.contact_name]
        opportunity_id = ids["opportunities"][account.name + " - " + account.stage]
        task_id = ids["tasks"][account.name + " - " + account.next_actions[0]]
        snapshot = {
            "industry": account.industry,
            "contact": f"{account.contact_name} {account.contact_role}",
            "crmContactId": contact_id,
            "crmOpportunityId": opportunity_id,
            "crmTaskId": task_id,
            "demoBatch": BATCH,
            "cloudccOrgId": CLOUDCC_ORG_ID,
            "lastInteraction": account.interactions[0],
            "stage": account.stage,
            "summary": account.summary,
            "risks": account.risks,
            "newCustomerSignals": account.new_signals,
            "existingCustomerSignals": account.existing_signals,
            "nextActions": account.next_actions,
            "tags": account.tags,
        }
        statements.append(
            "INSERT INTO customer_workbench_snapshot "
            "(public_id, org_id, crm_account_id, account_name, owner_name, segment, health_score, progress_score, "
            "risk_count, next_action_count, snapshot_json, created_at, updated_at) VALUES "
            f"({sql_quote(f'task172_v1_cw_{idx:03d}')}, {sql_quote(AGENT_ORG_ID)}, {sql_quote(account_id)}, "
            f"{sql_quote(account.name)}, {sql_quote(account.owner)}, {sql_quote(account.segment)}, "
            f"{account.health}, {account.progress}, {len(account.risks)}, {len(account.next_actions)}, "
            f"{sql_quote(json_text(snapshot))}, now(), now());"
        )
        for event_idx, interaction in enumerate(account.interactions, start=1):
            source = ["WECHAT", "PHONE", "MEETING"][(event_idx - 1) % 3]
            sentiment = "NEGATIVE" if account.segment == "RISK" else "NEUTRAL"
            lifecycle = "NEW_CUSTOMER" if account.segment == "NEW" else ("EXISTING_CUSTOMER" if account.segment == "EXISTING" else "MIXED")
            statements.append(
                "INSERT INTO customer_interaction_event "
                "(public_id, org_id, crm_account_id, crm_contact_id, source_type, occurred_at, subject, raw_summary, "
                "ai_summary, sentiment, intent_tags, lifecycle_area, created_at, updated_at) VALUES "
                f"({sql_quote(f'task172_v1_cwe_{idx:03d}_{event_idx}')}, {sql_quote(AGENT_ORG_ID)}, {sql_quote(account_id)}, "
                f"{sql_quote(contact_id)}, {sql_quote(source)}, "
                f"{sql_quote(f'2026-07-{event_idx + 8:02d}T0{min(9, event_idx + 1)}:30:00Z')}, "
                f"{sql_quote(account.name + '客户互动摘要 ' + str(event_idx))}, {sql_quote(interaction)}, "
                f"{sql_quote(interaction)}, {sql_quote(sentiment)}, {sql_quote(json_text(account.tags))}, "
                f"{sql_quote(lifecycle)}, now(), now());"
            )
        recommendations = [
            (
                "CREATE_TASK",
                "创建下一次跟进任务",
                "最近互动中出现明确待办，建议写入 CRM 任务并设置截止时间。",
                "0.91",
                {"objectApiName": "Task", "crmTaskId": task_id, "subject": account.next_actions[0], "accountId": account_id},
            ),
            (
                "UPDATE_OPPORTUNITY" if account.segment != "RISK" else "UPDATE_RISK",
                "更新商机推进记录" if account.segment != "RISK" else "更新客户经营风险",
                account.summary,
                "0.84",
                {"objectApiName": "Opportunity", "crmOpportunityId": opportunity_id, "accountId": account_id, "source": BATCH},
            ),
        ]
        for rec_idx, (rec_type, title, rationale, confidence, payload) in enumerate(recommendations, start=1):
            statements.append(
                "INSERT INTO customer_workbench_recommendation "
                "(public_id, org_id, crm_account_id, recommendation_type, title, rationale, confidence, status, "
                "crm_payload, applied_crm_id, created_at, updated_at) VALUES "
                f"({sql_quote(f'task172_v1_cwr_{idx:03d}_{rec_idx}')}, {sql_quote(AGENT_ORG_ID)}, {sql_quote(account_id)}, "
                f"{sql_quote(rec_type)}, {sql_quote(title)}, {sql_quote(rationale)}, {confidence}, 'PENDING', "
                f"{sql_quote(json_text(payload))}, NULL, now(), now());"
            )
    statements.extend(
        [
            "COMMIT;",
            f"SELECT 'snapshots=' || count(*) FROM customer_workbench_snapshot WHERE org_id = {sql_quote(AGENT_ORG_ID)};",
            f"SELECT 'events=' || count(*) FROM customer_interaction_event WHERE org_id = {sql_quote(AGENT_ORG_ID)};",
            f"SELECT 'recommendations=' || count(*) FROM customer_workbench_recommendation WHERE org_id = {sql_quote(AGENT_ORG_ID)};",
        ]
    )
    return "\n".join(statements) + "\n"


def apply_agentcici_sql(sql: str, ssh_key: Path, remote: str) -> str:
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    backup_cmd = (
        "set -euo pipefail; "
        f"BACKUP_DIR=/opt/cici/backups/{timestamp}-before-task172-demo-data; "
        "mkdir -p \"$BACKUP_DIR\"; "
        "docker exec cici-database pg_dump -U cici -d agentcici -Fc > \"$BACKUP_DIR/postgres.dump\"; "
        "printf '%s' \"$BACKUP_DIR\""
    )
    backup = run(["ssh", "-i", str(ssh_key), remote, backup_cmd], timeout=180)
    if backup.returncode != 0:
        raise RuntimeError(f"Remote backup failed: {backup.stderr.strip()}")

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".sql", delete=False) as handle:
        handle.write(sql)
        local_sql = Path(handle.name)
    try:
        remote_sql = f"/tmp/task172-demo-data-{timestamp}.sql"
        scp = run(["scp", "-i", str(ssh_key), str(local_sql), f"{remote}:{remote_sql}"], timeout=120)
        if scp.returncode != 0:
            raise RuntimeError(f"SCP SQL failed: {scp.stderr.strip()}")
        remote_cmd = (
            "set -euo pipefail; "
            f"docker cp {shlex.quote(remote_sql)} cici-database:/tmp/task172-demo-data.sql; "
            "docker exec cici-database psql -U cici -d agentcici -v ON_ERROR_STOP=1 -f /tmp/task172-demo-data.sql; "
            f"rm -f {shlex.quote(remote_sql)}"
        )
        apply = run(["ssh", "-i", str(ssh_key), remote, remote_cmd], timeout=180)
        if apply.returncode != 0:
            raise RuntimeError(f"Remote SQL apply failed: {apply.stderr.strip() or apply.stdout[-500:]}")
        return f"backup={backup.stdout.strip()}\n{apply.stdout.strip()}"
    finally:
        local_sql.unlink(missing_ok=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Seed TASK-172 demo data into CloudCC CRM and AgentCiCi.")
    parser.add_argument("--crm-only", action="store_true", help="Only create/reuse CloudCC CRM records.")
    parser.add_argument("--agent-only", action="store_true", help="Only refresh AgentCiCi aggregate tables after ensuring CRM ids.")
    parser.add_argument("--ssh-key", default=str(DEFAULT_SSH_KEY), help="SSH key for the AgentCiCi ECS host.")
    parser.add_argument("--remote", default=DEFAULT_REMOTE, help="AgentCiCi ECS SSH remote.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.crm_only and args.agent_only:
        raise SystemExit("--crm-only and --agent-only cannot be used together")
    if not CLOUDCC.exists():
        raise SystemExit(f"CloudCC CLI not found: {CLOUDCC}")
    print(f"Target AgentCiCi org: {AGENT_ORG_ID}")
    print(f"Target CloudCC org: {CLOUDCC_ORG_ID}")
    print(f"Demo batch: {BATCH}")

    ids = crm_records()
    print(
        "CloudCC records ready: "
        + ", ".join(f"{key}={len(value)}" for key, value in ids.items())
    )
    if args.crm_only:
        return 0

    sql = build_agent_sql(ids)
    result = apply_agentcici_sql(sql, Path(args.ssh_key), args.remote)
    print("AgentCiCi aggregate refresh complete:")
    print(result)
    return 0


if __name__ == "__main__":
    sys.exit(main())
