package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.semattice.SematticeSystemApiCatalogClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Curated cross-application contracts. Listing an API never grants its runtime permission. */
@Service
public class SystemApiCatalogService {

    private final SematticeSystemApiCatalogClient semattice;
    private final ObjectMapper objectMapper;

    public SystemApiCatalogService(SematticeSystemApiCatalogClient semattice, ObjectMapper objectMapper) {
        this.semattice = semattice;
        this.objectMapper = objectMapper;
    }

    public CatalogView catalog() {
        ProviderView agentCiCi = new ProviderView(
                "agentcici", "AgentCiCi", "统一身份、应用激活与跨应用交接控制面。",
                "v1", "available", "", agentCiCiEntries());
        ProviderView sematticeProvider = semattice.fetch()
                .map(this::projectSemattice)
                .orElseGet(() -> new ProviderView(
                        "semattice", "Semattice", "业务数据、元数据、身份投影与授权能力。",
                        "v1", "unavailable", "暂时无法读取 Semattice 提供方目录；AgentCiCi 目录仍可使用。", List.of()));
        return new CatalogView(
                "v1",
                "系统 API 目录只收录可被内部生态应用稳定依赖的核心契约；目录可见不代表已获得调用权限。",
                List.of(agentCiCi, sematticeProvider));
    }

    private ProviderView projectSemattice(JsonNode source) {
        List<ApiView> entries = new ArrayList<>();
        source.path("apis").forEach(item -> entries.add(new ApiView(
                item.path("id").asText(),
                item.path("title").asText(),
                item.path("summary").asText(),
                item.path("description").asText(),
                item.path("category").asText(),
                item.path("method").asText(),
                item.path("path").asText(),
                strings(item.path("protocols")),
                item.path("auth_type").asText(),
                item.path("audience").asText(),
                item.path("required_scope").asText(),
                item.path("risk_level").asText(),
                item.path("version").asText(),
                item.path("state").asText(),
                item.path("idempotency_required").asBoolean(),
                item.path("execution_mode").asText(),
                item.path("approval_required").asBoolean(),
                strings(item.path("consumers")),
                item.path("input_schema"),
                item.path("output_schema"),
                item.path("request_example"),
                item.path("response_example"),
                strings(item.path("error_codes")),
                item.path("compatibility").asText(),
                item.path("source_contract").asText(),
                List.of("Bearer OACT 中的租户与主体上下文由服务端绑定。", "HTTP、MCP 与 CLI 投影使用同一 Capability Contract。"),
                null
        )));
        return new ProviderView(
                textOr(source, "code", "semattice"), textOr(source, "name", "Semattice"),
                source.path("description").asText(), textOr(source, "contract_version", "v1"),
                textOr(source, "status", "available"), "", List.copyOf(entries));
    }

    private List<ApiView> agentCiCiEntries() {
        return List.of(
                entry("agentcici.organization.list", "可访问公司列表",
                        "使用当前 Keycloak HUMAN Access Token 查询账号拥有有效成员关系的公司。",
                        "身份与公司", "GET", "/openapi/v1/ecosystem/companies", "Bearer Keycloak HUMAN Access Token", "AgentCiCi",
                        EcosystemApplicationTrustService.ORGANIZATION_READ_SCOPE, "low", false,
                        schema("{\"type\":\"object\",\"description\":\"No request body. The HUMAN account is derived from the verified Keycloak access token.\"}"),
                        schema("{\"type\":\"object\",\"required\":[\"success\",\"data\",\"message\"],\"properties\":{\"success\":{\"type\":\"boolean\"},\"data\":{\"type\":\"object\",\"required\":[\"appCode\",\"accountId\",\"tokenExpiresAt\",\"companies\"],\"properties\":{\"appCode\":{\"type\":\"string\"},\"accountId\":{\"type\":\"string\"},\"tokenExpiresAt\":{\"type\":\"string\"},\"companies\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"required\":[\"companyId\",\"companyName\",\"memberId\",\"roleCode\"]}}}},\"message\":{\"type\":\"string\"}}}"),
                        Map.of(), Map.of(
                                "success", true,
                                "data", Map.of(
                                        "appCode", "${REGISTERED_APP_CODE}",
                                        "accountId", "${ACCOUNT_ID}",
                                        "tokenExpiresAt", "${ISO_8601}",
                                        "companies", List.of(Map.of(
                                                "companyId", "${COMPANY_ID}",
                                                "companyName", "${COMPANY_NAME}",
                                                "memberId", "${MEMBER_ID}",
                                                "roleCode", "ORG_USER"))),
                                "message", "OK"),
                        List.of("内部独立应用", "生态扩展应用"), "FEAT-183 Ecosystem Keycloak HUMAN Contract",
                        List.of("只返回当前全局账号下公司与成员状态均有效的公司，不接受客户端传入 accountId。",
                                "Bearer 必须是 access_token；id_token 不能调用系统 API。",
                                "应用身份由 Keycloak azp 与平台登记的 Client 精确匹配，调用方不传 appCode。"),
                        companyHumanAuthGuide()),
                entry("agentcici.organization.context", "建立公司调用上下文",
                        "校验当前 Keycloak 用户对目标公司的有效成员关系，返回后续请求使用的无状态公司上下文。",
                        "身份与公司", "POST", "/openapi/v1/ecosystem/company-context", "Bearer Keycloak HUMAN Access Token", "AgentCiCi",
                        EcosystemApplicationTrustService.ORGANIZATION_CONTEXT_SCOPE, "medium", false,
                        schema("{\"type\":\"object\",\"required\":[\"companyId\"],\"properties\":{\"companyId\":{\"type\":\"string\"}},\"additionalProperties\":false}"),
                        schema("{\"type\":\"object\",\"required\":[\"success\",\"data\",\"message\"],\"properties\":{\"success\":{\"type\":\"boolean\"},\"data\":{\"type\":\"object\",\"required\":[\"appCode\",\"companyId\",\"companyName\",\"memberId\",\"accountId\",\"roles\",\"companyHeader\",\"tokenExpiresAt\"]},\"message\":{\"type\":\"string\"}}}"),
                        Map.of("companyId", "${TARGET_COMPANY_ID}"), Map.of(
                                "success", true,
                                "data", Map.of(
                                        "appCode", "${REGISTERED_APP_CODE}",
                                        "companyId", "${TARGET_COMPANY_ID}",
                                        "companyName", "${COMPANY_NAME}",
                                        "memberId", "${TARGET_MEMBER_ID}",
                                        "accountId", "${ACCOUNT_ID}",
                                        "roles", List.of("ORG_USER"),
                                        "companyHeader", "X-Company-Id",
                                        "tokenExpiresAt", "${ISO_8601}"),
                                "message", "Company context authorized"),
                        List.of("内部独立应用", "生态扩展应用"), "FEAT-183 Ecosystem Keycloak HUMAN Contract",
                        List.of("目标 companyId 必须来自受信的公司列表，并由当前 HUMAN 用户主动选择。",
                                "服务端按当前账号重新校验目标公司的 ACTIVE 成员关系；不属于目标公司时返回 403。",
                                "不签发第二套长期令牌；后续调用继续使用同一 Keycloak access_token 并携带 X-Company-Id。",
                                "切换公司后必须清空上一公司的业务缓存；每个公司级 API 都会重新校验成员关系。"),
                        companyHumanAuthGuide()),
                entry("agentcici.service-token.exchange", "SERVICE Token 交换",
                        "把已验证的 Keycloak SERVICE Bearer Token 交换为面向 Semattice 的短时 OACT。",
                        "服务身份", "POST", "/openapi/v1/official/service-token", "Keycloak SERVICE Bearer", "Semattice",
                        "service principal scopes", "medium", true,
                        schema("{\"type\":\"object\",\"description\":\"No request body; bearer token is supplied in Authorization header.\"}"),
                        schema("{\"type\":\"object\",\"required\":[\"accessToken\",\"tokenType\",\"expiresAt\",\"tenantId\",\"companyId\",\"scopes\"]}"),
                        Map.of(), Map.of("accessToken", "${OACT}", "tokenType", "Bearer", "expiresAt", "${ISO_8601}", "tenantId", "${TENANT_ID}", "companyId", "${COMPANY_ID}", "scopes", List.of("${SCOPE}")),
                        List.of("DevAutopilot", "内部生态应用"), "FEAT-180 Official Access Token Contract",
                        List.of("必须使用已激活且已绑定租户应用的 SERVICE 身份。", "OACT 不应写入日志、浏览器存储或持久化配置。")),
                entry("agentcici.devautopilot.activation.resolve", "DevAutopilot 激活状态",
                        "基于已验证 OACT 解析当前公司的 DevAutopilot 应用激活、路由和授权准备状态。",
                        "租户与应用", "GET", "/openapi/v1/official/devautopilot/activation", "Bearer OACT", "AgentCiCi",
                        "signed OACT scopes", "low", false,
                        schema("{\"type\":\"object\",\"description\":\"No request body. Company is derived from the verified token.\"}"),
                        schema("{\"type\":\"object\",\"additionalProperties\":true}"),
                        Map.of(), Map.of("status", "ACTIVE", "companyId", "${COMPANY_ID}"),
                        List.of("DevAutopilot"), "FEAT-164 Standard Tenant Application Contract",
                        List.of("不得从 URL 或请求体传入 companyId。", "应用、主体或 Semattice 投影失效时失败关闭。")),
                entry("agentcici.devautopilot.handoff.exchange", "DevAutopilot 交接票据兑换",
                        "把高熵、短时、单次 handoff ticket 兑换为 DevAutopilot 会话所需的短时访问令牌。",
                        "跨应用交接", "POST", "/openapi/v1/official/devautopilot/handoff/exchange", "Single-use handoff ticket", "DevAutopilot",
                        "ticket-bound", "medium", true,
                        schema("{\"type\":\"object\",\"required\":[\"ticket\"],\"properties\":{\"ticket\":{\"type\":\"string\"}},\"additionalProperties\":false}"),
                        schema("{\"type\":\"object\",\"required\":[\"accessToken\",\"expiresAt\",\"tenantId\",\"companyId\"]}"),
                        Map.of("ticket", "${ONE_TIME_TICKET}"), Map.of("accessToken", "${OACT}", "expiresAt", "${ISO_8601}", "tenantId", "${TENANT_ID}", "companyId", "${COMPANY_ID}"),
                        List.of("DevAutopilot"), "INT-013 Cross-application Handoff Contract",
                        List.of("票据只能兑换一次且必须在短时窗口内使用。", "浏览器不应直接持有或长期保存兑换后的 OACT。")),
                entry("agentcici.semattice.provisioning.reserve", "Semattice 开通预留",
                        "由 Semattice 在执行租户开通前向 AgentCiCi 预留并校验公司身份。",
                        "租户与应用", "POST", "/internal/semattice/provisioning/reservations", "Internal HMAC", "AgentCiCi",
                        "internal service allowlist", "medium", true,
                        schema("{\"type\":\"object\",\"required\":[\"companyId\",\"idempotencyKey\"],\"properties\":{\"companyId\":{\"type\":\"string\"},\"idempotencyKey\":{\"type\":\"string\"}},\"additionalProperties\":false}"),
                        schema("{\"type\":\"object\",\"additionalProperties\":true}"),
                        Map.of("companyId", "${COMPANY_ID}", "idempotencyKey", "${IDEMPOTENCY_KEY}"), Map.of("state", "RESERVED", "reservationId", "${RESERVATION_ID}"),
                        List.of("Semattice"), "INT-001 Tenant Provisioning Contract",
                        List.of("只允许已登记的内部服务调用。", "签名覆盖 method、path、timestamp、nonce 和 body hash。")),
                entry("agentcici.semattice.console-handoff.redeem", "Semattice 控制台交接兑换",
                        "由 Semattice 后端兑换 AgentCiCi 签发的单次控制台交接票据。",
                        "跨应用交接", "POST", "/internal/semattice/console-handoffs/redeem", "Internal HMAC + single-use ticket", "Semattice",
                        "internal service allowlist", "medium", true,
                        schema("{\"type\":\"object\",\"required\":[\"ticket\"],\"properties\":{\"ticket\":{\"type\":\"string\"}},\"additionalProperties\":false}"),
                        schema("{\"type\":\"object\",\"additionalProperties\":true}"),
                        Map.of("ticket", "${ONE_TIME_TICKET}"), Map.of("accessToken", "${OACT}", "expiresAt", "${ISO_8601}"),
                        List.of("Semattice"), "INT-013 Console Handoff Contract",
                        List.of("只能由 Semattice 后端调用，浏览器不调用内部端点。", "兑换后由 Semattice 建立短时 HttpOnly 管理会话。")),
                entry("agentcici.oact.jwks", "OACT 签名公钥",
                        "发布 AgentCiCi OACT 验签所需的 JWKS，供受信应用按 key id 轮换缓存。",
                        "服务身份", "GET", "/.well-known/agentcici-oact-jwks.json", "Public verification metadata", "AgentCiCi",
                        "none", "low", false,
                        schema("{\"type\":\"object\",\"description\":\"No request body.\"}"),
                        schema("{\"type\":\"object\",\"required\":[\"keys\"],\"properties\":{\"keys\":{\"type\":\"array\"}}}"),
                        Map.of(), Map.of("keys", List.of(Map.of("kid", "${KEY_ID}", "kty", "RSA"))),
                        List.of("Semattice", "DevAutopilot", "内部生态应用"), "FEAT-180 OACT JWKS Contract",
                        List.of("缓存时间遵循响应 Cache-Control。", "未知 kid 时刷新 JWKS；不得跳过 issuer、audience 与时效校验。"))
        );
    }

    private ApiView entry(String id, String title, String summary, String category, String method, String path,
                          String authType, String audience, String scope, String risk, boolean idempotent,
                          JsonNode inputSchema, JsonNode outputSchema, Map<String, Object> requestExample,
                          Map<String, Object> responseExample, List<String> consumers, String sourceContract,
                          List<String> callNotes) {
        return entry(id, title, summary, category, method, path, authType, audience, scope, risk, idempotent,
                inputSchema, outputSchema, requestExample, responseExample, consumers, sourceContract, callNotes, null);
    }

    private ApiView entry(String id, String title, String summary, String category, String method, String path,
                          String authType, String audience, String scope, String risk, boolean idempotent,
                          JsonNode inputSchema, JsonNode outputSchema, Map<String, Object> requestExample,
                          Map<String, Object> responseExample, List<String> consumers, String sourceContract,
                          List<String> callNotes, AuthGuideView authGuide) {
        return new ApiView(id, title, summary, summary, category, method, path, List.of("HTTP"), authType, audience,
                scope, risk, "v1", "published", idempotent, "synchronous", false, consumers,
                inputSchema, outputSchema, objectMapper.valueToTree(requestExample), objectMapper.valueToTree(responseExample),
                List.of("UNAUTHENTICATED", "UNAUTHORIZED", "VALIDATION_FAILED", "FAILED_PRECONDITION", "CONFLICT", "INTERNAL"),
                "v1 新增可选字段向后兼容；破坏性变更需发布新契约版本。", sourceContract, callNotes, authGuide);
    }

    private static AuthGuideView companyHumanAuthGuide() {
        return new AuthGuideView(
                "Keycloak HUMAN Access Token",
                true,
                "已登记内部应用的 Keycloak access_token 可直接调用；id_token 只用于客户端登录展示，不能作为 API Bearer Token。",
                List.of(
                        new AuthFlowStepView("application-registration", "登记内部应用", "平台管理员登记 app_code、Keycloak client_id 和允许 Scope；运行时不需要 AgentCiCi Client Secret。"),
                        new AuthFlowStepView("keycloak-login", "使用应用自己的 Keycloak Client 登录", "应用执行标准 Authorization Code + PKCE，取得 aud 包含 agentcici-api 的 access_token。"),
                        new AuthFlowStepView("direct-call", "直接携带 Access Token", "应用发送 Authorization: Bearer <KEYCLOAK_ACCESS_TOKEN>；AgentCiCi 从 azp 识别受信应用。"),
                        new AuthFlowStepView("identity-membership", "服务端逐请求授权", "AgentCiCi 验证令牌并按 (issuer, sub) 映射 HUMAN 账号，重新校验应用 Scope 和 ACTIVE 公司成员关系。"),
                        new AuthFlowStepView("company-context", "显式传递公司上下文", "公司级 API 继续使用同一 access_token，并发送服务端已校验的 X-Company-Id；不再交换第二套长期 Token。")
                ),
                List.of(
                        new AuthScenarioView("new-standalone-app", "新加入的独立应用", "已支持", "登记 Keycloak Client 与允许 Scope 后，直接使用该 Client 取得的 access_token 调用 /openapi/v1/ecosystem/*；无需专用 handoff 或二次 Token 交换。"),
                        new AuthScenarioView("agentcici-web", "AgentCiCi 自身前端", "保持现状", "继续沿用 /auth/oidc/login 与 AgentCiCi BFF 会话，不需要改造为生态直调入口。"),
                        new AuthScenarioView("same-origin-extension", "AgentCiCi 同源扩展应用", "可选", "可以复用 AgentCiCi 受管会话，也可以登记独立 Keycloak Client 后使用同一生态直调契约。"),
                        new AuthScenarioView("service-app", "机器或服务应用", "不适用", "使用 SERVICE Principal、Keycloak SERVICE Token 交换与 OACT 契约；不得调用面向 HUMAN 会话的公司列表和切换接口。")
                ),
                List.of(
                        "aud 必须包含 agentcici-api",
                        "iss 必须是 AgentCiCi 配置的 Keycloak Realm，签名、exp 必须有效",
                        "Token typ 必须为 Bearer；Keycloak id_token 不满足本接口要求",
                        "azp 必须精确匹配平台登记且状态为 ACTIVE 的 Keycloak client_id",
                        "(iss, sub) 必须已绑定 ACTIVE AgentCiCi HUMAN 账号",
                        "应用必须具备接口要求的 Scope；公司级调用必须携带 X-Company-Id 并通过 ACTIVE 成员校验")
        );
    }

    private JsonNode schema(String source) {
        try {
            return objectMapper.readTree(source);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("invalid built-in system API schema", exception);
        }
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        Iterator<JsonNode> iterator = node.elements();
        while (iterator.hasNext()) values.add(iterator.next().asText());
        return List.copyOf(values);
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText();
        return value.isBlank() ? fallback : value;
    }

    public record CatalogView(String contractVersion, String notice, List<ProviderView> providers) { }

    public record ProviderView(String code, String name, String description, String contractVersion,
                               String status, String statusMessage, List<ApiView> apis) { }

    public record ApiView(String id, String title, String summary, String description, String category,
                          String method, String path, List<String> protocols, String authType, String audience,
                          String requiredScope, String riskLevel, String version, String state,
                          boolean idempotencyRequired, String executionMode, boolean approvalRequired,
                          List<String> consumers, JsonNode inputSchema, JsonNode outputSchema,
                          JsonNode requestExample, JsonNode responseExample, List<String> errorCodes,
                          String compatibility, String sourceContract, List<String> callNotes,
                          AuthGuideView authGuide) { }

    public record AuthGuideView(String acceptedToken, boolean directKeycloakTokenAccepted,
                                String directKeycloakTokenReason, List<AuthFlowStepView> currentFlow,
                                List<AuthScenarioView> scenarios, List<String> tokenRequirements) { }

    public record AuthFlowStepView(String code, String title, String description) { }

    public record AuthScenarioView(String code, String title, String status, String instruction) { }
}
