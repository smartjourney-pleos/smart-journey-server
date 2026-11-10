package com.smartjourney.server.common.realtime;

public enum RealtimeEventType {
    // /topic/stores/{id}/queue
    QUEUE_UPDATED,

    // /topic/waitings/{id}
    WAITING_UPDATED,
    CALLED,

    // /topic/pos/{storeId}
    WAITING_CREATED,
    PARTY_SIZE_CHANGED,
    ETA_UPDATED,
    ARRIVAL_REPORTED,
    WAITING_CANCELLED,
    WAITING_CLOSED
}
