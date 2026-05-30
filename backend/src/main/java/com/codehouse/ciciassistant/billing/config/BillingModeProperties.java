package com.codehouse.ciciassistant.billing.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.billing")
public class BillingModeProperties {

    private String deploymentMode = DeploymentMode.PRIVATE_DEPLOYMENT.code();

    public String getDeploymentMode() {
        return mode().code();
    }

    public void setDeploymentMode(String deploymentMode) {
        this.deploymentMode = deploymentMode == null ? "" : deploymentMode.trim();
    }

    public DeploymentMode mode() {
        return DeploymentMode.from(deploymentMode);
    }

    public boolean isPrivateDeployment() {
        return mode() == DeploymentMode.PRIVATE_DEPLOYMENT;
    }

    public boolean isSaas() {
        return mode() == DeploymentMode.SAAS;
    }

    public BillingModeView toView() {
        DeploymentMode current = mode();
        if (current == DeploymentMode.SAAS) {
            return new BillingModeView(
                    current.code(),
                    "SaaS",
                    "平台订阅 + 操作/构建席位 + Work Credits + 企业增值模块",
                    "平台代付模型、云端语音、第三方搜索和托管连接器可进入 credits 或实际用量扣费。",
                    "credits 是客户侧套餐额度、超额、预算控制和平台代付资源计费口径。",
                    List.of("platform_subscription", "operation_seat", "builder_seat", "work_credit", "enterprise_add_on"),
                    List.of("platform_paid", "included", "non_billable"));
        }
        return new BillingModeView(
                current.code(),
                "私有化部署",
                "私有化年费许可 + 操作/构建席位 + 模块/容量包 + 实施运维服务费",
                "客户自有本地模型 token、GPU 和推理成本由客户承担，默认不对本地模型 token 二次收费。",
                "credits 优先用于用量看板、成本归因、预算治理、合同额度和平台代付资源。",
                List.of("annual_license", "operation_seat", "builder_seat", "module_pack", "capacity_pack", "service_fee"),
                List.of("customer_paid", "platform_paid", "included", "non_billable"));
    }

    public enum DeploymentMode {
        PRIVATE_DEPLOYMENT("private_deployment"),
        SAAS("saas");

        private final String code;

        DeploymentMode(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static DeploymentMode from(String raw) {
            String normalized = raw == null ? "" : raw.trim()
                    .toLowerCase()
                    .replace('-', '_')
                    .replace(' ', '_');
            if ("saas".equals(normalized) || "cloud".equals(normalized) || "cloud_saas".equals(normalized)) {
                return SAAS;
            }
            return PRIVATE_DEPLOYMENT;
        }
    }

    public record BillingModeView(
            String deploymentMode,
            String label,
            String primaryRevenueModel,
            String localModelTokenPolicy,
            String creditsRole,
            List<String> primaryChargeItems,
            List<String> supportedBillingTypes
    ) {
    }
}
