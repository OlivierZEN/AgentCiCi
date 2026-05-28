import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import AssistantApp from "./assistant/AssistantApp";
import AutoServiceLanding from "./autoservice/AutoServiceLanding";
import SuiteLanding from "./suite/SuiteLanding";
import AdminGuard from "./admin/AdminGuard";
import AdminLogin from "./admin/AdminLogin";
import AdminShell from "./admin/AdminShell";
import AdminKnowledgePage from "./admin/pages/AdminKnowledgePage";
import AdminModelsPage from "./admin/pages/AdminModelsPage";
import AdminToolsPage from "./admin/pages/AdminToolsPage";
import AdminOpsPage from "./admin/pages/AdminOpsPage";
import AdminUsersPage from "./admin/pages/AdminUsersPage";
import AdminOrganizationPage from "./admin/pages/AdminOrganizationPage";
import AdminIntegrationsPage from "./admin/pages/AdminIntegrationsPage";
import AdminEmbedAppsPage from "./admin/pages/AdminEmbedAppsPage";
import AdminWecomKfAccountsPage from "./admin/pages/AdminWecomKfAccountsPage";
import AdminAgentBuilderPage from "./admin/pages/AdminAgentBuilderPage";
import AdminAgentOpenApiDocsPage from "./admin/pages/AdminAgentOpenApiDocsPage";
import AdminSkillComposePage from "./admin/pages/AdminSkillComposePage";
import AdminSkillsListPage from "./admin/pages/AdminSkillsListPage";
import EmbedMeetingMinutesPage from "./embed/EmbedMeetingMinutesPage";
import PlatformGuard from "./platform/PlatformGuard";
import PlatformLogin from "./platform/PlatformLogin";
import PlatformShell from "./platform/PlatformShell";
import PlatformAuditPage from "./platform/pages/PlatformAuditPage";
import PlatformAutoServiceDemoRequestsPage from "./platform/pages/PlatformAutoServiceDemoRequestsPage";
import PlatformBillingPage from "./platform/pages/PlatformBillingPage";
import PlatformHomePage from "./platform/pages/PlatformHomePage";
import PlatformSkillsPage from "./platform/pages/PlatformSkillsPage";
import PlatformTenantDetailPage from "./platform/pages/PlatformTenantDetailPage";
import PlatformTenantsPage from "./platform/pages/PlatformTenantsPage";
import PlatformToolsPage from "./platform/pages/PlatformToolsPage";

export default function App() {
  const isSuiteWebsiteHost =
    typeof window !== "undefined" && (window.location.hostname === "agentcici.com" || window.location.hostname === "www.agentcici.com");

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={isSuiteWebsiteHost ? <SuiteLanding siteOverride="china" /> : <AssistantApp />} />
        <Route path="/suite" element={<Navigate to="/suite/cn" replace />} />
        <Route path="/suite/cn" element={<SuiteLanding siteOverride="china" />} />
        <Route path="/suite/global" element={<SuiteLanding siteOverride="global" />} />
        <Route path="/autoservice" element={<Navigate to="/autoservice/global" replace />} />
        <Route path="/autoservice/global" element={<AutoServiceLanding />} />
        <Route path="/autoservice/cn" element={<AutoServiceLanding />} />
        <Route path="/autoservice/en" element={<Navigate to="/autoservice/global" replace />} />
        <Route path="/autoservice/zh" element={<Navigate to="/autoservice/cn" replace />} />
        <Route path="/embed/meeting-minutes" element={<EmbedMeetingMinutesPage />} />
        <Route path="/admin/login" element={<AdminLogin />} />
        <Route path="/admin" element={<AdminGuard />}>
          <Route element={<AdminShell />}>
            <Route index element={<Navigate to="kb" replace />} />
            <Route path="kb" element={<AdminKnowledgePage />} />
            <Route path="models" element={<AdminModelsPage />} />
            <Route path="tools" element={<AdminToolsPage />} />
            <Route path="skills" element={<AdminSkillsListPage />} />
            <Route path="skills/new" element={<AdminSkillComposePage />} />
            <Route path="skills/:skillId/edit" element={<AdminSkillComposePage />} />
            <Route path="agent-builder" element={<AdminAgentBuilderPage />} />
            <Route path="agent-builder/:agentId/openapi-docs" element={<AdminAgentOpenApiDocsPage />} />
            <Route path="agent-builder/:agentId" element={<AdminAgentBuilderPage />} />
            <Route path="integrations" element={<AdminIntegrationsPage />} />
            <Route path="embed-apps" element={<AdminEmbedAppsPage />} />
            <Route path="embed-apps/:appCode" element={<AdminEmbedAppsPage />} />
            <Route path="channels/wechat-kf" element={<AdminWecomKfAccountsPage />} />
            <Route path="ops" element={<AdminOpsPage />} />
            <Route path="users" element={<AdminUsersPage />} />
            <Route path="organization" element={<AdminOrganizationPage />} />
          </Route>
        </Route>
        <Route path="/platform/login" element={<PlatformLogin />} />
        <Route path="/platform" element={<PlatformGuard />}>
          <Route element={<PlatformShell />}>
            <Route index element={<PlatformHomePage />} />
            <Route path="skills" element={<PlatformSkillsPage />} />
            <Route path="tools" element={<PlatformToolsPage />} />
            <Route path="billing" element={<PlatformBillingPage />} />
            <Route path="tenants" element={<PlatformTenantsPage />} />
            <Route path="tenants/:orgId" element={<PlatformTenantDetailPage />} />
            <Route path="website-leads" element={<PlatformAutoServiceDemoRequestsPage />} />
            <Route path="audit" element={<PlatformAuditPage />} />
          </Route>
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
