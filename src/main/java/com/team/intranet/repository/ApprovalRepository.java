package com.team.intranet.repository;

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
     * 본인이 이미 처리한(승인/반려/보류) 결재선(level) 이 1건 이상 있는 결재 목록.
     * 다단계 결재에서 1차로 본인이 승인했고 다음 단계로 넘어간 IN_PROGRESS 도 본인 완료함에 포함하기 위함.
     */
    @Query("SELECT DISTINCT a FROM Approval a JOIN ApprovalLine l ON l.approval = a " +
           "WHERE l.approver.memberId = :memberId AND l.status <> :pending " +
           "ORDER BY a.draftedAt DESC")
    List<Approval> findProcessedByMember(
        @Param("memberId") Long memberId,
        @Param("pending") ApprovalStatus pending
    );

    /** 회사별 결재 수 (기안자 기준) — MASTER 사용량 대시보드. */
    @Query("SELECT COUNT(a) FROM Approval a WHERE a.drafter.company.companyId = :companyId")
    long countByCompanyId(@Param("companyId") Long companyId);
}
