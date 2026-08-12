import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig(function (_a) {
    var _b, _c;
    var mode = _a.mode;
    var env = loadEnv(mode, ".", "");
    var backendTarget = env.VITE_BACKEND_TARGET || "http://127.0.0.1:8080";
    var runtimeEnv = (_c = (_b = globalThis.process) === null || _b === void 0 ? void 0 : _b.env) !== null && _c !== void 0 ? _c : {};
    var releaseVersion = (runtimeEnv.VITE_CICI_APP_VERSION || env.VITE_CICI_APP_VERSION || "dev")
        .trim()
        .replace(/[^A-Za-z0-9._-]+/g, "-");
    return {
        plugins: [react()],
        build: {
            rollupOptions: {
                output: {
                    entryFileNames: "assets/[name]-[hash]-".concat(releaseVersion, ".js"),
                    chunkFileNames: "assets/[name]-[hash]-".concat(releaseVersion, ".js"),
                    assetFileNames: "assets/[name]-[hash]-".concat(releaseVersion, "[extname]"),
                },
            },
        },
        server: {
            host: "0.0.0.0",
            port: 5173,
            proxy: {
                "/auth": { target: backendTarget, changeOrigin: true },
                "/ai": { target: backendTarget, changeOrigin: true, ws: true },
                "/ai-table": { target: backendTarget, changeOrigin: true },
                "/ws": { target: backendTarget, changeOrigin: true, ws: true },
                "/kb": { target: backendTarget, changeOrigin: true },
                "/data-quality": { target: backendTarget, changeOrigin: true },
                "/ops": { target: backendTarget, changeOrigin: true },
                "/models": { target: backendTarget, changeOrigin: true },
                "/openapi": { target: backendTarget, changeOrigin: true },
                "/tools": { target: backendTarget, changeOrigin: true },
                "/security-rules": { target: backendTarget, changeOrigin: true },
                "/integrations": { target: backendTarget, changeOrigin: true },
                "/embed/v1": { target: backendTarget, changeOrigin: true },
                "/mcp-servers": { target: backendTarget, changeOrigin: true },
                "/customer-workbench": { target: backendTarget, changeOrigin: true },
                "^/api/admin(/|$)": {
                    target: backendTarget,
                    changeOrigin: true,
                    rewrite: function (path) {
                        return path.replace(/^\/api/, "");
                    },
                },
                "^/semantic-query(/|$)": { target: backendTarget, changeOrigin: true },
                "/api/autoservice": { target: backendTarget, changeOrigin: true },
                "/agents": { target: backendTarget, changeOrigin: true },
                "/evaluation": { target: backendTarget, changeOrigin: true },
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
