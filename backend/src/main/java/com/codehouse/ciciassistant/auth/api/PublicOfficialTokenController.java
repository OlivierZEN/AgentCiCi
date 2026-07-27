package com.codehouse.ciciassistant.auth.api;

import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.auth.service.ServicePrincipalTokenExchangeService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public only in routing terms: its Keycloak bearer token is verified before an OACT is issued. */
@RestController
@RequestMapping("/openapi/v1/official/service-token")
public class PublicOfficialTokenController {

    private final ServicePrincipalTokenExchangeService exchanges;

    public PublicOfficialTokenController(ServicePrincipalTokenExchangeService exchanges) {
        this.exchanges = exchanges;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> exchange(HttpServletRequest request) {
        Object value = request.getAttribute(OfficialServiceTokenExchangeFilter.TOKEN_ATTRIBUTE);
        if (!(value instanceof String token) || token.isBlank()) {
            throw new UnauthorizedException("Keycloak service bearer token is required");
        }
        OfficialAccessTokenService.IssuedToken issued = exchanges.exchangeForSemattice(token);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", issued.token());
        body.put("tokenType", "Bearer");
        body.put("expiresAt", issued.expiresAt());
        body.put("tenantId", issued.tenantId());
        body.put("companyId", issued.companyId());
        body.put("scopes", issued.scopes());
        return ApiResponse.ok(body);
    }
}
