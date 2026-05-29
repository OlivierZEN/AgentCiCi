package com.codehouse.ciciassistant.billing.api;

import com.codehouse.ciciassistant.billing.config.BillingModeProperties;
import com.codehouse.ciciassistant.billing.config.BillingModeProperties.BillingModeView;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/billing")
public class BillingModeController {

    private final BillingModeProperties billingModeProperties;

    public BillingModeController(BillingModeProperties billingModeProperties) {
        this.billingModeProperties = billingModeProperties;
    }

    @GetMapping("/mode")
    public ApiResponse<BillingModeView> currentMode() {
        return ApiResponse.ok(billingModeProperties.toView());
    }
}
