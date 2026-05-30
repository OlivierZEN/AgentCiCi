package com.codehouse.ciciassistant.billing.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.billing.config.BillingModeProperties.DeploymentMode;
import org.junit.jupiter.api.Test;

class BillingModePropertiesTest {

    @Test
    void defaultsToPrivateDeployment() {
        BillingModeProperties properties = new BillingModeProperties();

        assertThat(properties.mode()).isEqualTo(DeploymentMode.PRIVATE_DEPLOYMENT);
        assertThat(properties.toView().primaryRevenueModel()).contains("私有化年费许可");
        assertThat(properties.toView().localModelTokenPolicy()).contains("默认不对本地模型 token 二次收费");
        assertThat(properties.toView().supportedBillingTypes()).contains("customer_paid");
    }

    @Test
    void normalizesSaasAliases() {
        BillingModeProperties properties = new BillingModeProperties();

        properties.setDeploymentMode("cloud-saas");

        assertThat(properties.mode()).isEqualTo(DeploymentMode.SAAS);
        assertThat(properties.isSaas()).isTrue();
        assertThat(properties.toView().primaryChargeItems()).contains("work_credit");
        assertThat(properties.toView().supportedBillingTypes()).contains("platform_paid");
    }

    @Test
    void treatsUnknownValuesAsPrivateDeployment() {
        BillingModeProperties properties = new BillingModeProperties();

        properties.setDeploymentMode("unexpected");

        assertThat(properties.mode()).isEqualTo(DeploymentMode.PRIVATE_DEPLOYMENT);
        assertThat(properties.isPrivateDeployment()).isTrue();
    }
}
