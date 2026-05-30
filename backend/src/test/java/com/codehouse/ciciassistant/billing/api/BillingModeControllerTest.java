package com.codehouse.ciciassistant.billing.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.billing.config.BillingModeProperties;
import org.junit.jupiter.api.Test;

class BillingModeControllerTest {

    @Test
    void returnsConfiguredBillingModeView() {
        BillingModeProperties properties = new BillingModeProperties();
        properties.setDeploymentMode("saas");
        BillingModeController controller = new BillingModeController(properties);

        var response = controller.currentMode();

        assertThat(response.success()).isTrue();
        assertThat(response.data().deploymentMode()).isEqualTo("saas");
        assertThat(response.data().primaryRevenueModel()).contains("Work Credits");
    }
}
