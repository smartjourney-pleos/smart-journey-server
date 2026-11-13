package com.smartjourney.server.waiting.platform;

/**
 * 상용 웨이팅 플랫폼 연동 어댑터 계층 (Phase 6). 제휴가 생기면 이 인터페이스를 구현한 빈 하나만 추가한다.
 *
 * <p>규칙
 * <ul>
 *   <li>우리 DB 커밋이 끝난 뒤에 호출된다 — 외부가 실패해도 차량·POS 흐름은 멈추지 않는다</li>
 *   <li>넘기는 값은 {@link WaitingLifecycleEvent} 뿐 (인원·번호표·상태). 기기 토큰·좌표는 절대 넘기지 않는다 (정책 4)</li>
 *   <li>재시도·중복 처리는 구현체 책임 (waitingId + type 으로 멱등하게)</li>
 * </ul>
 */
public interface ExternalWaitingPlatform {

    /** 로그·설정에 쓰는 이름 (예: "none", "catchtable") */
    String name();

    void publish(WaitingLifecycleEvent event);
}
