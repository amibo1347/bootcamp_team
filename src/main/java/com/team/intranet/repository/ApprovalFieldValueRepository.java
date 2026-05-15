package com.team.intranet.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.ApprovalFieldValue;

public interface ApprovalFieldValueRepository extends JpaRepository<ApprovalFieldValue, Long> {

    List<ApprovalFieldValue> findByApproval_ApprovalIdOrderByFieldValueIdAsc(Long approvalId);
}
