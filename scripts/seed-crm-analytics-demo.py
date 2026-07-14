#!/usr/bin/env python3
"""Build and optionally seed the TASK-205 CloudCC CRM analytics demo dataset.

The default mode is a read-only dry run. Only ``--execute`` writes records.
Records are matched by stable batch markers and are created or updated without
deleting any tenant data. Output never includes credentials.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from collections import defaultdict
from dataclasses import dataclass
from datetime import date, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any, Callable, Iterable


ROOT = Path(__file__).resolve().parents[1]
CLOUDCC = Path("/Users/owenmacbook/.agents/skills/cc-customization-expert-msapi/tools/bin/cloudcc")
BATCH = "TASK-205-CRM-ANALYTICS-DEMO-V1"


@dataclass(frozen=True)
class Product:
    code: str
    name: str
    family: str
    price: int
    base_quantity: int


PRODUCTS = [
    Product("DEMO-X1", "智能巡检终端 X1", "智能终端", 6800, 24),
    Product("DEMO-G5", "边缘采集网关 G5", "边缘网关", 12800, 20),
    Product("DEMO-S2", "安全监测传感器 S2", "智能传感", 3200, 17),
    Product("DEMO-MP", "制造运营分析平台 MP", "分析平台", 38000, 13),
    Product("DEMO-PA", "预测性维护应用 PA", "业务应用", 26000, 11),
    Product("DEMO-FS", "现场服务协同套件 FS", "业务应用", 18000, 9),
    Product("DEMO-VI", "机器视觉质检模块 VI", "智能质检", 22000, 8),
    Product("DEMO-DH", "经营数据中台 DH", "数据平台", 46000, 7),
    Product("DEMO-IM", "设备集成管理器 IM", "集成软件", 15000, 6),
    Product("DEMO-TR", "工业培训服务 TR", "专业服务", 8000, 5),
    Product("DEMO-API", "开放接口服务包 API", "平台服务", 12000, 4),
    Product("DEMO-BK", "基础知识库服务 BK", "平台服务", 6000, 3),
]

ACCOUNT_NAMES = [
    "样例数据-佩坚有限责任公司",
    "样例数据-雷领有限公司",
    "样例数据-南营有限公司",
    "样例数据-识鸿集团",
    "样例数据-悦广科技实业有限公司",
    "样例数据-宏川有限公司",
    "样例数据-茂帝有限公司",
    "样例数据-德盛有限责任公司",
    "样例数据-拓士有限责任公司",
    "样例数据-银尚设计工作室",
    "样例数据-微荣有限公司",
    "样例数据-创佳有限责任公司",
    "样例数据-春帝有限责任公司",
    "样例数据-天东有限公司",
    "样例数据-宜创有限公司",
    "样例数据-伟彩有限责任公司",
]

OPPORTUNITY_STAGES = [
    "1-发现机会",
    "2-需求分析",
    "3-提出方案",
    "4-确定关键决策人",
    "5-招标/报价",
    "6-商讨/审核",
    "7-签约关单",
    "8-丢单",
]


def marker(kind: str, key: str) -> str:
    return f"{BATCH}|{kind}:{key}|"


def build_dataset(as_of: date) -> dict[str, list[dict[str, Any]]]:
    products = [
        {
            "_key": product.code,
            "name": product.name,
            "cpdm": product.code,
            "unit": "个",
            "productprice": str(product.price),
            "yqy": "true",
        }
        for product in PRODUCTS
    ]
    accounts = [
        {
            "_key": name,
            "name": name,
            "beizhu": f"{BATCH} | CRM 经营分析演示复用客户",
        }
        for name in ACCOUNT_NAMES
    ]

    opportunities: list[dict[str, Any]] = []
    for index in range(24):
        key = f"OPP-{index + 1:03d}"
        product = PRODUCTS[index % len(PRODUCTS)]
        opportunities.append(
            {
                "_key": key,
                "_account": ACCOUNT_NAMES[index % len(ACCOUNT_NAMES)],
                "name": f"经营分析演示-{key}-{product.name}",
                "jieduan": OPPORTUNITY_STAGES[index % len(OPPORTUNITY_STAGES)],
                "jine": str(product.price * (8 + index % 7)),
                "jsrq": (as_of + timedelta(days=15 + index * 3)).isoformat(),
                "description": marker("OPPORTUNITY", key)
                + f" 高仿真销售漏斗；主推产品 {product.code}；包含赢单、培育和丢单分布。",
            }
        )

    opportunity_products: list[dict[str, Any]] = []
    for opportunity_index in range(24):
        opportunity_key = f"OPP-{opportunity_index + 1:03d}"
        for slot in range(3):
            product = PRODUCTS[(opportunity_index + slot * 3) % len(PRODUCTS)]
            key = f"{opportunity_key}-{slot + 1:02d}"
            quantity = 2 + (opportunity_index + slot) % 9
            opportunity_products.append(
                {
                    "_key": key,
                    "_opportunity": opportunity_key,
                    "_product": product.code,
                    "name": f"经营分析演示商机产品-{key}",
                    "quantity": str(quantity),
                    "unitprice": str(product.price),
                    "totalprice": str(quantity * product.price),
                    "subtotal": str(quantity * product.price),
                    "unit": "个",
                    "description": marker("OPPORTUNITY_PRODUCT", key)
                    + " 演示商机产品组合和管道金额。",
                }
            )

    contracts: list[dict[str, Any]] = []
    contract_statuses = ["已启用", "审批通过", "审批中", "草稿"]
    for index in range(16):
        key = f"CONTRACT-{index + 1:03d}"
        product = PRODUCTS[index % len(PRODUCTS)]
        sign_date = as_of - timedelta(days=20 + index * 7)
        contracts.append(
            {
                "_key": key,
                "_account": ACCOUNT_NAMES[index],
                "_opportunity": f"OPP-{index + 1:03d}",
                "name": f"经营分析演示-{key}-{ACCOUNT_NAMES[index]}",
                "contractnumber": f"DEMO-CT-{index + 1:04d}",
                "htje": str(product.price * (15 + index)),
                "qdrq": sign_date.isoformat(),
                "qyrq": sign_date.isoformat(),
                "htksrq": sign_date.isoformat(),
                "htjsrq": (sign_date + timedelta(days=365)).isoformat(),
                "zhuangtai": contract_statuses[index % len(contract_statuses)],
                "beizhu": marker("CONTRACT", key)
                + " 演示合同状态、金额、签约周期及客户关联。",
            }
        )

    orders: list[dict[str, Any]] = []
    order_items: list[dict[str, Any]] = []
    valid_current_line = 0
    for order_index in range(48):
        order_key = f"ORDER-{order_index + 1:03d}"
        if order_index < 24:
            order_date = as_of - timedelta(days=(order_index * 3) % 28)
            status = "已生效" if order_index < 20 else "草稿"
            period = "CURRENT"
        elif order_index < 36:
            order_date = as_of - timedelta(days=31 + (order_index - 24) * 2)
            status = "已生效"
            period = "PREVIOUS"
        else:
            order_date = as_of - timedelta(days=70 + (order_index - 36) * 8)
            status = "草稿" if order_index == 47 else "已生效"
            period = "HISTORICAL"

        lines: list[dict[str, Any]] = []
        for slot in range(3):
            if period == "CURRENT" and status == "已生效":
                product_index = valid_current_line % len(PRODUCTS)
                round_no = valid_current_line // len(PRODUCTS)
                product = PRODUCTS[product_index]
                quantity = product.base_quantity + round_no
                valid_current_line += 1
            elif period == "CURRENT":
                product = PRODUCTS[(order_index + slot + 3) % len(PRODUCTS)]
                quantity = 700 + order_index * 10 + slot
            else:
                product = PRODUCTS[(order_index * 3 + slot) % len(PRODUCTS)]
                quantity = max(1, product.base_quantity - 5 + (order_index + slot) % 6)
            line_key = f"{order_key}-{slot + 1:02d}"
            lines.append(
                {
                    "_key": line_key,
                    "_order": order_key,
                    "_product": product.code,
                    "name": f"经营分析演示订单明细-{line_key}",
                    "quantity": str(quantity),
                    "unitprice": str(product.price),
                    "totalprice": str(quantity * product.price),
                    "productcode": product.code,
                    "unit": "个",
                    "description": marker("ORDER_ITEM", line_key)
                    + f" {period}；{'有效成交' if status == '已生效' else '无效高数量反例'}。",
                }
            )
        total_amount = sum(int(line["totalprice"]) for line in lines)
        account_name = ACCOUNT_NAMES[order_index % len(ACCOUNT_NAMES)]
        orders.append(
            {
                "_key": order_key,
                "_account": account_name,
                "_opportunity": f"OPP-{order_index % 24 + 1:03d}",
                "_contract": f"CONTRACT-{order_index % 16 + 1:03d}",
                "name": f"经营分析演示-{order_key}",
                "podate": order_date.isoformat(),
                "status": status,
                "totalamount": str(total_amount),
                "description": marker("ORDER", order_key)
                + f" {period}；{status}；多客户、多周期产品销售演示。",
            }
        )
        order_items.extend(lines)

    return {
        "products": products,
        "accounts": accounts,
        "opportunities": opportunities,
        "opportunityProducts": opportunity_products,
        "contracts": contracts,
        "orders": orders,
        "orderItems": order_items,
    }


def summarize(dataset: dict[str, list[dict[str, Any]]], as_of: date) -> dict[str, Any]:
    product_by_code = {product.code: product for product in PRODUCTS}
    order_by_key = {item["_key"]: item for item in dataset["orders"]}
    quantity: dict[str, Decimal] = defaultdict(Decimal)
    amount: dict[str, Decimal] = defaultdict(Decimal)
    invalid_quantity = Decimal(0)
    for item in dataset["orderItems"]:
        order = order_by_key[item["_order"]]
        order_date = date.fromisoformat(order["podate"])
        in_current = as_of - timedelta(days=29) <= order_date <= as_of
        if not in_current:
            continue
        item_quantity = Decimal(item["quantity"])
        if order["status"] != "已生效":
            invalid_quantity += item_quantity
            continue
        quantity[item["_product"]] += item_quantity
        amount[item["_product"]] += Decimal(item["totalprice"])

    quantity_rank = sorted(
        product_by_code,
        key=lambda code: (-quantity[code], -amount[code], code),
    )
    amount_rank = sorted(
        product_by_code,
        key=lambda code: (-amount[code], -quantity[code], code),
    )
    order_keys = set(order_by_key)
    product_codes = set(product_by_code)
    return {
        "mode": "DRY_RUN",
        "batch": BATCH,
        "asOf": as_of.isoformat(),
        "counts": {key: len(value) for key, value in dataset.items()},
        "invalidCurrentOrders": sum(
            1
            for order in dataset["orders"]
            if as_of - timedelta(days=29) <= date.fromisoformat(order["podate"]) <= as_of
            and order["status"] != "已生效"
        ),
        "expectedRankings": {
            "last30DaysQuantity": quantity_rank,
            "last30DaysAmount": amount_rank,
        },
        "expectedTopMetrics": [
            {
                "productCode": code,
                "productName": product_by_code[code].name,
                "salesQuantity": str(quantity[code]),
                "salesAmount": str(amount[code]),
            }
            for code in quantity_rank[:5]
        ],
        "qualityChecks": {
            "invalidHighQuantityExcluded": invalid_quantity > max(quantity.values()),
            "allOrderItemsResolveProduct": all(item["_product"] in product_codes for item in dataset["orderItems"]),
            "allOrderItemsResolveOrder": all(item["_order"] in order_keys for item in dataset["orderItems"]),
            "quantityAndAmountRankingDiffer": quantity_rank[:3] != amount_rank[:3],
        },
    }


def run_cloudcc(action: str, object_api: str, payload: dict[str, Any]) -> dict[str, Any]:
    body = json.dumps({"objectApiName": object_api, **payload}, ensure_ascii=False, separators=(",", ":"))
    process = subprocess.run(
        [str(CLOUDCC), action, "openapi", ".", body],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=180,
        check=False,
    )
    if process.returncode != 0:
        message = process.stderr.strip() or process.stdout.strip()[-500:]
        raise RuntimeError(f"CloudCC {action} {object_api} failed: {message}")
    start = process.stdout.find("{")
    if start < 0:
        raise RuntimeError(f"CloudCC {action} {object_api} did not return JSON")
    result = json.loads(process.stdout[start:])
    if result.get("result") is not True:
        raise RuntimeError(f"CloudCC {action} {object_api} returned failure: {result.get('returnInfo')}")
    return result


def page_query_all(object_api: str, fields: str) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    page = 1
    while page <= 50:
        result = run_cloudcc(
            "pageQuery",
            object_api,
            {"fields": fields, "pageNUM": page, "pageSize": 200},
        )
        batch = result.get("data")
        if not isinstance(batch, list):
            break
        records.extend(item for item in batch if isinstance(item, dict))
        page_count = int(result.get("pageCount") or (1 if batch else 0))
        if not batch or page >= page_count:
            break
        page += 1
    return records


def chunks(items: list[dict[str, Any]], size: int = 40) -> Iterable[list[dict[str, Any]]]:
    for index in range(0, len(items), size):
        yield items[index : index + size]


def clean_record(record: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in record.items() if not key.startswith("_")}


def extract_created_ids(result: dict[str, Any]) -> list[str]:
    data = result.get("data")
    raw_ids = data.get("ids", []) if isinstance(data, dict) else []
    ids: list[str] = []
    for item in raw_ids:
        if isinstance(item, dict):
            if item.get("success") is not True:
                raise RuntimeError(f"CloudCC record create failed: {item.get('errors')}")
            ids.append(str(item.get("id") or ""))
        elif item:
            ids.append(str(item))
    return ids


def sync_records(
    object_api: str,
    fields: str,
    desired: list[dict[str, Any]],
    existing_key: Callable[[dict[str, Any]], str | None],
    *,
    update_existing: bool = True,
) -> tuple[dict[str, str], dict[str, int]]:
    existing_rows = page_query_all(object_api, fields)
    ids: dict[str, str] = {}
    for row in existing_rows:
        key = existing_key(row)
        if key and row.get("id"):
            ids[key] = str(row["id"])

    missing = [record for record in desired if record["_key"] not in ids]
    for batch in chunks(missing):
        result = run_cloudcc("create", object_api, {"data": [clean_record(record) for record in batch]})
        created_ids = extract_created_ids(result)
        if len(created_ids) != len(batch):
            raise RuntimeError(f"CloudCC create {object_api} returned unexpected id count")
        for record, record_id in zip(batch, created_ids):
            ids[record["_key"]] = record_id

    existing_desired = [record for record in desired if record["_key"] in ids and record not in missing]
    if update_existing:
        for batch in chunks(existing_desired):
            payload = [
                {"id": ids[record["_key"]], **clean_record(record)}
                for record in batch
            ]
            run_cloudcc("update", object_api, {"data": payload})
    return ids, {
        "created": len(missing),
        "updated": len(existing_desired) if update_existing else 0,
        "reused": len(existing_desired) if not update_existing else 0,
    }


def key_from_marker(field: str, kind: str) -> Callable[[dict[str, Any]], str | None]:
    prefix = f"{BATCH}|{kind}:"

    def reader(row: dict[str, Any]) -> str | None:
        value = str(row.get(field) or "")
        start = value.find(prefix)
        if start < 0:
            return None
        remainder = value[start + len(prefix) :]
        return remainder.split("|", 1)[0] or None

    return reader


def resolve_records(
    records: list[dict[str, Any]],
    mappings: dict[str, tuple[str, dict[str, str]]],
) -> list[dict[str, Any]]:
    resolved: list[dict[str, Any]] = []
    for record in records:
        item = dict(record)
        for internal_field, (api_field, ids) in mappings.items():
            key = str(item.pop(internal_field))
            if key not in ids:
                raise RuntimeError(f"Cannot resolve {internal_field} reference: {key}")
            item[api_field] = ids[key]
        resolved.append(item)
    return resolved


def execute(dataset: dict[str, list[dict[str, Any]]]) -> dict[str, dict[str, int]]:
    if not CLOUDCC.exists():
        raise RuntimeError(f"CloudCC CLI not found: {CLOUDCC}")
    stats: dict[str, dict[str, int]] = {}

    account_ids, stats["accounts"] = sync_records(
        "Account",
        "id,name,beizhu",
        dataset["accounts"],
        lambda row: str(row.get("name")) if row.get("name") in ACCOUNT_NAMES else None,
        update_existing=False,
    )
    product_ids, stats["products"] = sync_records(
        "product",
        "id,name,cpdm,unit,productprice,yqy",
        dataset["products"],
        lambda row: str(row.get("cpdm")) if str(row.get("cpdm") or "").startswith("DEMO-") else None,
    )

    opportunity_records = resolve_records(
        dataset["opportunities"], {"_account": ("khmc", account_ids)}
    )
    opportunity_ids, stats["opportunities"] = sync_records(
        "Opportunity",
        "id,name,khmc,jieduan,jine,jsrq,description",
        opportunity_records,
        key_from_marker("description", "OPPORTUNITY"),
    )

    contract_records = resolve_records(
        dataset["contracts"],
        {
            "_account": ("khmc", account_ids),
            "_opportunity": ("opportunityid", opportunity_ids),
        },
    )
    contract_ids, stats["contracts"] = sync_records(
        "contract",
        "id,name,contractnumber,khmc,opportunityid,htje,qdrq,qyrq,htksrq,htjsrq,zhuangtai,beizhu",
        contract_records,
        key_from_marker("beizhu", "CONTRACT"),
    )

    order_records = resolve_records(
        dataset["orders"],
        {
            "_account": ("accountid", account_ids),
            "_opportunity": ("opportunityid", opportunity_ids),
            "_contract": ("contractid", contract_ids),
        },
    )
    order_ids, stats["orders"] = sync_records(
        "cloudccorder",
        "id,name,accountid,contractid,opportunityid,podate,status,totalamount,description",
        order_records,
        key_from_marker("description", "ORDER"),
    )

    order_item_records = resolve_records(
        dataset["orderItems"],
        {
            "_order": ("orderid", order_ids),
            "_product": ("product2id", product_ids),
        },
    )
    _, stats["orderItems"] = sync_records(
        "cloudccorderitem",
        "id,name,orderid,product2id,quantity,unitprice,totalprice,productcode,unit,description",
        order_item_records,
        key_from_marker("description", "ORDER_ITEM"),
    )

    opportunity_product_records = resolve_records(
        dataset["opportunityProducts"],
        {
            "_opportunity": ("opportunity", opportunity_ids),
            "_product": ("product2", product_ids),
        },
    )
    _, stats["opportunityProducts"] = sync_records(
        "opportunitypdt",
        "id,name,opportunity,product2,quantity,unitprice,totalprice,subtotal,unit,description",
        opportunity_product_records,
        key_from_marker("description", "OPPORTUNITY_PRODUCT"),
    )
    return stats


def inspect_existing() -> dict[str, int]:
    probes = [
        ("product", "id,cpdm", lambda row: str(row.get("cpdm") or "").startswith("DEMO-")),
        ("Opportunity", "id,description", lambda row: BATCH in str(row.get("description") or "")),
        ("contract", "id,beizhu", lambda row: BATCH in str(row.get("beizhu") or "")),
        ("cloudccorder", "id,description", lambda row: BATCH in str(row.get("description") or "")),
        ("cloudccorderitem", "id,description", lambda row: BATCH in str(row.get("description") or "")),
        ("opportunitypdt", "id,description", lambda row: BATCH in str(row.get("description") or "")),
    ]
    return {
        object_api: sum(1 for row in page_query_all(object_api, fields) if predicate(row))
        for object_api, fields, predicate in probes
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Seed high-fidelity CloudCC CRM analytics demo data.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="Build and inspect the plan without writes (default).")
    mode.add_argument("--execute", action="store_true", help="Create or update only TASK-205 batch records.")
    parser.add_argument("--offline", action="store_true", help="Skip all CloudCC reads; useful for contract tests.")
    parser.add_argument("--as-of", type=date.fromisoformat, default=date.today(), help="Dataset anchor date YYYY-MM-DD.")
    parser.add_argument("--json", action="store_true", help="Print machine-readable JSON only.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.execute and args.offline:
        raise SystemExit("--execute cannot be combined with --offline")
    dataset = build_dataset(args.as_of)
    report = summarize(dataset, args.as_of)
    if args.execute:
        report["mode"] = "EXECUTE"
        report["writeStats"] = execute(dataset)
    elif not args.offline:
        if not CLOUDCC.exists():
            raise SystemExit(f"CloudCC CLI not found: {CLOUDCC}")
        report["remoteExisting"] = inspect_existing()

    if args.json:
        print(json.dumps(report, ensure_ascii=False, separators=(",", ":")))
    else:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
