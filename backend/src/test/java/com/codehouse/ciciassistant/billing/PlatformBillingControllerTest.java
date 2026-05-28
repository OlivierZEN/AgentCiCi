package com.codehouse.ciciassistant.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.billing.api.PlatformBillingController;
import com.codehouse.ciciassistant.billing.api.PlatformBillingController.BillingActionRequest;
import com.codehouse.ciciassistant.billing.api.PlatformBillingController.BillingConfigRequest;
import com.codehouse.ciciassistant.billing.api.PlatformBillingController.BillingEnabledRequest;
import com.codehouse.ciciassistant.billing.service.BillingEditionConfigService;
import com.codehouse.ciciassistant.billing.service.BillingEditionConfigService.BillingEditionConfigCommand;
import com.codehouse.ciciassistant.billing.service.BillingEditionConfigService.BillingEditionConfigView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlatformBillingControllerTest {

    private final BillingEditionConfigService service = mock(BillingEditionConfigService.class);
    private final PlatformAccountProperties properties = new PlatformAccountProperties();
    private final PlatformBillingController controller = new PlatformBillingController(service, properties);

    @Test
    void listsBillingConfigurationFromPlatformGovernanceScope() {
        BillingEditionConfigView view = view(12L, "PLAN", "saas_business", 2, "PUBLISHED", true);
        when(service.list("demo-org")).thenReturn(List.of(view));

        var response = controller.listBillingPlans();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsExactly(view);
    }

    @Test
    void createsDraftWithReasonAndEditionIndicators() {
        BillingConfigRequest request = request("PLAN", "private_enterprise", "企业版", "private_deployment", "customer_paid");
        BillingEditionConfigView created = view(18L, "PLAN", "private_enterprise", 2, "DRAFT", true);
        ArgumentCaptor<BillingEditionConfigCommand> commandCaptor = ArgumentCaptor.forClass(BillingEditionConfigCommand.class);
        when(service.createDraft(eq("demo-org"), commandCaptor.capture())).thenReturn(created);

        var response = controller.createBillingPlanDraft(request);

        assertThat(response.data()).isEqualTo(created);
        BillingEditionConfigCommand command = commandCaptor.getValue();
        assertThat(command.itemCode()).isEqualTo("private_enterprise");
        assertThat(command.agentLimit()).isEqualTo(120);
        assertThat(command.billingTypePolicy()).isEqualTo("customer_paid");
        assertThat(command.policyJson()).contains("localModelTokenDoubleCharge");
        assertThat(command.changeReason()).contains("TASK-143");
    }

    @Test
    void publishesAndTogglesVersionsThroughExplicitReason() {
        BillingEditionConfigView published = view(18L, "PLAN", "private_enterprise", 2, "PUBLISHED", true);
        when(service.publish("demo-org", 18L, "TASK-143 publish verified billing edition draft")).thenReturn(published);
        when(service.setEnabled("demo-org", 18L, false, "TASK-143 temporarily disable high-risk package")).thenReturn(
                view(18L, "PLAN", "private_enterprise", 2, "PUBLISHED", false)
        );

        assertThat(controller.publishBillingPlan(18L, new BillingActionRequest("TASK-143 publish verified billing edition draft")).data())
                .isEqualTo(published);
        assertThat(controller.setBillingPlanEnabled(
                18L,
                new BillingEnabledRequest(false, "TASK-143 temporarily disable high-risk package")
        ).data().enabled()).isFalse();

        verify(service).publish("demo-org", 18L, "TASK-143 publish verified billing edition draft");
        verify(service).setEnabled("demo-org", 18L, false, "TASK-143 temporarily disable high-risk package");
    }

    private BillingConfigRequest request(String itemType,
                                         String itemCode,
                                         String displayName,
                                         String deploymentMode,
                                         String billingTypePolicy) {
        return new BillingConfigRequest(
                itemType,
                itemCode,
                displayName,
                deploymentMode,
                true,
                billingTypePolicy,
                0,
                200,
                50,
                120,
                500,
                1000,
                200,
                50,
                20,
                100,
                8,
                180,
                1095,
                3,
                "soft_limit",
                "business",
                null,
                "license_year",
                "{\"localModelTokenDoubleCharge\":false}",
                "TASK-143 platform billing configuration change"
        );
    }

    private BillingEditionConfigView view(Long id,
                                          String itemType,
                                          String itemCode,
                                          Integer versionNo,
                                          String publishStatus,
                                          boolean enabled) {
        return new BillingEditionConfigView(
                id,
                itemType,
                itemCode,
                "商业版",
                "saas",
                versionNo,
                publishStatus,
                enabled,
                "platform_paid",
                100000,
                80,
                15,
                40,
                160,
                200,
                100,
                25,
                12,
                40,
                4,
                90,
                365,
                2,
                "auto_charge",
                "business",
                null,
                "org_month",
                "{\"creditsPolicy\":\"included_top_up_and_contract_overage\"}",
                "TASK-143 platform billing configuration change",
                "platform-system",
                "2026-05-28T08:00:00Z",
                "2026-05-28T08:00:00Z",
                "2026-05-28T08:00:00Z",
                versionNo,
                versionNo,
                1
        );
    }
}
