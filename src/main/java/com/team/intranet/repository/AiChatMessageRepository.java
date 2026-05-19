package com.team.intranet.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.AiChatMessage;
import com.team.intranet.enums.AiChatRole;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    /** 세션의 모든 메시지 (시간순). */
    List<AiChatMessage> findBySession_SessionIdOrderByCreatedAtAscMessageIdAsc(Long sessionId);

    /** 세션의 최신 N개 (히스토리 캡 + 미리보기). */
    List<AiChatMessage> findBySession_SessionIdOrderByCreatedAtDescMessageIdDesc(Long sessionId, Pageable pageable);

    /** 세션 마지막 메시지 1건 (목록 미리보기). */
    Optional<AiChatMessage> findFirstBySession_SessionIdOrderByCreatedAtDescMessageIdDesc(Long sessionId);

    /** Rate limit 체크: 회원의 특정 role 메시지 수 (since 이후). */
    @Query("""
        SELECT COUNT(m) FROM AiChatMessage m
        WHERE m.session.member.memberId = :memberId
          AND m.role = :role
          AND m.createdAt >= :since
        """)
    long countByMemberAndRoleSince(@Param("memberId") Long memberId,
                                    @Param("role") AiChatRole role,
                                    @Param("since") LocalDateTime since);
}
