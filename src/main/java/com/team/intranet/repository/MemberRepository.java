package com.team.intranet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long>{
    
    Optional<Member> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
}
