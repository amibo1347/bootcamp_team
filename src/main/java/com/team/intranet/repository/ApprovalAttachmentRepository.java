package com.team.intranet.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.ApprovalAttachment;

public interface ApprovalAttachmentRepository extends JpaRepository<ApprovalAttachment, Long> {
    List<ApprovalAttachment> findByApproval_ApprovalId(Long approvalId);
}
