package com.codehouse.ciciassistant.wecom.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.wecom-kf")
public class WecomKfProperties {

    private String apiBaseUrl = "https://qyapi.weixin.qq.com";

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank()
                ? "https://qyapi.weixin.qq.com"
                : apiBaseUrl.trim();
    }
}
