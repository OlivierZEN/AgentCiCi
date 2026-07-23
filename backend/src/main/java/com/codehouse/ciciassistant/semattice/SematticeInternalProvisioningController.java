package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/semattice/provisioning")
public class SematticeInternalProvisioningController {

    private final InternalHmacVerifier verifier;
    private final SematticeProvisioningService service;
    private final ObjectMapper objectMapper;

    public SematticeInternalProvisioningController(InternalHmacVerifier verifier,
                                                   SematticeProvisioningService service,
                                                   ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/reservations")
    public ResponseEntity<ApiResponse<SematticeProvisioningService.BindingView>> reserve(
            @RequestBody String body, HttpServletRequest request) throws Exception {
        verify(request, body);
        ReserveRequest payload = strictReader(ReserveRequest.class).readValue(body);
        return ResponseEntity.ok(ApiResponse.ok(service.reserve(payload.companyId(), payload.idempotencyKey())));
    }

    @PostMapping("/reservations/{reservationId}/complete")
    public ResponseEntity<ApiResponse<SematticeProvisioningService.BindingView>> complete(
            @PathVariable String reservationId, @RequestBody String body, HttpServletRequest request) throws Exception {
        verify(request, body);
        CompleteRequest payload = strictReader(CompleteRequest.class).readValue(body);
        return ResponseEntity.ok(ApiResponse.ok(service.complete(reservationId, payload.companyId(), payload.tenantId(),
                payload.operationId(), payload.succeeded(), payload.failureCode())));
    }

    private void verify(HttpServletRequest request, String body) {
        verifier.verify(request.getHeader("X-Internal-Service"), request.getMethod(), request.getRequestURI(),
                request.getHeader("X-Internal-Timestamp"), request.getHeader("X-Internal-Nonce"),
                request.getHeader("X-Internal-Signature"), body);
    }

    private ObjectReader strictReader(Class<?> type) {
        return objectMapper.readerFor(type)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public record ReserveRequest(String companyId, String idempotencyKey) { }
    public record CompleteRequest(String companyId, String tenantId, String operationId, Boolean succeeded, String failureCode) { }
}
