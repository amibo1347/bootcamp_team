package com.team.intranet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.Board;
import com.team.intranet.entity.BoardAlertPref;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.member.Status;

public interface BoardAlertPrefRepository extends JpaRepository<BoardAlertPref, Long> {

    Optional<BoardAlertPref> findByBoardAndMember(Board board, Member member);

    // 기본 OFF 게시판용: 명시적으로 ON 한 활성 회원
    @Query("""
        SELECT p.member FROM BoardAlertPref p
        WHERE p.board = :board
          AND p.isEnabled = true
          AND p.member.status = :status
    """)
    List<Member> findOptedInMembers(@Param("board") Board board,
                                    @Param("status") Status status);

    // 기본 ON 게시판용: 명시적으로 OFF 한 멤버 ID들 (제외용)
    @Query("SELECT p.member.memberId FROM BoardAlertPref p WHERE p.board = :board AND p.isEnabled = false")
    List<Long> findOptedOutMemberIds(@Param("board") Board board);
}
