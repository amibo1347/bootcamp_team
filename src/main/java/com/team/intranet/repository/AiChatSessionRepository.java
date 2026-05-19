package com.team.intranet.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.AiChatSession;
import com.team.intranet.entity.Member;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {

    List<AiChatSession> findByMemberOrderByUpdatedAtDesc(Member member);
}
