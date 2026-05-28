export type BillingDeploymentMode = "private_deployment" | "saas";

export type BillingModeView = {
  deploymentMode: BillingDeploymentMode;
  label: string;
  primaryRevenueModel: string;
  localModelTokenPolicy: string;
  creditsRole: string;
  primaryChargeItems: string[];
  supportedBillingTypes: BillingType[];
};

export type BillingType = "customer_paid" | "platform_paid" | "included" | "non_billable";

export function normalizeBillingDeploymentMode(value: string | null | undefined): BillingDeploymentMode {
  const normalized = (value || "").trim().toLowerCase().replace(/[-\s]+/g, "_");
  if (normalized === "saas" || normalized === "cloud" || normalized === "cloud_saas") {
    return "saas";
  }
  return "private_deployment";
}

export function isPrivateDeploymentBilling(value: string | null | undefined): boolean {
  return normalizeBillingDeploymentMode(value) === "private_deployment";
}

export function defaultBillingModeView(value: string | null | undefined): BillingModeView {
  const deploymentMode = normalizeBillingDeploymentMode(value);
  if (deploymentMode === "saas") {
    return {
      deploymentMode,
      label: "SaaS",
      primaryRevenueModel: "平台订阅 + 操作/构建席位 + Work Credits + 企业增值模块",
      localModelTokenPolicy: "平台代付模型、云端语音、第三方搜索和托管连接器可进入 credits 或实际用量扣费。",
      creditsRole: "credits 是客户侧套餐额度、超额、预算控制和平台代付资源计费口径。",
      primaryChargeItems: ["platform_subscription", "operation_seat", "builder_seat", "work_credit", "enterprise_add_on"],
      supportedBillingTypes: ["platform_paid", "included", "non_billable"],
    };
  }
  return {
    deploymentMode,
    label: "私有化部署",
    primaryRevenueModel: "私有化年费许可 + 操作/构建席位 + 模块/容量包 + 实施运维服务费",
    localModelTokenPolicy: "客户自有本地模型 token、GPU 和推理成本由客户承担，默认不对本地模型 token 二次收费。",
    creditsRole: "credits 优先用于用量看板、成本归因、预算治理、合同额度和平台代付资源。",
    primaryChargeItems: ["annual_license", "operation_seat", "builder_seat", "module_pack", "capacity_pack", "service_fee"],
    supportedBillingTypes: ["customer_paid", "platform_paid", "included", "non_billable"],
  };
}
