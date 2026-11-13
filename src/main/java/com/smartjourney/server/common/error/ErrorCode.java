package com.smartjourney.server.common.error;

import org.springframework.http.HttpStatus;

/**
 * 모든 에러 응답은 {@code {code, message}} 형식. 클라이언트(차량 앱·POS)는 code 로 분기한다.
 */
public enum ErrorCode {

    // 400
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    PARTY_SIZE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "인원은 1명 이상 9명 이하로 입력해 주세요."),
    TOO_MANY_STOPS(HttpStatus.BAD_REQUEST, "방문지는 최대 3곳(경유지 2 + 목적지 1)까지 넣을 수 있습니다."),
    ARRIVAL_INVALID(HttpStatus.BAD_REQUEST, "하차 확인 값이 올바르지 않습니다."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더가 필요합니다."),

    // 401 / 403
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    POS_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "매장 PIN 이 올바르지 않습니다."),
    CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "외부 전송 동의가 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    DEMO_DISABLED(HttpStatus.FORBIDDEN, "데모 기능이 꺼져 있습니다."),

    POS_LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "PIN 을 여러 번 틀려 잠시 잠겼습니다. 잠시 후 다시 시도해 주세요."),

    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다."),
    JOURNEY_NOT_FOUND(HttpStatus.NOT_FOUND, "여정을 찾을 수 없습니다."),
    WAITING_NOT_FOUND(HttpStatus.NOT_FOUND, "웨이팅을 찾을 수 없습니다."),

    // 405
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 메서드입니다."),

    // 409
    WAITING_ALREADY_CALLED(HttpStatus.CONFLICT, "이미 호출된 웨이팅이라 바꿀 수 없습니다."),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "현재 상태에서는 할 수 없는 요청입니다."),
    DEFER_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "순번 미루기 횟수를 모두 썼습니다."),
    DUPLICATE_ACTIVE_WAITING(HttpStatus.CONFLICT, "이 매장에 이미 진행 중인 웨이팅이 있습니다."),
    ACTIVE_JOURNEY_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 여정이 있습니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "같은 Idempotency-Key 로 다른 요청이 들어왔습니다."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "다른 요청과 동시에 바뀌었습니다. 다시 시도해 주세요."),

    // 5xx
    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "아직 구현되지 않은 API 입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
