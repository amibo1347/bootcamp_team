package com.team.intranet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.ExpenseRequest;

public interface ExpenseRequestRepository extends JpaRepository<ExpenseRequest, Long> {

    Optional<ExpenseRequest> findByApproval_ApprovalId(Long approvalId);
    boolean existsByApproval_ApprovalId(Long approvalId);
}
