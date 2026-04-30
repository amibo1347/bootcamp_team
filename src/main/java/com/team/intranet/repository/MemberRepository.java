package com.team.intranet.repository;

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

    @Query("SELECT m.profileImg FROM Member m WHERE m.id = :id")
   byte[] findProfileImgById(@Param("id") Long id);

    @Query("SELECT m FROM Member m " +
       "WHERE m.company.companyId = :companyId " +
       "AND (:deptId IS NULL OR m.dept.deptId = :deptId) " +
       "AND (:positionId IS NULL OR m.position.positionId = :positionId) " +
       "AND (:status IS NULL OR m.status = :status) " + 
       "ORDER BY m.position.positionLevel ASC, m.name ASC")
    List<Member> searchMembers(@Param("companyId") Long companyId, 
                               @Param("deptId") Long deptId, 
                               @Param("status") Status status,
                               @Param("positionId") Long positionId);

}
