package com.team.intranet.repository;

import com.team.intranet.entity.Board;
import com.team.intranet.enums.board.BoardType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {
    List<Board> findAllByCompany_CompanyId(Long companyId);

    /** AI 비서가 참고 가능한 게시판 (활성 + AI 사용 ON). */
    List<Board> findAllByCompany_CompanyIdAndIsAiUseTrueAndIsActiveTrue(Long companyId);

    /** 회사의 첫 번째 NOTICE 타입 게시판 (= 시스템 디폴트 공지사항). */
    Optional<Board> findFirstByCompany_CompanyIdAndBoardTypeOrderByBoardIdAsc(Long companyId, BoardType boardType);
}
