package com.team.intranet.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.dto.leave.LeaveBalanceSummary;
import com.team.intranet.dto.leave.LeaveRosterRow;
import com.team.intranet.dto.leave.LeaveRosterView;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.LeaveBalance;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.VacationRequest;
import com.team.intranet.enums.ApprovalStatus;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.SystemLogAction;
import com.team.intranet.enums.VacationType;
import com.team.intranet.enums.member.Status;
import com.team.intranet.event.SystemLogEvent;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.LeaveBalanceRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.repository.VacationRepository;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * 연차 부여 + 잔여 계산 서비스 (B안 + 2계층).
 *  - 계층1(회사 기본정책): Company.defaultAnnualLeaveDays.
 *  - 계층2(개인 부여): LeaveBalance 연도별 원장.
 *  - 잔여 = 부여 - 사용. "사용" 은 승인된 ANNUAL_PAID_LEAVE 합산(저장 안 함).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveBalanceService {

    // 연차에서 차감되는 휴가 종류 — MVP 는 "연차유급휴가" 만. (병가/경조사 등은 별도 제도.)
    private static final VacationType LEAVE_TYPE = VacationType.ANNUAL_PAID_LEAVE;
    // 신청 중(아직 미차감, 참고 표시)으로 볼 결재 상태.
    private static final List<ApprovalStatus> PENDING_STATUSES =
            List.of(ApprovalStatus.PENDING, ApprovalStatus.IN_PROGRESS);

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final MemberRepository memberRepository;
    private final VacationRepository vacationRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 회사 + 연도 명부 조회 (상단 기본값 + 하단 직원별 행). ms 기준으로 행별 수정 가능 여부 판정. */
    public LeaveRosterView getRoster(MemberSession ms, int year) {
        Long companyId = ms.getCompanyId();
        Company company = findCompany(companyId);
        double companyDefault = company.getDefaultAnnualLeaveDaysOrDefault();

        // 재직(JOIN) 회원만 대상.
        List<Member> members = memberRepository.findByStatusAndCompanyCompanyId(Status.JOIN, companyId);

        // 개인 원장 맵 (memberId -> LeaveBalance).
        Map<Long, LeaveBalance> balanceByMember = new HashMap<>();
        for (LeaveBalance b : leaveBalanceRepository.findByMember_Company_CompanyIdAndYear(companyId, year)) {
            balanceByMember.put(b.getMember().getMemberId(), b);
        }

        // 사용/신청중 합산 (회계연도 1/1~12/31, startDate 기준).
        Map<Long, double[]> usage = aggregateUsage(companyId, year); // [used, pending]

        List<LeaveRosterRow> rows = members.stream()
            .map(m -> toRow(ms, m, balanceByMember.get(m.getMemberId()), companyDefault, usage))
            // 기본 정렬: 직급 레벨 내림차순(대표가 맨 위), 같은 레벨이면 이름 오름차순. 레벨 미상은 뒤로.
            .sorted(Comparator
                .comparing(LeaveRosterRow::getPositionLevel,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(r -> r.getName() == null ? "" : r.getName()))
            .toList();

        return new LeaveRosterView(year, companyDefault, rows);
    }

    private LeaveRosterRow toRow(MemberSession ms, Member m, LeaveBalance balance, double companyDefault,
                                 Map<Long, double[]> usage) {
        boolean hasRow = balance != null;
        double granted = hasRow ? balance.getGrantedDays() : companyDefault;
        String note = hasRow ? balance.getNote() : null;

        double[] u = usage.getOrDefault(m.getMemberId(), new double[] {0.0, 0.0});
        double used = u[0];
        double pending = u[1];
        double remaining = granted - used;

        Integer level = m.getPosition() != null ? m.getPosition().getPositionLevel() : null;

        return new LeaveRosterRow(
            m.getMemberId(),
            m.getName(),
            m.getDept() != null ? m.getDept().getDeptName() : null,
            m.getPosition() != null ? m.getPosition().getPositionName() : null,
            granted, used, pending, remaining, note, hasRow,
            m.getEffectiveHireDate(), level, canEditLeave(ms, m)
        );
    }

    /**
     * 현재 로그인 회원(ms)이 대상 직원(target)의 연차를 수정할 수 있는지.
     *  - ADMIN/MASTER 는 전원 가능.
     *  - 그 외(SUB_ADMIN): 대상의 '권한(Role)'이 더 높으면 불가. 같은 권한이면 '직급 레벨'로 판정
     *    (대상 레벨 < 내 레벨 일 때만 가능 = 나보다 낮은 직급만). 레벨 미상이면 보수적으로 불가.
     */
    private boolean canEditLeave(MemberSession ms, Member target) {
        if (ms.isAdminOrMaster()) return true;
        int editorRank = roleRank(ms.getRole());
        int targetRank = roleRank(target.getRole());
        if (targetRank > editorRank) return false;
        if (targetRank < editorRank) return true;
        // 같은 권한 → 직급 레벨 비교
        Integer editorLevel = ms.getPositionLevel();
        Integer targetLevel = target.getPosition() != null ? target.getPosition().getPositionLevel() : null;
        if (editorLevel == null || targetLevel == null) return false;
        return targetLevel < editorLevel;
    }

    /** 권한 서열 (높을수록 상위). enum ordinal 이 의미순이 아니라 명시적으로 부여. */
    private static int roleRank(com.team.intranet.enums.member.Role r) {
        if (r == null) return 0;
        return switch (r) {
            case MASTER -> 4;
            case ADMIN -> 3;
            case SUB_ADMIN -> 2;
            case USER -> 1;
        };
    }

    /**
     * 직원 본인 1명의 연차 요약 — 대시보드 배지 / AI 컨텍스트 공용.
     *  - 개인 원장이 없으면 회사 기본 부여일수를 부여로 간주(명부와 동일 규칙).
     */
    public LeaveBalanceSummary getMySummary(Long companyId, Long memberId, int year) {
        Company company = findCompany(companyId);
        double companyDefault = company.getDefaultAnnualLeaveDaysOrDefault();

        LeaveBalance balance = leaveBalanceRepository
                .findByMember_MemberIdAndYear(memberId, year).orElse(null);
        double granted = balance != null ? balance.getGrantedDays() : companyDefault;

        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        List<ApprovalStatus> statuses = new java.util.ArrayList<>(PENDING_STATUSES);
        statuses.add(ApprovalStatus.APPROVED);

        double used = 0.0;
        double pending = 0.0;
        for (VacationRequest v : vacationRepository.findForMemberLeaveLedger(
                memberId, LEAVE_TYPE, statuses, from, to)) {
            double days = v.getTotalDays() != null ? v.getTotalDays() : 0.0;
            if (v.getApproval().getStatus() == ApprovalStatus.APPROVED) {
                used += days;
            } else {
                pending += days;
            }
        }
        double remaining = granted - used;
        return new LeaveBalanceSummary(year, granted, used, pending, remaining);
    }

    /** memberId -> [used(승인), pending(대기/진행)] 합산. */
    private Map<Long, double[]> aggregateUsage(Long companyId, int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        List<ApprovalStatus> statuses = new java.util.ArrayList<>(PENDING_STATUSES);
        statuses.add(ApprovalStatus.APPROVED);

        List<VacationRequest> list = vacationRepository.findForLeaveLedger(
                companyId, LEAVE_TYPE, statuses, from, to);

        Map<Long, double[]> map = new HashMap<>();
        for (VacationRequest v : list) {
            Long memberId = v.getApproval().getDrafter().getMemberId();
            double days = v.getTotalDays() != null ? v.getTotalDays() : 0.0;
            double[] acc = map.computeIfAbsent(memberId, k -> new double[] {0.0, 0.0});
            if (v.getApproval().getStatus() == ApprovalStatus.APPROVED) {
                acc[0] += days; // used
            } else {
                acc[1] += days; // pending
            }
        }
        return map;
    }

    // ===== 계층1: 회사 기본정책 =====

    @Transactional
    public void updateCompanyDefault(MemberSession ms, double days) {
        if (days < 0) {
            throw new BusinessException(ErrorCode.INVALID_LEAVE_DAYS);
        }
        Company company = findCompany(ms.getCompanyId());
        company.updateDefaultAnnualLeaveDays(days);
        publishLog(ms, SystemLogAction.UPDATE, "LEAVE_POLICY", company.getCompanyId(),
                company.getCompanyName(), "회사 기본 연차 부여일수 변경: " + days + "일");
    }

    // ===== 계층2: 개인별 부여 =====

    @Transactional
    public void upsertMemberBalance(MemberSession ms, Long memberId, int year,
                                    double granted, String note) {
        Member member = findMemberAndValidateOwner(ms, memberId);
        if (!canEditLeave(ms, member)) {
            throw new BusinessException(ErrorCode.SUPERIOR_MEMBER_PROTECTED);
        }
        upsertGranted(member, year, granted, note);
        publishLog(ms, SystemLogAction.UPDATE, "LEAVE_BALANCE", member.getMemberId(),
                member.getName(), year + "년 연차 부여 설정 — " + granted + "일");
    }

    /**
     * 여러 직원에게 같은 부여 연차를 일괄 적용. 메모는 건드리지 않는다(기존 유지).
     * @return 적용된 인원 수.
     */
    @Transactional
    public int bulkSetGranted(MemberSession ms, List<Long> memberIds, int year, double granted) {
        if (memberIds == null || memberIds.isEmpty()) return 0;
        if (granted < 0) {
            throw new BusinessException(ErrorCode.INVALID_LEAVE_DAYS);
        }
        int applied = 0;
        for (Long memberId : memberIds) {
            Member member = findMemberAndValidateOwner(ms, memberId); // 회사 격리 검증
            if (!canEditLeave(ms, member)) continue; // 상위/동급 권한은 일괄에서 제외
            LeaveBalance existing = leaveBalanceRepository
                    .findByMember_MemberIdAndYear(memberId, year).orElse(null);
            String note = existing != null ? existing.getNote() : null;
            upsertGranted(member, year, granted, note);
            applied++;
        }
        publishLog(ms, SystemLogAction.UPDATE, "LEAVE_POLICY", ms.getCompanyId(),
                ms.getCompanyName(), year + "년 연차 부여 일괄 적용 — " + applied + "명 / " + granted + "일");
        return applied;
    }

    /** 부여 연차 upsert 공통 로직(검증 포함). 호출 측이 member 회사 검증을 끝낸 상태로 호출. */
    private void upsertGranted(Member member, int year, double granted, String note) {
        if (granted < 0) {
            throw new BusinessException(ErrorCode.INVALID_LEAVE_DAYS);
        }
        String cleanNote = (note == null || note.isBlank()) ? null : note.trim();
        LeaveBalance balance = leaveBalanceRepository
                .findByMember_MemberIdAndYear(member.getMemberId(), year)
                .orElse(null);
        if (balance == null) {
            leaveBalanceRepository.save(LeaveBalance.create(member, year, granted, cleanNote));
        } else {
            balance.update(granted, cleanNote);
        }
    }

    // ===== 헬퍼 =====

    private Company findCompany(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.COMPANY_NOT_FOUND);
        }
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));
    }

    /** 회원 조회 + 회사 일치 검증(멀티테넌시). */
    private Member findMemberAndValidateOwner(MemberSession ms, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.getCompany() == null
                || !member.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return member;
    }

    private void publishLog(MemberSession ms, SystemLogAction action, String targetType, Long targetId,
                            String targetLabel, String detail) {
        if (ms == null || ms.getCompanyId() == null) return;
        eventPublisher.publishEvent(new SystemLogEvent(
                ms.getCompanyId(), ms.getMemberId(), ms.getName(),
                action, targetType, targetId, targetLabel, detail));
    }
}
