package com.codehouse.ciciassistant.auth.api;

import com.codehouse.ciciassistant.auth.service.AuthService;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PostMapping("/password/login")
    public ApiResponse<Map<String, Object>> loginByPassword(@Valid @RequestBody PasswordLoginRequest request) {
        return ApiResponse.ok(authService.loginByPassword(request.orgId(), request.identifierValue(), request.password()), "Login success");
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request.mobile(), request.password(), request.organizationName()), "Register success");
    }

    @GetMapping("/organizations")
    public ApiResponse<Map<String, Object>> organizations() {
        String orgId = TenantContext.requireOrgId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.organizations(orgId, userId));
    }

    @PostMapping("/switch-organization")
    public ApiResponse<Map<String, Object>> switchOrganization(@Valid @RequestBody SwitchOrganizationRequest request) {
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.switchOrganization(userId, request.orgId()), "Organization switched");
    }

    @PostMapping("/organizations")
    public ApiResponse<Map<String, Object>> createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.createOrganization(userId, request.organizationName()), "Organization created");
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentUser() {
        if (TenantContext.getTokenType().filter("platform"::equals).isPresent()
                || (TenantContext.getOrgId().isEmpty() && TenantContext.getRoles().stream().anyMatch(RoleCodes::isPlatformRole))) {
            throw new ForbiddenException("平台账号不能访问组织用户信息");
        }
        String orgId = TenantContext.requireOrgId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.currentUser(orgId, userId));
    }

    @PutMapping("/me/avatar")
    public ApiResponse<Map<String, Object>> updateMyAvatar(@Valid @RequestBody UpdateMyAvatarRequest request) {
        String orgId = TenantContext.requireOrgId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.updateCurrentUserAvatar(orgId, userId, request.avatarBase64()), "Avatar updated");
    }

    @PutMapping("/me/profile")
    public ApiResponse<Map<String, Object>> updateMyProfile(@Valid @RequestBody UpdateMyProfileRequest request) {
        String orgId = TenantContext.requireOrgId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.updateCurrentUserProfile(
                orgId,
                userId,
                request.firstName(),
                request.lastName(),
                request.displayName(),
                request.mobile(),
                request.email()), "Profile updated");
    }

    @PutMapping("/me/password")
    public ApiResponse<Map<String, Object>> changeMyPassword(@Valid @RequestBody ChangeMyPasswordRequest request) {
        String orgId = TenantContext.requireOrgId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.changeCurrentUserPassword(
                orgId,
                userId,
                request.currentPassword(),
                request.newPassword()), "Password updated");
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

    public record PasswordLoginRequest(
            String orgId,
            String identifier,
            String mobile,
            @NotBlank String password
    ) {
        public String identifierValue() {
            return identifier == null || identifier.isBlank() ? mobile : identifier;
        }
    }

    public record RegisterRequest(
            @NotBlank
            @Pattern(regexp = "^1\\d{10}$", message = "must be an 11-digit mainland China mobile number")
            String mobile,
            @NotBlank String password,
            @NotBlank String organizationName
    ) {
    }

    public record SwitchOrganizationRequest(@NotBlank String orgId) {
    }

    public record CreateOrganizationRequest(@NotBlank String organizationName) {
    }

    public record UpdateMyAvatarRequest(String avatarBase64) {
    }

    public record UpdateMyProfileRequest(
            String firstName,
            String lastName,
            String displayName,
            @NotBlank
            @Pattern(regexp = "^1\\d{10}$", message = "must be an 11-digit mainland China mobile number")
            String mobile,
            String email
    ) {
    }

    public record ChangeMyPasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank String newPassword
    ) {
    }
}
