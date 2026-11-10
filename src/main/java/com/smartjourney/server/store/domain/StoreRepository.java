package com.smartjourney.server.store.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

    /**
     * 번호표 발급·순번 조정처럼 한 매장 대기열을 바꾸는 작업은 매장 행을 잠그고 한다.
     * (동시에 두 팀이 접수돼도 번호·순번이 꼬이지 않게)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Store s where s.id = :id")
    Optional<Store> findByIdForUpdate(@Param("id") Long id);
}
