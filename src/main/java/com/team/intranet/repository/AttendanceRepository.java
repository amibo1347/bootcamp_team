package com.team.intranet.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.Attendance;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByMember_MemberIdAndWorkDate(Long memberId, LocalDate workDate);

    /** 본인 월별 근태 (work_date 오름차순). */
    @Query("""
        SELECT a FROM Attendance a
        WHERE a.member.memberId = :memberId
          AND a.workDate BETWEEN :from AND :to
        ORDER BY a.workDate ASC
        """)
    List<Attendance> findMonthlyByMember(
        @Param("memberId") Long memberId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to);

    /** 회사 전체 월별 근태 (work_date DESC, 이름 ASC). 회원 fetch join — 화면에서 부서/직급 표시. */
    @Query("""
        SELECT a FROM Attendance a
        JOIN FETCH a.member m
        LEFT JOIN FETCH m.dept
        LEFT JOIN FETCH m.position
        WHERE m.company.companyId = :companyId
          AND a.workDate BETWEEN :from AND :to
        ORDER BY a.workDate DESC, m.name ASC
        """)
    List<Attendance> findMonthlyByCompany(
        @Param("companyId") Long companyId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to);

    /** 회사 전체 특정 일자 근태 — 간트 차트용. */
    @Query("""
        SELECT a FROM Attendance a
        JOIN FETCH a.member m
        LEFT JOIN FETCH m.dept
        LEFT JOIN FETCH m.position
        WHERE m.company.companyId = :companyId
          AND a.workDate = :date
        ORDER BY m.name ASC
        """)
    List<Attendance> findDailyByCompany(
        @Param("companyId") Long companyId,
        @Param("date") LocalDate date);
}
