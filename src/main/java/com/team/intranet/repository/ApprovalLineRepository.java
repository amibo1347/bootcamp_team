package com.team.intranet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.ApprovalLine;

public interface ApprovalLineRepository extends JpaRepository<ApprovalLine, Long> {

    List<ApprovalLine> findByApproval_ApprovalIdOrderByLevelAsc(Long approvalId);
    Optional<ApprovalLine> findByApproval_ApprovalIdAndLevel(Long approvalId, Integer level);
}
