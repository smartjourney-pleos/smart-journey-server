package com.smartjourney.server.privacy.api;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 개인정보 처리방침 공개 페이지. Pleos App Market 등록 시 방침 URL 이 필요하다 (앱 리뷰 가이드 — 등록정보).
 * 내용은 static/privacy.html (팀 검토 후 확정).
 */
@Hidden
@Controller
public class PrivacyController {

    @GetMapping("/privacy")
    public String privacy() {
        return "forward:/privacy.html";
    }
}
