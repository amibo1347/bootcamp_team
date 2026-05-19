package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import com.team.intranet.entity.CalendarShareMember;
import com.team.intranet.entity.Calendar;
import com.team.intranet.entity.Member;

public interface CalendarShareMemberRepository extends JpaRepository<CalendarShareMember, Long>{

    List<CalendarShareMember> findAllByCalendar(Calendar calendar);
    List<CalendarShareMember> findAllByMember(Member member);

    /**
     * 일정의 모든 공유 회원 행 즉시 삭제.
     * @Modifying + flushAutomatically=true 로 동일 트랜잭션 내 후속 INSERT 보다 먼저 SQL DELETE 실행되도록 강제.
     *   (derived 형태에선 Hibernate ActionQueue 가 INSERT 를 먼저 실행해 ORA-00001 발생함)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CalendarShareMember s WHERE s.calendar = :calendar")
    void deleteAllByCalendar(@Param("calendar") Calendar calendar);

    boolean existsByCalendarAndMember(Calendar calendar, Member member);
}
