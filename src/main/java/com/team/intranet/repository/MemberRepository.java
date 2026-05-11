package com.team.intranet.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.Member;
import com.team.intranet.enums.member.Status;

public interface MemberRepository extends JpaRepository<Member, Long>{
    
    Optional<Member> findByLoginId(String loginId);
    List<Member> findByCompanyCompanyId(Long companyId);
    List<Member> findByStatusAndCompanyCompanyId(Status status, Long companyId);
    boolean existsByLoginId(String loginId);

    // 보존 기간을 넘긴 종료 상태 회원 (스케줄러 영구 삭제 대상)
    List<Member> findByStatusAndStatusChangedAtBefore(Status status, LocalDateTime threshold);
    

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
