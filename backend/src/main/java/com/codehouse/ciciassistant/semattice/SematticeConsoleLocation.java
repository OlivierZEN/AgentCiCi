package com.codehouse.ciciassistant.semattice;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Validates the deployment-owned public Semattice origin before it reaches a browser. */
@Component
public class SematticeConsoleLocation {
    private final String publicOrigin;

    public SematticeConsoleLocation(
            @Value("${app.semattice.console-base-url:${app.semattice.base-url:}}") String configuredOrigin) {
        this.publicOrigin = validateOrigin(configuredOrigin);
    }

    public boolean configured() {
        return !publicOrigin.isBlank();
    }

    public URI handoffUri(String ticket) {
        if (!configured()) {
            throw new IllegalStateException("Semattice console is not configured");
        }
        return URI.create(publicOrigin + "/console/handoff?ticket="
                + URLEncoder.encode(ticket, StandardCharsets.UTF_8));
    }

    static String validateOrigin(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "";
        }
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Semattice console base URL must be a valid HTTPS origin", exception);
        }
        String path = uri.getRawPath();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (path != null && !path.isBlank() && !"/".equals(path))) {
            throw new IllegalArgumentException("Semattice console base URL must be an HTTPS origin without path, query or fragment");
        }
        String origin = "https://" + uri.getHost();
        if (uri.getPort() >= 0) {
            origin += ":" + uri.getPort();
        }
        return origin;
    }
}
