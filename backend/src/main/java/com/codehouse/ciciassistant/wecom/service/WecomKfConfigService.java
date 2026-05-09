package com.codehouse.ciciassistant.wecom.service;

import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.wecom.domain.WecomKfAccountEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfAccountRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class WecomKfConfigService {

    private final WecomKfAccountRepository accountRepository;
    private final SecretCipherService secretCipherService;

    public WecomKfConfigService(WecomKfAccountRepository accountRepository,
                                SecretCipherService secretCipherService) {
        this.accountRepository = accountRepository;
        this.secretCipherService = secretCipherService;
    }

    public List<ResolvedAccount> enabledAccounts() {
        return accountRepository.findByEnabledTrue().stream().map(this::resolve).toList();
    }

    public Optional<ResolvedAccount> findEnabled(String orgId, String openKfId) {
        if (orgId == null || orgId.isBlank() || openKfId == null || openKfId.isBlank()) {
            return Optional.empty();
        }
        return accountRepository.findByOrgIdAndOpenKfIdAndEnabledTrue(orgId.trim(), openKfId.trim()).map(this::resolve);
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

    public record ResolvedAccount(WecomKfAccountEntity account, String secret, String encodingAesKey) {
    }
}
