package com.codehouse.ciciassistant.billing.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.billing.service.AdminBillingService;
import com.codehouse.ciciassistant.billing.service.AdminBillingService.AdminBillingOverviewView;
import com.codehouse.ciciassistant.billing.service.AdminBillingService.AdminSubscriptionView;
import com.codehouse.ciciassistant.billing.service.AdminBillingService.LedgerEntryView;
import com.codehouse.ciciassistant.billing.service.AdminBillingService.QuotaWarningView;
import com.codehouse.ciciassistant.billing.service.AdminBillingService.UsageEventView;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/billing")
@RequireOrgAdmin
public class AdminBillingController {

    private final AdminBillingService adminBillingService;

    public AdminBillingController(AdminBillingService adminBillingService) {
        this.adminBillingService = adminBillingService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminBillingOverviewView> overview() {
        return ApiResponse.ok(adminBillingService.overview(TenantContext.requireCompanyId()));
    }

    @GetMapping("/subscription")
    public ApiResponse<AdminSubscriptionView> subscription() {
        return ApiResponse.ok(adminBillingService.subscription(TenantContext.requireCompanyId()));
    }

    @GetMapping("/usage-events")
    public ApiResponse<List<UsageEventView>> usageEvents() {
        return ApiResponse.ok(adminBillingService.usageEvents(TenantContext.requireCompanyId()));
    }

    @GetMapping("/ledger")
    public ApiResponse<List<LedgerEntryView>> ledger() {
        return ApiResponse.ok(adminBillingService.ledger(TenantContext.requireCompanyId()));
    }

    @GetMapping("/quota")
    public ApiResponse<List<QuotaWarningView>> quota() {
        return ApiResponse.ok(adminBillingService.quota(TenantContext.requireCompanyId()));
    }
}
