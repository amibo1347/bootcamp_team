package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import com.team.intranet.entity.CalendarShareDept;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Calendar;

public interface CalendarShareDeptRepository extends JpaRepository<CalendarShareDept, Long>{
    
    List<CalendarShareDept> findAllByCalendar(Calendar calendar);
    List<CalendarShareDept> findAllByDept(Dept dept);
    void deleteAllByCalendar(Calendar calendar);
    boolean existsByCalendarAndDept(Calendar calendar, Dept dept);
}
