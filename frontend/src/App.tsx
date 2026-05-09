import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import AssistantApp from "./assistant/AssistantApp";
import AutoServiceLanding from "./autoservice/AutoServiceLanding";
import AdminGuard from "./admin/AdminGuard";
import AdminLogin from "./admin/AdminLogin";
import AdminShell from "./admin/AdminShell";
import AdminKnowledgePage from "./admin/pages/AdminKnowledgePage";
import AdminModelsPage from "./admin/pages/AdminModelsPage";
import AdminToolsPage from "./admin/pages/AdminToolsPage";
import AdminOpsPage from "./admin/pages/AdminOpsPage";
import AdminUsersPage from "./admin/pages/AdminUsersPage";
import AdminIntegrationsPage from "./admin/pages/AdminIntegrationsPage";
import AdminAgentBuilderPage from "./admin/pages/AdminAgentBuilderPage";
import AdminAgentOpenApiDocsPage from "./admin/pages/AdminAgentOpenApiDocsPage";
import AdminSkillComposePage from "./admin/pages/AdminSkillComposePage";
import AdminSkillsListPage from "./admin/pages/AdminSkillsListPage";
import PlatformGuard from "./platform/PlatformGuard";
import PlatformLogin from "./platform/PlatformLogin";
import PlatformShell from "./platform/PlatformShell";
import PlatformAuditPage from "./platform/pages/PlatformAuditPage";
import PlatformAutoServiceDemoRequestsPage from "./platform/pages/PlatformAutoServiceDemoRequestsPage";
import PlatformHomePage from "./platform/pages/PlatformHomePage";
import PlatformSkillsPage from "./platform/pages/PlatformSkillsPage";
import PlatformTenantsPage from "./platform/pages/PlatformTenantsPage";
import PlatformToolsPage from "./platform/pages/PlatformToolsPage";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<AssistantApp />} />
        <Route path="/autoservice" element={<Navigate to="/autoservice/global" replace />} />
        <Route path="/autoservice/global" element={<AutoServiceLanding />} />
        <Route path="/autoservice/cn" element={<AutoServiceLanding />} />
        <Route path="/autoservice/en" element={<Navigate to="/autoservice/global" replace />} />
        <Route path="/autoservice/zh" element={<Navigate to="/autoservice/cn" replace />} />
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
            <Route path="ops" element={<AdminOpsPage />} />
            <Route path="users" element={<AdminUsersPage />} />
          </Route>
        </Route>
        <Route path="/platform/login" element={<PlatformLogin />} />
        <Route path="/platform" element={<PlatformGuard />}>
          <Route element={<PlatformShell />}>
            <Route index element={<PlatformHomePage />} />
            <Route path="skills" element={<PlatformSkillsPage />} />
            <Route path="tools" element={<PlatformToolsPage />} />
            <Route path="tenants" element={<PlatformTenantsPage />} />
            <Route path="website-leads" element={<PlatformAutoServiceDemoRequestsPage />} />
            <Route path="audit" element={<PlatformAuditPage />} />
          </Route>
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
