package com.smartjourney.server.waiting.domain;

/**
 * 승인 방식 (정책 2: 화면 승인과 음성 승인은 같은 규칙). KPI "단일 승인 처리 비율" 산출용.
 */
public enum ApprovalMethod {
    TOUCH,
    VOICE
}
