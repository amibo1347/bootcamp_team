package com.team.intranet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.ChatConversation;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    /** 정규화된 (peerA<peerB) 쌍 조회. */
    Optional<ChatConversation> findByCompany_CompanyIdAndPeerA_MemberIdAndPeerB_MemberId(
        Long companyId, Long peerAId, Long peerBId);

    /** 회원이 참여한 모든 대화방 (최근 활성 순). */
    @Query("""
        SELECT c FROM ChatConversation c
        JOIN FETCH c.peerA pa
        JOIN FETCH c.peerB pb
        LEFT JOIN FETCH pa.dept
        LEFT JOIN FETCH pa.position
        LEFT JOIN FETCH pb.dept
        LEFT JOIN FETCH pb.position
        WHERE c.company.companyId = :companyId
          AND (pa.memberId = :memberId OR pb.memberId = :memberId)
        ORDER BY c.updatedAt DESC
        """)
    List<ChatConversation> findMineByCompany(
        @Param("companyId") Long companyId,
        @Param("memberId") Long memberId);
}
