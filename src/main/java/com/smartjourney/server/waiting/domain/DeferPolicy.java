package com.smartjourney.server.waiting.domain;

import java.util.List;

/**
 * 순번 미루기 (기능 4.2, P2-1). 순수 함수 — 단위 테스트 대상.
 *
 * <p>늦어지는 분만큼 뒤 팀 몇 팀 뒤로 보낸다: {@code teams = max(1, ceil(delayMin / minutesPerTeam))}.
 * 뒤에 팀이 그보다 적으면 맨 뒤로 간다. 미루기 횟수는 매장의 maxDefers(기본 2, 캐치테이블 기준) 까지.
 */
public final class DeferPolicy {

    private DeferPolicy() {
    }

    public static boolean canDefer(int deferCount, int maxDefers) {
        return deferCount < maxDefers;
    }

    public static int teamsToPushBack(int delayMin, int minutesPerTeam) {
        if (minutesPerTeam <= 0) {
            return 1;
        }
        int teams = (int) Math.ceil((double) Math.max(0, delayMin) / minutesPerTeam);
        return Math.max(1, teams);
    }

    /**
     * @param currentOrder 내 queue_order
     * @param ordersBehind 내 뒤에 있는 활성 팀들의 queue_order (오름차순)
     * @param teams        몇 팀 뒤로 갈지
     * @return 새 queue_order. 뒤에 아무도 없으면 그대로.
     */
    public static long newQueueOrder(long currentOrder, List<Long> ordersBehind, int teams) {
        if (ordersBehind.isEmpty() || teams <= 0) {
            return currentOrder;
        }
        int index = Math.min(teams, ordersBehind.size()) - 1;
        long anchor = ordersBehind.get(index);
        if (index + 1 < ordersBehind.size()) {
            long next = ordersBehind.get(index + 1);
            if (next - anchor > 1) {
                return anchor + (next - anchor) / 2;   // 두 팀 사이에 끼워 넣기
            }
        }
        return anchor + 1;
    }
}
