package com.smartjourney.server.common.realtime;

/**
 * STOMP 구독 토픽. (개발 명세서 6.3절)
 *
 * <pre>
 * /topic/stores/{storeId}/queue   차량(코스에 포함된 매장), POS   QUEUE_UPDATED
 * /topic/waitings/{waitingId}     차량(그 웨이팅 소유 기기만)     WAITING_UPDATED, CALLED
 * /topic/pos/{storeId}            그 매장 POS 만                 WAITING_CREATED, PARTY_SIZE_CHANGED, ETA_UPDATED,
 *                                                                ARRIVAL_REPORTED, WAITING_CANCELLED, WAITING_CLOSED
 * </pre>
 */
public final class Topics {

    public static final String STORE_QUEUE_PREFIX = "/topic/stores/";
    public static final String WAITING_PREFIX = "/topic/waitings/";
    public static final String POS_PREFIX = "/topic/pos/";

    private Topics() {
    }

    public static String storeQueue(long storeId) {
        return STORE_QUEUE_PREFIX + storeId + "/queue";
    }

    public static String waiting(long waitingId) {
        return WAITING_PREFIX + waitingId;
    }

    public static String pos(long storeId) {
        return POS_PREFIX + storeId;
    }
}
