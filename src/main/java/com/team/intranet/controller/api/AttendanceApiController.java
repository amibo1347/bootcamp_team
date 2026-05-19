package com.team.intranet.controller.api;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.dto.AttendanceDto;
import com.team.intranet.dto.AttendancePolicyDto;
import com.team.intranet.service.AttendanceService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * 근태 API.
 *  - 출퇴근/오늘 조회/본인 월별: 인증된 모든 회원
 *  - 회사 월별/정책 편집: ATTENDANCE_MANAGEMENT 권한 — 서비스에서 검증
 */
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceApiController {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AttendanceService attendanceService;

    @PostMapping("/clock-in")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendanceDto> clockIn(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(attendanceService.clockIn(ms));
    }

    @PostMapping("/clock-out")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendanceDto> clockOut(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(attendanceService.clockOut(ms));
    }

    @PostMapping("/clock-out-cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendanceDto> clockOutCancel(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(attendanceService.cancelClockOut(ms));
    }

    @GetMapping("/today")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendanceDto> today(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return attendanceService.findToday(ms.getMemberId())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AttendanceDto>> myMonth(
            @RequestParam(value = "month", required = false) String month,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        YearMonth ym = parseMonth(month);
        return ResponseEntity.ok(attendanceService.findMyMonth(ms.getMemberId(), ym));
    }

    @GetMapping("/company")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AttendanceDto>> companyMonth(
            @RequestParam(value = "month", required = false) String month,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        YearMonth ym = parseMonth(month);
        return ResponseEntity.ok(attendanceService.findCompanyMonth(ms, ym));
    }

    @GetMapping("/company/day")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AttendanceDto>> companyDay(
            @RequestParam(value = "date", required = false) String date,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        LocalDate day;
        try {
            day = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        } catch (Exception e) {
            day = LocalDate.now();
        }
        return ResponseEntity.ok(attendanceService.findCompanyDay(ms, day));
    }

    /** 임의 from~to 범위 조회 (주/월 보기용). */
    @GetMapping("/company/range")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AttendanceDto>> companyRange(
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        LocalDate fromDate, toDate;
        try {
            fromDate = LocalDate.parse(from);
            toDate = LocalDate.parse(to);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(attendanceService.findCompanyRange(ms, fromDate, toDate));
    }

    @GetMapping("/policy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendancePolicyDto> policy(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(attendanceService.getPolicyDto(ms.getCompanyId()));
    }

    @PostMapping("/policy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendancePolicyDto> updatePolicy(
            @RequestBody AttendancePolicyDto dto,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(attendanceService.updatePolicy(ms, dto));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(month, YM);
        } catch (Exception e) {
            return YearMonth.now();
        }
    }
}
