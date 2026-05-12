package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import com.team.intranet.entity.CalendarShareMember;
import com.team.intranet.entity.Calendar;
import com.team.intranet.entity.Member;

public interface CalendarShareMemberRepository extends JpaRepository<CalendarShareMember, Long>{
    
    List<CalendarShareMember> findAllByCalendar(Calendar calendar);
    List<CalendarShareMember> findAllByMember(Member member);
    void deleteAllByCalendar(Calendar calendar);
    boolean existsByCalendarAndMember(Calendar calendar, Member member);
}
