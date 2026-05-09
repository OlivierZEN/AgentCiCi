package com.codehouse.ciciassistant.kb.service;

import java.util.List;

public record VectorStoreAuditResult(
        boolean success,
        int scannedCount,
        int registeredCount,
        int orphanCount,
        List<String> orphanVectorIds,
        String message
) {

    public static VectorStoreAuditResult success(int scannedCount, int registeredCount, List<String> orphanVectorIds) {
        List<String> sample = orphanVectorIds == null ? List.of() : orphanVectorIds;
        return new VectorStoreAuditResult(true, scannedCount, registeredCount, sample.size(), sample, "");
    }

    public static VectorStoreAuditResult success(int scannedCount, int registeredCount, int orphanCount, List<String> orphanVectorIds) {
        List<String> sample = orphanVectorIds == null ? List.of() : orphanVectorIds;
        return new VectorStoreAuditResult(true, scannedCount, registeredCount, orphanCount, sample, "");
    }

    public static VectorStoreAuditResult failure(int registeredCount, String message) {
        return new VectorStoreAuditResult(false, 0, registeredCount, 0, List.of(), message == null ? "" : message);
    }
}
