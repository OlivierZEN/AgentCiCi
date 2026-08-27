package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.semattice.SematticeProvisioningService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Combines the published application catalog with tenant-specific runtime facts. */
@Service
public class TenantApplicationCatalogService {

    private final CompanyRepository companies;
    private final InternalApplicationRegistryService registry;
    private final SematticeProvisioningService sematticeProvisioning;
    private final DevAutopilotTenantApplicationService devAutopilot;
    private final GenericTenantApplicationLifecycleService genericLifecycle;

    public TenantApplicationCatalogService(CompanyRepository companies,
                                           InternalApplicationRegistryService registry,
                                           SematticeProvisioningService sematticeProvisioning,
                                           DevAutopilotTenantApplicationService devAutopilot,
                                           GenericTenantApplicationLifecycleService genericLifecycle) {
        this.companies = companies;
        this.registry = registry;
        this.sematticeProvisioning = sematticeProvisioning;
        this.devAutopilot = devAutopilot;
        this.genericLifecycle = genericLifecycle;
    }

    @Transactional(readOnly = true)
    public CatalogView list(String companyId) {
        CompanyEntity company = companies.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("tenant not found"));
        List<InternalApplicationRegistryService.ApplicationSummaryView> catalog = registry.list().stream()
                .filter(application -> Set.of(
                        InternalApplicationRegistryService.STATUS_PUBLISHED,
                        InternalApplicationRegistryService.STATUS_SUSPENDED)
                        .contains(application.catalogStatus()))
                .toList();

        Map<String, RuntimeSnapshot> runtime = runtimeSnapshots(company);
        List<ApplicationView> applications = new ArrayList<>();
        for (InternalApplicationRegistryService.ApplicationSummaryView application : catalog) {
            RuntimeSnapshot snapshot = runtime.get(application.appCode());
            if (snapshot == null && "PLATFORM_BASE".equals(application.tenantMode())) {
                snapshot = RuntimeSnapshot.platformBase(company.getStatus(), application.defaultVersion());
            }
            if (snapshot == null) {
                GenericTenantApplicationLifecycleService.RuntimeView generic =
                        genericLifecycle.runtime(companyId, application.appCode());
                snapshot = new RuntimeSnapshot(
                        generic.enabled(), generic.installedVersion(), generic.desiredState(), generic.actualState(),
                        generic.healthState(), generic.initializationReady(), generic.activationStage(),
                        generic.failedStage(), generic.lastErrorCode(), generic.attemptCount());
            }
            InternalApplicationRegistryService.VersionView version = defaultVersion(application);
            List<DependencyView> dependencies = dependencies(version, runtime);
            boolean dependencyBlocked = dependencies.stream()
                    .anyMatch(item -> item.required() && !item.satisfied());
            boolean supported = activationSupported(application, version);
            String managementRoute = managementRoute(companyId, application);
            List<String> actions = actions(application, snapshot, dependencyBlocked, supported,
                    managementRoute != null);
            String healthState = dependencyBlocked ? "BLOCKED" : snapshot.healthState();
            applications.add(new ApplicationView(
                    application.appCode(),
                    application.displayName(),
                    application.summary(),
                    application.iconKey(),
                    application.ownerTeam(),
                    application.tenantMode(),
                    application.catalogStatus(),
                    application.defaultVersion(),
                    snapshot.installedVersion(),
                    snapshot.enabled(),
                    snapshot.desiredState(),
                    snapshot.actualState(),
                    healthState,
                    snapshot.initializationReady(),
                    supported,
                    dependencies,
                    snapshot.activationStage(),
                    snapshot.failedStage(),
                    snapshot.lastErrorCode(),
                    snapshot.attemptCount(),
                    actions,
                    managementRoute));
        }

        long enabled = applications.stream().filter(ApplicationView::enabled).count();
        long pending = applications.stream().filter(item -> Set.of("PROVISIONING", "FAILED", "BLOCKED")
                .contains(item.actualState()) || "BLOCKED".equals(item.healthState())).count();
        return new CatalogView(companyId, company.getStatus(), enabled, pending, applications);
    }

    private Map<String, RuntimeSnapshot> runtimeSnapshots(CompanyEntity company) {
        Map<String, RuntimeSnapshot> snapshots = new LinkedHashMap<>();
        boolean activeCompany = "ACTIVE".equals(company.getStatus());
        snapshots.put("agentcici", new RuntimeSnapshot(
                true, "1.0.0", activeCompany ? "ACTIVE" : "SUSPENDED",
                activeCompany ? "ACTIVE" : "SUSPENDED", activeCompany ? "READY" : "BLOCKED",
                activeCompany, "ACTIVE", null, null, 0));

        SematticeProvisioningService.BindingView semattice = sematticeProvisioning.getProvisioningStatus(company.getId());
        String sematticeState = switch (semattice.state()) {
            case "PROVISIONED" -> "ACTIVE";
            case "RESERVED" -> "PROVISIONING";
            case "FAILED" -> "FAILED";
            default -> "NOT_ENABLED";
        };
        snapshots.put("semattice", new RuntimeSnapshot(
                "PROVISIONED".equals(semattice.state()),
                "PROVISIONED".equals(semattice.state()) ? "1.0.0" : null,
                "PROVISIONED".equals(semattice.state()) ? "ACTIVE" : "NOT_ENABLED",
                sematticeState,
                "PROVISIONED".equals(semattice.state()) ? "READY"
                        : "FAILED".equals(semattice.state()) ? "BLOCKED" : "UNKNOWN",
                "PROVISIONED".equals(semattice.state()),
                sematticeState,
                "FAILED".equals(semattice.state()) ? "PROVISIONING" : null,
                semattice.failureCode(),
                0));

        DevAutopilotTenantApplicationService.View devAutopilotView = devAutopilot.get(company.getId());
        snapshots.put("devautopilot", new RuntimeSnapshot(
                devAutopilotView.enabled(),
                devAutopilotView.templateVersion(),
                devAutopilotView.desiredState(),
                devAutopilotView.actualState(),
                "ACTIVE".equals(devAutopilotView.actualState()) && devAutopilotView.initializationReady()
                        ? "READY" : "FAILED".equals(devAutopilotView.actualState()) ? "BLOCKED" : "UNKNOWN",
                devAutopilotView.initializationReady(),
                devAutopilotView.activationStage(),
                devAutopilotView.failedStage(),
                devAutopilotView.lastErrorCode(),
                devAutopilotView.attemptCount()));
        return snapshots;
    }

    private InternalApplicationRegistryService.VersionView defaultVersion(
            InternalApplicationRegistryService.ApplicationSummaryView application) {
        if (application.defaultVersion() == null) {
            return null;
        }
        return registry.get(application.appCode()).versions().stream()
                .filter(version -> application.defaultVersion().equals(version.version()))
                .findFirst()
                .orElse(null);
    }

    private List<DependencyView> dependencies(InternalApplicationRegistryService.VersionView version,
                                              Map<String, RuntimeSnapshot> runtime) {
        if (version == null) {
            return List.of();
        }
        return version.dependencies().stream().map(dependency -> {
            RuntimeSnapshot dependencyRuntime = runtime.getOrDefault(dependency.appCode(), RuntimeSnapshot.notEnabled());
            boolean required = !"OPTIONAL".equals(dependency.dependencyType());
            boolean satisfied = "ACTIVE".equals(dependencyRuntime.actualState());
            return new DependencyView(
                    dependency.appCode(),
                    dependency.versionConstraint(),
                    dependency.dependencyType(),
                    dependency.activationPolicy(),
                    dependencyRuntime.actualState(),
                    required,
                    satisfied);
        }).toList();
    }

    private List<String> actions(InternalApplicationRegistryService.ApplicationSummaryView application,
                                 RuntimeSnapshot runtime,
                                 boolean dependencyBlocked,
                                 boolean activationSupported,
                                 boolean openSupported) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        if ("PLATFORM_BASE".equals(application.tenantMode())) {
            if (openSupported) actions.add("OPEN");
            return List.copyOf(actions);
        }
        if (InternalApplicationRegistryService.STATUS_SUSPENDED.equals(application.catalogStatus())) {
            if (runtime.enabled() && !"SUSPENDED".equals(runtime.actualState())) {
                actions.add("SUSPEND");
            }
            return List.copyOf(actions);
        }
        if ("semattice".equals(application.appCode())) {
            if (!runtime.enabled() && !"PROVISIONING".equals(runtime.actualState())) {
                actions.add("ACTIVATE");
            }
            return List.copyOf(actions);
        }
        if ("devautopilot".equals(application.appCode())) {
            if (!runtime.enabled()) {
                if (!dependencyBlocked) actions.add("ACTIVATE");
            } else if (Set.of("FAILED", "PROVISIONING").contains(runtime.actualState())) {
                actions.add("CONTINUE");
            } else if (!runtime.initializationReady()) {
                actions.add("RECONCILE");
            } else if ("SUSPENDED".equals(runtime.actualState())) {
                actions.add("RESUME");
            } else {
                actions.add("RECONCILE");
                actions.add("SUSPEND");
            }
            if (openSupported) actions.add("OPEN");
            return List.copyOf(actions);
        }
        if (!activationSupported) {
            return List.copyOf(actions);
        }
        if (!runtime.enabled()) {
            if (!dependencyBlocked && !"PROVISIONING".equals(runtime.actualState())) actions.add("ACTIVATE");
        } else if ("SUSPENDED".equals(runtime.actualState())) {
            actions.add("RESUME");
        } else {
            actions.add("RECONCILE");
            actions.add("SUSPEND");
        }
        return List.copyOf(actions);
    }

    private boolean activationSupported(
            InternalApplicationRegistryService.ApplicationSummaryView application,
            InternalApplicationRegistryService.VersionView version) {
        if ("PLATFORM_BASE".equals(application.tenantMode())) {
            return true;
        }
        if (Set.of("agentcici", "semattice", "devautopilot").contains(application.appCode())) {
            return true;
        }
        return version != null && version.providerBindingKey() != null
                && providerConnectionSupported(application.appCode(), version.providerBindingKey());
    }

    private boolean providerConnectionSupported(String appCode, String bindingKey) {
        try {
            return genericLifecycle.connectionSupported(appCode, bindingKey);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String managementRoute(
            String companyId,
            InternalApplicationRegistryService.ApplicationSummaryView application) {
        if (!"PLATFORM_ROUTE".equals(application.launchMode())) {
            return null;
        }
        return switch (application.launchRouteKey()) {
            case "agentcici.lifecycle" -> "/platform/tenants/" + companyId + "/applications/agentcici";
            case "demo-example.page" -> "/platform/internal-applications/demo-example/example";
            default -> null;
        };
    }

    public record CatalogView(
            String companyId,
            String companyStatus,
            long enabledCount,
            long pendingCount,
            List<ApplicationView> applications) {
    }

    public record ApplicationView(
            String appCode,
            String displayName,
            String summary,
            String iconKey,
            String ownerTeam,
            String tenantMode,
            String catalogStatus,
            String defaultVersion,
            String installedVersion,
            boolean enabled,
            String desiredState,
            String actualState,
            String healthState,
            boolean initializationReady,
            boolean activationSupported,
            List<DependencyView> dependencies,
            String activationStage,
            String failedStage,
            String lastErrorCode,
            int attemptCount,
            List<String> actions,
            String managementRoute) {
    }

    public record DependencyView(
            String appCode,
            String versionConstraint,
            String dependencyType,
            String activationPolicy,
            String actualState,
            boolean required,
            boolean satisfied) {
    }

    private record RuntimeSnapshot(
            boolean enabled,
            String installedVersion,
            String desiredState,
            String actualState,
            String healthState,
            boolean initializationReady,
            String activationStage,
            String failedStage,
            String lastErrorCode,
            int attemptCount) {

        private static RuntimeSnapshot notEnabled() {
            return new RuntimeSnapshot(false, null, "NOT_ENABLED", "NOT_ENABLED", "UNKNOWN",
                    false, "NOT_ENABLED", null, null, 0);
        }

        private static RuntimeSnapshot platformBase(String companyStatus, String version) {
            boolean active = "ACTIVE".equals(companyStatus);
            return new RuntimeSnapshot(
                    true,
                    version,
                    active ? "ACTIVE" : "SUSPENDED",
                    active ? "ACTIVE" : "SUSPENDED",
                    active ? "READY" : "BLOCKED",
                    active,
                    active ? "ACTIVE" : "SUSPENDED",
                    null,
                    null,
                    0);
        }
    }
}
