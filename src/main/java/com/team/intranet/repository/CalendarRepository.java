package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.Calendar;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Member;

import java.util.List;
import java.time.LocalDateTime;

public interface CalendarRepository extends JpaRepository<Calendar, Long>{
    List<Calendar> findByMember(Member member);
    List<Calendar> findByMemberAndStartAtBetween(Member member, LocalDateTime startAt, LocalDateTime endAt);

    /**
     * AI 비서 컨텍스트용 — 시간 오름차순 정렬.
     *  - 정렬이 보장돼야 LLM 이 "지난주 X 회의", "다음 미팅" 같은 시간 표현을 일관되게 매칭한다.
     */
    List<Calendar> findByMemberAndStartAtBetweenOrderByStartAtAsc(
            Member member, LocalDateTime startAt, LocalDateTime endAt);

    @Query("""
    SELECT DISTINCT c FROM Calendar c
    WHERE c.member = :me
       OR c.visibility = 'COMPANY'
       OR (c.visibility = 'DEPARTMENT' AND EXISTS (
             SELECT 1 FROM CalendarShareDept s
             WHERE s.calendar = c AND s.dept = :myDept))
       OR (c.visibility = 'SPECIFIC' AND EXISTS (
             SELECT 1 FROM CalendarShareMember s
             WHERE s.calendar = c AND s.member = :me))
  """)
  List<Calendar> findAccessibleByMember(@Param("me") Member me, @Param("myDept") Dept myDept);

    // 알림 스캐너용: 알림 사용 ON이고 startAt이 [from, to] 범위인 일정
    @Query("""
        SELECT c FROM Calendar c
        WHERE c.isAlert = true
          AND c.startAt IS NOT NULL
          AND c.startAt BETWEEN :from AND :to
    """)
    List<Calendar> findUpcomingForAlert(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);
}
