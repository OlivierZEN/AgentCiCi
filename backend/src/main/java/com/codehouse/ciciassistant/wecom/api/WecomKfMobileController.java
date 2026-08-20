package com.codehouse.ciciassistant.wecom.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import com.codehouse.ciciassistant.wecom.service.WecomKfConfigService;
import com.codehouse.ciciassistant.wecom.service.WecomKfHandoffService;
import com.codehouse.ciciassistant.wecom.service.WecomKfMobileService;
import com.codehouse.ciciassistant.wecom.service.WecomKfMobileSessionStore;
import com.codehouse.ciciassistant.wecom.service.WecomKfClient;
import com.codehouse.ciciassistant.wecom.service.WecomKfProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/wecom/kf/mobile")
public class WecomKfMobileController {

    public static final String STATE_COOKIE = "CICI_WECOM_KF_STATE";
    public static final String SESSION_COOKIE = "CICI_WECOM_KF_SESSION";
    private static final Duration OAUTH_TTL = Duration.ofMinutes(5);
    private static final Duration SESSION_TTL = Duration.ofHours(8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WecomKfConfigService configService;
    private final WecomKfMobileSessionStore sessionStore;
    private final WecomKfMobileService mobileService;
    private final WecomKfClient client;
    private final WecomKfProperties properties;

    public WecomKfMobileController(WecomKfConfigService configService,
                                   WecomKfMobileSessionStore sessionStore,
                                   WecomKfMobileService mobileService,
                                   WecomKfClient client,
                                   WecomKfProperties properties) {
        this.configService = configService;
        this.sessionStore = sessionStore;
        this.mobileService = mobileService;
        this.client = client;
        this.properties = properties;
    }

    @GetMapping("/start")
    public ResponseEntity<Void> start(@RequestParam UUID entry) {
        WecomKfConfigService.ResolvedAccount resolved = configService.findMobileEntry(entry)
                .orElseThrow(() -> new IllegalArgumentException("mobile customer service entry not found or disabled"));
        String publicBaseUrl = requirePublicBaseUrl();
        String state = randomToken();
        sessionStore.saveOAuthState(state, entry, OAUTH_TTL);
        String callback = publicBaseUrl + "/wecom/kf/mobile/callback";
        String authorize = properties.getOauthBaseUrl().replaceAll("/+$", "")
                + "/connect/oauth2/authorize?appid=" + encode(resolved.account().getCorpId())
                + "&redirect_uri=" + encode(callback)
                + "&response_type=code&scope=snsapi_base&state=" + encode(state)
                + "#wechat_redirect";
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, authorize)
                .header(HttpHeaders.SET_COOKIE, cookie(STATE_COOKIE, state, OAUTH_TTL, "/wecom/kf/mobile").toString())
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String code,
                                         @RequestParam String state,
                                         @CookieValue(name = STATE_COOKIE, required = false) String stateCookie) {
        if (!constantTimeEquals(state, stateCookie)) {
            throw new UnauthorizedException("invalid mobile OAuth state");
        }
        WecomKfMobileSessionStore.OAuthState oauth = sessionStore.consumeOAuthState(state);
        if (oauth == null) {
            throw new UnauthorizedException("mobile OAuth transaction expired");
        }
        WecomKfConfigService.ResolvedAccount resolved = configService.findMobileEntry(oauth.entryId())
                .orElseThrow(() -> new UnauthorizedException("mobile customer service entry is disabled"));
        String userId = client.resolveCurrentMember(resolved, code)
                .orElseThrow(() -> new UnauthorizedException("WeCom did not identify the current member"));
        boolean accepting = client.listServicers(resolved).stream()
                .anyMatch(servicer -> servicer.accepting() && userId.equals(servicer.userId()));
        if (!accepting) {
            throw new ForbiddenException("当前企业微信成员不是该客服账号的接待中坐席");
        }
        String sessionToken = randomToken();
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        sessionStore.saveSession(sessionToken, new WecomKfMobileSessionStore.MobileSession(
                oauth.entryId(), resolved.account().getCompanyId(), userId, expiresAt), SESSION_TTL);
        ResponseCookie clearState = cookie(STATE_COOKIE, "", Duration.ZERO, "/wecom/kf/mobile");
        ResponseCookie session = cookie(SESSION_COOKIE, sessionToken, SESSION_TTL, "/wecom/kf/mobile");
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, requirePublicBaseUrl() + "/mobile/wechat-kf")
                .header(HttpHeaders.SET_COOKIE, clearState.toString())
                .header(HttpHeaders.SET_COOKIE, session.toString())
                .build();
    }

    @GetMapping("/api/context")
    public ApiResponse<WecomKfMobileService.MobileContext> context(
            @CookieValue(name = SESSION_COOKIE, required = false) String sessionToken,
            @RequestParam String pageUrl) {
        return ApiResponse.ok(mobileService.context(sessionToken, pageUrl));
    }

    @PostMapping("/api/conversations/{conversationId}/refresh")
    public ApiResponse<WecomKfMobileService.ConversationSummary> refresh(
            @CookieValue(name = SESSION_COOKIE, required = false) String sessionToken,
            @RequestHeader(name = "X-Wecom-Kf-Request") String requestMarker,
            @PathVariable UUID conversationId) {
        requireRequestMarker(requestMarker);
        return ApiResponse.ok(mobileService.refresh(sessionToken, conversationId));
    }

    @PostMapping("/api/conversations/{conversationId}/takeover")
    public ApiResponse<WecomKfHandoffService.HandoffReceipt> takeover(
            @CookieValue(name = SESSION_COOKIE, required = false) String sessionToken,
            @RequestHeader(name = "X-Wecom-Kf-Request") String requestMarker,
            @PathVariable UUID conversationId,
            @Valid @RequestBody TakeoverRequest request) {
        requireRequestMarker(requestMarker);
        return ApiResponse.ok(mobileService.takeover(sessionToken, conversationId, request.expectedRevision(),
                request.idempotencyKey(), request.correlationId()));
    }

    private void requireRequestMarker(String value) {
        if (!"1".equals(value)) {
            throw new ForbiddenException("invalid same-origin request marker");
        }
    }

    private String requirePublicBaseUrl() {
        String value = properties.getPublicBaseUrl();
        if (value.isBlank()) {
            throw new IllegalStateException("app.wecom-kf.public-base-url is required for mobile handoff");
        }
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("app.wecom-kf.public-base-url must be an HTTPS origin");
        }
        return value;
    }

    private ResponseCookie cookie(String name, String value, Duration ttl, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(path)
                .maxAge(ttl)
                .build();
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] a = left == null ? new byte[0] : left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right == null ? new byte[0] : right.getBytes(StandardCharsets.UTF_8);
        return a.length > 0 && MessageDigest.isEqual(a, b);
    }

    private String randomToken() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record TakeoverRequest(@PositiveOrZero long expectedRevision,
                                  @NotBlank @Size(max = 128) String idempotencyKey,
                                  @NotBlank @Size(max = 128) String correlationId) {
    }
}
