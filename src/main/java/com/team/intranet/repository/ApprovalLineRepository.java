package com.team.intranet.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.ApprovalLine;

public interface ApprovalLineRepository extends JpaRepository<ApprovalLine, Long> {

    List<ApprovalLine> findByApproval_ApprovalIdOrderByLevelAsc(Long approvalId);
    Optional<ApprovalLine> findByApproval_ApprovalIdAndLevel(Long approvalId, Integer level);

    // 결재 목록 단위로 결재선 일괄 조회 (단계 오름차순).
    List<ApprovalLine> findByApproval_ApprovalIdInOrderByApproval_ApprovalIdAscLevelAsc(
        Collection<Long> approvalIds
    );
}
