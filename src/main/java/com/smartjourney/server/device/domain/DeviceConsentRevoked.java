package com.smartjourney.server.device.domain;

/**
 * 기기가 외부 전송 동의를 철회했다 (같은 트랜잭션 안에서 동기 처리).
 */
public record DeviceConsentRevoked(long deviceId) {
}
