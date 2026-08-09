package com.codehouse.ciciassistant.auth.api;

import com.codehouse.ciciassistant.auth.service.AuthService;
import com.codehouse.ciciassistant.auth.service.KeycloakOidcLoginService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.service.CloudccSsoService;
import com.codehouse.ciciassistant.platform.service.DevAutopilotHandoffService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import java.net.URI;

@Validated
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final CloudccSsoService cloudccSsoService;
    private final KeycloakOidcLoginService keycloakOidcLoginService;
    private final OfficialAccessTokenService officialAccessTokenService;
    private final DevAutopilotHandoffService devAutopilotHandoffs;
    private final String sematticeBaseUrl;

    public AuthController(AuthService authService,
                          CloudccSsoService cloudccSsoService,
                          KeycloakOidcLoginService keycloakOidcLoginService,
                          OfficialAccessTokenService officialAccessTokenService,
                          DevAutopilotHandoffService devAutopilotHandoffs,
                          @Value("${app.semattice.base-url:}") String sematticeBaseUrl) {
        this.authService = authService;
        this.cloudccSsoService = cloudccSsoService;
        this.keycloakOidcLoginService = keycloakOidcLoginService;
        this.officialAccessTokenService = officialAccessTokenService;
        this.devAutopilotHandoffs = devAutopilotHandoffs;
        this.sematticeBaseUrl = trimTrailingSlash(sematticeBaseUrl);
    }

    @PostMapping("/sms/send")
    public ApiResponse<Map<String, Object>> sendSmsCode(@Valid @RequestBody SendSmsCodeRequest request) {
        return ApiResponse.ok(authService.sendSmsCode(request.companyId(), request.mobile()), "SMS code sent");
    }

    @PostMapping("/sms/login")
    public ApiResponse<Map<String, Object>> loginBySms(@Valid @RequestBody SmsLoginRequest request) {
        return ApiResponse.ok(authService.loginBySms(request.companyId(), request.mobile(), request.code()), "Login success");
    }

    @PostMapping("/password/login")
    public ApiResponse<Map<String, Object>> loginByPassword(@Valid @RequestBody PasswordLoginRequest request) {
        return ApiResponse.ok(authService.loginByPassword(request.companyId(), request.identifierValue(), request.password()), "Login success");
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request.mobile(), request.password(), request.companyName()), "Register success");
    }

    @GetMapping("/oidc/login")
    public ResponseEntity<Void> startOidcLogin(@RequestParam(name = "return_to", required = false) String returnTo,
                                               HttpServletRequest request) {
        return beginOidcLogin(returnTo, request, false);
    }

    @GetMapping("/oidc/password")
    public ResponseEntity<Void> startOidcPasswordUpdate(@RequestParam(name = "return_to", required = false) String returnTo,
                                                        HttpServletRequest request) {
        return beginOidcLogin(returnTo, request, true);
    }

    private ResponseEntity<Void> beginOidcLogin(String returnTo, HttpServletRequest request, boolean passwordUpdate) {
        if (!keycloakOidcLoginService.isCanonicalStartHost(request.getHeader(HttpHeaders.HOST))) {
            URI canonical = passwordUpdate
                    ? keycloakOidcLoginService.canonicalPasswordUpdateUri(returnTo)
                    : keycloakOidcLoginService.canonicalStartUri(returnTo);
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, canonical.toString())
                    .build();
        }
        KeycloakOidcLoginService.LoginStart login = passwordUpdate
                ? keycloakOidcLoginService.startPasswordUpdate(returnTo)
                : keycloakOidcLoginService.start(returnTo);
        ResponseCookie cookie = ResponseCookie.from(KeycloakOidcLoginService.STATE_COOKIE, login.state())
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/auth/oidc")
                .maxAge(300)
                .build();
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, login.redirectUri().toString())
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @GetMapping("/oidc/callback")
    public ResponseEntity<Void> oidcCallback(@RequestParam String code,
                                             @RequestParam String state,
                                             @CookieValue(name = KeycloakOidcLoginService.STATE_COOKIE, required = false) String stateCookie) {
        URI location = keycloakOidcLoginService.complete(code, state, stateCookie);
        ResponseCookie clearCookie = ResponseCookie.from(KeycloakOidcLoginService.STATE_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/auth/oidc")
                .maxAge(0)
                .build();
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, location.toString())
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    @GetMapping("/oidc/complete")
    public ApiResponse<Map<String, Object>> completeOidcLogin(@RequestParam("ticket") String ticket) {
        return ApiResponse.ok(keycloakOidcLoginService.consumeCompletion(ticket), "Login success");
    }

    @PostMapping("/cloudcc-sso/ticket")
    public ApiResponse<Map<String, Object>> cloudccSsoTicket(@Valid @RequestBody CloudccSsoTicketRequest request) {
        return ApiResponse.ok(cloudccSsoService.issueTicket(
                request.agentCompanyId(),
                request.cloudccAccessToken(),
                request.cloudccUser(),
                request.targetPath()), "SSO ticket issued");
    }

    @PostMapping("/cloudcc-sso/consume")
    public ApiResponse<Map<String, Object>> cloudccSsoConsume(@Valid @RequestBody CloudccSsoConsumeRequest request) {
        return ApiResponse.ok(cloudccSsoService.consumeTicket(request.ticket()), "Login success");
    }

    @GetMapping("/companies")
    public ApiResponse<Map<String, Object>> companies() {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.companies(companyId, userId));
    }

    @PostMapping("/switch-company")
    public ApiResponse<Map<String, Object>> switchCompany(@Valid @RequestBody SwitchCompanyRequest request) {
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.switchCompany(userId, request.companyId()), "Company switched");
    }

    @PostMapping("/semattice/console")
    public ApiResponse<Map<String, String>> enterSematticeConsole() {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        if (TenantContext.getRoles().stream().noneMatch(RoleCodes::isOrgAdminRole)) {
            throw new ForbiddenException("当前成员没有组织管理权限");
        }
        if (sematticeBaseUrl.isBlank()) {
            throw new IllegalStateException("Semattice console is not configured");
        }
        OfficialAccessTokenService.IssuedToken issued = authService.issueSematticeOfficialAccess(
                companyId, userId, officialAccessTokenService);
        return ApiResponse.ok(Map.of("redirectUri", sematticeBaseUrl + "/console/#oact=" + issued.token()),
                "Semattice console access issued");
    }

    @PostMapping("/devautopilot/handoff")
    public ApiResponse<Map<String, Object>> createDevAutopilotHandoff() {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        DevAutopilotHandoffService.HandoffTicket handoff = devAutopilotHandoffs.issue(companyId, userId);
        return ApiResponse.ok(Map.of("ticket", handoff.ticket(), "expiresInSeconds", handoff.expiresInSeconds()),
                "DevAutopilot handoff issued");
    }

    @PostMapping("/companies")
    public ApiResponse<Map<String, Object>> createCompany(@Valid @RequestBody CreateCompanyRequest request) {
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.createCompany(userId, request.companyName()), "Company created");
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentUser() {
        if (TenantContext.getTokenType().filter("platform"::equals).isPresent()
                || (TenantContext.getCompanyId().isEmpty() && TenantContext.getRoles().stream().anyMatch(RoleCodes::isPlatformRole))) {
            throw new ForbiddenException("平台账号不能访问组织用户信息");
        }
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.currentUser(companyId, userId));
    }

    @PutMapping("/me/avatar")
    public ApiResponse<Map<String, Object>> updateMyAvatar(@Valid @RequestBody UpdateMyAvatarRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.updateCurrentUserAvatar(companyId, userId, request.avatarBase64()), "Avatar updated");
    }

    @PutMapping("/me/profile")
    public ApiResponse<Map<String, Object>> updateMyProfile(@Valid @RequestBody UpdateMyProfileRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.updateCurrentUserProfile(
                companyId,
                userId,
                request.firstName(),
                request.lastName(),
                request.displayName(),
                request.mobile(),
                request.email()), "Profile updated");
    }

    @PutMapping("/me/theme")
    public ApiResponse<Map<String, Object>> updateMyTheme(@Valid @RequestBody UpdateMyThemeRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.updateCurrentUserTheme(companyId, userId, request.themeCode()), "Theme updated");
    }

    @PutMapping("/me/password")
    public ApiResponse<Map<String, Object>> changeMyPassword(@Valid @RequestBody ChangeMyPasswordRequest request) {
        if (keycloakOidcLoginService.isEnabled()) {
            throw new IllegalStateException("统一认证已启用，请在统一账号中心修改密码");
        }
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(authService.changeCurrentUserPassword(
                companyId,
                userId,
                request.currentPassword(),
                request.newPassword()), "Password updated");
    }

    public record SendSmsCodeRequest(
            @NotBlank String companyId,
            @NotBlank
            @Pattern(regexp = "^1\\d{10}$", message = "must be an 11-digit mainland China mobile number")
            String mobile
    ) {
    }

    public record SmsLoginRequest(
            @NotBlank String companyId,
            @NotBlank
            @Pattern(regexp = "^1\\d{10}$", message = "must be an 11-digit mainland China mobile number")
            String mobile,
            @NotBlank String code
    ) {
    }

    public record PasswordLoginRequest(
            String companyId,
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
            @NotBlank String companyName
    ) {
    }

    public record CloudccSsoTicketRequest(
            @NotBlank String agentCompanyId,
            @NotBlank String cloudccAccessToken,
            Map<String, Object> cloudccUser,
            String parentOrigin,
            String targetPath
    ) {
    }

    public record CloudccSsoConsumeRequest(@NotBlank String ticket) {
    }

    public record SwitchCompanyRequest(@NotBlank String companyId) {
    }

    public record CreateCompanyRequest(@NotBlank String companyName) {
    }

    public record UpdateMyAvatarRequest(String avatarBase64) {
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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

    public record UpdateMyThemeRequest(@NotBlank String themeCode) {
    }
}
