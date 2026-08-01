package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.common.error.ForbiddenException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Exchanges a Keycloak client-credentials token for a short-lived, Semattice-only OACT.
 * This is intentionally an infrequent boundary operation; Semattice does not call it per API request.
 */
@Service
public class ServicePrincipalTokenExchangeService {

    private final JdbcTemplate jdbcTemplate;
    private final KeycloakOidcLoginService keycloak;
    private final OfficialAccessTokenService officialAccessTokens;
    private final boolean enabled;

    public ServicePrincipalTokenExchangeService(JdbcTemplate jdbcTemplate,
                                                KeycloakOidcLoginService keycloak,
                                                OfficialAccessTokenService officialAccessTokens,
                                                @Value("${app.auth.oidc.service-token-exchange.enabled:false}") boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.keycloak = keycloak;
        this.officialAccessTokens = officialAccessTokens;
        this.enabled = enabled;
    }

    public OfficialAccessTokenService.IssuedToken exchangeForSemattice(String keycloakAccessToken) {
        if (!enabled) {
            throw new ForbiddenException("机器账户令牌交换尚未启用");
        }
        KeycloakOidcLoginService.ServiceAccessToken source = keycloak.verifyServiceAccessToken(keycloakAccessToken);
        ServiceContext context = resolveActiveContext(source);
        List<String> scopes = jdbcTemplate.queryForList("""
                SELECT scope_code
                FROM service_principal_scope
                WHERE service_principal_id = ?
                ORDER BY scope_code
                """, String.class, context.principalId());
        return officialAccessTokens.issueForSematticeService(
                context.principalId(), context.ownerPrincipalId(), context.clientId(),
                context.tenantId(), context.companyId(), scopes);
    }

    private ServiceContext resolveActiveContext(KeycloakOidcLoginService.ServiceAccessToken source) {
        List<ServiceContext> matches = jdbcTemplate.query("""
                SELECT sp.principal_id, owner.owner_principal_id, sp.client_id,
                       binding.semattice_tenant_id, member.company_id
                FROM service_principal sp
                JOIN principal p ON p.id = sp.principal_id
                JOIN principal_identity identity_record ON identity_record.principal_id = sp.principal_id
                JOIN service_principal_owner owner ON owner.service_principal_id = sp.principal_id
                JOIN company_member member ON member.id = owner.company_member_id
                    AND member.account_id = owner.owner_principal_id
                JOIN company c ON c.id = member.company_id
                JOIN semattice_provisioning_binding binding ON binding.company_id = member.company_id
                WHERE identity_record.provider = 'KEYCLOAK'
                  AND identity_record.identity_type = 'SERVICE_ACCOUNT'
                  AND identity_record.issuer = ?
                  AND identity_record.subject = ?
                  AND identity_record.keycloak_client_id = ?
                  AND identity_record.binding_status = 'ACTIVE'
                  AND sp.client_id = ?
                  AND sp.token_audience = ?
                  AND p.lifecycle_status = 'ACTIVE'
                  AND owner.owner_role = 'PRIMARY'
                  AND owner.owner_status = 'ACTIVE'
                  AND member.member_status = 'ACTIVE'
                  AND c.status = 'ACTIVE'
                  AND binding.state = 'PROVISIONED'
                  AND binding.semattice_tenant_id IS NOT NULL
                """, (rs, rowNum) -> new ServiceContext(
                        rs.getString("principal_id"), rs.getString("owner_principal_id"),
                        rs.getString("client_id"), rs.getString("semattice_tenant_id"),
                        rs.getString("company_id")),
                keycloak.issuer(), source.subject(), source.clientId(), source.clientId(), OfficialAccessTokenService.SEMATTICE_AUDIENCE);
        if (matches.size() != 1) {
            throw new ForbiddenException("机器账户未绑定可用的数据平台组织，或责任人已失效");
        }
        return matches.get(0);
    }

    private record ServiceContext(String principalId, String ownerPrincipalId, String clientId,
                                  String tenantId, String companyId) {
    }
}
