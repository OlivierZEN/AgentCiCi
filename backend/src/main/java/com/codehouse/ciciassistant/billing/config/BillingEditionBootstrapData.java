package com.codehouse.ciciassistant.billing.config;

import com.codehouse.ciciassistant.billing.service.BillingEditionConfigurationService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BillingEditionBootstrapData implements ApplicationRunner {

    private final BillingEditionConfigurationService billingEditionConfigurationService;

    public BillingEditionBootstrapData(BillingEditionConfigurationService billingEditionConfigurationService) {
        this.billingEditionConfigurationService = billingEditionConfigurationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        billingEditionConfigurationService.ensureDefaultCatalog();
    }
}
