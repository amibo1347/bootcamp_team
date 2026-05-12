package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
