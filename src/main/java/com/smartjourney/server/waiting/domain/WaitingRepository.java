package com.smartjourney.server.waiting.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitingRepository extends JpaRepository<Waiting, Long> {

    boolean existsByIdAndDeviceId(Long id, Long deviceId);

    /** 대기열 (POS 보드·순번 계산) */
    List<Waiting> findByStoreIdAndStatusInOrderByQueueOrderAsc(Long storeId, Collection<WaitingStatus> statuses);

    /** 내 앞 팀 수 */
    long countByStoreIdAndStatusInAndQueueOrderLessThan(Long storeId, Collection<WaitingStatus> statuses,
                                                        long queueOrder);

    /** 같은 여정·같은 매장에 진행 중인 웨이팅이 있는지 (중복 접수 방지) */
    boolean existsByJourneyIdAndStoreIdAndStatusIn(Long journeyId, Long storeId, Collection<WaitingStatus> statuses);

    List<Waiting> findByJourneyId(Long journeyId);

    /** 오늘 번호표 최댓값. 호출 전 매장 행을 잠근다 (StoreRepository.findByIdForUpdate) */
    @Query("select coalesce(max(w.ticketNo), 0) from Waiting w "
            + "where w.storeId = :storeId and w.businessDate = :businessDate")
    int findMaxTicketNo(@Param("storeId") Long storeId, @Param("businessDate") LocalDate businessDate);

    /** 데모 패널 −1: 가장 늦게 들어온 현장 대기 팀 */
    List<Waiting> findByStoreIdAndChannelAndStatusInOrderByQueueOrderDesc(
            Long storeId, WaitingChannel channel, Collection<WaitingStatus> statuses);
}
