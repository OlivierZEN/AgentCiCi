package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HMAC-authenticated redemption endpoint used only by the Semattice backend. */
@RestController
@RequestMapping("/internal/semattice/console-handoffs")
public class SematticeConsoleHandoffController {
    private final InternalHmacVerifier verifier;
    private final SematticeConsoleHandoffService handoffs;
    private final ObjectMapper objectMapper;

    public SematticeConsoleHandoffController(InternalHmacVerifier verifier,
                                             SematticeConsoleHandoffService handoffs,
                                             ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.handoffs = handoffs;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<SematticeConsoleHandoffService.ExchangedAccess>> redeem(
            @RequestBody String body, HttpServletRequest request) throws Exception {
        verifier.verify(request.getHeader("X-Internal-Service"), request.getMethod(), request.getRequestURI(),
                request.getHeader("X-Internal-Timestamp"), request.getHeader("X-Internal-Nonce"),
                request.getHeader("X-Internal-Signature"), body);
        RedeemRequest payload = strictReader().readValue(body);
        return ResponseEntity.ok(ApiResponse.ok(handoffs.redeem(payload.ticket())));
    }

    private ObjectReader strictReader() {
        return objectMapper.readerFor(RedeemRequest.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public record RedeemRequest(String ticket) { }
}
