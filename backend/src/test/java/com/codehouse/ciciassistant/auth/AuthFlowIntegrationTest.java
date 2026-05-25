package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.auth.domain.OrgRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrgRepository orgRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AgentDefinitionRepository agentDefinitionRepository;

    @Test
    void shouldLoginByPasswordAndReadCurrentUser() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138000",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginJson.path("data").path("token").asText();
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRegisterAccountAndOwnerOrganization() throws Exception {
        String mobile = "13902400101";
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234",
                                  "organizationName": "首个组织"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.accountId").isNotEmpty())
                .andExpect(jsonPath("$.data.memberId").isNotEmpty())
                .andExpect(jsonPath("$.data.orgId").isNotEmpty())
                .andExpect(jsonPath("$.data.roles[0]").value("OWNER"))
                .andReturn();

        String orgId = objectMapper.readTree(registerResult.getResponse().getContentAsString()).path("data").path("orgId").asText();
        assertThat(orgId).matches("^org[a-z0-9]{17}$");

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString()).path("data").path("token").asText();
        mockMvc.perform(get("/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectDuplicateRegistrationForExistingMobile() throws Exception {
        String mobile = "13902400102";
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234",
                                  "organizationName": "重复组织 A"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234",
                                  "organizationName": "重复组织 B"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该手机号已注册，请登录后创建或切换组织"));
    }

    @Test
    void shouldLoginWithoutOrgDirectlyForSingleOrganizationAccount() throws Exception {
        String mobile = "13902400103";
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234",
                                  "organizationName": "单组织"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.roles[0]").value("OWNER"))
                .andExpect(jsonPath("$.data.requiresOrganizationSelection").doesNotExist());
    }

    @Test
    void shouldReturnOrganizationChoicesAndSwitchOrganizationForMultiOrgAccount() throws Exception {
        String mobile = "13902400104";
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234",
                                  "organizationName": "多组织 A"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk())
                .andReturn();
        String firstToken = objectMapper.readTree(registerResult.getResponse().getContentAsString()).path("data").path("token").asText();

        MvcResult createOrgResult = mockMvc.perform(post("/auth/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "organizationName": "多组织 B" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("OWNER"))
                .andReturn();
        String secondOrgId = objectMapper.readTree(createOrgResult.getResponse().getContentAsString()).path("data").path("orgId").asText();
        assertThat(secondOrgId).matches("^org[a-z0-9]{17}$");

        MvcResult choicesResult = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiresOrganizationSelection").value(true))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.organizations.length()").value(2))
                .andReturn();
        assertThat(objectMapper.readTree(choicesResult.getResponse().getContentAsString()).path("data").path("organizations").toString())
                .contains(secondOrgId);

        mockMvc.perform(post("/auth/switch-organization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "orgId": "%s" }
                                """.formatted(secondOrgId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orgId").value(secondOrgId))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void shouldRejectSwitchingToOrganizationOwnedByAnotherAccount() throws Exception {
        MvcResult accountA = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "13902400105",
                                  "password": "szyd1234",
                                  "organizationName": "账号 A"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult accountB = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "13902400106",
                                  "password": "szyd1234",
                                  "organizationName": "账号 B"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String tokenA = objectMapper.readTree(accountA.getResponse().getContentAsString()).path("data").path("token").asText();
        String orgB = objectMapper.readTree(accountB.getResponse().getContentAsString()).path("data").path("orgId").asText();

        mockMvc.perform(post("/auth/switch-organization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "orgId": "%s" }
                                """.formatted(orgB)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("当前账号不属于该组织"));
    }

    @Test
    void shouldRejectOrgUserForOrgAdminEndpoints() throws Exception {
        String mobile = "13902400107";
        mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult loginResult = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("token").asText();

        mockMvc.perform(get("/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldInviteSuspendRestoreAndTransferOwnerForOrganizationMembers() throws Exception {
        String ownerMobile = "13902400120";
        String memberMobile = "13902400121";
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234",
                                  "organizationName": "成员治理组织"
                                }
                                """.formatted(ownerMobile)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode registerData = objectMapper.readTree(registerResult.getResponse().getContentAsString()).path("data");
        String ownerToken = registerData.path("token").asText();
        String ownerMemberId = registerData.path("memberId").asText();

        MvcResult inviteResult = mockMvc.perform(post("/admin/users/invitations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "nickname": "治理成员",
                                  "roleCode": "ORG_ADMIN"
                                }
                                """.formatted(memberMobile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("ORG_ADMIN"))
                .andExpect(jsonPath("$.data.memberStatus").value("ACTIVE"))
                .andReturn();
        String memberId = objectMapper.readTree(inviteResult.getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(memberMobile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("ORG_ADMIN"));

        mockMvc.perform(post("/admin/users/%s/suspend".formatted(memberId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberStatus").value("SUSPENDED"));

        mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(memberMobile)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("No active organization membership"));

        mockMvc.perform(post("/admin/users/%s/restore".formatted(memberId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberStatus").value("ACTIVE"));

        mockMvc.perform(post("/admin/users/%s/suspend".formatted(ownerMemberId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("不能停用当前登录成员"));

        mockMvc.perform(post("/admin/users/%s/transfer-owner".formatted(memberId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("OWNER"));

        mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(memberMobile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("OWNER"));
    }

    @Test
    void shouldRejectWrongPassword() throws Exception {
        mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138001",
                                  "password": "bad-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid mobile or password"));
    }

    @Test
    void shouldUpdateCurrentProfileAndChangeAccountPassword() throws Exception {
        String mobile = "13902400130";
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234",
                                  "organizationName": "个人简档组织"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString()).path("data").path("token").asText();

        mockMvc.perform(put("/auth/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Yan",
                                  "lastName": "Zheng",
                                  "displayName": "郑言",
                                  "mobile": "13902400131",
                                  "email": "zhengyan@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Yan"))
                .andExpect(jsonPath("$.data.lastName").value("Zheng"))
                .andExpect(jsonPath("$.data.displayName").value("郑言"))
                .andExpect(jsonPath("$.data.nickname").value("郑言"))
                .andExpect(jsonPath("$.data.mobile").value("13902400131"))
                .andExpect(jsonPath("$.data.email").value("zhengyan@example.com"));

        mockMvc.perform(put("/auth/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "szyd1234",
                                  "newPassword": "newpass123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(true));

        mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "13902400131",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid mobile or password"));

        mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "13902400131",
                                  "password": "newpass123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "ZHENGYAN@EXAMPLE.COM",
                                  "password": "newpass123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void shouldRejectDuplicateProfileEmailIdentifier() throws Exception {
        MvcResult firstRegister = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "13902400132",
                                  "password": "szyd1234",
                                  "organizationName": "邮箱组织 A"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String firstToken = objectMapper.readTree(firstRegister.getResponse().getContentAsString()).path("data").path("token").asText();
        mockMvc.perform(put("/auth/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "邮箱账号 A",
                                  "mobile": "13902400132",
                                  "email": "shared-login@example.com"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult secondRegister = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "13902400133",
                                  "password": "szyd1234",
                                  "organizationName": "邮箱组织 B"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String secondToken = objectMapper.readTree(secondRegister.getResponse().getContentAsString()).path("data").path("token").asText();

        mockMvc.perform(put("/auth/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "邮箱账号 B",
                                  "mobile": "13902400133",
                                  "email": "SHARED-LOGIN@EXAMPLE.COM"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该邮箱已被其他账号使用"));
    }

    @Test
    void shouldDisableSmsLoginEndpoints() throws Exception {
        mockMvc.perform(post("/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138002"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SMS verification login is disabled"));

        mockMvc.perform(post("/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138002",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SMS verification login is disabled"));
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    void shouldPromoteExistingOrgUserToAdminWhenMobileInBootstrapList() throws Exception {
        var org = orgRepository.findById("demo-org").orElseThrow();
        userRepository.findByOrgIdAndMobile("demo-org", "13800138188").ifPresent(userRepository::delete);
        userRepository.flush();
        var account = userAccountRepository.findByPrimaryMobile("13800138188")
                .orElseGet(() -> userAccountRepository.save(new UserAccountEntity("13800138188")));
        userRepository.save(new UserEntity(org, account, RoleCodes.ORG_USER));

        mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138188",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("ORG_ADMIN"));
    }

    @Test
    void shouldKeepPlatformRoleOutOfOrganizationLoginAndAllowDedicatedPlatformBootstrap() throws Exception {
        mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138111",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isArray())
                .andExpect(jsonPath("$.data.roles[?(@ == 'PLATFORM_ADMIN')]").doesNotExist());

        MvcResult platformLoginResult = mockMvc.perform(post("/auth/platform/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "admin@cloudcc.com",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[?(@ == 'PLATFORM_ADMIN')]").exists())
                .andExpect(jsonPath("$.data.orgId").doesNotExist())
                .andReturn();

        String token = objectMapper.readTree(platformLoginResult.getResponse().getContentAsString()).path("data").path("token").asText();

        mockMvc.perform(get("/platform/bootstrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[?(@ == 'PLATFORM_ADMIN')]").exists())
                .andExpect(jsonPath("$.data.orgId").doesNotExist());
    }

    @Test
    void shouldExposePublicAgentAvatarsForLoginModeCube() throws Exception {
        agentDefinitionRepository.findByOrgIdAndAgentId("demo-org", "public-avatar-agent")
                .ifPresent(agentDefinitionRepository::delete);
        agentDefinitionRepository.findByOrgIdAndAgentId("demo-org", "public-avatar-disabled")
                .ifPresent(agentDefinitionRepository::delete);
        agentDefinitionRepository.flush();

        String avatarDataUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ"
                + "AAAADUlEQVR42mP8z8BQDwAFgwJ/lx6mHQAAAABJRU5ErkJggg==";
        agentDefinitionRepository.save(new AgentDefinitionEntity(
                "demo-org",
                "public-avatar-agent",
                "登录立方体智能体",
                "登录页展示头像",
                "",
                "deepseek-v4-pro",
                "",
                "",
                "standard",
                "autonomous",
                "v1",
                avatarDataUrl,
                true,
                true
        ));
        agentDefinitionRepository.save(new AgentDefinitionEntity(
                "demo-org",
                "public-avatar-disabled",
                "禁用智能体",
                "不应出现在公开头像池",
                "",
                "deepseek-v4-pro",
                "",
                "",
                "standard",
                "autonomous",
                "v1",
                avatarDataUrl,
                true,
                false
        ));
        agentDefinitionRepository.flush();

        MvcResult result = mockMvc.perform(get("/public/agents/avatars")
                        .queryParam("orgId", "demo-org"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        List<JsonNode> items = new ArrayList<>();
        data.forEach(items::add);
        JsonNode avatarItem = items.stream()
                .filter(item -> item.path("agentId").asText().equals("public-avatar-agent"))
                .findFirst()
                .orElseThrow();
        assertThat(avatarItem.path("name").asText()).isEqualTo("登录立方体智能体");
        assertThat(avatarItem.path("avatarBase64").asText()).isEqualTo(avatarDataUrl);
        assertThat(items.stream().map(item -> item.path("agentId").asText()))
                .doesNotContain("public-avatar-disabled");
    }
}
