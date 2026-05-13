package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
