package com.team.intranet.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.ApprovalAttachment;

public interface ApprovalAttachmentRepository extends JpaRepository<ApprovalAttachment, Long> {
    List<ApprovalAttachment> findByApproval_ApprovalId(Long approvalId);

    /** 회사별 결재 첨부 누적 바이트 합. */
    @Query("SELECT COALESCE(SUM(a.fileSize), 0) FROM ApprovalAttachment a WHERE a.company.companyId = :companyId")
    long sumFileSizeByCompanyId(@Param("companyId") Long companyId);

    /** 전체 결재 첨부 누적 바이트 합. */
    @Query("SELECT COALESCE(SUM(a.fileSize), 0) FROM ApprovalAttachment a")
    long sumAllFileSize();

    /** 회사별 결재 첨부 바이트 합 일괄. [companyId, sumBytes]. */
    @Query("SELECT a.company.companyId, COALESCE(SUM(a.fileSize), 0) FROM ApprovalAttachment a " +
           "WHERE a.company.companyId IS NOT NULL GROUP BY a.company.companyId")
    List<Object[]> sumFileSizePerCompany();
}
