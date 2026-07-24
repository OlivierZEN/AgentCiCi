package com.codehouse.ciciassistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.domain.AgentAccessGrantEntity;
import com.codehouse.ciciassistant.agent.domain.AgentAccessGrantRepository;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.domain.AgentPermissionAuditRepository;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentAccessControlServiceTest {

    private static final String COMPANY_ID = "demo-org";
    private static final String AGENT_ID = "access-agent";
    private static final String USER_ID = "member-1";

    @Mock
    private AgentAccessGrantRepository grantRepository;

    @Mock
    private AgentPermissionAuditRepository auditRepository;

    @Mock
    private AgentDefinitionRepository agentDefinitionRepository;

    @Mock
    private UserRepository userRepository;

    private AgentAccessControlService service;

    @BeforeEach
    void setUp() {
        service = new AgentAccessControlService(
                grantRepository,
                auditRepository,
                agentDefinitionRepository,
                userRepository,
                new ObjectMapper());
    }

    @Test
    void shouldAllowOrgAdminImplicitPermissions() {
        givenAgent(null);
        givenMember(RoleCodes.ORG_USER);

        boolean allowed = service.can(COMPANY_ID, USER_ID, List.of(RoleCodes.ORG_ADMIN), AGENT_ID, AgentPermission.MANAGE);

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldAllowOwnerImplicitPermissions() {
        givenAgent(USER_ID);
        givenMember(RoleCodes.ORG_USER);

        boolean allowed = service.can(COMPANY_ID, USER_ID, List.of(RoleCodes.ORG_USER), AGENT_ID, AgentPermission.PUBLISH);

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldAllowExplicitUserGrant() {
        givenAgent(null);
        givenMember(RoleCodes.ORG_USER);
        when(grantRepository.findByCompanyIdAndAgentIdAndStatus(COMPANY_ID, AGENT_ID, AgentAccessGrantEntity.STATUS_ACTIVE))
                .thenReturn(List.of(new AgentAccessGrantEntity(
                        COMPANY_ID,
                        AGENT_ID,
                        "USER",
                        USER_ID,
                        "RUN",
                        "MANUAL",
                        "admin-1",
                        null)));

        boolean allowed = service.can(COMPANY_ID, USER_ID, List.of(RoleCodes.ORG_USER), AGENT_ID, AgentPermission.RUN);

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldIgnoreExpiredGrant() {
        givenAgent(null);
        givenMember(RoleCodes.ORG_USER);
        when(grantRepository.findByCompanyIdAndAgentIdAndStatus(COMPANY_ID, AGENT_ID, AgentAccessGrantEntity.STATUS_ACTIVE))
                .thenReturn(List.of(new AgentAccessGrantEntity(
                        COMPANY_ID,
                        AGENT_ID,
                        "USER",
                        USER_ID,
                        "RUN",
                        "MANUAL",
                        "admin-1",
                        Instant.now().minusSeconds(60))));

        boolean allowed = service.can(COMPANY_ID, USER_ID, List.of(RoleCodes.ORG_USER), AGENT_ID, AgentPermission.RUN);

        assertThat(allowed).isFalse();
    }

    private void givenAgent(String ownerUserId) {
        AgentDefinitionEntity agent = new AgentDefinitionEntity(
                COMPANY_ID,
                AGENT_ID,
                "Access Agent",
                "",
                "",
                "qwen",
                "system",
                "",
                "BALANCED",
                "COPILOT",
                "v1",
                null,
                ownerUserId,
                false,
                true);
        when(agentDefinitionRepository.findByCompanyIdAndAgentIdAndEnabledTrue(COMPANY_ID, AGENT_ID))
                .thenReturn(Optional.of(agent));
    }

    private void givenMember(String roleCode) {
        UserEntity member = mock(UserEntity.class);
        when(member.getMemberStatus()).thenReturn(UserEntity.STATUS_ACTIVE);
        when(userRepository.findByIdAndCompany_Id(USER_ID, COMPANY_ID)).thenReturn(Optional.of(member));
    }
}
