package com.team.intranet.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.Member;
import com.team.intranet.entity.Calendar;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.Status;

public interface MemberRepository extends JpaRepository<Member, Long>{
    
    List<Member> findByCompanyCompanyId(Long companyId);
    List<Member> findByStatusAndCompanyCompanyId(Status status, Long companyId);

    /** 입사일 backfill 마이그레이션용 — hireDate 미설정 회원. */
    List<Member> findByHireDateIsNull();

    /** 로그인 인증 — loginId 는 회사별로만 유니크하므로 (회사, loginId) 복합키로 조회. */
    Optional<Member> findByCompany_CompanyIdAndLoginId(Long companyId, String loginId);

    /** 회원가입 아이디/사번 중복 확인 — 같은 회사 안에서만 검사. */
    boolean existsByCompany_CompanyIdAndLoginId(Long companyId, String loginId);

    /** 회사별 회원 수 (MASTER 사용량 대시보드). */
    long countByCompany_CompanyId(Long companyId);

    /** 회사별 회원 수 일괄 — N+1 회피. [companyId, count]. */
    @Query("SELECT m.company.companyId, COUNT(m) FROM Member m GROUP BY m.company.companyId")
    List<Object[]> countMembersPerCompany();

    /**
     * 회사별 기간 내 신규 가입 회원 수 일괄.
     *  - createdAt >= since 인 회원만 카운트.
     *  - MASTER 사용량 대시보드의 "이번 달 신규" 컬럼 및 KPI.
     */
    @Query("SELECT m.company.companyId, COUNT(m) FROM Member m WHERE m.createdAt >= :since GROUP BY m.company.companyId")
    List<Object[]> countMembersPerCompanySince(@Param("since") LocalDateTime since);

    /** 전체 회원 수 — KPI 카드. */
    @Query("SELECT COUNT(m) FROM Member m")
    long countAllMembers();

    /** 전체 신규 회원 수 (since 이후) — KPI 카드. */
    @Query("SELECT COUNT(m) FROM Member m WHERE m.createdAt >= :since")
    long countAllMembersSince(@Param("since") LocalDateTime since);

    /**
     * since 이후 가입한 회원들의 createdAt 목록 — 시계열 차트용.
     *  - 일별 그룹은 호출 측(service)이 Java 스트림으로 처리한다.
     *    JPQL 의 EXTRACT/native TRUNC 분기 없이 가장 안전한 경로.
     *  - KPI 대시보드 규모(30일치)에서는 수천 row 이하라 전송량 부담 미미.
     */
    @Query("SELECT m.createdAt FROM Member m WHERE m.createdAt >= :since")
    List<LocalDateTime> findCreatedAtSince(@Param("since") LocalDateTime since);

    /** 회사의 특정 권한 회원 — 대표(ADMIN) 조회용. memberId 오름차순(= 회사 생성 시 만든 원 대표가 먼저). */
    List<Member> findByCompany_CompanyIdAndRoleOrderByMemberIdAsc(Long companyId, Role role);

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

   /** profileImg 회사 스코프 검증용 — 회원의 회사 ID 만 가볍게 조회. 회원 미존재 시 null. */
   @Query("SELECT m.company.companyId FROM Member m WHERE m.memberId = :id")
   Long findCompanyIdByMemberId(@Param("id") Long id);

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
