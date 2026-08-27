package com.codehouse.ciciassistant.embed.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.embed.service.EmbedTokenService;
import com.codehouse.ciciassistant.embed.service.MeetingEmbedRuntimeService;
import com.codehouse.ciciassistant.embed.service.SisiEmbedRuntimeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/embed/v1/apps")
public class EmbedAppController {

    private final EmbedTokenService tokenService;
    private final MeetingEmbedRuntimeService runtimeService;
    private final SisiEmbedRuntimeService sisiRuntimeService;

    public EmbedAppController(EmbedTokenService tokenService,
                              MeetingEmbedRuntimeService runtimeService,
                              SisiEmbedRuntimeService sisiRuntimeService) {
        this.tokenService = tokenService;
        this.runtimeService = runtimeService;
        this.sisiRuntimeService = sisiRuntimeService;
    }

    @PostMapping("/{appCode}/tokens")
    public ApiResponse<EmbedTokenService.TokenIssue> issueToken(@PathVariable String appCode,
                                                                @RequestBody EmbedTokenService.TokenCommand command,
                                                                HttpServletRequest request) {
        return ApiResponse.ok(tokenService.issueToken(appCode, command, request));
    }

    @PostMapping("/{appCode}/sessions")
    public ApiResponse<Map<String, Object>> createSession(@PathVariable String appCode, HttpServletRequest request) {
        EmbedTokenService.AuthenticatedEmbedToken token = tokenService.authenticateEmbedToken(appCode, request);
        return ApiResponse.ok("sisi".equals(appCode)
                ? sisiRuntimeService.createSession(token)
                : runtimeService.createSession(token));
    }

    @PostMapping("/{appCode}/sessions/{sessionId}/summary")
    public ApiResponse<Map<String, Object>> summarize(@PathVariable String appCode,
                                                      @PathVariable String sessionId,
                                                      @Valid @RequestBody MeetingEmbedRuntimeService.SummaryCommand command,
                                                      HttpServletRequest request) {
        return ApiResponse.ok(runtimeService.summarize(
                tokenService.authenticateEmbedToken(appCode, request),
                sessionId,
                command));
    }

    @PostMapping("/{appCode}/sessions/{sessionId}/writeback-preview")
    public ApiResponse<Map<String, Object>> writebackPreview(@PathVariable String appCode,
                                                             @PathVariable String sessionId,
                                                             HttpServletRequest request) {
        return ApiResponse.ok(runtimeService.writebackPreview(
                tokenService.authenticateEmbedToken(appCode, request),
                sessionId));
    }

    @PostMapping("/{appCode}/sessions/{sessionId}/writeback")
    public ApiResponse<Map<String, Object>> writeback(@PathVariable String appCode,
                                                      @PathVariable String sessionId,
                                                      @RequestBody(required = false) MeetingEmbedRuntimeService.WritebackCommand command,
                                                      HttpServletRequest request) {
        return ApiResponse.ok(runtimeService.writeback(
                tokenService.authenticateEmbedToken(appCode, request),
                sessionId,
                command));
    }
}
