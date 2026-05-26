package com.team.intranet.controller.api;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.intranet.entity.SystemMaintenance;
import com.team.intranet.service.SystemMaintenanceService;

import lombok.RequiredArgsConstructor;

/**
 * 시스템 점검 상태 — 회원/비회원 폴링용.
 *  - 헤더 배너·점검 페이지가 주기적으로 호출.
 *  - 비로그인도 접근 가능해야 한다 (점검 페이지에서도 표시 정보 갱신).
 */
@RestController
@RequestMapping("/api/system-maintenance")
@RequiredArgsConstructor
public class SystemMaintenanceApiController {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SystemMaintenanceService maintenanceService;

    /**
     * 현재 점검 모드 상태.
     *  - active: true 면 점검 중 (수동 OR 예약 적용).
     *  - 예약 정보(startsAt / endsAt) 와 reason 도 함께 반환 — 헤더 배너에서 "X시간 후 시작" 같은 안내를 그릴 수 있도록.
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> current() {
        SystemMaintenance m = maintenanceService.getCurrent();
        boolean active = m.isCurrentlyOn(LocalDateTime.now());

        Map<String, Object> body = new HashMap<>();
        body.put("active",   active);
        body.put("enabled",  m.isEnabled()); // 수동 토글 여부 (예약과 별개)
        body.put("reason",   m.getReason());
        body.put("startsAt", m.getStartsAt() != null ? m.getStartsAt().format(DT) : null);
        body.put("endsAt",   m.getEndsAt()   != null ? m.getEndsAt().format(DT)   : null);
        return ResponseEntity.ok(body);
    }
}
