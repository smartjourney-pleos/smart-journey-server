package com.smartjourney.server.common.error;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** Phase 표시용: 아직 구현 안 된 API 가 호출되면 501 + 어느 Phase 작업인지 알려 준다. */
    public static BusinessException notImplemented(String phaseTask) {
        return new BusinessException(ErrorCode.NOT_IMPLEMENTED, "아직 구현되지 않은 API 입니다. (" + phaseTask + ")");
    }
}
