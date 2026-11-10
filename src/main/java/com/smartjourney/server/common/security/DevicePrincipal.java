package com.smartjourney.server.common.security;

/**
 * 인증된 차량(기기). 컨트롤러에서 {@code @AuthenticationPrincipal DevicePrincipal device} 로 받는다.
 */
public record DevicePrincipal(long deviceId) {

    public static final String ROLE = "DEVICE";
}
