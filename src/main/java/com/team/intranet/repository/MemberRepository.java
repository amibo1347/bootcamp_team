package com.team.intranet.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.Member;
import com.team.intranet.enums.member.Status;

public interface MemberRepository extends JpaRepository<Member, Long>{
    
    Optional<Member> findByLoginId(String loginId);
    List<Member> findByStatusAndCompanyCompanyId(Status status, Long companyId);
    boolean existsByLoginId(String loginId);
}
