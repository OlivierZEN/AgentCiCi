package com.codehouse.ciciassistant.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.platform.service.InternalApplicationRegistryService.ApplicationDetailView;
import com.codehouse.ciciassistant.platform.service.InternalApplicationRegistryService.ApplicationSummaryView;
import com.codehouse.ciciassistant.platform.service.InternalApplicationRegistryService.DependencyView;
import com.codehouse.ciciassistant.platform.service.InternalApplicationRegistryService.VersionView;
import com.codehouse.ciciassistant.semattice.SematticeProvisioningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantApplicationCatalogServiceTest {

    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final InternalApplicationRegistryService registry = mock(InternalApplicationRegistryService.class);
    private final SematticeProvisioningService semattice = mock(SematticeProvisioningService.class);
    private final DevAutopilotTenantApplicationService devAutopilot = mock(DevAutopilotTenantApplicationService.class);
    private final GenericTenantApplicationLifecycleService genericLifecycle = mock(GenericTenantApplicationLifecycleService.class);
    private final TenantApplicationCatalogService service = new TenantApplicationCatalogService(
            companies, registry, semattice, devAutopilot, genericLifecycle);

    @Test
    void dynamicallyIncludesPublishedApplicationsAndBlocksRequiredRuntimeDependencies() {
        String companyId = "org1234567890abcdefg";
        when(companies.findById(companyId)).thenReturn(Optional.of(new CompanyEntity(companyId, "Demo", "ACTIVE")));
        ApplicationSummaryView agentcici = application("agentcici", "AgentCiCi", "PLATFORM_BASE", "1.0.0");
        ApplicationSummaryView sematticeApp = application("semattice", "Semattice", "SHARED_RUNTIME_TENANT_ISOLATED", "1.0.0");
        ApplicationSummaryView dev = application("devautopilot", "DevAutopilot", "SHARED_RUNTIME_TENANT_ISOLATED", "1.0.0");
        ApplicationSummaryView newApp = application("sales-workbench", "销售工作台", "SHARED_RUNTIME_TENANT_ISOLATED", "1.0.0");
        when(registry.list()).thenReturn(List.of(agentcici, sematticeApp, dev, newApp));
        when(registry.get("agentcici")).thenReturn(detail(agentcici, List.of()));
        when(registry.get("semattice")).thenReturn(detail(sematticeApp, List.of()));
        when(registry.get("devautopilot")).thenReturn(detail(dev, List.of(
                new DependencyView("semattice", ">=1.0.0", "REQUIRED_RUNTIME", "REQUIRE_EXISTING"))));
        when(registry.get("sales-workbench")).thenReturn(detail(newApp, List.of()));
        when(semattice.getProvisioningStatus(companyId)).thenReturn(
                new SematticeProvisioningService.BindingView(null, companyId, "NOT_PROVISIONED", null, null, null));
        when(devAutopilot.get(companyId)).thenReturn(
                DevAutopilotTenantApplicationService.View.notEnabled(companyId));
        when(genericLifecycle.runtime(companyId, "sales-workbench")).thenReturn(
                GenericTenantApplicationLifecycleService.RuntimeView.notEnabled());

        var catalog = service.list(companyId);

        assertThat(catalog.applications()).extracting(TenantApplicationCatalogService.ApplicationView::appCode)
                .containsExactly("agentcici", "semattice", "devautopilot", "sales-workbench");
        assertThat(catalog.enabledCount()).isEqualTo(1);
        assertThat(catalog.applications().stream().filter(item -> item.appCode().equals("devautopilot")).findFirst())
                .hasValueSatisfying(application -> {
                    assertThat(application.healthState()).isEqualTo("BLOCKED");
                    assertThat(application.dependencies()).singleElement().satisfies(dependency -> {
                        assertThat(dependency.required()).isTrue();
                        assertThat(dependency.satisfied()).isFalse();
                    });
                    assertThat(application.actions()).doesNotContain("ACTIVATE");
                });
        assertThat(catalog.applications().stream().filter(item -> item.appCode().equals("sales-workbench")).findFirst())
                .hasValueSatisfying(application -> {
                    assertThat(application.activationSupported()).isFalse();
                    assertThat(application.actions()).isEmpty();
                });
    }

    @Test
    void projectsEveryPlatformBaseApplicationAndOnlyReturnsAllowlistedRelativeRoutes() {
        String companyId = "org1234567890abcdefg";
        when(companies.findById(companyId)).thenReturn(Optional.of(new CompanyEntity(companyId, "Demo", "ACTIVE")));
        ApplicationSummaryView agentcici = application(
                "agentcici", "AgentCiCi", "PLATFORM_BASE", "1.0.0",
                "PLATFORM_ROUTE", "agentcici.lifecycle");
        ApplicationSummaryView demo = application(
                "demo-example", "DEMO示例应用", "PLATFORM_BASE", "1.0.0",
                "PLATFORM_ROUTE", "demo-example.page");
        ApplicationSummaryView unknown = application(
                "unknown-platform", "未知平台应用", "PLATFORM_BASE", "1.0.0",
                "PLATFORM_ROUTE", "unknown.page");
        when(registry.list()).thenReturn(List.of(agentcici, demo, unknown));
        when(registry.get("agentcici")).thenReturn(detail(agentcici, List.of()));
        when(registry.get("demo-example")).thenReturn(detail(demo, List.of(
                new DependencyView("semattice", ">=1.0.0", "OPTIONAL", "AUTO_PROVISION_ALLOWED"))));
        when(registry.get("unknown-platform")).thenReturn(detail(unknown, List.of()));
        when(semattice.getProvisioningStatus(companyId)).thenReturn(
                new SematticeProvisioningService.BindingView(null, companyId, "NOT_PROVISIONED", null, null, null));
        when(devAutopilot.get(companyId)).thenReturn(DevAutopilotTenantApplicationService.View.notEnabled(companyId));

        var catalog = service.list(companyId);

        assertThat(catalog.enabledCount()).isEqualTo(3);
        assertThat(catalog.applications()).filteredOn(item -> item.appCode().equals("demo-example"))
                .singleElement().satisfies(application -> {
                    assertThat(application.actualState()).isEqualTo("ACTIVE");
                    assertThat(application.initializationReady()).isTrue();
                    assertThat(application.activationSupported()).isTrue();
                    assertThat(application.actions()).containsExactly("OPEN");
                    assertThat(application.managementRoute())
                            .isEqualTo("/platform/internal-applications/demo-example/example");
                    assertThat(application.dependencies()).singleElement()
                            .satisfies(dependency -> assertThat(dependency.required()).isFalse());
                });
        assertThat(catalog.applications()).filteredOn(item -> item.appCode().equals("unknown-platform"))
                .singleElement().satisfies(application -> {
                    assertThat(application.actualState()).isEqualTo("ACTIVE");
                    assertThat(application.actions()).isEmpty();
                    assertThat(application.managementRoute()).isNull();
                });
    }

    private ApplicationSummaryView application(String code, String name, String tenantMode, String version) {
        return application(code, name, tenantMode, version, "NONE", null);
    }

    private ApplicationSummaryView application(
            String code,
            String name,
            String tenantMode,
            String version,
            String launchMode,
            String launchRouteKey) {
        return new ApplicationSummaryView(
                code, name, name + " summary", "workflow", name + " team", tenantMode,
                "PUBLISHED", null, launchMode, launchRouteKey, version, 1, Instant.now(), Instant.now());
    }

    private ApplicationDetailView detail(ApplicationSummaryView application, List<DependencyView> dependencies) {
        VersionView version = new VersionView(
                application.appCode() + "-1", application.appCode(), "1.0.0", "tenant-application/v1",
                null, "NONE", new ObjectMapper().createObjectNode(), "0".repeat(64), "PUBLISHED",
                dependencies, "seed", "seed", Instant.now(), "seed", Instant.now(), Instant.now(), Instant.now());
        return new ApplicationDetailView(application, List.of(version));
    }
}
