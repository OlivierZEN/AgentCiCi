package com.codehouse.ciciassistant.billing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.billing.service.AdminBillingService;
import com.codehouse.ciciassistant.billing.service.AdminBillingService.AdminBillingOverviewView;
import com.codehouse.ciciassistant.billing.service.AdminBillingService.AdminSubscriptionView;
import com.codehouse.ciciassistant.billing.service.AdminBillingService.CreditSummaryView;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AdminBillingControllerTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void readsBillingOverviewForCurrentCompanyOnly() {
        AdminBillingService service = mock(AdminBillingService.class);
        AdminBillingController controller = new AdminBillingController(service);
        TenantContext.setCompanyId("org-demo");
        AdminSubscriptionView subscription = new AdminSubscriptionView(
                "org-demo",
                "private_deployment",
                "私有化",
                "private_department",
                "部门版",
                "active",
                "2026-05-01T00:00:00Z",
                "2027-05-01T00:00:00Z",
                new BigDecimal("50000.00"),
                new BigDecimal("100.00"),
                new BigDecimal("49900.00"),
                "soft_limit",
                "disabled",
                "customer_paid",
                "本地模型 token 不二次收费。",
                7,
                50,
                2,
                5,
                20,
                50,
                90,
                List.of("私有化容量包"));
        AdminBillingOverviewView overview = new AdminBillingOverviewView(
                subscription,
                new CreditSummaryView(new BigDecimal("50000.00"), new BigDecimal("100.00"),
                        new BigDecimal("49900.00"), new BigDecimal("0.2")),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        when(service.overview("org-demo")).thenReturn(overview);

        var response = controller.overview();

        assertThat(response.success()).isTrue();
        assertThat(response.data().subscription().companyId()).isEqualTo("org-demo");
        verify(service).overview("org-demo");
    }

    @Test
    void requiresCompanyAdminRole() {
        assertThat(AdminBillingController.class).hasAnnotation(RequireOrgAdmin.class);
    }
}
