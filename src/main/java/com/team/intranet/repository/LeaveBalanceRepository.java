package com.team.intranet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.LeaveBalance;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByMember_MemberIdAndYear(Long memberId, int year);

    /** 회사 + 연도 단위 전체 원장 (명부 화면용). */
    List<LeaveBalance> findByMember_Company_CompanyIdAndYear(Long companyId, int year);
}
