package com.codehouse.ciciassistant.integration.service;

/** Shared runtime limits for synchronous platform-managed integrations. */
public final class PlatformIntegrationRuntimeLimits {

    public static final int DEFAULT_REQUEST_TIMEOUT_MS = 120_000;
    public static final int MIN_REQUEST_TIMEOUT_MS = 10_000;
    public static final int MAX_REQUEST_TIMEOUT_MS = 3_600_000;

    private PlatformIntegrationRuntimeLimits() {
    }
}
