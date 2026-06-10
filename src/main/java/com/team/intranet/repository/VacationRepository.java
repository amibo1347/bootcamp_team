package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import com.team.intranet.entity.VacationRequest;
import com.team.intranet.enums.ApprovalStatus;
import com.team.intranet.enums.VacationType;
public interface VacationRepository extends JpaRepository<VacationRequest, Long>{

    Optional<VacationRequest> findByApproval_ApprovalId(Long approvalId);
    boolean existsByApproval_ApprovalId(Long approvalId);
    List<VacationRequest> findByApproval_StatusAndStartDateBetween(ApprovalStatus status, LocalDate from, LocalDate to);

    /**
     * 연차 원장 계산용 — 회사 + 연도 범위 + 휴가종류 + 결재상태(복수) 조회.
     *  - drafter 까지 join fetch 해 N+1 없이 신청자별 합산을 서비스에서 수행한다.
     *  - 호출 측이 startDate 가 해당 연도(1/1~12/31)에 들어오는 건만 넘긴다(회계연도 기준).
     */
    @Query("select v from VacationRequest v " +
           "join fetch v.approval a " +
           "join fetch a.drafter d " +
           "where a.company.companyId = :companyId " +
           "and v.vacationType = :type " +
           "and a.status in :statuses " +
           "and v.startDate between :from and :to")
    List<VacationRequest> findForLeaveLedger(@Param("companyId") Long companyId,
                                             @Param("type") VacationType type,
                                             @Param("statuses") List<ApprovalStatus> statuses,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /** 본인 1명 연차 원장 계산용 — 회원 + 연도 범위 + 휴가종류 + 결재상태(복수). */
    @Query("select v from VacationRequest v " +
           "join fetch v.approval a " +
           "where a.drafter.memberId = :memberId " +
           "and v.vacationType = :type " +
           "and a.status in :statuses " +
           "and v.startDate between :from and :to")
    List<VacationRequest> findForMemberLeaveLedger(@Param("memberId") Long memberId,
                                                   @Param("type") VacationType type,
                                                   @Param("statuses") List<ApprovalStatus> statuses,
                                                   @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);
}
