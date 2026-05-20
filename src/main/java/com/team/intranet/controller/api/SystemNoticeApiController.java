package com.team.intranet.controller.api;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.intranet.entity.SystemNotice;
import com.team.intranet.service.SystemNoticeService;

import lombok.RequiredArgsConstructor;

/**
 * 시스템 공지 폴링 API.
 *  - 회원 화면의 system-notice.js 가 주기적으로 호출해 배너를 실시간 갱신한다.
 *  - 공지 시작 시각 도달 시 노출, 종료/삭제 시 제거 — 새로고침 불필요.
 */
@RestController
@RequestMapping("/api/system-notice")
@RequiredArgsConstructor
public class SystemNoticeApiController {

    private final SystemNoticeService systemNoticeService;

    /** 현재 노출 대상 공지. 없으면 {active:false}. */
    @GetMapping("/current")
    public Map<String, Object> current() {
        SystemNotice notice = systemNoticeService.findActiveNotice();
        Map<String, Object> result = new HashMap<>();
        if (notice == null) {
            result.put("active", false);
        } else {
            result.put("active", true);
            result.put("content", notice.getContent());
            result.put("type", notice.getNoticeType().name());
        }
        return result;
    }
}
