package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

import com.team.intranet.entity.CalendarShareDept;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Calendar;

public interface CalendarShareDeptRepository extends JpaRepository<CalendarShareDept, Long>{

    List<CalendarShareDept> findAllByCalendar(Calendar calendar);
    List<CalendarShareDept> findAllByDept(Dept dept);

    /** 일정 리스트의 dept 공유를 한 방에 — getCalendars() 의 N+1 회피용. */
    List<CalendarShareDept> findAllByCalendarIn(Collection<Calendar> calendars);

    /** {@link CalendarShareMemberRepository#deleteAllByCalendar} 와 동일 패턴 — 즉시 flush (clear 안 함). */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM CalendarShareDept s WHERE s.calendar = :calendar")
    void deleteAllByCalendar(@Param("calendar") Calendar calendar);

    boolean existsByCalendarAndDept(Calendar calendar, Dept dept);
}
