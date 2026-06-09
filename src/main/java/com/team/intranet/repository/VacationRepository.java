package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import com.team.intranet.entity.VacationRequest;
import com.team.intranet.enums.ApprovalStatus;
public interface VacationRepository extends JpaRepository<VacationRequest, Long>{
    
    Optional<VacationRequest> findByApproval_ApprovalId(Long approvalId);
    boolean existsByApproval_ApprovalId(Long approvalId);
    List<VacationRequest> findByApproval_StatusAndStartDateBetween(ApprovalStatus status, LocalDate from, LocalDate to);
}
