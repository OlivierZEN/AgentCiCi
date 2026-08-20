package com.codehouse.ciciassistant.wecom.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.wecom-kf")
public class WecomKfProperties {

    private String apiBaseUrl = "https://qyapi.weixin.qq.com";
    private String oauthBaseUrl = "https://open.weixin.qq.com";
    private String publicBaseUrl = "";

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank()
                ? "https://qyapi.weixin.qq.com"
                : apiBaseUrl.trim();
    }

    public String getOauthBaseUrl() {
        return oauthBaseUrl;
    }

    public void setOauthBaseUrl(String oauthBaseUrl) {
        this.oauthBaseUrl = oauthBaseUrl == null || oauthBaseUrl.isBlank()
                ? "https://open.weixin.qq.com"
                : oauthBaseUrl.trim();
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
    }
}
