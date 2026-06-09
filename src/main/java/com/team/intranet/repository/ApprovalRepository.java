package com.team.intranet.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import com.team.intranet.entity.Approval;
import com.team.intranet.enums.ApprovalStatus;
public interface ApprovalRepository extends JpaRepository<Approval, Long>{

    List<Approval> findByDrafter_MemberIdOrderByDraftedAtDesc(Long drafterId); // 내가 신청한 결재 목록
    List<Approval> findByApprover_MemberIdOrderByDraftedAtDesc(Long approverId); // 내가 결재할 목록
    List<Approval> findByApprover_MemberIdAndStatusOrderByDraftedAtDesc(Long approverId, ApprovalStatus status);
    // 결재함을 대기/승인/반려로 구분
    List<Approval> findByDrafter_MemberIdAndStatusOrderByDraftedAtDesc(Long drafterId, ApprovalStatus status);
    // 신청함 탭별
    long countByApprover_MemberIdAndStatus(Long approverId, ApprovalStatus status);
    // 헤더 알림 뱃지?

    /**
     * 내 결재함 페이징 버전 — schema_snapshot(CLOB) 컬럼이 EAGER 라 모든 row 전송 시 응답이 느려지는 문제 해결.
     * Pageable 로 DB 레벨 페이징 → 한 페이지 분량만 fetch.
     */
    Page<Approval> findByDrafter_MemberIdOrderByDraftedAtDesc(Long drafterId, Pageable pageable);
    Page<Approval> findByDrafter_MemberIdAndStatusOrderByDraftedAtDesc(
        Long drafterId, ApprovalStatus status, Pageable pageable);

    /**
     * 본인이 이미 처리한(승인/반려/보류) 결재선(level) 이 1건 이상 있는 결재 목록.
     * 다단계 결재에서 1차로 본인이 승인했고 다음 단계로 넘어간 IN_PROGRESS 도 본인 완료함에 포함하기 위함.
     *
     * ※ EXISTS 서브쿼리 사용 이유:
     *   - JOIN + DISTINCT 조합은 Approval.schema_snapshot(CLOB) 때문에 Oracle 에서 ORA-00932 발생
     *     (Oracle 은 SELECT DISTINCT 절에 LOB 컬럼을 허용하지 않음)
     *   - EXISTS 는 select 절에 LOB 컬럼이 들어가지 않고 중복 row 도 발생하지 않으므로 의미 동등.
     */
    @Query("SELECT a FROM Approval a "
         + "WHERE EXISTS (SELECT 1 FROM ApprovalLine l "
         + "              WHERE l.approval = a "
         + "              AND l.approver.memberId = :memberId "
         + "              AND l.status <> :pending) "
         + "ORDER BY a.draftedAt DESC")
    List<Approval> findProcessedByMember(
        @Param("memberId") Long memberId,
        @Param("pending") ApprovalStatus pending
    );

    /** 회사별 결재 수 (기안자 기준) — MASTER 사용량 대시보드. */
    @Query("SELECT COUNT(a) FROM Approval a WHERE a.drafter.company.companyId = :companyId")
    long countByCompanyId(@Param("companyId") Long companyId);

    /** 회사별 결재 수 일괄 (기안자 기준) — N+1 회피. [companyId, count]. */
    @Query("SELECT a.drafter.company.companyId, COUNT(a) FROM Approval a GROUP BY a.drafter.company.companyId")
    List<Object[]> countApprovalsPerCompany();

    /** 전체 결재 수 — KPI. */
    @Query("SELECT COUNT(a) FROM Approval a")
    long countAllApprovals();
}
