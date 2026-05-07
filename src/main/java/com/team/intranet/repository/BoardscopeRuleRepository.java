package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.team.intranet.entity.BoardScopeRule;
import java.util.List;
import com.team.intranet.enums.board.ScopeType;

public interface BoardscopeRuleRepository extends JpaRepository<BoardScopeRule, Long> {
    List<BoardScopeRule> findByBoardBoardIdAndScopeType(Long boardId, ScopeType scopeType);
    void deleteByBoardBoardId(Long boardId);    
}
