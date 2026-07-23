package com.codehouse.ciciassistant.semattice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.OrgEntity;
import com.codehouse.ciciassistant.auth.domain.OrgRepository;
import com.codehouse.ciciassistant.auth.domain.SematticeProvisioningBindingEntity;
import com.codehouse.ciciassistant.auth.domain.SematticeProvisioningBindingRepository;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SematticeProvisioningServiceTest {

    private static final String ORG_ID = "org2sva14i4udjmi2t4s";

    @Test
    void reservesActiveAgentCiCiOrganizationOnceAndReplaysSameKey() {
        OrgRepository orgs = mock(OrgRepository.class);
        SematticeProvisioningBindingRepository bindings = mock(SematticeProvisioningBindingRepository.class);
        PlatformAuditService audit = mock(PlatformAuditService.class);
        SematticeProvisioningService service = new SematticeProvisioningService(orgs, bindings, audit);
        SematticeProvisioningBindingEntity binding = new SematticeProvisioningBindingEntity("reservation-1", ORG_ID, "agentcici:request-1");
        when(bindings.findByIdempotencyKey("agentcici:request-1")).thenReturn(Optional.empty(), Optional.of(binding));
        when(orgs.findById(ORG_ID)).thenReturn(Optional.of(new OrgEntity(ORG_ID, "Test org", "ACTIVE")));
        when(bindings.findByOrgId(ORG_ID)).thenReturn(Optional.empty());
        when(bindings.saveAndFlush(any(SematticeProvisioningBindingEntity.class))).thenReturn(binding);

        SematticeProvisioningService.BindingView first = service.reserve(ORG_ID, "agentcici:request-1");
        SematticeProvisioningService.BindingView replay = service.reserve(ORG_ID, "agentcici:request-1");

        assertEquals("RESERVED", first.state());
        assertEquals(first.reservationId(), replay.reservationId());
        verify(audit).log(eq(ORG_ID), eq("semattice"), eq("INTERNAL_SERVICE"),
                eq("platform.native_provisioning.reserve"), eq("semattice_provisioning"), eq("reservation-1"), any());
    }

    @Test
    void rejectsUnknownInactiveOrAlreadyBoundCompany() {
        OrgRepository orgs = mock(OrgRepository.class);
        SematticeProvisioningBindingRepository bindings = mock(SematticeProvisioningBindingRepository.class);
        SematticeProvisioningService service = new SematticeProvisioningService(orgs, bindings, mock(PlatformAuditService.class));
        when(bindings.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(orgs.findById(ORG_ID)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.reserve(ORG_ID, "request-1"));

        when(orgs.findById(ORG_ID)).thenReturn(Optional.of(new OrgEntity(ORG_ID, "Test org", "SUSPENDED")));
        assertStatus(HttpStatus.PRECONDITION_FAILED, () -> service.reserve(ORG_ID, "request-2"));

        when(orgs.findById(ORG_ID)).thenReturn(Optional.of(new OrgEntity(ORG_ID, "Test org", "ACTIVE")));
        when(bindings.findByOrgId(ORG_ID)).thenReturn(Optional.of(new SematticeProvisioningBindingEntity("bound", ORG_ID, "other-key")));
        assertStatus(HttpStatus.CONFLICT, () -> service.reserve(ORG_ID, "request-3"));
    }

    private void assertStatus(HttpStatus status, Runnable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(status, exception.getStatusCode());
    }
}
