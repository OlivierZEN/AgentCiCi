package com.codehouse.ciciassistant.email.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.email.service.EmailAccountService;
import com.codehouse.ciciassistant.email.service.EmailAccountService.EmailAccountView;
import com.codehouse.ciciassistant.email.service.EmailAccountService.UpsertCommand;
import com.codehouse.ciciassistant.email.service.EmailAccountService.VerifyResult;
import com.codehouse.ciciassistant.email.service.EmailProviderRegistry;
import com.codehouse.ciciassistant.ops.service.AuditService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-scoped mailbox CRUD + verify. Every endpoint implicitly acts on the current
 * {@link TenantContext#getUserId() user} — no admin privilege required, and the user
 * cannot touch others' mailboxes.
 */
@RestController
@RequestMapping("/me/email-accounts")
public class EmailAccountController {

    private final EmailAccountService emailAccountService;
    private final AuditService auditService;

    public EmailAccountController(EmailAccountService emailAccountService, AuditService auditService) {
        this.emailAccountService = emailAccountService;
        this.auditService = auditService;
    }

    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> providers() {
        List<Map<String, Object>> out = EmailProviderRegistry.all().values().stream()
                .map(preset -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("code", preset.code());
                    row.put("displayLabel", preset.displayLabel());
                    Map<String, Object> pop3 = new LinkedHashMap<>();
                    pop3.put("host", preset.pop3Host());
                    pop3.put("port", preset.pop3Port());
                    pop3.put("ssl", preset.pop3Ssl());
                    row.put("pop3", pop3);
                    Map<String, Object> smtp = new LinkedHashMap<>();
                    smtp.put("host", preset.smtpHost());
                    smtp.put("port", preset.smtpPort());
                    smtp.put("sslMode", preset.smtpSslMode());
                    row.put("smtp", smtp);
                    row.put("defaultAuthType", preset.defaultAuthType());
                    return row;
                })
                .toList();
        return ApiResponse.ok(out);
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        String companyId = TenantContext.requireCompanyId();
        String userId = currentUser();
        return ApiResponse.ok(emailAccountService.list(companyId, userId).stream()
                .map(EmailAccountView::payload)
                .toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        String userId = currentUser();
        return ApiResponse.ok(emailAccountService.get(companyId, userId, id).payload());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody EmailAccountRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String userId = currentUser();
        EmailAccountView view = emailAccountService.create(companyId, userId, request.toCommand());
        auditService.log(companyId, userId, "email.account.create",
                "email=" + view.payload().get("emailAddress") + ",provider=" + view.payload().get("providerCode"));
        return ApiResponse.ok(view.payload());
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody EmailAccountRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String userId = currentUser();
        EmailAccountView view = emailAccountService.update(companyId, userId, id, request.toCommand());
        auditService.log(companyId, userId, "email.account.update",
                "id=" + id + ",email=" + view.payload().get("emailAddress"));
        return ApiResponse.ok(view.payload());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        String userId = currentUser();
        emailAccountService.delete(companyId, userId, id);
        auditService.log(companyId, userId, "email.account.delete", "id=" + id);
        return ApiResponse.ok(Map.of("id", id, "deleted", true));
    }

    @PostMapping("/{id}/verify")
    public ApiResponse<Map<String, Object>> verify(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        String userId = currentUser();
        VerifyResult result = emailAccountService.verify(companyId, userId, id);
        auditService.log(companyId, userId, "email.account.verify",
                "id=" + id + ",success=" + result.success() + ",message=" + result.message());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("success", result.success());
        out.put("message", result.message());
        return ApiResponse.ok(out);
    }

    private String currentUser() {
        return TenantContext.getUserId()
                .orElseThrow(() -> new IllegalArgumentException("Missing user context"));
    }

    public record EmailAccountRequest(
            @NotBlank String providerCode,
            @NotBlank String emailAddress,
            String loginUsername,
            String displayName,
            String authType,
            String secret,
            String pop3Host,
            Integer pop3Port,
            Boolean pop3Ssl,
            String smtpHost,
            Integer smtpPort,
            String smtpSslMode,
            Boolean requireSendConfirm,
            Boolean enabled
    ) {
        UpsertCommand toCommand() {
            return new UpsertCommand(
                    providerCode,
                    displayName,
                    emailAddress,
                    loginUsername,
                    authType,
                    secret,
                    pop3Host,
                    pop3Port,
                    pop3Ssl,
                    smtpHost,
                    smtpPort,
                    smtpSslMode,
                    requireSendConfirm,
                    enabled);
        }
    }
}
