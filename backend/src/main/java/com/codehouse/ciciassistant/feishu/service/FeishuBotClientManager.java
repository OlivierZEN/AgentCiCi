package com.codehouse.ciciassistant.feishu.service;

import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.ws.Client;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class FeishuBotClientManager {

    private static final Logger log = LoggerFactory.getLogger(FeishuBotClientManager.class);

    private final FeishuBotConfigService feishuBotConfigService;
    private final FeishuBotEventBridgeService feishuBotEventBridgeService;
    private final Map<String, ManagedClient> managedClients = new ConcurrentHashMap<>();

    public FeishuBotClientManager(FeishuBotConfigService feishuBotConfigService,
                                  FeishuBotEventBridgeService feishuBotEventBridgeService) {
        this.feishuBotConfigService = feishuBotConfigService;
        this.feishuBotEventBridgeService = feishuBotEventBridgeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadEnabledClients() {
        java.util.concurrent.CompletableFuture.runAsync(() ->
                feishuBotConfigService.listEnabledConfigs().forEach(this::startOrRefreshClient));
    }

    public void refreshOrg(String companyId) {
        feishuBotConfigService.getEnabledConfig(companyId)
                .ifPresentOrElse(this::startOrRefreshClient, () -> stopClient(companyId));
    }

    private synchronized void startOrRefreshClient(FeishuBotConfigService.FeishuBotConfig config) {
        String signature = config.appId() + "::" + config.appSecret();
        ManagedClient existing = managedClients.get(config.companyId());
        if (existing != null && existing.signature().equals(signature)) {
            return;
        }
        if (existing != null) {
            shutdownSdkClient(existing.client());
        }

        EventDispatcher eventDispatcher = EventDispatcher.newBuilder("", "")
                .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                    @Override
                    public void handle(P2MessageReceiveV1 event) {
                        feishuBotEventBridgeService.acceptMessageEvent(config.companyId(), event);
                    }
                })
                .build();

        Client wsClient = new Client.Builder(config.appId(), config.appSecret())
                .eventHandler(eventDispatcher)
                .build();
        wsClient.start();
        managedClients.put(config.companyId(), new ManagedClient(signature, wsClient));
        log.info("Feishu long connection started for org {}", config.companyId());
    }

    private synchronized void stopClient(String companyId) {
        ManagedClient removed = managedClients.remove(companyId);
        if (removed != null) {
            shutdownSdkClient(removed.client());
            log.info("Feishu long connection stopped for org {}", companyId);
        }
    }

    private void shutdownSdkClient(Client client) {
        try {
            Field autoReconnectField = Client.class.getDeclaredField("autoReconnect");
            autoReconnectField.setAccessible(true);
            autoReconnectField.set(client, false);
        } catch (Exception ex) {
            log.debug("Failed to disable Feishu autoReconnect", ex);
        }
        try {
            Method disconnectMethod = Client.class.getDeclaredMethod("disconnect");
            disconnectMethod.setAccessible(true);
            disconnectMethod.invoke(client);
        } catch (Exception ex) {
            log.debug("Failed to disconnect Feishu client cleanly", ex);
        }
        try {
            Field executorField = Client.class.getDeclaredField("executor");
            executorField.setAccessible(true);
            ExecutorService executor = (ExecutorService) executorField.get(client);
            executor.shutdownNow();
        } catch (Exception ex) {
            log.debug("Failed to shutdown Feishu client executor", ex);
        }
    }

    private record ManagedClient(String signature, Client client) {
    }
}
