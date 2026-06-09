package com.team.intranet.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.dto.AlertDto;
import com.team.intranet.service.AlertService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertApiController {

    private final AlertService alertService;

    // 내 알림 전체 (최신순)
    @GetMapping("")
    public ResponseEntity<List<AlertDto>> getMyAlerts(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(alertService.getMyAlerts(ms));
    }

    // 안 읽은 알림만
    @GetMapping("/unread")
    public ResponseEntity<List<AlertDto>> getMyUnreadAlerts(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(alertService.getMyUnreadAlerts(ms));
    }

    // 안 읽은 알림 개수 (헤더 종 아이콘 뱃지용)
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getMyUnreadCount(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("count", alertService.getMyUnreadCount(ms)));
    }

    // 단일 읽음 처리
    @PostMapping("/{alertId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long alertId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        alertService.markAsRead(ms, alertId);
        return ResponseEntity.noContent().build();
    }

    // 전체 읽음 처리
    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        alertService.markAllAsRead(ms);
        return ResponseEntity.noContent().build();
    }

    // 단일 삭제
    @PostMapping("/{alertId}")
    public ResponseEntity<Void> deleteAlert(
            @PathVariable Long alertId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        alertService.deleteAlert(ms, alertId);
        return ResponseEntity.noContent().build();
    }

    // 전체 삭제
    @PostMapping("")
    public ResponseEntity<Void> deleteAllMyAlerts(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        alertService.deleteAllMyAlerts(ms);
        return ResponseEntity.noContent().build();
    }
}
