package com.codehouse.ciciassistant.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        String code,
        Map<String, Object> details
) {

    public ApiResponse(boolean success, T data, String message) {
        this(success, data, message, null, null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "OK");
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    public static ApiResponse<Void> okMessage(String message) {
        return new ApiResponse<>(true, null, message);
    }

    public static ApiResponse<Void> fail(String message) {
        String code = message != null && message.matches("[A-Z][A-Z0-9_]{2,127}")
                ? message
                : "需要组织管理员权限".equals(message)
                        ? "ORG_ADMIN_REQUIRED"
                        : null;
        return new ApiResponse<>(false, null, message, code, null);
    }

    public static ApiResponse<Void> fail(
            String code,
            String message,
            Map<String, Object> details) {
        return new ApiResponse<>(false, null, message, code, details);
    }
}
