package com.team.intranet.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.dto.AttendanceDto;
import com.team.intranet.dto.AttendancePolicyDto;
import com.team.intranet.entity.Attendance;
import com.team.intranet.entity.AttendancePolicy;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.SystemLogAction;
import com.team.intranet.enums.attendance.AttendanceStatus;
import com.team.intranet.enums.member.SubAdminPermission;
import com.team.intranet.event.SystemLogEvent;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.AttendancePolicyRepository;
import com.team.intranet.repository.AttendanceRepository;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * 근태 도메인 서비스 (MVP).
 *  - 출근/퇴근 기록
 *  - 회사 정책 조회/편집
 *  - 본인 월별 / 회사 전체 월별 조회
 *
 * ※ MVP 범위 외: 정정 요청, 휴가 결재 자동 반영, 결근/휴일 cron 마킹, 통계.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final AttendancePolicyRepository policyRepository;
    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ─── 정책 ────────────────────────────────────────────────────────

    /** 회사 정책 조회. 없으면 디폴트(09-18) 자동 생성. */
    @Transactional
    public AttendancePolicy getOrCreatePolicy(Long companyId) {
        return policyRepository.findByCompany_CompanyId(companyId)
            .orElseGet(() -> {
                Company company = companyRepository.findById(companyId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));
                return policyRepository.save(AttendancePolicy.defaultFor(company));
            });
    }

    @Transactional(readOnly = true)
    public AttendancePolicyDto getPolicyDto(Long companyId) {
        return AttendancePolicyDto.fromEntity(getOrCreatePolicy(companyId));
    }

    @Transactional
    public AttendancePolicyDto updatePolicy(MemberSession ms, AttendancePolicyDto dto) {
        if (!ms.hasPermission(SubAdminPermission.ATTENDANCE_MANAGEMENT)) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        AttendancePolicy policy = getOrCreatePolicy(ms.getCompanyId());

        // 변경 전 값 보관 (in-place update 직전).
        LocalTime oldStart = policy.getWorkStart();
        LocalTime oldEnd = policy.getWorkEnd();
        int oldBreak = policy.getBreakMinutes();
        int oldLate = policy.getLateThresholdMin();

        policy.update(dto.parseStart(), dto.parseEnd(), dto.getBreakMinutes(), dto.getLateThresholdMin());

        String detail = String.format(
            "근태 정책 수정: 출근 %s→%s, 퇴근 %s→%s, 휴게 %d→%d분, 지각 %d→%d분",
            oldStart, policy.getWorkStart(),
            oldEnd, policy.getWorkEnd(),
            oldBreak, policy.getBreakMinutes(),
            oldLate, policy.getLateThresholdMin());
        publishLog(ms, SystemLogAction.UPDATE, "ATTENDANCE_POLICY",
            policy.getCompany() != null ? policy.getCompany().getCompanyId() : null,
            "근태 정책", detail);

        return AttendancePolicyDto.fromEntity(policy);
    }

    /** 시스템 로그 이벤트 발행 — AFTER_COMMIT 리스너가 적재. */
    private void publishLog(MemberSession ms, SystemLogAction action, String targetType, Long targetId,
                            String targetLabel, String detail) {
        if (ms == null || ms.getCompanyId() == null) return;
        eventPublisher.publishEvent(new SystemLogEvent(
            ms.getCompanyId(), ms.getMemberId(), ms.getName(),
            action, targetType, targetId, targetLabel, detail));
    }

    // ─── 본인 출퇴근 ────────────────────────────────────────────────────

    @Transactional
    public AttendanceDto clockIn(MemberSession ms) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        Optional<Attendance> existing = attendanceRepository
            .findByMember_MemberIdAndWorkDate(ms.getMemberId(), today);
        if (existing.isPresent()) {
            // 이미 출근한 경우 — 멱등하게 기존 기록 반환.
            return AttendanceDto.fromEntity(existing.get());
        }

        Member member = memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        AttendancePolicy policy = getOrCreatePolicy(ms.getCompanyId());

        AttendanceStatus status = isLate(now, policy) ? AttendanceStatus.LATE : AttendanceStatus.NORMAL;
        Attendance saved = attendanceRepository.save(
            Attendance.createClockIn(member, today, now, status)
        );
        return AttendanceDto.fromEntity(saved);
    }

    @Transactional
    public AttendanceDto clockOut(MemberSession ms) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        Attendance attendance = attendanceRepository
            .findByMember_MemberIdAndWorkDate(ms.getMemberId(), today)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATUS));
        if (attendance.getClockOut() != null) {
            // 이미 퇴근 처리 — 멱등.
            return AttendanceDto.fromEntity(attendance);
        }

        AttendancePolicy policy = getOrCreatePolicy(ms.getCompanyId());
        int totalMin = (int) Duration.between(attendance.getClockIn(), now).toMinutes();
        int actualWork = Math.max(0, totalMin - policy.getBreakMinutes());
        int overtime = computeOvertime(now, today, policy);

        // 상태 결정: 기존 LATE 유지, 그 외에는 조퇴/정상 판정.
        AttendanceStatus status = attendance.getStatus() == AttendanceStatus.LATE
            ? AttendanceStatus.LATE
            : (isEarlyLeave(now, today, policy) ? AttendanceStatus.EARLY_LEAVE : AttendanceStatus.NORMAL);

        attendance.clockOut(now, actualWork, overtime, status);
        return AttendanceDto.fromEntity(attendance);
    }

    /**
     * 퇴근 취소.
     * ※ 본인 오늘 기록의 clock_out 만 null 로 되돌린다.
     *   출근 시 LATE 였으면 LATE 로, 아니면 NORMAL 로 복원.
     */
    @Transactional
    public AttendanceDto cancelClockOut(MemberSession ms) {
        LocalDate today = LocalDate.now();
        Attendance attendance = attendanceRepository
            .findByMember_MemberIdAndWorkDate(ms.getMemberId(), today)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATUS));
        if (attendance.getClockOut() == null) {
            return AttendanceDto.fromEntity(attendance);
        }
        AttendancePolicy policy = getOrCreatePolicy(ms.getCompanyId());
        AttendanceStatus restored = isLate(attendance.getClockIn(), policy)
            ? AttendanceStatus.LATE
            : AttendanceStatus.NORMAL;
        attendance.cancelClockOut(restored);
        return AttendanceDto.fromEntity(attendance);
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceDto> findToday(Long memberId) {
        return attendanceRepository.findByMember_MemberIdAndWorkDate(memberId, LocalDate.now())
            .map(AttendanceDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto> findMyMonth(Long memberId, YearMonth ym) {
        return attendanceRepository.findMonthlyByMember(memberId, ym.atDay(1), ym.atEndOfMonth())
            .stream().map(AttendanceDto::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto> findCompanyMonth(MemberSession ms, YearMonth ym) {
        if (!ms.hasPermission(SubAdminPermission.ATTENDANCE_MANAGEMENT)) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        return attendanceRepository.findMonthlyByCompany(ms.getCompanyId(), ym.atDay(1), ym.atEndOfMonth())
            .stream().map(AttendanceDto::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto> findCompanyDay(MemberSession ms, LocalDate date) {
        if (!ms.hasPermission(SubAdminPermission.ATTENDANCE_MANAGEMENT)) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        return attendanceRepository.findDailyByCompany(ms.getCompanyId(), date)
            .stream().map(AttendanceDto::fromEntity).toList();
    }

    /** 임의 from~to 범위 조회 — 주/월 보기 공통 사용. */
    @Transactional(readOnly = true)
    public List<AttendanceDto> findCompanyRange(MemberSession ms, LocalDate from, LocalDate to) {
        if (!ms.hasPermission(SubAdminPermission.ATTENDANCE_MANAGEMENT)) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        return attendanceRepository.findMonthlyByCompany(ms.getCompanyId(), from, to)
            .stream().map(AttendanceDto::fromEntity).toList();
    }

    // ─── 내부 ─────────────────────────────────────────────────────────

    private boolean isLate(LocalDateTime clockIn, AttendancePolicy policy) {
        LocalDateTime threshold = clockIn.toLocalDate()
            .atTime(policy.getWorkStart())
            .plusMinutes(policy.getLateThresholdMin());
        return clockIn.isAfter(threshold);
    }

    private boolean isEarlyLeave(LocalDateTime clockOut, LocalDate workDate, AttendancePolicy policy) {
        LocalDateTime end = workDate.atTime(policy.getWorkEnd());
        return clockOut.isBefore(end);
    }

    private int computeOvertime(LocalDateTime clockOut, LocalDate workDate, AttendancePolicy policy) {
        LocalDateTime end = workDate.atTime(policy.getWorkEnd());
        if (!clockOut.isAfter(end)) return 0;
        return (int) Duration.between(end, clockOut).toMinutes();
    }
}
