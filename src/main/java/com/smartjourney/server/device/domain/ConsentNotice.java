package com.smartjourney.server.device.domain;

import java.util.List;

/**
 * 외부 전송 고지 내용 (V-01 동의 화면). Pleos 앱 리뷰 가이드의 필수 고지 항목을 모두 담는다:
 * 수집 항목 · 처리 목적 · 보유 기간 · 받는 곳 · 동의를 거부할 권리.
 */
public record ConsentNotice(
        String version,
        List<String> items,
        List<String> notCollected,
        List<String> recipients,
        String purpose,
        int retentionDays,
        String refusalRight,
        String privacyPolicyPath
) {

    public static ConsentNotice current(String version, int retentionDays) {
        return new ConsentNotice(
                version,
                List.of("탑승 인원 수", "하차 인원 수", "도착 예상 시간(분)", "도착 여부", "웨이팅 정보(매장·번호·상태)"),
                List.of("위치 좌표", "이동 경로", "차량 고유번호(VIN)", "이름·전화번호"),
                List.of("스마트 저니 서버", "웨이팅을 건 매장"),
                "원격 웨이팅 접수와 매장의 입장 준비(인원·도착 확인)",
                retentionDays,
                "동의하지 않아도 앱은 쓸 수 있지만 원격 웨이팅은 쓸 수 없습니다. 설정에서 언제든 철회할 수 있습니다.",
                "/privacy"
        );
    }
}
