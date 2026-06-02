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

    public List<WecomKfAccountEntity> list(String orgId) {
        return accountRepository.findByOrgIdOrderByUpdatedAtDescIdDesc(requireText(orgId, "orgId"));
    }

    public Optional<ResolvedAccount> findEnabled(String orgId, String openKfId) {
        if (orgId == null || orgId.isBlank() || openKfId == null || openKfId.isBlank()) {
            return Optional.empty();
        }
        return accountRepository.findByOrgIdAndOpenKfIdAndEnabledTrue(orgId.trim(), openKfId.trim()).map(this::resolve);
    }

    @Transactional
    public WecomKfAccountEntity upsert(String orgId, String actorUserId, UpsertCommand command) {
        String normalizedOrgId = requireText(orgId, "orgId");
        String corpId = requireText(command.corpId(), "corpId");
        String openKfId = requireText(command.openKfId(), "openKfId");
        String name = fallback(command.name(), "微信客服 " + openKfId);
        String token = requireText(command.token(), "token");
        String agentId = normalizeAgentId(fallback(command.agentId(), DEFAULT_AFTER_SALES_AGENT_ID));
        String runAsUserId = fallback(command.runAsUserId(), actorUserId);
        boolean enabled = command.enabled() == null || command.enabled();
        requireAgent(normalizedOrgId, agentId);

        Optional<WecomKfAccountEntity> existing = accountRepository.findByOrgIdAndOpenKfId(normalizedOrgId, openKfId);
        if (existing.isPresent()) {
            WecomKfAccountEntity account = existing.get();
            account.updateProfile(corpId, openKfId, name, token, agentId, requireText(runAsUserId, "runAsUserId"), enabled);
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
        String encodingAesKey = requireText(command.encodingAesKey(), "encodingAesKey");
        validateEncodingAesKey(encodingAesKey);
        SecretCipherService.EncryptedSecret encryptedSecret = secretCipherService.encryptUtf8(secret);
        SecretCipherService.EncryptedSecret encryptedAesKey = secretCipherService.encryptUtf8(encodingAesKey);
        return accountRepository.save(new WecomKfAccountEntity(
                normalizedOrgId,
                corpId,
                openKfId,
                name,
                encryptedSecret.cipherBase64(),
                encryptedSecret.ivBase64(),
                token,
                encryptedAesKey.cipherBase64(),
                encryptedAesKey.ivBase64(),
                agentId,
                requireText(runAsUserId, "runAsUserId")));
    }

    @Transactional
    public WecomKfAccountEntity update(String orgId, Long id, String actorUserId, UpsertCommand command) {
        WecomKfAccountEntity existing = requireAccount(orgId, id);
        String openKfId = fallback(command.openKfId(), existing.getOpenKfId());
        if (!existing.getOpenKfId().equals(openKfId)) {
            throw new IllegalArgumentException("openKfId cannot be changed after creation");
        }
        return upsert(existing.getOrgId(), actorUserId, new UpsertCommand(
                fallback(command.corpId(), existing.getCorpId()),
                openKfId,
                fallback(command.name(), existing.getName()),
                command.secret(),
                fallback(command.token(), existing.getToken()),
                command.encodingAesKey(),
                fallback(command.agentId(), existing.getAgentId()),
                fallback(command.runAsUserId(), existing.getRunAsUserId()),
                command.enabled() == null ? existing.isEnabled() : command.enabled()));
    }

    @Transactional
    public WecomKfAccountEntity setEnabled(String orgId, Long id, boolean enabled) {
        WecomKfAccountEntity account = requireAccount(orgId, id);
        account.updateEnabled(enabled);
        return accountRepository.save(account);
    }

    public WecomKfAccountEntity requireAccount(String orgId, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        return accountRepository.findByIdAndOrgId(id, requireText(orgId, "orgId"))
                .orElseThrow(() -> new IllegalArgumentException("WeCom customer service account not found: " + id));
    }

    public ResolvedAccount resolveAccount(String orgId, Long id) {
        return resolve(requireAccount(orgId, id));
    }

    public Map<String, Object> toPayload(WecomKfAccountEntity account) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", account.getId());
        payload.put("corpId", account.getCorpId());
        payload.put("openKfId", account.getOpenKfId());
        payload.put("name", account.getName());
        payload.put("agentId", account.getAgentId());
        payload.put("runAsUserId", account.getRunAsUserId());
        payload.put("enabled", account.isEnabled());
        payload.put("syncCursorPresent", account.getSyncCursor() != null && !account.getSyncCursor().isBlank());
        payload.put("accessTokenExpiresAt", account.getAccessTokenExpiresAt() == null ? "" : account.getAccessTokenExpiresAt().toString());
        payload.put("callbackPath", "/wecom/kf/callback?orgId=" + account.getOrgId() + "&openKfId=" + account.getOpenKfId());
        return payload;
    }

    public WecomKfAccountEntity save(WecomKfAccountEntity account) {
        return accountRepository.save(account);
    }

    private ResolvedAccount resolve(WecomKfAccountEntity account) {
        return new ResolvedAccount(
                account,
                secretCipherService.decryptUtf8(account.getSecretCipher(), account.getSecretIv()),
                secretCipherService.decryptUtf8(account.getEncodingAesKeyCipher(), account.getEncodingAesKeyIv()));
    }

    private void requireAgent(String orgId, String agentId) {
        agentDefinitionService.warmupBuiltinAgents(orgId);
        if (agentDefinitionRepository.findByOrgIdAndAgentId(orgId, agentId).isEmpty()) {
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
                                Boolean enabled) {
    }

    public record ResolvedAccount(WecomKfAccountEntity account, String secret, String encodingAesKey) {
    }
}
