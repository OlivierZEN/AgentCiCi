package com.codehouse.ciciassistant.auth.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.auth.service.AuthService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/sms/send")
    public ApiResponse<Map<String, Object>> sendSmsCode(@Valid @RequestBody SendSmsCodeRequest request) {
        return ApiResponse.ok(authService.sendSmsCode(request.orgId(), request.mobile()), "SMS code sent");
    }

    @PostMapping("/sms/login")
    public ApiResponse<Map<String, Object>> loginBySms(@Valid @RequestBody SmsLoginRequest request) {
        return ApiResponse.ok(authService.loginBySms(request.orgId(), request.mobile(), request.code()), "Login success");
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentUser() {
        String orgId = TenantContext.requireOrgId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.currentUser(orgId, userId));
    }

    public record SendSmsCodeRequest(
            @NotBlank String orgId,
            @NotBlank
            @Pattern(regexp = "^1\\d{10}$", message = "must be an 11-digit mainland China mobile number")
            String mobile
    ) {
    }

    public record SmsLoginRequest(
            @NotBlank String orgId,
            @NotBlank
            @Pattern(regexp = "^1\\d{10}$", message = "must be an 11-digit mainland China mobile number")
            String mobile,
            @NotBlank String code
    ) {
    }
}
