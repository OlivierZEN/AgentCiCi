package com.codehouse.ciciassistant.wecom.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WecomKfCallbackService {

    private static final Logger log = LoggerFactory.getLogger(WecomKfCallbackService.class);

    private final WecomKfConfigService configService;
    private final WecomKfCryptoService cryptoService;
    private final WecomKfClient client;
    private final WecomKfConversationService conversationService;

    public WecomKfCallbackService(WecomKfConfigService configService,
                                  WecomKfCryptoService cryptoService,
                                  WecomKfClient client,
                                  WecomKfConversationService conversationService) {
        this.configService = configService;
        this.cryptoService = cryptoService;
        this.client = client;
        this.conversationService = conversationService;
    }

    public String verifyUrl(String msgSignature,
                            String timestamp,
                            String nonce,
                            String echostr,
                            String companyId,
                            String openKfId) {
        String encrypted = blank(echostr);
        Match match = matchAccount(msgSignature, timestamp, nonce, encrypted, companyId, openKfId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid WeCom callback signature"));
        return cryptoService.decrypt(match.account().encodingAesKey(), encrypted);
    }

    public void acceptCallback(String msgSignature,
                               String timestamp,
                               String nonce,
                               String body,
                               String companyId,
                               String openKfId) {
        String encrypted = cryptoService.extractEncrypt(body);
        if (encrypted.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing WeCom Encrypt payload");
        }
        Match match = matchAccount(msgSignature, timestamp, nonce, encrypted, companyId, openKfId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid WeCom callback signature"));
        String decryptedXml = cryptoService.decrypt(match.account().encodingAesKey(), encrypted);
        CompletableFuture.runAsync(() -> processDecryptedCallback(match.account(), decryptedXml));
    }

    private void processDecryptedCallback(WecomKfConfigService.ResolvedAccount account, String xml) {
        try {
            String event = cryptoService.text(xml, "Event");
            if (!"kf_msg_or_event".equalsIgnoreCase(event)) {
                return;
            }
            String token = cryptoService.text(xml, "Token");
            if (token.isBlank()) {
                log.warn("Ignore WeCom kf callback without sync token, companyId={}, openKfId={}",
                        account.account().getCompanyId(), account.account().getOpenKfId());
                return;
            }
            String cursor = account.account().getSyncCursor();
            boolean hasMore;
            do {
                WecomKfClient.SyncResult result = client.syncMessages(account, token, cursor);
                for (WecomKfClient.SyncedMessage message : result.messages()) {
                    conversationService.acceptCustomerMessage(account, message);
                }
                cursor = result.nextCursor();
                account.account().updateSyncCursor(cursor);
                configService.save(account.account());
                hasMore = result.hasMore();
            } while (hasMore);
        } catch (Exception ex) {
            log.error("Failed to process WeCom kf callback, companyId={}, openKfId={}",
                    account.account().getCompanyId(), account.account().getOpenKfId(), ex);
        }
    }

    private Optional<Match> matchAccount(String msgSignature,
                                         String timestamp,
                                         String nonce,
                                         String encrypted,
                                         String companyId,
                                         String openKfId) {
        List<WecomKfConfigService.ResolvedAccount> candidates = candidates(companyId, openKfId);
        return candidates.stream()
                .filter(account -> cryptoService.matches(account.account().getToken(), timestamp, nonce, encrypted, msgSignature))
                .findFirst()
                .map(Match::new);
    }

    private List<WecomKfConfigService.ResolvedAccount> candidates(String companyId, String openKfId) {
        if (companyId != null && !companyId.isBlank() && openKfId != null && !openKfId.isBlank()) {
            return configService.findEnabled(companyId, openKfId).stream().toList();
        }
        return configService.enabledAccounts();
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private record Match(WecomKfConfigService.ResolvedAccount account) {
    }
}
