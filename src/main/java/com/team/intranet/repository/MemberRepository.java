package com.team.intranet.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.Member;
import com.team.intranet.entity.Calendar;
import com.team.intranet.enums.member.Status;

public interface MemberRepository extends JpaRepository<Member, Long>{
    
    Optional<Member> findByLoginId(String loginId);
    List<Member> findByCompanyCompanyId(Long companyId);
    List<Member> findByStatusAndCompanyCompanyId(Status status, Long companyId);
    boolean existsByLoginId(String loginId);

    /** AI 일정 공유: 회사 + 활성 회원 + 이름 매칭. */
    List<Member> findByStatusAndCompanyCompanyIdAndNameIn(Status status, Long companyId, List<String> names);

    // 보존 기간을 넘긴 종료 상태 회원 (스케줄러 영구 삭제 대상)
    List<Member> findByStatusAndStatusChangedAtBefore(Status status, LocalDateTime threshold);

    // 부서 공유 → 활성 회원 한 방에
  @Query("""
      SELECT DISTINCT m FROM Member m
      WHERE m.status = :status
        AND m.dept IN (
            SELECT s.dept FROM CalendarShareDept s WHERE s.calendar = :calendar
        )
  """)
  List<Member> findJoinByCalendarDeptShares(@Param("calendar") Calendar calendar,
                                              @Param("status") Status status);

  // 멤버 공유 → 활성 회원 한 방에
  @Query("""
      SELECT m FROM CalendarShareMember s JOIN s.member m
      WHERE s.calendar = :calendar
        AND m.status = :status
  """)
  List<Member> findJoinByCalendarMemberShares(@Param("calendar") Calendar calendar,
                                                @Param("status") Status status);

   @Query("SELECT m.profileImg FROM Member m WHERE m.memberId = :id")
   byte[] findProfileImgById(@Param("id") Long id);

   @Query("SELECT m FROM Member m " +
   "LEFT JOIN m.position p " +
   "WHERE m.company.companyId = :companyId " +
   "AND (:keyword IS NULL OR :keyword = '' " +
   "     OR LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
   "AND (:deptId IS NULL OR m.dept.deptId = :deptId) " +
   "AND (:positionId IS NULL OR p.positionId = :positionId) " +
   "AND (:statuses IS NULL OR m.status IN :statuses) " +
   "ORDER BY p.positionLevel ASC NULLS LAST, m.name ASC")
List<Member> searchMembers(
    @Param("companyId") Long companyId,
    @Param("keyword") String keyword,
    @Param("deptId") Long deptId,
    @Param("statuses") List<Status> statuses,
    @Param("positionId") Long positionId
);

}
