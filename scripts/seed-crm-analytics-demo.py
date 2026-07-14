#!/usr/bin/env python3
"""Plan and optionally apply the TASK-205 CRM analytics ownership repair.

The default mode is a read-only dry run. The script discovers the existing
TASK-205 batch and the existing TASK-203 V2 accounts, validates the complete
boundary, and produces a minimal update/rollback plan. Only explicit
``--execute`` mode writes, and the only write operation is OpenAPI ``update``.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from collections import defaultdict
from dataclasses import dataclass
from datetime import date, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
CLOUDCC = Path("/Users/owenmacbook/.agents/skills/cc-customization-expert-msapi/tools/bin/cloudcc")
BATCH = "TASK-205-CRM-ANALYTICS-DEMO-V1"
V2_ACCOUNT_BATCH = "TASK-203-DEMO-V2"
TARGET_OWNER_ID = "00520264AE58B11bw6gE"
TARGET_OWNER_NAME = "SalesA"
SOURCE_OWNER_ID = "0052017BE8702F1PIi4j"

EXPECTED_MIGRATION_COUNTS = {
    "products": 12,
    "opportunities": 24,
    "opportunityProducts": 72,
    "contracts": 16,
    "orders": 48,
    "orderItems": 144,
}


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


@dataclass(frozen=True)
class MigrationSpec:
    dataset_key: str
    object_api: str
    fields: str
    marker_field: str | None
    marker_kind: str | None
    account_field: str | None = None
    required_references: tuple[str, ...] = ()


MIGRATION_SPECS = (
    MigrationSpec("products", "product", "id,cpdm,ownerid", None, None),
    MigrationSpec(
        "opportunities",
        "Opportunity",
        "id,ownerid,khmc,description",
        "description",
        "OPPORTUNITY",
        "khmc",
    ),
    MigrationSpec(
        "opportunityProducts",
        "opportunitypdt",
        "id,ownerid,opportunity,product2,description",
        "description",
        "OPPORTUNITY_PRODUCT",
        required_references=("opportunity", "product2"),
    ),
    MigrationSpec(
        "contracts",
        "contract",
        "id,ownerid,khmc,opportunityid,beizhu",
        "beizhu",
        "CONTRACT",
        "khmc",
        ("opportunityid",),
    ),
    MigrationSpec(
        "orders",
        "cloudccorder",
        "id,ownerid,accountid,opportunityid,contractid,description",
        "description",
        "ORDER",
        "accountid",
        ("opportunityid", "contractid"),
    ),
    MigrationSpec(
        "orderItems",
        "cloudccorderitem",
        "id,ownerid,orderid,product2id,description",
        "description",
        "ORDER_ITEM",
        required_references=("orderid", "product2id"),
    ),
)


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
            "ownerid": TARGET_OWNER_ID,
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
                "ownerid": TARGET_OWNER_ID,
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
                    "ownerid": TARGET_OWNER_ID,
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
                "ownerid": TARGET_OWNER_ID,
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
                    "ownerid": TARGET_OWNER_ID,
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
                "ownerid": TARGET_OWNER_ID,
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
        "migrationPlan": {
            "sourceBatch": BATCH,
            "targetOwner": {"id": TARGET_OWNER_ID, "name": TARGET_OWNER_NAME},
            "targetAccounts": {
                "marker": V2_ACCOUNT_BATCH,
                "requiredCount": 16,
                "stableSort": "customerName",
            },
            "expectedCounts": EXPECTED_MIGRATION_COUNTS,
            "expectedOwnerUpdates": sum(EXPECTED_MIGRATION_COUNTS.values()),
            "expectedAccountRelinks": (
                EXPECTED_MIGRATION_COUNTS["opportunities"]
                + EXPECTED_MIGRATION_COUNTS["contracts"]
                + EXPECTED_MIGRATION_COUNTS["orders"]
            ),
            "writePolicy": {
                "allowedActions": ["update"],
                "creates": 0,
                "accountWrites": 0,
            },
            "rollbackManifest": {
                "requiredFields": [
                    "objectApiName",
                    "id",
                    "oldOwnerId",
                    "oldAccountId",
                    "targetOwnerId",
                    "targetAccountId",
                ],
                "records": [],
            },
        },
    }


def run_cloudcc(
    action: str,
    object_api: str,
    payload: dict[str, Any],
    project: Path,
    cloudcc_cli: Path = CLOUDCC,
) -> dict[str, Any]:
    body = json.dumps({"objectApiName": object_api, **payload}, ensure_ascii=False, separators=(",", ":"))
    process = subprocess.run(
        [str(cloudcc_cli), action, "openapi", str(project), body],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=180,
        check=False,
    )
    if process.returncode != 0:
        raise RuntimeError(f"CloudCC {action} {object_api} failed (exit {process.returncode})")
    start = process.stdout.find("{")
    if start < 0:
        raise RuntimeError(f"CloudCC {action} {object_api} did not return JSON")
    result = json.loads(process.stdout[start:])
    if result.get("result") is not True:
        raise RuntimeError(f"CloudCC {action} {object_api} returned failure")
    return result


def page_query_all(
    object_api: str,
    fields: str,
    project: Path,
    cloudcc_cli: Path = CLOUDCC,
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    page = 1
    while page <= 50:
        result = run_cloudcc(
            "pageQuery",
            object_api,
            {"fields": fields, "pageNUM": page, "pageSize": 200},
            project,
            cloudcc_cli,
        )
        batch = result.get("data")
        if not isinstance(batch, list):
            raise RuntimeError(f"CloudCC pageQuery {object_api} returned invalid data")
        records.extend(item for item in batch if isinstance(item, dict))
        page_count = int(result.get("pageCount") or (1 if batch else 0))
        if not batch or page >= page_count:
            break
        page += 1
    if page > 50:
        raise RuntimeError(f"CloudCC pageQuery {object_api} exceeded pagination safety limit")
    return records


def chunks(items: list[dict[str, Any]], size: int = 40) -> Iterable[list[dict[str, Any]]]:
    for index in range(0, len(items), size):
        yield items[index : index + size]


def reference_id(value: Any) -> str:
    if isinstance(value, dict):
        value = value.get("id")
    return str(value or "").strip()


def marker_key(value: Any, kind: str) -> str | None:
    text = str(value or "")
    prefix = f"{BATCH}|{kind}:"
    start = text.find(prefix)
    if start < 0:
        return None
    remainder = text[start + len(prefix) :]
    key, separator, _ = remainder.partition("|")
    return key if separator and key else None


def require_rows(snapshot: dict[str, Any], key: str) -> list[dict[str, Any]]:
    value = snapshot.get(key)
    if not isinstance(value, list) or any(not isinstance(item, dict) for item in value):
        raise RuntimeError(f"snapshot {key} must be an array of records")
    return value


def load_snapshot(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise RuntimeError("snapshot root must be an object")
    return value


def read_remote_snapshot(project: Path, cloudcc_cli: Path = CLOUDCC) -> dict[str, Any]:
    if not cloudcc_cli.exists():
        raise RuntimeError(f"CloudCC CLI not found: {cloudcc_cli}")
    snapshot: dict[str, Any] = {
        "users": page_query_all("ccuser", "id,name,loginname,isusing", project, cloudcc_cli),
        "accounts": page_query_all("Account", "id,name,ownerid,beizhu", project, cloudcc_cli),
    }
    for spec in MIGRATION_SPECS:
        snapshot[spec.dataset_key] = page_query_all(spec.object_api, spec.fields, project, cloudcc_cli)
    return snapshot


def validate_dataset_contract(dataset: dict[str, list[dict[str, Any]]]) -> None:
    for key, expected_count in EXPECTED_MIGRATION_COUNTS.items():
        records = dataset.get(key, [])
        if len(records) != expected_count:
            raise RuntimeError(f"dataset {key} count mismatch: expected {expected_count}, found {len(records)}")
        keys = [str(record.get("_key") or "") for record in records]
        if any(not key_value for key_value in keys) or len(set(keys)) != expected_count:
            raise RuntimeError(f"dataset {key} has missing or duplicate stable keys")


def discover_target_owner(snapshot: dict[str, Any]) -> None:
    matches = [row for row in require_rows(snapshot, "users") if reference_id(row.get("id")) == TARGET_OWNER_ID]
    if len(matches) != 1:
        raise RuntimeError(f"target owner lookup mismatch: expected 1 SalesA user, found {len(matches)}")
    owner = matches[0]
    if str(owner.get("name") or "").strip() != TARGET_OWNER_NAME:
        raise RuntimeError("target owner name mismatch")
    if str(owner.get("isusing") or "").strip().lower() not in {"1", "true"}:
        raise RuntimeError("target owner is not active")


def discover_target_accounts(snapshot: dict[str, Any]) -> list[dict[str, Any]]:
    accounts = [
        row
        for row in require_rows(snapshot, "accounts")
        if V2_ACCOUNT_BATCH in str(row.get("beizhu") or "")
    ]
    if len(accounts) != 16:
        raise RuntimeError(f"V2 Account count mismatch: expected 16, found {len(accounts)}")
    ids = [reference_id(row.get("id")) for row in accounts]
    names = [str(row.get("name") or "").strip() for row in accounts]
    if any(not account_id for account_id in ids) or len(set(ids)) != 16:
        raise RuntimeError("V2 Account has missing or duplicate id")
    if any(not name for name in names) or len(set(names)) != 16:
        raise RuntimeError("V2 Account has missing or duplicate name")
    if any(reference_id(row.get("ownerid")) != TARGET_OWNER_ID for row in accounts):
        raise RuntimeError("V2 Account owner mismatch")
    return sorted(accounts, key=lambda row: str(row.get("name") or ""))


def select_batch_records(
    dataset: dict[str, list[dict[str, Any]]],
    snapshot: dict[str, Any],
) -> dict[str, list[tuple[str, dict[str, Any]]]]:
    selected: dict[str, list[tuple[str, dict[str, Any]]]] = {}
    for spec in MIGRATION_SPECS:
        desired_keys = {str(record["_key"]) for record in dataset[spec.dataset_key]}
        rows = require_rows(snapshot, spec.dataset_key)
        if spec.marker_field is None:
            candidates = [row for row in rows if str(row.get("cpdm") or "") in desired_keys]
        else:
            candidates = [row for row in rows if BATCH in str(row.get(spec.marker_field) or "")]
        expected_count = EXPECTED_MIGRATION_COUNTS[spec.dataset_key]
        if len(candidates) != expected_count:
            raise RuntimeError(
                f"{spec.object_api} batch count mismatch: expected {expected_count}, found {len(candidates)}"
            )
        by_key: dict[str, dict[str, Any]] = {}
        for row in candidates:
            key = (
                str(row.get("cpdm") or "")
                if spec.marker_field is None
                else marker_key(row.get(spec.marker_field), str(spec.marker_kind))
            )
            if not key or key not in desired_keys:
                raise RuntimeError(f"{spec.object_api} marker mismatch")
            if key in by_key:
                raise RuntimeError(f"{spec.object_api} duplicate stable key: {key}")
            if not reference_id(row.get("id")):
                raise RuntimeError(f"{spec.object_api} record {key} missing id")
            owner_id = reference_id(row.get("ownerid"))
            if not owner_id:
                raise RuntimeError(f"{spec.object_api} record {key} missing owner")
            if owner_id not in {SOURCE_OWNER_ID, TARGET_OWNER_ID}:
                raise RuntimeError(f"{spec.object_api} record {key} owner mismatch")
            by_key[key] = row
        if set(by_key) != desired_keys:
            raise RuntimeError(f"{spec.object_api} stable key boundary mismatch")
        selected[spec.dataset_key] = [(key, by_key[key]) for key in sorted(by_key)]
    validate_batch_references(selected)
    return selected


def validate_batch_references(selected: dict[str, list[tuple[str, dict[str, Any]]]]) -> None:
    id_sets = {
        key: {reference_id(row.get("id")) for _, row in records}
        for key, records in selected.items()
    }
    reference_targets = {
        ("opportunityProducts", "opportunity"): "opportunities",
        ("opportunityProducts", "product2"): "products",
        ("contracts", "opportunityid"): "opportunities",
        ("orders", "opportunityid"): "opportunities",
        ("orders", "contractid"): "contracts",
        ("orderItems", "orderid"): "orders",
        ("orderItems", "product2id"): "products",
    }
    for spec in MIGRATION_SPECS:
        for key, row in selected[spec.dataset_key]:
            if spec.account_field and not reference_id(row.get(spec.account_field)):
                raise RuntimeError(f"{spec.object_api} record {key} missing {spec.account_field}")
            for field in spec.required_references:
                target_key = reference_targets[(spec.dataset_key, field)]
                value = reference_id(row.get(field))
                if not value:
                    raise RuntimeError(f"{spec.object_api} record {key} missing {field}")
                if value not in id_sets[target_key]:
                    raise RuntimeError(f"{spec.object_api} record {key} has invalid {field}")


def build_migration_plan(
    dataset: dict[str, list[dict[str, Any]]],
    snapshot: dict[str, Any],
) -> dict[str, Any]:
    validate_dataset_contract(dataset)
    discover_target_owner(snapshot)
    target_accounts = discover_target_accounts(snapshot)
    selected = select_batch_records(dataset, snapshot)

    logical_accounts = sorted(str(record["_key"]) for record in dataset["accounts"])
    account_by_logical = dict(zip(logical_accounts, target_accounts))
    account_mapping = [
        {
            "logicalCustomerName": logical_name,
            "targetAccountId": reference_id(account_by_logical[logical_name].get("id")),
            "targetAccountName": str(account_by_logical[logical_name].get("name") or ""),
        }
        for logical_name in logical_accounts
    ]

    plan_records: list[dict[str, Any]] = []
    rollback_records: list[dict[str, Any]] = []
    object_stats: dict[str, dict[str, int]] = {}
    owner_changes = 0
    account_changes = 0

    for spec in MIGRATION_SPECS:
        desired_by_key = {str(record["_key"]): record for record in dataset[spec.dataset_key]}
        spec_owner_changes = 0
        spec_account_changes = 0
        spec_updates = 0
        for key, row in selected[spec.dataset_key]:
            record_id = reference_id(row.get("id"))
            old_owner_id = reference_id(row.get("ownerid"))
            target_account_id: str | None = None
            old_account_id: str | None = None
            if spec.account_field:
                logical_name = str(desired_by_key[key].get("_account") or "")
                if logical_name not in account_by_logical:
                    raise RuntimeError(f"{spec.object_api} record {key} has unknown logical customer")
                target_account_id = reference_id(account_by_logical[logical_name].get("id"))
                old_account_id = reference_id(row.get(spec.account_field))

            changes: dict[str, dict[str, str]] = {}
            if old_owner_id != TARGET_OWNER_ID:
                changes["ownerid"] = {"old": old_owner_id, "new": TARGET_OWNER_ID}
                spec_owner_changes += 1
            if spec.account_field and old_account_id != target_account_id:
                changes[spec.account_field] = {
                    "old": str(old_account_id),
                    "new": str(target_account_id),
                }
                spec_account_changes += 1
            rollback_records.append(
                {
                    "objectApiName": spec.object_api,
                    "key": key,
                    "id": record_id,
                    "oldOwnerId": old_owner_id,
                    "oldAccountId": old_account_id,
                    "targetOwnerId": TARGET_OWNER_ID,
                    "targetAccountId": target_account_id,
                }
            )
            if changes:
                spec_updates += 1
                plan_records.append(
                    {
                        "objectApiName": spec.object_api,
                        "key": key,
                        "id": record_id,
                        "changes": changes,
                    }
                )
        owner_changes += spec_owner_changes
        account_changes += spec_account_changes
        object_stats[spec.dataset_key] = {
            "expected": EXPECTED_MIGRATION_COUNTS[spec.dataset_key],
            "found": len(selected[spec.dataset_key]),
            "plannedUpdates": spec_updates,
            "ownerChanges": spec_owner_changes,
            "accountChanges": spec_account_changes,
        }

    return {
        "status": "READY",
        "sourceBatch": BATCH,
        "targetOwner": {"id": TARGET_OWNER_ID, "name": TARGET_OWNER_NAME, "active": True},
        "targetAccounts": {
            "marker": V2_ACCOUNT_BATCH,
            "requiredCount": 16,
            "foundCount": len(target_accounts),
            "stableSort": "customerName",
        },
        "accountMapping": account_mapping,
        "expectedCounts": EXPECTED_MIGRATION_COUNTS,
        "expectedOwnerUpdates": sum(EXPECTED_MIGRATION_COUNTS.values()),
        "expectedAccountRelinks": 88,
        "writePolicy": {"allowedActions": ["update"], "creates": 0, "accountWrites": 0},
        "updatePlan": {
            "summary": {
                "plannedRecordUpdates": len(plan_records),
                "ownerChanges": owner_changes,
                "accountChanges": account_changes,
                "fieldChanges": owner_changes + account_changes,
                "creates": 0,
                "duplicates": 0,
            },
            "objectStats": object_stats,
            "records": plan_records,
        },
        "rollbackManifest": {
            "version": 1,
            "sourceBatch": BATCH,
            "requiredFields": [
                "objectApiName",
                "id",
                "oldOwnerId",
                "oldAccountId",
                "targetOwnerId",
                "targetAccountId",
            ],
            "recordCount": len(rollback_records),
            "records": rollback_records,
        },
    }


def validate_update_result(result: dict[str, Any], expected: list[dict[str, Any]], object_api: str) -> None:
    data = result.get("data")
    raw_ids = data.get("ids") if isinstance(data, dict) else None
    if not isinstance(raw_ids, list) or len(raw_ids) != len(expected):
        raise RuntimeError(f"CloudCC update {object_api} returned unexpected result count")
    for expected_row, returned in zip(expected, raw_ids):
        if isinstance(returned, dict):
            if returned.get("success") is not True:
                raise RuntimeError(f"CloudCC update {object_api} failed for one record")
            returned_id = reference_id(returned.get("id"))
        else:
            returned_id = reference_id(returned)
        if not returned_id:
            raise RuntimeError(f"CloudCC update {object_api} returned missing id")
        if returned_id != reference_id(expected_row.get("id")):
            raise RuntimeError(f"CloudCC update {object_api} returned mismatched id")


def verify_forward_batch_old_values(
    batch: list[dict[str, Any]],
    spec: MigrationSpec,
    project: Path,
    cloudcc_cli: Path,
) -> None:
    current_rows = page_query_all(spec.object_api, spec.fields, project, cloudcc_cli)
    current_by_id: dict[str, dict[str, Any]] = {}
    duplicate_ids: set[str] = set()
    for row in current_rows:
        record_id = reference_id(row.get("id"))
        if not record_id:
            continue
        if record_id in current_by_id:
            duplicate_ids.add(record_id)
        current_by_id[record_id] = row

    allowed_fields = {"ownerid"}
    if spec.account_field:
        allowed_fields.add(spec.account_field)
    for record in batch:
        record_id = reference_id(record.get("id"))
        record_label = str(record.get("key") or record_id or "unknown")
        if not record_id or record_id in duplicate_ids or record_id not in current_by_id:
            raise RuntimeError(
                f"forward update aborted: live record lookup mismatch for "
                f"{spec.object_api} record {record_label} before batch write"
            )
        changes = record.get("changes")
        if not isinstance(changes, dict) or not changes:
            raise RuntimeError(
                f"forward update aborted: invalid changes for "
                f"{spec.object_api} record {record_label} before batch write"
            )
        current = current_by_id[record_id]
        for field, change in changes.items():
            if field not in allowed_fields or not isinstance(change, dict) or "old" not in change:
                raise RuntimeError(
                    f"forward update aborted: invalid change field for "
                    f"{spec.object_api} record {record_label} before batch write"
                )
            if reference_id(current.get(field)) != reference_id(change.get("old")):
                raise RuntimeError(
                    f"forward update aborted: live drift detected for "
                    f"{spec.object_api} record {record_label} field {field} before batch write"
                )


def apply_update_plan(
    update_plan: dict[str, Any],
    project: Path,
    cloudcc_cli: Path = CLOUDCC,
    verify_forward_state: bool = False,
) -> dict[str, int]:
    records = update_plan.get("records")
    if not isinstance(records, list):
        raise RuntimeError("update plan records are invalid")
    stats: dict[str, int] = {}
    for spec in MIGRATION_SPECS:
        object_records = [row for row in records if row.get("objectApiName") == spec.object_api]
        stats[spec.dataset_key] = len(object_records)
        for batch in chunks(object_records):
            if verify_forward_state:
                verify_forward_batch_old_values(batch, spec, project, cloudcc_cli)
            payload = [
                {
                    "id": reference_id(record.get("id")),
                    **{
                        field: str(change.get("new") or "")
                        for field, change in record.get("changes", {}).items()
                    },
                }
                for record in batch
            ]
            result = run_cloudcc("update", spec.object_api, {"data": payload}, project, cloudcc_cli)
            validate_update_result(result, payload, spec.object_api)
    return stats


def write_backup_manifest(path: Path, manifest: dict[str, Any]) -> None:
    if not path.parent.exists():
        raise RuntimeError(f"backup directory does not exist: {path.parent}")
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())
    directory_flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0)
    directory_descriptor = os.open(path.parent, directory_flags)
    try:
        os.fsync(directory_descriptor)
    finally:
        os.close(directory_descriptor)


def extract_rollback_manifest(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise RuntimeError("rollback file root must be an object")
    if isinstance(value.get("migrationPlan"), dict):
        value = value["migrationPlan"]
    if isinstance(value.get("rollbackManifest"), dict):
        value = value["rollbackManifest"]
    if not isinstance(value.get("records"), list):
        raise RuntimeError("rollback manifest records are missing")
    return value


def build_rollback_plan(current_plan: dict[str, Any], manifest: dict[str, Any]) -> dict[str, Any]:
    if manifest.get("sourceBatch") != BATCH:
        raise RuntimeError("rollback manifest batch mismatch")
    records = manifest.get("records")
    expected_total = sum(EXPECTED_MIGRATION_COUNTS.values())
    if not isinstance(records, list) or len(records) != expected_total:
        raise RuntimeError(f"rollback manifest count mismatch: expected {expected_total}")
    current_records = {
        str(record.get("id")): record
        for record in current_plan["rollbackManifest"]["records"]
    }
    manifest_ids = [reference_id(record.get("id")) for record in records if isinstance(record, dict)]
    if len(manifest_ids) != expected_total or len(set(manifest_ids)) != expected_total:
        raise RuntimeError("rollback manifest has missing or duplicate id")
    if set(manifest_ids) != set(current_records):
        raise RuntimeError("rollback manifest record boundary mismatch")

    allowed_objects = {spec.object_api for spec in MIGRATION_SPECS}
    rollback_updates: list[dict[str, Any]] = []
    owner_changes = 0
    account_changes = 0
    for record in records:
        object_api = str(record.get("objectApiName") or "")
        record_id = reference_id(record.get("id"))
        if object_api not in allowed_objects:
            raise RuntimeError("rollback manifest object mismatch")
        current = current_records[record_id]
        if current.get("objectApiName") != object_api:
            raise RuntimeError("rollback manifest id/object mismatch")
        if reference_id(record.get("targetOwnerId")) != TARGET_OWNER_ID:
            raise RuntimeError("rollback manifest target owner mismatch")
        old_owner_id = reference_id(record.get("oldOwnerId"))
        if old_owner_id not in {SOURCE_OWNER_ID, TARGET_OWNER_ID}:
            raise RuntimeError("rollback manifest old owner mismatch")
        current_owner_id = reference_id(current.get("oldOwnerId"))
        if current_owner_id not in {old_owner_id, TARGET_OWNER_ID}:
            raise RuntimeError("rollback refused: current owner differs from both old and target values")
        changes: dict[str, dict[str, str]] = {}
        if current_owner_id != old_owner_id:
            changes["ownerid"] = {"old": current_owner_id, "new": old_owner_id}
            owner_changes += 1

        target_account_id = reference_id(record.get("targetAccountId"))
        old_account_id = reference_id(record.get("oldAccountId"))
        current_account_id = reference_id(current.get("oldAccountId"))
        account_field = next(
            (
                spec.account_field
                for spec in MIGRATION_SPECS
                if spec.object_api == object_api
            ),
            None,
        )
        if account_field:
            if not target_account_id or not old_account_id:
                raise RuntimeError("rollback manifest old account is missing")
            if current_account_id not in {old_account_id, target_account_id}:
                raise RuntimeError("rollback refused: current account differs from both old and target values")
            if current_account_id != old_account_id:
                changes[account_field] = {"old": current_account_id, "new": old_account_id}
                account_changes += 1
        elif target_account_id or old_account_id:
            raise RuntimeError("rollback manifest has unexpected account relation")
        if changes:
            rollback_updates.append(
                {
                    "objectApiName": object_api,
                    "id": record_id,
                    "key": str(record.get("key") or ""),
                    "changes": changes,
                }
            )
    return {
        "summary": {
            "plannedRecordUpdates": len(rollback_updates),
            "ownerChanges": owner_changes,
            "accountChanges": account_changes,
            "fieldChanges": owner_changes + account_changes,
            "creates": 0,
        },
        "records": rollback_updates,
    }


def verify_rollback(current_plan: dict[str, Any], manifest: dict[str, Any]) -> None:
    current = {
        str(record.get("id")): record
        for record in current_plan["rollbackManifest"]["records"]
    }
    for expected in manifest["records"]:
        record_id = reference_id(expected.get("id"))
        actual = current.get(record_id)
        if actual is None:
            raise RuntimeError("rollback verification record missing")
        if reference_id(actual.get("oldOwnerId")) != reference_id(expected.get("oldOwnerId")):
            raise RuntimeError("rollback verification owner mismatch")
        if reference_id(actual.get("oldAccountId")) != reference_id(expected.get("oldAccountId")):
            raise RuntimeError("rollback verification account mismatch")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Repair the existing TASK-205 CRM analytics demo batch.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="Build the read-only plan (default).")
    mode.add_argument("--execute", action="store_true", help="Apply the validated plan using update only.")
    parser.add_argument("--offline", action="store_true", help="Skip all CloudCC reads and print the contract only.")
    parser.add_argument("--snapshot", type=Path, help="Use a local read-only CloudCC snapshot instead of live reads.")
    parser.add_argument("--cloudcc-cli", type=Path, default=CLOUDCC, help="CloudCC CLI executable path.")
    parser.add_argument("--cloudcc-project", type=Path, default=ROOT, help="Directory containing CloudCC CLI config.")
    parser.add_argument("--backup-file", type=Path, help="New file required before execute; existing files are refused.")
    parser.add_argument("--rollback-from", type=Path, help="With --execute, restore a prior rollback manifest.")
    parser.add_argument("--as-of", type=date.fromisoformat, default=date.today(), help="Dataset anchor date YYYY-MM-DD.")
    parser.add_argument("--json", action="store_true", help="Print machine-readable JSON only.")
    return parser.parse_args()


def validate_args(args: argparse.Namespace) -> None:
    if args.offline and args.snapshot:
        raise RuntimeError("--offline cannot be combined with --snapshot")
    if args.execute and (args.offline or args.snapshot):
        raise RuntimeError("--execute requires fresh live CloudCC reads")
    if args.execute and args.backup_file is None:
        raise RuntimeError("--execute requires --backup-file")
    if not args.execute and args.backup_file is not None:
        raise RuntimeError("--backup-file is only valid with --execute")
    if args.rollback_from is not None and not args.execute:
        raise RuntimeError("--rollback-from requires --execute")


def main() -> int:
    args = parse_args()
    validate_args(args)
    dataset = build_dataset(args.as_of)
    report = summarize(dataset, args.as_of)
    if args.offline:
        report["migrationPlan"]["status"] = "OFFLINE_CONTRACT"
    else:
        snapshot = (
            load_snapshot(args.snapshot)
            if args.snapshot
            else read_remote_snapshot(args.cloudcc_project, args.cloudcc_cli)
        )
        migration_plan = build_migration_plan(dataset, snapshot)
        report["migrationPlan"] = migration_plan
        if args.execute:
            write_backup_manifest(args.backup_file, migration_plan["rollbackManifest"])
            if args.rollback_from:
                rollback_manifest = extract_rollback_manifest(args.rollback_from)
                rollback_plan = build_rollback_plan(migration_plan, rollback_manifest)
                report["mode"] = "ROLLBACK"
                report["rollbackPlan"] = rollback_plan
                report["writeStats"] = apply_update_plan(
                    rollback_plan,
                    args.cloudcc_project,
                    args.cloudcc_cli,
                )
                after = build_migration_plan(
                    dataset,
                    read_remote_snapshot(args.cloudcc_project, args.cloudcc_cli),
                )
                verify_rollback(after, rollback_manifest)
                report["verification"] = {"status": "VERIFIED", "restoredRecords": len(rollback_manifest["records"])}
            else:
                report["mode"] = "EXECUTE"
                report["writeStats"] = apply_update_plan(
                    migration_plan["updatePlan"],
                    args.cloudcc_project,
                    args.cloudcc_cli,
                    verify_forward_state=True,
                )
                after = build_migration_plan(
                    dataset,
                    read_remote_snapshot(args.cloudcc_project, args.cloudcc_cli),
                )
                if after["updatePlan"]["summary"]["plannedRecordUpdates"] != 0:
                    raise RuntimeError("execute verification failed: updates remain")
                report["verification"] = {
                    "status": "CONFIGURED_PROJECT_FIELD_STATE_VERIFIED",
                    "externalAcceptance": "REQUIRED",
                    "plannedRecordUpdatesAfterExecute": 0,
                    "recordCounts": EXPECTED_MIGRATION_COUNTS,
                }

    if args.json:
        print(json.dumps(report, ensure_ascii=False, separators=(",", ":")))
    else:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, ValueError, OSError, json.JSONDecodeError) as error:
        print(json.dumps({"mode": "FAILED", "error": str(error)}, ensure_ascii=False), file=sys.stderr)
        sys.exit(2)
