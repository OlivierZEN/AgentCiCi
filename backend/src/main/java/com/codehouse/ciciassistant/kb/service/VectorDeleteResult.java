package com.codehouse.ciciassistant.kb.service;

public record VectorDeleteResult(
        boolean success,
        int requestedCount,
        int deletedCount,
        String message
) {

    public static VectorDeleteResult success(int requestedCount, int deletedCount) {
        return new VectorDeleteResult(true, requestedCount, deletedCount, "");
    }

    public static VectorDeleteResult failure(int requestedCount, String message) {
        return new VectorDeleteResult(false, requestedCount, 0, message == null ? "" : message);
    }
}
