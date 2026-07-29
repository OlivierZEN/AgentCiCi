#!/usr/bin/env python3
"""Seed AgentCiCi + CloudCC CRM customer-workbench demo data.

This script creates real simulated records in the bound CloudCC CRM tenant and
refreshes the AgentCiCi customer workbench aggregate tables for the demo org.
It intentionally prints only record counts and public IDs, never tokens or
secret-bearing configuration.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shlex
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any


ROOT = Path(os.environ.get("CLOUDCC_PROJECT_ROOT", Path(__file__).resolve().parents[1])).resolve()
CLOUDCC = Path("/Users/owenmacbook/.agents/skills/cc-customization-expert-msapi/tools/bin/cloudcc")
DEFAULT_SSH_KEY = Path("/Volumes/AISpace/datafiles/ecs-key/cc-cici-ecs.pem")
DEFAULT_REMOTE = "root@47.97.119.160"
AGENT_COMPANY_ID = "org2sva14i4udjmi2t4s"
CLOUDCC_ORG_ID = "org0720f814430017229"
BATCH = "TASK-203-DEMO-V2"
NEW_PIPELINE_BATCH = "TASK-203-NEW-PIPELINE-R1"
CRM_OWNER_ID = "00520264AE58B11bw6gE"
CRM_OWNER_NAME = "SalesA"
AGENT_USER_ID = "7ccaa686-8977-41e8-abc7-6f18f45b2b08"
EXISTING_KEYS = {"a05", "a06", "a07", "a08", "a10", "a14", "a15", "a16"}
NO_CONTACT_KEYS = {"a12"}
NO_OPPORTUNITY_KEYS = {"a11"}
SILENT_KEYS = {"a16"}


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
    DemoAccount(
        "a11",
        "杭州数智零售有限公司",
        "NEW",
        "张伟",
        "零售",
        "唐悦",
        "数字化负责人",
        "13810001011",
        "tangyue.demo-a11@example.com",
        "商机待建立",
        61,
        42,
        "客户已完成需求访谈并认可门店协同价值，但 CRM 中尚未建立正式商机。",
        ["正式商机尚未建立", "预算口径待销售与运营统一"],
        ["门店协同需求明确", "试点范围已初步确认"],
        ["暂无存量经营信号"],
        ["创建门店协同商机", "确认首批试点门店", "补充预算测算"],
        ["商机缺失", "门店协同", "试点意向"],
        [
            "需求访谈确认首批选择 20 家门店试点。",
            "客户要求下周给出试点预算和成功标准。",
            "数字化负责人明确愿意推动内部立项。",
        ],
        "1-发现机会",
        320000,
    ),
    DemoAccount(
        "a12",
        "天津绿色能源研究院",
        "NEW",
        "陈晨",
        "新能源",
        "待补关键联系人",
        "待确认",
        "13810001012",
        "contact.demo-a12@example.com",
        "关系待补齐",
        58,
        55,
        "项目需求由公开交流会引入，已有初步商机，但尚未沉淀任何正式联系人。",
        ["关键联系人缺失", "决策链和采购流程未知"],
        ["碳资产管理方向明确", "已有初步项目窗口"],
        ["暂无存量经营信号"],
        ["补齐项目负责人", "确认采购与决策链", "安排需求澄清"],
        ["联系人缺失", "碳资产", "关系风险"],
        [
            "交流会纪要显示客户关注碳资产台账和审计追踪。",
            "现有信息未包含项目负责人姓名和联系方式。",
            "项目窗口预计在下一季度。",
        ],
        "2-需求分析",
        410000,
    ),
    DemoAccount(
        "a13",
        "厦门海洋科技有限公司",
        "NEW",
        "赵鹏",
        "海洋科技",
        "高辰",
        "项目经理",
        "13810001013",
        "gaochen.demo-a13@example.com",
        "下一步待确认",
        56,
        68,
        "技术方案已经提交，但商机下一步为空且跟进任务逾期，需尽快恢复推进节奏。",
        ["商机下一步为空", "方案回访任务已逾期"],
        ["技术方案已提交", "项目经理保持响应"],
        ["暂无存量经营信号"],
        ["确认方案反馈", "补充商机下一步", "重排决策会"],
        ["下一步缺失", "逾期跟进", "方案阶段"],
        [
            "客户已收到海洋监测数据治理方案。",
            "原定方案回访未完成，客户尚未给出明确反馈。",
            "项目经理建议重新安排决策会。",
        ],
        "3-提出方案",
        570000,
    ),
    DemoAccount(
        "a14",
        "重庆山城商业集团",
        "EXISTING",
        "李娜",
        "商业地产",
        "梁颖",
        "运营副总裁",
        "13810001014",
        "liangying.demo-a14@example.com",
        "续约高风险",
        42,
        65,
        "合同将在 25 天内到期，核心报表故障仍未关闭，续约需要服务整改和高层沟通并行。",
        ["25 天内续约", "高优先级服务个案未关闭", "满意度下降"],
        ["存在商业分析增购讨论"],
        ["进入高优续约窗口", "服务问题阻碍续约", "关键人要求整改承诺"],
        ["提交服务整改计划", "安排高层续约会", "准备价值证明"],
        ["30天内续约", "服务高风险", "满意度下降"],
        [
            "客户反馈经营报表连续两周延迟。",
            "运营副总裁明确表示整改是续约前置条件。",
            "续约评审会计划在本月完成。",
        ],
        "续约挽回",
        860000,
    ),
    DemoAccount(
        "a15",
        "合肥创新材料有限公司",
        "EXISTING",
        "王磊",
        "新材料",
        "沈博",
        "新任信息总监",
        "13810001015",
        "shenbo.demo-a15@example.com",
        "关键人交接",
        70,
        67,
        "合同将在 80 天内到期，原信息总监离任，新任关键人需要完成价值认知和关系交接。",
        ["关键人发生变化", "新任负责人尚未完成价值复盘"],
        ["实验室协同模块存在增购空间"],
        ["进入中期续约窗口", "关键关系需要重建", "当前服务总体稳定"],
        ["完成关键人交接", "安排价值复盘", "准备实验室协同方案"],
        ["90天内续约", "关键人变化", "关系重建"],
        [
            "客户通知原信息总监已离任。",
            "新任信息总监希望重新了解一期成果和路线图。",
            "业务部门提出实验室协同扩展需求。",
        ],
        "增购培育",
        640000,
    ),
    DemoAccount(
        "a16",
        "西安航空装备研究所",
        "EXISTING",
        "周敏",
        "航空装备",
        "冯毅",
        "信息中心主任",
        "13810001016",
        "fengyi.demo-a16@example.com",
        "沉默经营",
        54,
        52,
        "合同仍在有效期内且历史交付稳定，但超过 45 天没有任何可见互动，需要主动唤醒。",
        ["超过 45 天无联系", "缺少近期互动"],
        ["暂无新增推进信号"],
        ["历史履约稳定", "当前关系沉默", "无未关闭服务个案"],
        ["发起季度回访", "更新关键人近况", "确认下半年规划"],
        ["沉默客户", "价值稳定", "互动缺口"],
        [
            "历史验收记录显示项目交付稳定。",
            "最近一次有效沟通已超过 45 天。",
            "当前没有未关闭服务问题。",
        ],
        "7-签约关单",
        980000,
    ),
]


NEW_PIPELINE_ACCOUNTS: list[dict[str, Any]] = [
    {
        "key": "n01", "name": "无锡澄远机器人有限公司", "industry": "智能制造",
        "contact": "顾明远", "role": "智能制造总监", "phone": "13810002001",
        "stage": "6-商讨/审核", "amount": 920000, "next": "确认采购委员会评审时间",
        "summary": "机器人产线数字化项目已完成方案与预算沟通，等待采购委员会终审。",
    },
    {
        "key": "n02", "name": "东莞凌越精密电子有限公司", "industry": "电子制造",
        "contact": "梁思琪", "role": "采购经理", "phone": "13810002002",
        "stage": "5-招标/报价", "amount": 760000, "next": "提交分阶段报价与交付承诺",
        "summary": "客户进入三家供应商比选，重点关注分阶段报价、交付周期与审计能力。",
    },
    {
        "key": "n03", "name": "长沙擎岳数字能源有限公司", "industry": "新能源",
        "contact": "彭立新", "role": "数字化副总经理", "phone": "13810002003",
        "stage": "4-确定关键决策人", "amount": 680000, "next": "安排决策层价值验证会",
        "summary": "数字化负责人已认可方案，需向决策层证明跨区域能源项目的复制价值。",
    },
    {
        "key": "n04", "name": "济南睿驰工业软件有限公司", "industry": "工业软件",
        "contact": "韩若彤", "role": "产品副总裁", "phone": "13810002004",
        "stage": "4-确定关键决策人", "amount": 550000, "next": "确认联合解决方案商业条款",
        "summary": "双方正在讨论联合解决方案，技术路线明确，商业分成与首批客户范围待定。",
    },
    {
        "key": "n05", "name": "佛山恒拓智能装备有限公司", "industry": "装备制造",
        "contact": "周启航", "role": "信息中心主任", "phone": "13810002005",
        "stage": "3-提出方案", "amount": 430000, "next": "",
        "summary": "方案已提交，但商机下一步尚未填写，需要尽快确认现场验证安排。",
    },
    {
        "key": "n06", "name": "温州蓝港供应链有限公司", "industry": "供应链物流",
        "contact": "林嘉怡", "role": "运营总监", "phone": "13810002006",
        "stage": "", "amount": 360000, "next": "建立供应链协同正式商机",
        "summary": "需求访谈已完成并确定首批仓配试点，但 CRM 尚未建立正式商机。",
    },
    {
        "key": "n07", "name": "常州启盛新材料有限公司", "industry": "新材料",
        "contact": "待补采购负责人", "role": "待确认", "phone": "13810002007",
        "stage": "5-招标/报价", "amount": 810000, "next": "补齐采购与财务决策联系人",
        "summary": "项目已进入报价阶段，但 CRM 中尚未沉淀采购与财务关键联系人。",
    },
    {
        "key": "n08", "name": "郑州云帆医疗科技有限公司", "industry": "医疗科技",
        "contact": "苏婉清", "role": "信息安全负责人", "phone": "13810002008",
        "stage": "4-确定关键决策人", "amount": 620000, "next": "重排合规评审与产品演示",
        "summary": "合规评审材料已准备，原定演示跟进逾期，需要重排关键人会议。",
    },
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


def chunks(items: list[dict[str, Any]], size: int = 20) -> list[list[dict[str, Any]]]:
    return [items[index:index + size] for index in range(0, len(items), size)]


def existing_by_key(object_api: str, fields: str, keys: set[str], key_field: str) -> dict[str, str]:
    found: dict[str, str] = {}
    for row in page_query(object_api, fields):
        value = row.get(key_field)
        if value in keys and row.get("id"):
            found[str(value)] = str(row["id"])
    return found


def upsert_records(object_api: str, fields: str, records: list[dict[str, Any]], key_field: str = "name") -> dict[str, str]:
    keys = {str(item[key_field]) for item in records}
    found = existing_by_key(object_api, fields, keys, key_field)
    missing = [item for item in records if str(item[key_field]) not in found]
    for group in chunks(missing):
        result = cloudcc("create", object_api, {"data": group})
        created = result.get("data", {}).get("ids", [])
        if len(created) != len(group):
            raise RuntimeError(f"CloudCC create {object_api} returned unexpected id count")
        for record, item in zip(group, created):
            if item.get("success") is not True:
                raise RuntimeError(f"CloudCC create {object_api} failed for {record[key_field]}: {item.get('errors')}")
            found[str(record[key_field])] = str(item["id"])
    updates = [{"id": found[str(record[key_field])], **record} for record in records]
    for group in chunks(updates):
        result = cloudcc("update", object_api, {"data": group})
        updated = result.get("data", {}).get("ids", [])
        if len(updated) != len(group) or any(item.get("success") is not True for item in updated):
            raise RuntimeError(f"CloudCC update {object_api} failed for one or more {BATCH} records")
    return found


def require_existing(object_api: str, fields: str, keys: set[str], key_field: str = "name") -> dict[str, str]:
    found = existing_by_key(object_api, fields, keys, key_field)
    missing = sorted(keys - set(found))
    if missing:
        raise RuntimeError(f"CloudCC {object_api} is missing {len(missing)} required {BATCH} records")
    return found


def resolve_agent_record_ids() -> dict[str, dict[str, str]]:
    account_names = {account.name for account in ACCOUNTS}
    contact_names = {account.contact_name for account in ACCOUNTS if account.key not in NO_CONTACT_KEYS}
    opportunity_names = {
        account.name + " - " + account.stage for account in ACCOUNTS if account.key not in NO_OPPORTUNITY_KEYS
    }
    task_names = {
        account.name + " - " + account.next_actions[0] for account in ACCOUNTS if account.key not in SILENT_KEYS
    }
    return {
        "accounts": require_existing("Account", "id,name", account_names),
        "contacts": require_existing("Contact", "id,name", contact_names),
        "opportunities": require_existing("Opportunity", "id,name", opportunity_names),
        "tasks": require_existing("Task", "id,name", task_names),
    }


def seed_new_customer_pipeline(today: date) -> dict[str, dict[str, str]]:
    """Create a dedicated new-customer dataset that no existing-customer demo may reuse."""
    account_records = [
        {
            "name": item["name"],
            "ownerid": CRM_OWNER_ID,
            "hangye": item["industry"],
            "fenji": "重点客户",
            "dianhua": "010-" + str(item["phone"])[-8:],
            "beizhu": f"{NEW_PIPELINE_BATCH} | 新客户推进专属样本 | {item['summary']}",
        }
        for item in NEW_PIPELINE_ACCOUNTS
    ]
    account_ids = upsert_records("Account", "id,name,ownerid,beizhu", account_records)

    contact_records = [
        {
            "name": item["contact"],
            "ownerid": CRM_OWNER_ID,
            "khmc": account_ids[str(item["name"])],
            "contactrole": item["role"],
            "zhiwu": item["role"],
            "shouji": item["phone"],
            "email": f"new-pipeline.{item['key']}@example.com",
            "beizhu": f"{NEW_PIPELINE_BATCH} | {item['name']} | {item['role']}",
        }
        for item in NEW_PIPELINE_ACCOUNTS
        if item["key"] != "n07"
    ]
    contact_ids = upsert_records("Contact", "id,name,ownerid,khmc,beizhu", contact_records)

    lead_records = [
        {
            "name": f"{item['contact']} - {item['name']}",
            "ownerid": CRM_OWNER_ID,
            "company": item["name"],
            "phone": item["phone"],
            "email": f"new-pipeline.{item['key']}@example.com",
            "qzkhly": "市场活动",
            "qzkhzt": "已联系",
            "beizhu": f"{NEW_PIPELINE_BATCH} | 新客户推进线索 | {item['summary']}",
        }
        for item in NEW_PIPELINE_ACCOUNTS
    ]
    lead_ids = upsert_records("cloudcclead", "id,name,ownerid,company,beizhu", lead_records)

    opportunity_records = [
        {
            "name": f"{item['name']} - 新客户项目",
            "ownerid": CRM_OWNER_ID,
            "khmc": account_ids[str(item["name"])],
            "jieduan": item["stage"],
            "jine": str(item["amount"]),
            "jsrq": (today + timedelta(days=60 + int(str(item["key"])[1:]) * 5)).isoformat(),
            "xyb": item["next"],
            "description": f"{NEW_PIPELINE_BATCH} | {item['summary']}",
            "ywjhsm": item["next"] or "待确认客户反馈并补充下一步行动",
        }
        for item in NEW_PIPELINE_ACCOUNTS
        if item["key"] != "n06"
    ]
    opportunity_ids = upsert_records("Opportunity", "id,name,ownerid,khmc,description", opportunity_records)

    task_records: list[dict[str, Any]] = []
    for item in NEW_PIPELINE_ACCOUNTS:
        common: dict[str, Any] = {
            "ownerid": CRM_OWNER_ID,
            "relateid": account_ids[str(item["name"])],
            "relateobj": "Account",
            "tasktype": "跟进",
        }
        contact_id = contact_ids.get(str(item["contact"]))
        if contact_id:
            common.update({"whoid": contact_id, "whoobj": "Contact"})
        due = today - timedelta(days=3) if item["key"] == "n08" else today + timedelta(days=2 + int(str(item["key"])[1:]))
        subject = str(item["next"] or "确认方案反馈并补充商机下一步")
        task_records.extend([
            {
                **common,
                "name": f"{item['name']} - {subject}",
                "subject": f"{item['name']} - {subject}",
                "status": "未开始",
                "priority": "高" if item["key"] in {"n01", "n02", "n07", "n08"} else "普通",
                "expiredate": due.isoformat(),
                "remark": f"{NEW_PIPELINE_BATCH} | 新客户待办 | {item['summary']}",
            },
            {
                **common,
                "name": f"{item['name']} - 已完成首次需求访谈",
                "subject": f"{item['name']} - 已完成首次需求访谈",
                "status": "已完成",
                "priority": "普通",
                "expiredate": (today - timedelta(days=7)).isoformat(),
                "remark": f"{NEW_PIPELINE_BATCH} | 已完成任务 | {item['summary']}",
            },
        ])
    task_ids = upsert_records("Task", "id,name,subject,ownerid,relateid,remark", task_records)

    event_records: list[dict[str, Any]] = []
    for item in NEW_PIPELINE_ACCOUNTS:
        messages = [
            f"已完成首次需求访谈。{item['summary']}",
            f"客户确认下一步关注：{item['next'] or '需要补充明确行动计划'}。",
        ]
        for index, message in enumerate(messages, start=1):
            occurred = datetime.now().replace(microsecond=0) - timedelta(days=3 + index * 4 + int(str(item["key"])[1:]))
            subject = f"{item['name']} - 新客户互动 {index}"
            event: dict[str, Any] = {
                "name": subject,
                "subject": subject,
                "ownerid": CRM_OWNER_ID,
                "relateid": account_ids[str(item["name"])],
                "relateobj": "Account",
                "begintime": occurred.strftime("%Y-%m-%d %H:%M:%S"),
                "endtime": (occurred + timedelta(minutes=45)).strftime("%Y-%m-%d %H:%M:%S"),
                "status": "已完成",
                "type": "会议" if index == 1 else "电话",
                "remark": f"{NEW_PIPELINE_BATCH} | {message}",
            }
            contact_id = contact_ids.get(str(item["contact"]))
            if contact_id:
                event.update({"whoid": contact_id, "whoobj": "Contact"})
            event_records.append(event)
    event_ids = upsert_records("Event", "id,name,subject,ownerid,relateid,remark", event_records)

    ids_by_object = {
        "Account": account_ids,
        "Contact": contact_ids,
        "cloudcclead": lead_ids,
        "Opportunity": opportunity_ids,
        "Task": task_ids,
        "Event": event_ids,
    }
    for object_api, record_ids in ids_by_object.items():
        wanted = set(record_ids.values())
        observed = {
            str(row["id"]): str(row.get("ownerid", ""))
            for row in page_query(object_api, "id,ownerid", 500)
            if str(row.get("id", "")) in wanted
        }
        wrong = [record_id for record_id in wanted if observed.get(record_id) != CRM_OWNER_ID]
        if wrong:
            raise RuntimeError(f"CloudCC {object_api} owner verification failed for {len(wrong)} {NEW_PIPELINE_BATCH} records")
    return {
        "new_pipeline_accounts": account_ids,
        "new_pipeline_contacts": contact_ids,
        "new_pipeline_leads": lead_ids,
        "new_pipeline_opportunities": opportunity_ids,
        "new_pipeline_tasks": task_ids,
        "new_pipeline_events": event_ids,
    }


def crm_records() -> dict[str, dict[str, str]]:
    today = date.today()
    secondary_contacts = {
        account.key: name for account, name in zip(ACCOUNTS, [
            "周航", "罗晴", "顾言", "宋洁", "蒋峰", "杜晓", "叶欣", "吴桐",
            "魏岚", "范舟", "邹宁", "待补采购联系人", "陆远", "贺敏", "韩雪", "秦川",
        ])
    }
    account_records = [
        {
            "name": account.name,
            "ownerid": CRM_OWNER_ID,
            "hangye": account.industry,
            "fenji": "战略客户" if account.key in {"a01", "a06", "a09"} else ("风险客户" if account.key in {"a05", "a10", "a13", "a14"} else "重点客户"),
            "dianhua": "010-" + account.phone[-8:],
            "beizhu": f"{BATCH} | AgentCiCi/CloudCC 双环境演示客户 | {account.summary}",
        }
        for account in ACCOUNTS
    ]
    account_ids = upsert_records("Account", "id,name,ownerid,beizhu", account_records)

    contact_records: list[dict[str, Any]] = []
    for account in ACCOUNTS:
        if account.key in NO_CONTACT_KEYS:
            continue
        contact_records.extend([
            {
                "name": account.contact_name,
                "ownerid": CRM_OWNER_ID,
                "khmc": account_ids[account.name],
                "contactrole": account.contact_role,
                "zhiwu": account.contact_role,
                "shouji": account.phone,
                "email": account.email,
                "beizhu": f"{BATCH} | {account.name} 关键联系人 | {account.contact_role}",
            },
            {
                "name": secondary_contacts[account.key],
                "ownerid": CRM_OWNER_ID,
                "khmc": account_ids[account.name],
                "contactrole": "采购与业务协同",
                "zhiwu": "采购/业务负责人",
                "shouji": "139" + account.phone[-8:],
                "email": f"secondary.{account.key}@example.com",
                "beizhu": f"{BATCH} | {account.name} 第二关系联系人 | 采购与业务协同",
            },
        ])
    contact_ids = upsert_records("Contact", "id,name,ownerid,khmc,beizhu", contact_records)

    lead_records = [
        {
            "name": account.contact_name + " - " + account.name,
            "ownerid": CRM_OWNER_ID,
            "company": account.name,
            "phone": account.phone,
            "email": account.email,
            "qzkhly": "市场活动",
            "qzkhzt": "已联系",
            "beizhu": f"{BATCH} | 新客户推进线索 | {account.summary}",
        }
        for account in ACCOUNTS
        if account.key not in EXISTING_KEYS
    ]
    lead_ids = upsert_records("cloudcclead", "id,name,ownerid,company,beizhu", lead_records)

    stage_by_key = {
        "a01": "6-商讨/审核", "a02": "5-招标/报价", "a03": "4-确定关键决策人", "a04": "2-需求分析",
        "a05": "2-需求分析", "a06": "3-提出方案", "a07": "3-提出方案", "a08": "5-招标/报价",
        "a09": "4-确定关键决策人", "a10": "2-需求分析", "a12": "2-需求分析", "a13": "3-提出方案",
        "a14": "5-招标/报价", "a15": "3-提出方案", "a16": "7-签约关单",
    }
    opportunity_records: list[dict[str, Any]] = []
    for account in ACCOUNTS:
        if account.key in NO_OPPORTUNITY_KEYS:
            continue
        opportunity_records.append({
            "name": account.name + " - " + account.stage,
            "ownerid": CRM_OWNER_ID,
            "khmc": account_ids[account.name],
            "jieduan": stage_by_key[account.key],
            "jine": str(account.opportunity_amount),
            "jsrq": (today + timedelta(days=75)).isoformat(),
            "xyb": "" if account.key == "a13" else account.next_actions[0],
            "description": f"{BATCH} | {account.summary}",
            "ywjhsm": "；".join(account.next_actions),
        })
    for account in ACCOUNTS:
        if account.key not in {"a05", "a06", "a07", "a08", "a10", "a15"}:
            continue
        opportunity_records.append({
            "name": account.name + " - 增购项目",
            "ownerid": CRM_OWNER_ID,
            "khmc": account_ids[account.name],
            "jieduan": "增购培育",
            "jine": str(max(180000, account.opportunity_amount // 3)),
            "jsrq": (today + timedelta(days=120)).isoformat(),
            "xyb": "完成增购需求澄清并安排方案演示",
            "description": f"{BATCH} | 存量客户独立增购场景 | {account.existing_signals[0]}",
            "ywjhsm": "完成增购需求澄清；安排方案演示；确认预算窗口",
        })
    opportunity_ids = upsert_records("Opportunity", "id,name,ownerid,khmc,description", opportunity_records)

    task_records: list[dict[str, Any]] = []
    for account in ACCOUNTS:
        if account.key in SILENT_KEYS:
            continue
        primary_contact = contact_ids.get(account.contact_name)
        common: dict[str, Any] = {
            "ownerid": CRM_OWNER_ID,
            "relateid": account_ids[account.name],
            "relateobj": "Account",
            "tasktype": "跟进",
        }
        if primary_contact:
            common.update({"whoid": primary_contact, "whoobj": "Contact"})
        due = today - timedelta(days=4) if account.key == "a13" else today + timedelta(days=3 + int(account.key[1:]) % 7)
        task_records.extend([
            {
                **common,
                "name": account.name + " - " + account.next_actions[0],
                "subject": account.name + " - " + account.next_actions[0],
                "status": "未开始",
                "priority": "高" if account.key in {"a01", "a05", "a13", "a14"} else "普通",
                "expiredate": due.isoformat(),
                "remark": f"{BATCH} | 待办跟进 | " + "；".join(account.next_actions),
            },
            {
                **common,
                "name": account.name + " - 已完成需求复盘",
                "subject": account.name + " - 已完成需求复盘",
                "status": "已完成",
                "priority": "普通",
                "expiredate": (today - timedelta(days=5)).isoformat(),
                "remark": f"{BATCH} | 已完成任务 | {account.interactions[0]}",
            },
        ])
    task_ids = upsert_records("Task", "id,name,subject,ownerid,relateid,remark", task_records)

    event_records: list[dict[str, Any]] = []
    for account in ACCOUNTS:
        if account.key in SILENT_KEYS:
            continue
        for index, interaction in enumerate(account.interactions, start=1):
            subject = f"{account.name} - CRM 客户互动 {index}"
            occurred = datetime.now().replace(microsecond=0) - timedelta(days=[2, 9, 20][index - 1])
            event: dict[str, Any] = {
                "name": subject,
                "subject": subject,
                "ownerid": CRM_OWNER_ID,
                "relateid": account_ids[account.name],
                "relateobj": "Account",
                "begintime": occurred.strftime("%Y-%m-%d %H:%M:%S"),
                "endtime": (occurred + timedelta(minutes=45)).strftime("%Y-%m-%d %H:%M:%S"),
                "status": "已完成",
                "type": ["客户沟通", "会议", "电话"][index - 1],
                "remark": f"{BATCH} | {interaction}",
            }
            if account.contact_name in contact_ids:
                event.update({"whoid": contact_ids[account.contact_name], "whoobj": "Contact"})
            event_records.append(
                event
            )
    event_ids = upsert_records("Event", "id,name,subject,ownerid,relateid,remark", event_records)

    contract_days = {"a05": 15, "a06": 180, "a07": 365, "a08": 60, "a10": 120, "a14": 25, "a15": 80, "a16": 240}
    contract_records = []
    for account in ACCOUNTS:
        if account.key not in EXISTING_KEYS:
            continue
        contract_records.append({
            "name": account.name + " - 年度服务合同",
            "ownerid": CRM_OWNER_ID,
            "khmc": account_ids[account.name],
            "zhuangtai": "审批通过",
            "htksrq": (today - timedelta(days=300)).isoformat(),
            "htjsrq": (today + timedelta(days=contract_days[account.key])).isoformat(),
            "htje": str(account.opportunity_amount),
        })
    contract_ids = upsert_records("contract", "id,name,ownerid,khmc,zhuangtai,htjsrq", contract_records)

    case_specs = [
        ("a05", "数据同步延迟整改", "升级", "高"),
        ("a05", "历史报表核对", "关闭", "低"),
        ("a10", "投诉报表更新滞后", "回访", "中"),
        ("a14", "经营报表连续延迟", "新建", "高"),
        ("a14", "门店权限配置异常", "升级", "高"),
        ("a15", "关键人交接支持", "搁置", "中"),
        ("a06", "移动端登录咨询", "关闭", "低"),
        ("a08", "续约数据核验", "关闭", "低"),
    ]
    by_key = {account.key: account for account in ACCOUNTS}
    case_records = []
    for index, (key, title, status, priority) in enumerate(case_specs, start=1):
        account = by_key[key]
        case_records.append({
            "name": f"TASK203-{index:03d}",
            "ownerid": CRM_OWNER_ID,
            "khmc": account_ids[account.name],
            "zhuangtai": status,
            "yxj": priority,
            "duedate": (datetime.now() + timedelta(days=5 + index)).strftime("%Y-%m-%d 18:00:00"),
            "zhuti": f"{BATCH} | {account.name} | {title}",
            "problemdescription": f"{BATCH} 演示个案：{title}。用于展示服务状态、优先级、到期时间和风险闭环。",
        })
    case_ids = upsert_records("cloudcccase", "id,name,ownerid,khmc,zhuangtai,yxj,zhuti", case_records, "zhuti")

    ids_by_object = {
        "Account": account_ids, "Contact": contact_ids, "cloudcclead": lead_ids,
        "Opportunity": opportunity_ids, "Task": task_ids, "Event": event_ids,
        "contract": contract_ids, "cloudcccase": case_ids,
    }
    for object_api, record_ids in ids_by_object.items():
        wanted = set(record_ids.values())
        observed = {
            str(row["id"]): str(row.get("ownerid", ""))
            for row in page_query(object_api, "id,ownerid", 500)
            if str(row.get("id", "")) in wanted
        }
        wrong = [record_id for record_id in wanted if observed.get(record_id) != CRM_OWNER_ID]
        if wrong:
            raise RuntimeError(f"CloudCC {object_api} owner verification failed for {len(wrong)} records")

    result = {
        "accounts": account_ids,
        "contacts": contact_ids,
        "leads": lead_ids,
        "opportunities": opportunity_ids,
        "tasks": task_ids,
        "events": event_ids,
        "contracts": contract_ids,
        "cases": case_ids,
    }
    result.update(seed_new_customer_pipeline(today))
    return result


def sql_quote(value: Any) -> str:
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def json_text(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def build_agent_sql(ids: dict[str, dict[str, str]]) -> str:
    now = datetime.now(timezone.utc).replace(microsecond=0)
    account_id_list = ", ".join(sql_quote(value) for value in ids["accounts"].values())
    statements: list[str] = [
        "BEGIN;",
        f"DELETE FROM customer_workbench_recommendation WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND public_id LIKE 'task203_v2_%';",
        f"DELETE FROM customer_workbench_recommendation WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND public_id LIKE 'task172_v1_%' AND status = 'PENDING';",
        f"DELETE FROM customer_dynamic_signal WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND crm_account_id IN ({account_id_list});",
        f"DELETE FROM customer_memory_item WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND crm_account_id IN ({account_id_list});",
        f"DELETE FROM customer_interaction_asset WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND batch_id IN (SELECT id FROM customer_interaction_batch WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND public_id LIKE 'task203_v2_%');",
        f"DELETE FROM customer_interaction_event WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND (public_id LIKE 'task203_v2_%' OR public_id LIKE 'task172_v1_%');",
        f"DELETE FROM customer_interaction_batch WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND public_id LIKE 'task203_v2_%';",
        f"DELETE FROM customer_score_snapshot WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND crm_account_id IN ({account_id_list});",
        f"DELETE FROM customer_workbench_snapshot WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND crm_account_id IN ({account_id_list});",
    ]
    first_archives: dict[str, tuple[str, str, str]] = {}
    source_types = ["WECHAT", "PHONE", "MEETING", "EMAIL", "CUSTOMER_FEEDBACK", "CRM_TASK", "CRM_EVENT"]
    memory_types = ["FACT", "NEED", "RISK", "OPPORTUNITY", "COMMITMENT", "NEXT_ACTION", "PENDING_QUESTION"]
    dimensions = ["HEALTH", "EXPANSION", "RENEWAL", "RELATIONSHIP", "RISK"]
    global_event_index = 0
    for idx, account in enumerate(ACCOUNTS, start=1):
        account_id = ids["accounts"][account.name]
        contact_id = ids["contacts"].get(account.contact_name)
        opportunity_id = ids["opportunities"].get(account.name + " - " + account.stage)
        task_id = ids["tasks"].get(account.name + " - " + account.next_actions[0])
        customer_mode = "EXISTING" if account.key in EXISTING_KEYS else "NEW"
        snapshot = {
            "industry": account.industry,
            "contact": f"{account.contact_name} {account.contact_role}" if contact_id else "CRM 中尚未建立联系人",
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
            "(public_id, company_id, crm_account_id, account_name, owner_name, segment, health_score, progress_score, "
            "risk_count, next_action_count, snapshot_json, created_at, updated_at) VALUES "
            f"({sql_quote(f'task203_v2_snap_{idx:03d}')}, {sql_quote(AGENT_COMPANY_ID)}, {sql_quote(account_id)}, "
            f"{sql_quote(account.name)}, {sql_quote(CRM_OWNER_NAME)}, {sql_quote(customer_mode)}, "
            f"{account.health}, {account.progress}, {len(account.risks)}, {len(account.next_actions)}, "
            f"{sql_quote(json_text(snapshot))}, now(), now());"
        )
        expansion_score = 82 if account.key in {"a05", "a06", "a07", "a08", "a10", "a15"} else 52
        renewal_score = 88 if account.key in {"a05", "a08", "a14", "a15"} else 58
        relationship_score = 38 if account.key in {"a12", "a15", "a16"} else 74
        risk_score = 84 if account.key in {"a05", "a10", "a13", "a14", "a16"} else 32
        net_change = -12.0 if risk_score >= 80 else 7.5
        active_count = 0 if account.key in SILENT_KEYS else 2
        pending_count = 1 if account.key in {"a04", "a11", "a12", "a15"} else 0
        statements.append(
            "INSERT INTO customer_score_snapshot "
            "(company_id, crm_account_id, health_score, health_dimension_score, expansion_score, renewal_score, "
            "relationship_score, risk_score, net_change_30d, active_signal_count, pending_signal_count, "
            "calculation_version, calculated_at, created_at, updated_at) VALUES "
            f"({sql_quote(AGENT_COMPANY_ID)}, {sql_quote(account_id)}, {account.health}, {account.health}, {expansion_score}, "
            f"{renewal_score}, {relationship_score}, {risk_score}, {net_change}, {active_count}, {pending_count}, "
            f"'task203-demo-v2', now(), now(), now());"
        )
        if account.key in SILENT_KEYS:
            continue
        for event_idx, interaction in enumerate(account.interactions[:2], start=1):
            global_event_index += 1
            source = source_types[(global_event_index - 1) % len(source_types)]
            sentiment = "NEGATIVE" if account.key in {"a05", "a10", "a13", "a14"} else ("POSITIVE" if account.key in {"a06", "a07", "a09"} else "NEUTRAL")
            lifecycle = "EXISTING_CUSTOMER" if account.key in EXISTING_KEYS else "NEW_CUSTOMER"
            occurred_at = now - timedelta(days=event_idx * 3 + idx % 5, hours=idx % 7)
            occurred_text = occurred_at.isoformat()
            batch_id = f"task203_v2_batch_{account.key}_{event_idx:02d}"
            event_id = f"task203_v2_event_{account.key}_{event_idx:02d}"
            memory_id = f"task203_v2_memory_{account.key}_{event_idx:02d}"
            signal_id = f"task203_v2_signal_{account.key}_{event_idx:02d}"
            evidence = [{
                "source": source,
                "quote": interaction,
                "occurredAt": occurred_text,
                "accountId": account_id,
            }]
            dimension = dimensions[(global_event_index - 1) % len(dimensions)]
            direction = "NEGATIVE" if account.key in {"a05", "a10", "a13", "a14"} else "POSITIVE"
            confidence = 0.58 if global_event_index % 7 == 0 else 0.86
            signal_status = "PENDING" if confidence < 0.65 else ("EXPIRED" if global_event_index % 13 == 0 else ("SUPERSEDED" if global_event_index % 11 == 0 else "ACTIVE"))
            memory_status = "RESOLVED" if global_event_index % 10 == 0 else ("SUPERSEDED" if global_event_index % 11 == 0 else "ACTIVE")
            analysis = {
                "summary": interaction,
                "sentiment": sentiment,
                "facts": [account.summary],
                "risks": account.risks,
                "opportunities": account.new_signals if lifecycle == "NEW_CUSTOMER" else account.existing_signals,
                "commitments": [account.next_actions[0]],
                "nextActions": account.next_actions,
                "evidence": evidence,
                "scoringSignals": [{
                    "dimension": dimension,
                    "direction": direction,
                    "impact": 8 if direction == "NEGATIVE" else 6,
                    "confidence": confidence,
                    "title": account.tags[0],
                    "reason": account.summary,
                    "evidence": interaction,
                    "validDays": 120,
                }],
            }
            statements.append(
                "INSERT INTO customer_interaction_batch "
                "(public_id, company_id, crm_account_id, created_by, source_type, occurred_at, subject, narration_text, "
                "pasted_text, status, combined_text, analysis_json, error_message, confirmed_event_id, created_at, updated_at) VALUES "
                f"({sql_quote(batch_id)}, {sql_quote(AGENT_COMPANY_ID)}, {sql_quote(account_id)}, {sql_quote(AGENT_USER_ID)}, "
                f"{sql_quote(source)}, {sql_quote(occurred_text)}, {sql_quote(account.name + '互动归档 ' + str(event_idx))}, "
                f"{sql_quote(interaction)}, '', 'CONFIRMED', {sql_quote(interaction)}, {sql_quote(json_text(analysis))}, '', "
                f"{sql_quote(event_id)}, now(), now());"
            )
            statements.append(
                "INSERT INTO customer_interaction_event "
                "(public_id, company_id, crm_account_id, crm_contact_id, source_type, occurred_at, subject, raw_summary, "
                "ai_summary, sentiment, intent_tags, lifecycle_area, source_batch_id, analysis_json, evidence_count, "
                "analysis_version, created_at, updated_at) VALUES "
                f"({sql_quote(event_id)}, {sql_quote(AGENT_COMPANY_ID)}, {sql_quote(account_id)}, "
                f"{sql_quote(contact_id)}, {sql_quote(source)}, {sql_quote(occurred_text)}, "
                f"{sql_quote(account.name + '客户互动摘要 ' + str(event_idx))}, {sql_quote(interaction)}, "
                f"{sql_quote(interaction)}, {sql_quote(sentiment)}, {sql_quote(json_text(account.tags))}, "
                f"{sql_quote(lifecycle)}, {sql_quote(batch_id)}, {sql_quote(json_text(analysis))}, 1, 2, now(), now());"
            )
            statements.append(
                "INSERT INTO customer_memory_item "
                "(public_id, company_id, crm_account_id, source_event_id, source_batch_id, memory_type, content, status, "
                "confidence, occurred_at, valid_until, evidence_json, created_at, updated_at) VALUES "
                f"({sql_quote(memory_id)}, {sql_quote(AGENT_COMPANY_ID)}, {sql_quote(account_id)}, {sql_quote(event_id)}, "
                f"{sql_quote(batch_id)}, {sql_quote(memory_types[(global_event_index - 1) % len(memory_types)])}, "
                f"{sql_quote(interaction)}, {sql_quote(memory_status)}, {confidence}, {sql_quote(occurred_text)}, "
                f"{sql_quote((now + timedelta(days=180)).isoformat())}, {sql_quote(json_text(evidence))}, now(), now());"
            )
            fingerprint = hashlib.sha256(f"{account_id}|{dimension}|{interaction}".encode("utf-8")).hexdigest()
            valid_until = now - timedelta(days=1) if signal_status == "EXPIRED" else now + timedelta(days=120)
            statements.append(
                "INSERT INTO customer_dynamic_signal "
                "(public_id, company_id, crm_account_id, source_event_id, source_batch_id, source_type, dimension, direction, "
                "impact, confidence, title, rationale, evidence_quote, status, occurred_at, valid_until, content_fingerprint, "
                "model_version, created_at, updated_at) VALUES "
                f"({sql_quote(signal_id)}, {sql_quote(AGENT_COMPANY_ID)}, {sql_quote(account_id)}, {sql_quote(event_id)}, "
                f"{sql_quote(batch_id)}, {sql_quote(source)}, {sql_quote(dimension)}, {sql_quote(direction)}, "
                f"{8 if direction == 'NEGATIVE' else 6}, {confidence}, {sql_quote(account.tags[0])}, "
                f"{sql_quote(account.summary)}, {sql_quote(interaction)}, {sql_quote(signal_status)}, {sql_quote(occurred_text)}, "
                f"{sql_quote(valid_until.isoformat())}, {sql_quote(fingerprint)}, 'task203-demo-v2', now(), now());"
            )
            if event_idx == 1:
                first_archives[account.key] = (event_id, batch_id, interaction)

    action_accounts = [account for account in ACCOUNTS if account.key not in SILENT_KEYS][:12]
    action_types = ["CREATE_TASK", "CREATE_OPPORTUNITY", "UPDATE_OPPORTUNITY"]
    for action_index, account in enumerate(action_accounts, start=1):
        account_id = ids["accounts"][account.name]
        event_id, batch_id, evidence_quote = first_archives[account.key]
        action_type = action_types[(action_index - 1) % len(action_types)]
        title = account.next_actions[0]
        rationale = f"互动原文出现明确经营承诺，建议{title}。"
        target_object = "Task" if action_type == "CREATE_TASK" else "Opportunity"
        target_record_id = None
        if action_type == "CREATE_TASK":
            payload = {
                "subject": title,
                "relateid": account_id,
                "relateobj": "Account",
                "status": "未开始",
                "priority": "普通",
                "expiredate": (date.today() + timedelta(days=7)).isoformat(),
                "remark": rationale + "\n证据：" + evidence_quote,
            }
        elif action_type == "CREATE_OPPORTUNITY":
            payload = {
                "name": account.name + " - 互动识别新机会",
                "khmc": account_id,
                "jieduan": "1-发现机会",
                "xyb": rationale,
            }
        else:
            target_record_id = ids["opportunities"][account.name + " - " + account.stage]
            payload = {"id": target_record_id, "xyb": rationale}
        evidence_json = [{
            "eventId": event_id,
            "batchId": batch_id,
            "title": title,
            "detail": evidence_quote,
            "source": "互动识别",
            "occurredAt": now.isoformat(),
        }]
        statements.append(
            "INSERT INTO customer_workbench_recommendation "
            "(public_id, company_id, crm_account_id, recommendation_type, title, rationale, confidence, status, crm_payload, "
            "applied_crm_id, version, target_object, target_record_id, evidence_json, dismissal_reason, confirmed_by, "
            "confirmed_at, applied_at, last_error_code, last_error_message, source_event_id, source_batch_id, action_key, "
            "trigger_type, valid_until, created_at, updated_at) VALUES "
            f"({sql_quote(f'task203_v2_action_{action_index:03d}')}, {sql_quote(AGENT_COMPANY_ID)}, {sql_quote(account_id)}, "
            f"{sql_quote(action_type)}, {sql_quote(title)}, {sql_quote(rationale)}, 0.88, 'PENDING', "
            f"{sql_quote(json_text(payload))}, NULL, 0, {sql_quote(target_object)}, {sql_quote(target_record_id)}, "
            f"{sql_quote(json_text(evidence_json))}, NULL, NULL, NULL, NULL, NULL, NULL, {sql_quote(event_id)}, "
            f"{sql_quote(batch_id)}, {sql_quote(f'task203:{account.key}:{action_type.lower()}')}, 'INTERACTION_AI', "
            f"{sql_quote((now + timedelta(days=45)).isoformat())}, now(), now());"
        )
    statements.extend(
        [
            "COMMIT;",
            f"SELECT 'snapshots=' || count(*) FROM customer_workbench_snapshot WHERE company_id = {sql_quote(AGENT_COMPANY_ID)};",
            f"SELECT 'batches=' || count(*) FROM customer_interaction_batch WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND public_id LIKE 'task203_v2_%';",
            f"SELECT 'events=' || count(*) FROM customer_interaction_event WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND public_id LIKE 'task203_v2_%';",
            f"SELECT 'memories=' || count(*) FROM customer_memory_item WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND public_id LIKE 'task203_v2_%';",
            f"SELECT 'dynamic_signals=' || count(*) FROM customer_dynamic_signal WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND public_id LIKE 'task203_v2_%';",
            f"SELECT 'score_snapshots=' || count(*) FROM customer_score_snapshot WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND crm_account_id IN ({account_id_list});",
            f"SELECT 'recommendations=' || count(*) FROM customer_workbench_recommendation WHERE company_id = {sql_quote(AGENT_COMPANY_ID)} AND public_id LIKE 'task203_v2_%';",
        ]
    )
    return "\n".join(statements) + "\n"


def apply_agentcici_sql(sql: str, ssh_key: Path, remote: str) -> str:
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    backup_cmd = (
        "set -euo pipefail; "
        f"BACKUP_DIR=/opt/cici/backups/{timestamp}-before-task203-demo-v2; "
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
        remote_sql = f"/tmp/task203-demo-v2-{timestamp}.sql"
        scp = run(["scp", "-i", str(ssh_key), str(local_sql), f"{remote}:{remote_sql}"], timeout=120)
        if scp.returncode != 0:
            raise RuntimeError(f"SCP SQL failed: {scp.stderr.strip()}")
        remote_cmd = (
            "set -euo pipefail; "
            f"docker cp {shlex.quote(remote_sql)} cici-database:/tmp/task203-demo-v2.sql; "
            "docker exec cici-database psql -U cici -d agentcici -v ON_ERROR_STOP=1 -f /tmp/task203-demo-v2.sql; "
            f"rm -f {shlex.quote(remote_sql)}"
        )
        apply = run(["ssh", "-i", str(ssh_key), remote, remote_cmd], timeout=180)
        if apply.returncode != 0:
            raise RuntimeError(f"Remote SQL apply failed: {apply.stderr.strip() or apply.stdout[-500:]}")
        return f"backup={backup.stdout.strip()}\n{apply.stdout.strip()}"
    finally:
        local_sql.unlink(missing_ok=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Seed TASK-203 comprehensive demo data into CloudCC CRM and AgentCiCi.")
    parser.add_argument("--dry-run", action="store_true", help="Validate identity and print planned counts without writes.")
    parser.add_argument("--crm-only", action="store_true", help="Only create/reuse CloudCC CRM records.")
    parser.add_argument("--agent-only", action="store_true", help="Only refresh AgentCiCi aggregate tables after ensuring CRM ids.")
    parser.add_argument(
        "--new-pipeline-only",
        action="store_true",
        help="Only create/reuse the dedicated CloudCC new-customer pipeline recovery batch.",
    )
    parser.add_argument("--ssh-key", default=str(DEFAULT_SSH_KEY), help="SSH key for the AgentCiCi ECS host.")
    parser.add_argument("--remote", default=DEFAULT_REMOTE, help="AgentCiCi ECS SSH remote.")
    return parser.parse_args()


def planned_counts() -> dict[str, int]:
    return {
        "accounts": len(ACCOUNTS),
        "contacts": (len(ACCOUNTS) - len(NO_CONTACT_KEYS)) * 2,
        "leads": len(ACCOUNTS) - len(EXISTING_KEYS),
        "opportunities": len(ACCOUNTS) - len(NO_OPPORTUNITY_KEYS) + 6,
        "tasks": (len(ACCOUNTS) - len(SILENT_KEYS)) * 2,
        "events": (len(ACCOUNTS) - len(SILENT_KEYS)) * 3,
        "contracts": len(EXISTING_KEYS),
        "cases": 8,
        "interaction_archives": (len(ACCOUNTS) - len(SILENT_KEYS)) * 2,
        "memories": (len(ACCOUNTS) - len(SILENT_KEYS)) * 2,
        "dynamic_signals": (len(ACCOUNTS) - len(SILENT_KEYS)) * 2,
        "evidence_actions": 12,
        "new_pipeline_accounts": len(NEW_PIPELINE_ACCOUNTS),
        "new_pipeline_contacts": len(NEW_PIPELINE_ACCOUNTS) - 1,
        "new_pipeline_leads": len(NEW_PIPELINE_ACCOUNTS),
        "new_pipeline_opportunities": len(NEW_PIPELINE_ACCOUNTS) - 1,
        "new_pipeline_tasks": len(NEW_PIPELINE_ACCOUNTS) * 2,
        "new_pipeline_events": len(NEW_PIPELINE_ACCOUNTS) * 2,
    }


def verify_owner_identity() -> None:
    rows = page_query("ccuser", "id,name,email", 200)
    matched = [row for row in rows if str(row.get("id", "")) == CRM_OWNER_ID and str(row.get("name", "")) == CRM_OWNER_NAME]
    if not matched:
        raise RuntimeError("CloudCC SalesA owner identity is no longer available in the bound tenant")


def main() -> int:
    args = parse_args()
    if sum((args.crm_only, args.agent_only, args.new_pipeline_only)) > 1:
        raise SystemExit("--crm-only, --agent-only and --new-pipeline-only cannot be used together")
    if not CLOUDCC.exists():
        raise SystemExit(f"CloudCC CLI not found: {CLOUDCC}")
    print(f"Target AgentCiCi company: {AGENT_COMPANY_ID}")
    print(f"Target CloudCC org: {CLOUDCC_ORG_ID}")
    print(f"Demo batch: {BATCH}")
    print("Planned records: " + ", ".join(f"{key}={value}" for key, value in planned_counts().items()))
    verify_owner_identity()
    print(f"CloudCC owner verified: {CRM_OWNER_NAME}")
    if args.dry_run:
        print("Dry-run complete: no CRM or AgentCiCi writes were performed.")
        return 0

    if args.agent_only:
        ids = resolve_agent_record_ids()
    elif args.new_pipeline_only:
        ids = seed_new_customer_pipeline(date.today())
    else:
        ids = crm_records()
    print(
        "CloudCC records ready: "
        + ", ".join(f"{key}={len(value)}" for key, value in ids.items())
    )
    if args.crm_only or args.new_pipeline_only:
        return 0

    sql = build_agent_sql(ids)
    result = apply_agentcici_sql(sql, Path(args.ssh_key), args.remote)
    print("AgentCiCi aggregate refresh complete:")
    print(result)
    return 0


if __name__ == "__main__":
    sys.exit(main())
