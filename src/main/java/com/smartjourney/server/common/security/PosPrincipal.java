package com.smartjourney.server.common.security;

/**
 * 인증된 매장 POS. JWT subject = storeId.
 */
public record PosPrincipal(long storeId) {

    public static final String ROLE = "POS";
}
