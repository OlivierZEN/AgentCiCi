import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig(function (_a) {
    var mode = _a.mode;
    var env = loadEnv(mode, ".", "");
    var backendTarget = env.VITE_BACKEND_TARGET || "http://127.0.0.1:8080";
    return {
        plugins: [react()],
        server: {
            host: "0.0.0.0",
            port: 5173,
            proxy: {
                "/auth": { target: backendTarget, changeOrigin: true },
                "/ai": { target: backendTarget, changeOrigin: true, ws: true },
                "/ws": { target: backendTarget, changeOrigin: true, ws: true },
                "/kb": { target: backendTarget, changeOrigin: true },
                "/ops": { target: backendTarget, changeOrigin: true },
                "/models": { target: backendTarget, changeOrigin: true },
                "/openapi": { target: backendTarget, changeOrigin: true },
                "/tools": { target: backendTarget, changeOrigin: true },
                "/integrations": { target: backendTarget, changeOrigin: true },
                "/embed/v1": { target: backendTarget, changeOrigin: true },
                "/mcp-servers": { target: backendTarget, changeOrigin: true },
                "/admin/users": { target: backendTarget, changeOrigin: true },
                "/admin/agents": { target: backendTarget, changeOrigin: true },
                "^/admin/organization/(profile|export-jobs)(/|$)": { target: backendTarget, changeOrigin: true },
                "/admin/wecom": { target: backendTarget, changeOrigin: true },
                "/api/autoservice": { target: backendTarget, changeOrigin: true },
                "/agents": { target: backendTarget, changeOrigin: true },
                "/skills": { target: backendTarget, changeOrigin: true },
                "/feishu": { target: backendTarget, changeOrigin: true },
                "/wecom": { target: backendTarget, changeOrigin: true },
                "/me": { target: backendTarget, changeOrigin: true },
                "/api/platform": {
                    target: backendTarget,
                    changeOrigin: true,
                    rewrite: function (path) {
                        return path.replace(/^\/api\/platform/, "/platform");
                    },
                },
            },
        },
    };
});
