package com.team.intranet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.GenericRequest;

public interface GenericRequestRepository extends JpaRepository<GenericRequest, Long> {

    Optional<GenericRequest> findByApproval_ApprovalId(Long approvalId);
    boolean existsByApproval_ApprovalId(Long approvalId);
}
