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
    List<Member> findByStatusAndCompanyCompanyId(Status status, Long companyId);
    boolean existsByLoginId(String loginId);

    @Query("SELECT m FROM Member m " +
           "WHERE m.company.companyId = :companyId " +
           "AND (:deptId IS NULL OR m.dept.id = :deptId) " +
           "AND (:status IS NULL OR m.status = :status) " +
           "AND (:positionId IS NULL OR m.position.id = :positionId)")
    List<Member> searchMembers(@Param("companyId") Long companyId, 
                               @Param("deptId") Long deptId, 
                               @Param("status") Status status,
                               @Param("positionId") Long positionId);

}
