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
        // 휴직 기간 중에는 출근 시도 차단 — 인입 즉시 거부.
        if (isOnLeaveOnDate(member, today)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
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

        // 휴직 회원은 그날 attendance row 자체가 없겠지만, 방어적 차단.
        Member self = memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (isOnLeaveOnDate(self, today)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

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
        Optional<Attendance> existing = attendanceRepository
            .findByMember_MemberIdAndWorkDate(memberId, LocalDate.now());
        if (existing.isPresent()) return existing.map(AttendanceDto::fromEntity);
        // 휴직 기간이라면 오늘자 가상 ON_LEAVE row 제공 (배지 표시용).
        Member m = memberRepository.findById(memberId).orElse(null);
        if (m != null && isOnLeaveOnDate(m, LocalDate.now())) {
            return Optional.of(AttendanceDto.onLeavePlaceholder(m, LocalDate.now()));
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto> findMyMonth(Long memberId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<AttendanceDto> real = attendanceRepository.findMonthlyByMember(memberId, from, to)
            .stream().map(AttendanceDto::fromEntity).toList();
        Member m = memberRepository.findById(memberId).orElse(null);
        return mergeWithLeaveDays(real, m == null ? List.of(m) : List.of(m), from, to);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto> findCompanyMonth(MemberSession ms, YearMonth ym) {
        if (!ms.hasPermission(SubAdminPermission.ATTENDANCE_MANAGEMENT)) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<AttendanceDto> real = attendanceRepository.findMonthlyByCompany(ms.getCompanyId(), from, to)
            .stream().map(AttendanceDto::fromEntity).toList();
        List<Member> companyMembers = memberRepository.findByCompanyCompanyId(ms.getCompanyId());
        return mergeWithLeaveDays(real, companyMembers, from, to);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto> findCompanyDay(MemberSession ms, LocalDate date) {
        if (!ms.hasPermission(SubAdminPermission.ATTENDANCE_MANAGEMENT)) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        List<AttendanceDto> real = attendanceRepository.findDailyByCompany(ms.getCompanyId(), date)
            .stream().map(AttendanceDto::fromEntity).toList();
        List<Member> companyMembers = memberRepository.findByCompanyCompanyId(ms.getCompanyId());
        return mergeWithLeaveDays(real, companyMembers, date, date);
    }

    /** 임의 from~to 범위 조회 — 주/월 보기 공통 사용. */
    @Transactional(readOnly = true)
    public List<AttendanceDto> findCompanyRange(MemberSession ms, LocalDate from, LocalDate to) {
        if (!ms.hasPermission(SubAdminPermission.ATTENDANCE_MANAGEMENT)) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        List<AttendanceDto> real = attendanceRepository.findMonthlyByCompany(ms.getCompanyId(), from, to)
            .stream().map(AttendanceDto::fromEntity).toList();
        List<Member> companyMembers = memberRepository.findByCompanyCompanyId(ms.getCompanyId());
        return mergeWithLeaveDays(real, companyMembers, from, to);
    }

    /**
     * 실제 attendance row + 휴직 기간 가상 row 합성.
     *  - 같은 (memberId, date) 의 실제 row 가 있으면 가상 row 는 생략 (출근/퇴근 우선).
     *  - 휴직 기간: leaveStartDate ≤ date < leaveExpectedReturnDate.
     *  - 결과 정렬은 호출자가 신경 X — 프론트에서 정렬한다.
     */
    private List<AttendanceDto> mergeWithLeaveDays(List<AttendanceDto> real,
                                                   List<Member> members,
                                                   LocalDate from, LocalDate to) {
        if (members == null || members.isEmpty()) return real;
        // (memberId, workDate) 가 실제 row 에 이미 있는지 빠르게 조회.
        java.util.Set<String> realKeys = new java.util.HashSet<>();
        for (AttendanceDto d : real) {
            if (d.getMemberId() != null && d.getWorkDate() != null) {
                realKeys.add(d.getMemberId() + "@" + d.getWorkDate());
            }
        }
        List<AttendanceDto> merged = new java.util.ArrayList<>(real);
        for (Member m : members) {
            if (m == null || m.getLeaveStartDate() == null || m.getLeaveExpectedReturnDate() == null) {
                continue;
            }
            LocalDate s = m.getLeaveStartDate().isBefore(from) ? from : m.getLeaveStartDate();
            // 복귀 예정일은 복귀 시작일이므로 그 전날까지가 휴직.
            LocalDate e = m.getLeaveExpectedReturnDate().minusDays(1);
            if (e.isAfter(to)) e = to;
            for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
                String key = m.getMemberId() + "@" + d;
                if (realKeys.contains(key)) continue;
                merged.add(AttendanceDto.onLeavePlaceholder(m, d));
            }
        }
        return merged;
    }

    /** 회원이 주어진 날짜에 휴직 중인지 여부 — leaveStart ≤ date < leaveExpectedReturn. */
    private boolean isOnLeaveOnDate(Member m, LocalDate date) {
        if (m == null) return false;
        if (m.getStatus() != com.team.intranet.enums.member.Status.ON_LEAVE) return false;
        LocalDate start = m.getLeaveStartDate();
        LocalDate end = m.getLeaveExpectedReturnDate();
        if (start == null || end == null) return false;
        return !date.isBefore(start) && date.isBefore(end);
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
