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

    public TenantApplicationCatalogService(CompanyRepository companies,
                                           InternalApplicationRegistryService registry,
                                           SematticeProvisioningService sematticeProvisioning,
                                           DevAutopilotTenantApplicationService devAutopilot) {
        this.companies = companies;
        this.registry = registry;
        this.sematticeProvisioning = sematticeProvisioning;
        this.devAutopilot = devAutopilot;
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
            RuntimeSnapshot snapshot = runtime.getOrDefault(application.appCode(), RuntimeSnapshot.notEnabled());
            InternalApplicationRegistryService.VersionView version = defaultVersion(application);
            List<DependencyView> dependencies = dependencies(version, runtime);
            boolean dependencyBlocked = dependencies.stream()
                    .anyMatch(item -> item.required() && !item.satisfied());
            List<String> actions = actions(application, snapshot, dependencyBlocked);
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
                    activationSupported(application.appCode()),
                    dependencies,
                    snapshot.activationStage(),
                    snapshot.failedStage(),
                    snapshot.lastErrorCode(),
                    snapshot.attemptCount(),
                    actions,
                    managementRoute(companyId, application.appCode())));
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
                                 boolean dependencyBlocked) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        if ("agentcici".equals(application.appCode())) {
            actions.add("OPEN");
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
            actions.add("OPEN");
            return List.copyOf(actions);
        }
        if (runtime.enabled()) {
            actions.add("OPEN");
        }
        return List.copyOf(actions);
    }

    private static boolean activationSupported(String appCode) {
        return Set.of("agentcici", "semattice", "devautopilot").contains(appCode);
    }

    private static String managementRoute(String companyId, String appCode) {
        return switch (appCode) {
            case "agentcici" -> "/platform/tenants/" + companyId + "/applications/agentcici";
            default -> "/platform/tenants/" + companyId + "/applications/" + appCode;
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
    }
}
