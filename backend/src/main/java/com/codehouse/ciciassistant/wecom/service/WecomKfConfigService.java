package com.codehouse.ciciassistant.wecom.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.wecom.domain.WecomKfAccountEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfAccountRepository;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WecomKfConfigService {

    public static final String DEFAULT_AFTER_SALES_AGENT_ID = "after-sales-agent";

    private final WecomKfAccountRepository accountRepository;
    private final SecretCipherService secretCipherService;
    private final AgentDefinitionService agentDefinitionService;
    private final AgentDefinitionRepository agentDefinitionRepository;

    public WecomKfConfigService(WecomKfAccountRepository accountRepository,
                                SecretCipherService secretCipherService,
                                AgentDefinitionService agentDefinitionService,
                                AgentDefinitionRepository agentDefinitionRepository) {
        this.accountRepository = accountRepository;
        this.secretCipherService = secretCipherService;
        this.agentDefinitionService = agentDefinitionService;
        this.agentDefinitionRepository = agentDefinitionRepository;
    }

    public List<ResolvedAccount> enabledAccounts() {
        return accountRepository.findByEnabledTrue().stream().map(this::resolve).toList();
    }

    public List<WecomKfAccountEntity> list(String companyId) {
        return accountRepository.findByCompanyIdOrderByUpdatedAtDescIdDesc(requireText(companyId, "companyId"));
    }

    public Optional<ResolvedAccount> findEnabled(String companyId, String openKfId) {
        if (companyId == null || companyId.isBlank() || openKfId == null || openKfId.isBlank()) {
            return Optional.empty();
        }
        return accountRepository.findByCompanyIdAndOpenKfIdAndEnabledTrue(companyId.trim(), openKfId.trim()).map(this::resolve);
    }

    public Optional<ResolvedAccount> findMobileEntry(UUID entryId) {
        if (entryId == null) {
            return Optional.empty();
        }
        return accountRepository.findByMobileEntryIdAndEnabledTrueAndMobileHandoffEnabledTrue(entryId).map(this::resolve);
    }

    @Transactional
    public WecomKfAccountEntity upsert(String companyId, String actorUserId, UpsertCommand command) {
        String normalizedCompanyId = requireText(companyId, "companyId");
        String corpId = requireText(command.corpId(), "corpId");
        String openKfId = requireText(command.openKfId(), "openKfId");
        String name = fallback(command.name(), "微信客服 " + openKfId);
        String token = requireText(command.token(), "token");
        String agentId = normalizeAgentId(fallback(command.agentId(), DEFAULT_AFTER_SALES_AGENT_ID));
        String runAsUserId = fallback(command.runAsUserId(), actorUserId);
        String wecomAppAgentId = blank(command.wecomAppAgentId());
        String wecomAppSecret = blank(command.wecomAppSecret());
        boolean mobileHandoffEnabled = Boolean.TRUE.equals(command.mobileHandoffEnabled());
        boolean enabled = command.enabled() == null || command.enabled();
        requireAgent(normalizedCompanyId, agentId);

        Optional<WecomKfAccountEntity> existing = accountRepository.findByCompanyIdAndOpenKfId(normalizedCompanyId, openKfId);
        if (existing.isPresent()) {
            WecomKfAccountEntity account = existing.get();
            boolean hasStoredAppSecret = !blank(account.getWecomAppSecretCipher()).isBlank()
                    && !blank(account.getWecomAppSecretIv()).isBlank();
            if (mobileHandoffEnabled && (wecomAppAgentId.isBlank() || (wecomAppSecret.isBlank() && !hasStoredAppSecret))) {
                throw new IllegalArgumentException("wecomAppAgentId and wecomAppSecret are required when mobile handoff is enabled");
            }
            account.updateProfile(corpId, openKfId, name, token, agentId, requireText(runAsUserId, "runAsUserId"),
                    wecomAppAgentId, mobileHandoffEnabled, enabled);
            if (!wecomAppSecret.isBlank()) {
                SecretCipherService.EncryptedSecret encrypted = secretCipherService.encryptUtf8(wecomAppSecret);
                account.updateWecomAppSecret(encrypted.cipherBase64(), encrypted.ivBase64());
            }
            if (!blank(command.secret()).isBlank()) {
                SecretCipherService.EncryptedSecret encrypted = secretCipherService.encryptUtf8(requireText(command.secret(), "secret"));
                account.updateSecret(encrypted.cipherBase64(), encrypted.ivBase64());
            }
            if (!blank(command.encodingAesKey()).isBlank()) {
                validateEncodingAesKey(command.encodingAesKey());
                SecretCipherService.EncryptedSecret encrypted = secretCipherService.encryptUtf8(requireText(command.encodingAesKey(), "encodingAesKey"));
                account.updateEncodingAesKey(encrypted.cipherBase64(), encrypted.ivBase64());
            }
            return accountRepository.save(account);
        }

        String secret = requireText(command.secret(), "secret");
        if (mobileHandoffEnabled && (wecomAppAgentId.isBlank() || wecomAppSecret.isBlank())) {
            throw new IllegalArgumentException("wecomAppAgentId and wecomAppSecret are required when mobile handoff is enabled");
        }
        String encodingAesKey = requireText(command.encodingAesKey(), "encodingAesKey");
        validateEncodingAesKey(encodingAesKey);
        SecretCipherService.EncryptedSecret encryptedSecret = secretCipherService.encryptUtf8(secret);
        SecretCipherService.EncryptedSecret encryptedAesKey = secretCipherService.encryptUtf8(encodingAesKey);
        WecomKfAccountEntity account = new WecomKfAccountEntity(
                normalizedCompanyId,
                corpId,
                openKfId,
                name,
                encryptedSecret.cipherBase64(),
                encryptedSecret.ivBase64(),
                token,
                encryptedAesKey.cipherBase64(),
                encryptedAesKey.ivBase64(),
                agentId,
                requireText(runAsUserId, "runAsUserId"));
        account.updateProfile(corpId, openKfId, name, token, agentId, requireText(runAsUserId, "runAsUserId"),
                wecomAppAgentId, mobileHandoffEnabled, enabled);
        if (!wecomAppSecret.isBlank()) {
            SecretCipherService.EncryptedSecret encrypted = secretCipherService.encryptUtf8(wecomAppSecret);
            account.updateWecomAppSecret(encrypted.cipherBase64(), encrypted.ivBase64());
        }
        return accountRepository.save(account);
    }

    @Transactional
    public WecomKfAccountEntity update(String companyId, Long id, String actorUserId, UpsertCommand command) {
        WecomKfAccountEntity existing = requireAccount(companyId, id);
        String openKfId = fallback(command.openKfId(), existing.getOpenKfId());
        if (!existing.getOpenKfId().equals(openKfId)) {
            throw new IllegalArgumentException("openKfId cannot be changed after creation");
        }
        return upsert(existing.getCompanyId(), actorUserId, new UpsertCommand(
                fallback(command.corpId(), existing.getCorpId()),
                openKfId,
                fallback(command.name(), existing.getName()),
                command.secret(),
                fallback(command.token(), existing.getToken()),
                command.encodingAesKey(),
                fallback(command.agentId(), existing.getAgentId()),
                fallback(command.runAsUserId(), existing.getRunAsUserId()),
                fallback(command.wecomAppAgentId(), existing.getWecomAppAgentId()),
                command.wecomAppSecret(),
                command.mobileHandoffEnabled() == null ? existing.isMobileHandoffEnabled() : command.mobileHandoffEnabled(),
                command.enabled() == null ? existing.isEnabled() : command.enabled()));
    }

    @Transactional
    public WecomKfAccountEntity setEnabled(String companyId, Long id, boolean enabled) {
        WecomKfAccountEntity account = requireAccount(companyId, id);
        account.updateEnabled(enabled);
        return accountRepository.save(account);
    }

    public WecomKfAccountEntity requireAccount(String companyId, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        return accountRepository.findByIdAndCompanyId(id, requireText(companyId, "companyId"))
                .orElseThrow(() -> new IllegalArgumentException("WeCom customer service account not found: " + id));
    }

    public ResolvedAccount resolveAccount(String companyId, Long id) {
        return resolve(requireAccount(companyId, id));
    }

    public Map<String, Object> toPayload(WecomKfAccountEntity account) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", account.getId());
        payload.put("corpId", account.getCorpId());
        payload.put("openKfId", account.getOpenKfId());
        payload.put("name", account.getName());
        payload.put("agentId", account.getAgentId());
        payload.put("runAsUserId", account.getRunAsUserId());
        payload.put("mobileEntryId", account.getMobileEntryId());
        payload.put("wecomAppAgentId", account.getWecomAppAgentId() == null ? "" : account.getWecomAppAgentId());
        payload.put("mobileHandoffEnabled", account.isMobileHandoffEnabled());
        payload.put("mobileEntryPath", "/wecom/kf/mobile/start?entry=" + account.getMobileEntryId());
        payload.put("enabled", account.isEnabled());
        payload.put("syncCursorPresent", account.getSyncCursor() != null && !account.getSyncCursor().isBlank());
        payload.put("accessTokenExpiresAt", account.getAccessTokenExpiresAt() == null ? "" : account.getAccessTokenExpiresAt().toString());
        payload.put("callbackPath", "/wecom/kf/callback?companyId=" + account.getCompanyId() + "&openKfId=" + account.getOpenKfId());
        return payload;
    }

    public WecomKfAccountEntity save(WecomKfAccountEntity account) {
        return accountRepository.save(account);
    }

    private ResolvedAccount resolve(WecomKfAccountEntity account) {
        return new ResolvedAccount(
                account,
                secretCipherService.decryptUtf8(account.getSecretCipher(), account.getSecretIv()),
                secretCipherService.decryptUtf8(account.getEncodingAesKeyCipher(), account.getEncodingAesKeyIv()),
                blank(account.getWecomAppSecretCipher()).isBlank() || blank(account.getWecomAppSecretIv()).isBlank()
                        ? ""
                        : secretCipherService.decryptUtf8(account.getWecomAppSecretCipher(), account.getWecomAppSecretIv()));
    }

    private void requireAgent(String companyId, String agentId) {
        agentDefinitionService.warmupBuiltinAgents(companyId);
        if (agentDefinitionRepository.findByCompanyIdAndAgentId(companyId, agentId).isEmpty()) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }
    }

    private void validateEncodingAesKey(String value) {
        String text = requireText(value, "encodingAesKey");
        if (text.length() != 43) {
            throw new IllegalArgumentException("encodingAesKey must be 43 characters");
        }
    }

    private String normalizeAgentId(String value) {
        String text = requireText(value, "agentId").toLowerCase(Locale.ROOT);
        if (!text.matches("^[a-z0-9][a-z0-9-]{1,63}$")) {
            throw new IllegalArgumentException("agentId must match ^[a-z0-9][a-z0-9-]{1,63}$");
        }
        return text;
    }

    private String fallback(String value, String fallback) {
        String text = blank(value);
        return text.isBlank() ? blank(fallback) : text;
    }

    private String requireText(String value, String field) {
        String text = blank(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return text;
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    public record UpsertCommand(String corpId,
                                String openKfId,
                                String name,
                                String secret,
                                String token,
                                String encodingAesKey,
                                String agentId,
                                String runAsUserId,
                                String wecomAppAgentId,
                                String wecomAppSecret,
                                Boolean mobileHandoffEnabled,
                                Boolean enabled) {
    }

    public record ResolvedAccount(WecomKfAccountEntity account, String secret, String encodingAesKey, String wecomAppSecret) {
        public ResolvedAccount(WecomKfAccountEntity account, String secret, String encodingAesKey) {
            this(account, secret, encodingAesKey, "");
        }
    }
}
