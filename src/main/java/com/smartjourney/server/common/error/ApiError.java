package com.smartjourney.server.common.error;

import java.util.List;

/**
 * 공통 에러 응답 바디: {@code {"code": "...", "message": "...", "fields": [...]}}
 */
public record ApiError(String code, String message, List<FieldError> fields) {

    public record FieldError(String field, String reason) {
    }

    public static ApiError of(ErrorCode errorCode) {
        return new ApiError(errorCode.name(), errorCode.defaultMessage(), null);
    }

    public static ApiError of(ErrorCode errorCode, String message) {
        return new ApiError(errorCode.name(), message, null);
    }

    public static ApiError of(ErrorCode errorCode, List<FieldError> fields) {
        return new ApiError(errorCode.name(), errorCode.defaultMessage(), fields);
    }
}
