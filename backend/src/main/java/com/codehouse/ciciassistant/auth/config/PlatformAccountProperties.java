package com.codehouse.ciciassistant.auth.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.auth.bootstrap-platform-account")
public class PlatformAccountProperties {

    private boolean enabled = true;
    private String email = "admin@cloudcc.com";
    private String mobile = "18611892001";
    private String displayName = "CloudCC Platform Admin";
    private List<String> roles = new ArrayList<>(List.of("PLATFORM_ADMIN"));
    private String governanceCompanyId = "demo-org";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile == null ? "" : mobile.trim();
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? "" : displayName.trim();
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
    }

    public String getGovernanceCompanyId() {
        return governanceCompanyId;
    }

    public void setGovernanceCompanyId(String governanceCompanyId) {
        this.governanceCompanyId = governanceCompanyId == null ? "" : governanceCompanyId.trim();
    }
}
