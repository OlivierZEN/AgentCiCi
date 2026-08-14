package com.codehouse.ciciassistant.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.codehouse.ciciassistant.semattice.SematticePrincipalProjectionClient;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ServicePrincipalServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UserRepository users = mock(UserRepository.class);
    private final KeycloakIdentityProvisioningService keycloak = mock(KeycloakIdentityProvisioningService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private ServicePrincipalService service;

    @BeforeEach
    void setUp() {
        service = new ServicePrincipalService(jdbc, users, keycloak, audit, mock(SematticePrincipalProjectionClient.class),
                List.of("identity.principal.sync", "runtime.record.read", "runtime.record.update"),
                List.of("identity.principal.sync", "runtime.record.read", "runtime.record.update", "runtime.record.delete"));
    }

    @Test
    void rotatesSecretOnceWithoutWritingItToAudit() throws Exception {
        UserEntity actor = activeMember("account-director");
        when(users.findByIdAndCompany_Id("member-director", "company-a")).thenReturn(Optional.of(actor));
        stubGovernedService("principal-developer", "company-a", "dev-autopilot-developer", "ACTIVE");
        when(keycloak.rotateServiceClientSecret("dev-autopilot-developer")).thenReturn("replacement-secret");

        Map<String, Object> result = service.rotateSecret(
                "company-a", "member-director", "principal-developer");

        assertThat(result).containsEntry("clientSecret", "replacement-secret")
                .containsEntry("clientId", "dev-autopilot-developer");
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("company-a"), eq("account-director"), eq("ORG_ADMIN"),
                eq("service_principal.credential_rotated"), eq("service_principal"),
                eq("principal-developer"), detail.capture());
        assertThat(detail.getValue()).doesNotContain("replacement-secret");
    }

    @Test
    void rejectsCrossCompanyLifecycleRequestsBeforeCallingKeycloak() {
        UserEntity actor = activeMember("account-director");
        when(users.findByIdAndCompany_Id("member-director", "company-a")).thenReturn(Optional.of(actor));
        when(jdbc.query(anyString(), any(RowMapper.class), eq("principal-other"), eq("company-a")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.rotateSecret(
                "company-a", "member-director", "principal-other"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("不属于当前组织");
    }

    @Test
    void renamesClientIdWithoutRotatingTheCredential() throws Exception {
        UserEntity actor = activeMember("account-director");
        when(users.findByIdAndCompany_Id("member-director", "company-a")).thenReturn(Optional.of(actor));
        stubGovernedService("principal-developer", "company-a", "dev-autopilot-developer", "ACTIVE");
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
                eq("dev-autopilot-developer-wukong"), eq("principal-developer"))).thenReturn(0);

        Map<String, Object> result = service.renameClientId(
                "company-a", "member-director", "principal-developer", "dev-autopilot-developer-wukong");

        assertThat(result).containsEntry("clientId", "dev-autopilot-developer-wukong")
                .containsEntry("changed", true);
        verify(keycloak).renameServiceClient("dev-autopilot-developer", "dev-autopilot-developer-wukong");
        verify(jdbc).update(contains("UPDATE service_principal SET client_id"),
                eq("dev-autopilot-developer-wukong"), any(), eq("principal-developer"));
        verify(jdbc).update(contains("UPDATE principal_identity SET keycloak_client_id"),
                eq("dev-autopilot-developer-wukong"), any(), any(), eq("principal-developer"));
        verify(audit).log(eq("company-a"), eq("account-director"), eq("ORG_ADMIN"),
                eq("service_principal.client_id_renamed"), eq("service_principal"),
                eq("principal-developer"), contains("dev-autopilot-developer-wukong"));
    }

    @Test
    void replacesScopesWithinTheServiceAllowlistAndAuditsTheDelta() throws Exception {
        UserEntity actor = activeMember("account-director");
        when(users.findByIdAndCompany_Id("member-director", "company-a")).thenReturn(Optional.of(actor));
        stubGovernedService("principal-manager", "company-a", "dev-autopilot-product-manager", "ACTIVE");
        stubDevAutopilotProductManager("principal-manager", "company-a", 1);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("principal-manager")))
                .thenReturn(List.of("identity.principal.sync", "runtime.record.read", "runtime.record.update"));

        Map<String, Object> result = service.updateScopes("company-a", "member-director", "principal-manager",
                List.of("runtime.record.update", "runtime.record.delete", "runtime.record.read", "identity.principal.sync"));

        assertThat(result).containsEntry("changed", true);
        assertThat((List<String>) result.get("scopes")).containsExactly(
                "identity.principal.sync", "runtime.record.delete", "runtime.record.read", "runtime.record.update");
        verify(jdbc).update("DELETE FROM service_principal_scope WHERE service_principal_id=?", "principal-manager");
        verify(jdbc).update(contains("INSERT INTO service_principal_scope"),
                eq("principal-manager"), eq("runtime.record.delete"), any());
        verify(audit).log(eq("company-a"), eq("account-director"), eq("ORG_ADMIN"),
                eq("service_principal.scopes_updated"), eq("service_principal"),
                eq("principal-manager"), contains("runtime.record.delete"));
    }

    @Test
    void rejectsRecordDeleteForADeveloperEvenWhenTheServiceIssuerAllowsIt() throws Exception {
        UserEntity actor = activeMember("account-director");
        when(users.findByIdAndCompany_Id("member-director", "company-a")).thenReturn(Optional.of(actor));
        stubGovernedService("principal-developer", "company-a", "dev-autopilot-developer", "ACTIVE");
        stubDevAutopilotProductManager("principal-developer", "company-a", 0);

        assertThatThrownBy(() -> service.updateScopes("company-a", "member-director", "principal-developer",
                List.of("runtime.record.read", "runtime.record.update", "runtime.record.delete")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("未授权");
    }

    @Test
    void rejectsScopeOutsideTheServiceAllowlistWithoutChangingPersistence() throws Exception {
        UserEntity actor = activeMember("account-director");
        when(users.findByIdAndCompany_Id("member-director", "company-a")).thenReturn(Optional.of(actor));
        stubGovernedService("principal-manager", "company-a", "dev-autopilot-product-manager", "ACTIVE");

        assertThatThrownBy(() -> service.updateScopes("company-a", "member-director", "principal-manager",
                List.of("runtime.record.read", "authorization.manage")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("未授权");
    }

    @Test
    void rejectsAnOwnerOutsideTheCurrentTenantBeforeUpdatingAPrincipal() {
        UserEntity actor = activeMember("account-director");
        when(users.findByIdAndCompany_Id("member-director", "company-a")).thenReturn(Optional.of(actor));
        when(users.findByIdAndCompany_Id("member-other-company", "company-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(
                "company-a", "member-director", "principal-developer", "悟空", "member-other-company"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("同组织有效人类成员");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubGovernedService(String principalId, String companyId, String clientId, String status) throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(principalId), eq(companyId)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("client_id")).thenReturn(clientId);
                    when(resultSet.getString("lifecycle_status")).thenReturn(status);
                    return List.of(mapper.mapRow(resultSet, 0));
                });
    }

    private void stubDevAutopilotProductManager(String principalId, String companyId, int count) {
        when(jdbc.queryForObject(contains("resource.logical_role='product_manager'"), eq(Integer.class),
                eq(principalId), eq(companyId))).thenReturn(count);
    }

    private UserEntity activeMember(String accountId) {
        UserEntity member = mock(UserEntity.class);
        when(member.getMemberStatus()).thenReturn(UserEntity.STATUS_ACTIVE);
        when(member.getAccountId()).thenReturn(accountId);
        return member;
    }
}
