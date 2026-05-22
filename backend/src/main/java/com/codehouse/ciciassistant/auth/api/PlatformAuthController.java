package com.codehouse.ciciassistant.auth.api;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.auth.service.PlatformAuthService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/auth/platform")
public class PlatformAuthController {

    private final PlatformAuthService platformAuthService;

    public PlatformAuthController(PlatformAuthService platformAuthService) {
        this.platformAuthService = platformAuthService;
    }

    @PostMapping("/password/login")
    public ApiResponse<Map<String, Object>> loginByPassword(@Valid @RequestBody PlatformPasswordLoginRequest request) {
        return ApiResponse.ok(platformAuthService.loginByPassword(request.identifierValue(), request.password()), "Login success");
    }

    @GetMapping("/me")
    @RequirePlatformRole
    public ApiResponse<Map<String, Object>> currentPlatformAccount() {
        String platformAccountId = TenantContext.getUserId()
                .orElseThrow(() -> new UnauthorizedException("Missing platform account context"));
        return ApiResponse.ok(platformAuthService.currentPlatformAccount(platformAccountId));
    }

    public record PlatformPasswordLoginRequest(
            String identifier,
            String email,
            String mobile,
            @NotBlank String password
    ) {
        public String identifierValue() {
            if (identifier != null && !identifier.isBlank()) {
                return identifier;
            }
            if (email != null && !email.isBlank()) {
                return email;
            }
            return mobile;
        }
    }
}
