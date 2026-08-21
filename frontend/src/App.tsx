import {
  createBrowserRouter,
  createRoutesFromElements,
  Navigate,
  Route,
  RouterProvider,
} from "react-router-dom";
import AssistantApp from "./assistant/AssistantApp";
import AgentCiciWebsite from "./suite/AgentCiciWebsite";
import AdminGuard from "./admin/AdminGuard";
import AdminShell from "./admin/AdminShell";
import AdminKnowledgePage from "./admin/pages/AdminKnowledgePage";
import AdminDataQualityPage from "./admin/pages/AdminDataQualityPage";
import AdminToolsPage from "./admin/pages/AdminToolsPage";
import AdminOpsPage from "./admin/pages/AdminOpsPage";
import AdminUsersPage from "./admin/pages/AdminUsersPage";
import AdminServicePrincipalsPage from "./admin/pages/AdminServicePrincipalsPage";
import AdminCompanyPage from "./admin/pages/AdminCompanyPage";
import AdminBillingPage from "./admin/pages/AdminBillingPage";
import AdminIntegrationsPage from "./admin/pages/AdminIntegrationsPage";
import AdminEmbedAppsPage from "./admin/pages/AdminEmbedAppsPage";
import AdminWecomKfAccountsPage from "./admin/pages/AdminWecomKfAccountsPage";
import AdminAgentBuilderPage from "./admin/pages/AdminAgentBuilderPage";
import AdminAgentOpenApiDocsPage from "./admin/pages/AdminAgentOpenApiDocsPage";
import AdminSecurityRulesPage from "./admin/pages/AdminSecurityRulesPage";
import AdminSkillComposePage from "./admin/pages/AdminSkillComposePage";
import AdminSkillsListPage from "./admin/pages/AdminSkillsListPage";
import AdminEvaluationPage from "./admin/pages/AdminEvaluationPage";
import AdminOntologyPage from "./admin/pages/AdminOntologyPage";
import EmbedMeetingMinutesPage from "./embed/EmbedMeetingMinutesPage";
import PlatformGuard from "./platform/PlatformGuard";
import PlatformLogin from "./platform/PlatformLogin";
import PlatformShell from "./platform/PlatformShell";
import PlatformAuditPage from "./platform/pages/PlatformAuditPage";
import PlatformAppearancePage from "./platform/pages/PlatformAppearancePage";
import PlatformAutoServiceDemoRequestsPage from "./platform/pages/PlatformAutoServiceDemoRequestsPage";
import PlatformRegisteredUsersPage from "./platform/pages/PlatformRegisteredUsersPage";
import PlatformHomePage from "./platform/pages/PlatformHomePage";
import PlatformBillingPage from "./platform/pages/PlatformBillingPage";
import PlatformModelsPage from "./platform/pages/PlatformModelsPage";
import PlatformIntegrationsPage from "./platform/pages/PlatformIntegrationsPage";
import PlatformSkillsPage from "./platform/pages/PlatformSkillsPage";
import PlatformTenantApplicationsPage from "./platform/pages/PlatformTenantApplicationsPage";
import PlatformTenantDetailPage from "./platform/pages/PlatformTenantDetailPage";
import PlatformTenantsPage from "./platform/pages/PlatformTenantsPage";
import PlatformToolsPage from "./platform/pages/PlatformToolsPage";
import PlatformEvaluationPage from "./platform/pages/PlatformEvaluationPage";
import PlatformSystemApisPage from "./platform/pages/PlatformSystemApisPage";
import PlatformInternalApplicationsPage from "./platform/pages/PlatformInternalApplicationsPage";
import PlatformApplicationIntegrationGuidePage from "./platform/pages/PlatformApplicationIntegrationGuidePage";
import PlatformDeploymentInstallationPage from "./platform/pages/PlatformDeploymentInstallationPage";
import WecomKfMobilePage from "./mobile/WecomKfMobilePage";

const router = createBrowserRouter(createRoutesFromElements(
  <>
        <Route path="/" element={<AgentCiciWebsite />} />
        <Route path="/solutions" element={<AgentCiciWebsite />} />
        <Route path="/skill-hub" element={<AgentCiciWebsite />} />
        <Route path="/pricing" element={<AgentCiciWebsite />} />
        <Route path="/docs" element={<AgentCiciWebsite />} />
        <Route path="/community" element={<AgentCiciWebsite />} />
        <Route path="/login" element={<AssistantApp />} />
        <Route path="/app" element={<AssistantApp />} />
        <Route path="/global" element={<AgentCiciWebsite />} />
        <Route path="/global/solutions" element={<AgentCiciWebsite />} />
        <Route path="/global/skill-hub" element={<AgentCiciWebsite />} />
        <Route path="/global/pricing" element={<AgentCiciWebsite />} />
        <Route path="/global/docs" element={<AgentCiciWebsite />} />
        <Route path="/global/community" element={<AgentCiciWebsite />} />
        <Route path="/suite/*" element={<Navigate to="/solutions" replace />} />
        <Route path="/pricing/global" element={<Navigate to="/global/pricing" replace />} />
        <Route path="/autoservice/*" element={<Navigate to="/solutions" replace />} />
        <Route path="/embed/meeting-minutes" element={<EmbedMeetingMinutesPage />} />
        <Route path="/mobile/wechat-kf" element={<WecomKfMobilePage />} />
        <Route path="/admin/login" element={<Navigate to="/app" replace />} />
        <Route path="/admin" element={<AdminGuard />}>
          <Route element={<AdminShell />}>
            <Route index element={<Navigate to="kb" replace />} />
            <Route path="kb" element={<AdminKnowledgePage />} />
            <Route path="ontology" element={<AdminOntologyPage />} />
            <Route path="data-quality" element={<AdminDataQualityPage />} />
            <Route path="models" element={<Navigate to="billing" replace />} />
            <Route path="tools" element={<AdminToolsPage />} />
            <Route path="skills" element={<AdminSkillsListPage />} />
            <Route path="skills/new" element={<AdminSkillComposePage />} />
            <Route path="skills/:skillId/edit" element={<AdminSkillComposePage />} />
            <Route path="agent-builder" element={<AdminAgentBuilderPage />} />
            <Route path="agent-builder/:agentId/openapi-docs" element={<AdminAgentOpenApiDocsPage />} />
            <Route path="agent-builder/:agentId" element={<AdminAgentBuilderPage />} />
            <Route path="evaluation" element={<AdminEvaluationPage />} />
            <Route path="integrations" element={<AdminIntegrationsPage />} />
            <Route path="embed-apps" element={<AdminEmbedAppsPage />} />
            <Route path="embed-apps/:appCode" element={<AdminEmbedAppsPage />} />
            <Route path="channels/wechat-kf" element={<AdminWecomKfAccountsPage />} />
            <Route path="ops" element={<AdminOpsPage />} />
            <Route path="billing" element={<AdminBillingPage />} />
            <Route path="users" element={<AdminUsersPage />} />
            <Route path="service-principals" element={<AdminServicePrincipalsPage />} />
            <Route path="company" element={<AdminCompanyPage />} />
          </Route>
        </Route>
        <Route path="/platform/login" element={<PlatformLogin />} />
        <Route path="/platform" element={<PlatformGuard />}>
          <Route element={<PlatformShell />}>
            <Route index element={<PlatformHomePage />} />
            <Route path="preferences/appearance" element={<PlatformAppearancePage />} />
            <Route path="models" element={<PlatformModelsPage />} />
            <Route path="models/providers" element={<PlatformModelsPage />} />
            <Route path="models/catalog" element={<Navigate to="/platform/models/providers" replace />} />
            <Route path="models/routes" element={<PlatformModelsPage />} />
            <Route path="integrations" element={<PlatformIntegrationsPage />} />
            <Route path="billing" element={<PlatformBillingPage />} />
            <Route path="billing/packages" element={<PlatformBillingPage />} />
            <Route path="billing/packages/:packageCode" element={<PlatformBillingPage />} />
            <Route path="billing/editions/:editionCode" element={<PlatformBillingPage />} />
            <Route path="skills" element={<PlatformSkillsPage />} />
            <Route path="skills/policies" element={<PlatformSkillsPage />} />
            <Route path="skills/dependencies" element={<PlatformSkillsPage />} />
            <Route path="skills/policy/edit" element={<PlatformSkillsPage />} />
            <Route path="skills/:skillId/edit" element={<PlatformSkillsPage />} />
            <Route path="skills/:skillId/preview" element={<PlatformSkillsPage />} />
            <Route path="skills/:skillId" element={<PlatformSkillsPage />} />
            <Route path="tools" element={<PlatformToolsPage />} />
            <Route path="tools/:toolName" element={<PlatformToolsPage />} />
            <Route path="system-apis" element={<PlatformSystemApisPage />} />
            <Route path="system-apis/applications" element={<PlatformSystemApisPage />} />
            <Route path="system-apis/:providerCode" element={<PlatformSystemApisPage />} />
            <Route path="system-apis/:providerCode/:apiId" element={<PlatformSystemApisPage />} />
            <Route path="system-apis/:providerCode/:apiId/docs" element={<PlatformSystemApisPage />} />
            <Route path="internal-applications" element={<PlatformInternalApplicationsPage />} />
            <Route path="internal-applications/integration-guide" element={<PlatformApplicationIntegrationGuidePage />} />
            <Route path="internal-applications/:appCode" element={<PlatformInternalApplicationsPage />} />
            <Route path="operations/deployment-installation" element={<PlatformDeploymentInstallationPage />} />
            <Route path="evaluation" element={<PlatformEvaluationPage />} />
            <Route path="evaluation/suites" element={<PlatformEvaluationPage />} />
            <Route path="evaluation/runs" element={<PlatformEvaluationPage />} />
            <Route path="tenants" element={<PlatformTenantsPage />} />
            <Route path="tenants/:companyId" element={<PlatformTenantApplicationsPage />} />
            <Route path="tenants/:companyId/applications/agentcici" element={<PlatformTenantDetailPage />} />
            <Route path="registered-users" element={<PlatformRegisteredUsersPage />} />
            <Route path="demo-leads" element={<PlatformAutoServiceDemoRequestsPage />} />
            <Route path="website-leads" element={<Navigate to="/platform/demo-leads" replace />} />
            <Route path="audit" element={<PlatformAuditPage />} />
          </Route>
        </Route>
  </>,
));

export default function App() {
  return <RouterProvider router={router} />;
}
