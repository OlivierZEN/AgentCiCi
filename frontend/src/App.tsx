import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import AssistantApp from "./assistant/AssistantApp";
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
import AdminSkillComposePage from "./admin/pages/AdminSkillComposePage";
import AdminSkillsListPage from "./admin/pages/AdminSkillsListPage";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<AssistantApp />} />
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
            <Route path="agent-builder/:agentId" element={<AdminAgentBuilderPage />} />
            <Route path="integrations" element={<AdminIntegrationsPage />} />
            <Route path="ops" element={<AdminOpsPage />} />
            <Route path="users" element={<AdminUsersPage />} />
          </Route>
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
