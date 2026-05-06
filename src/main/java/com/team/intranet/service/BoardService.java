package com.team.intranet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.team.intranet.repository.BoardRepository;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.PositionRepository;

import jakarta.persistence.EntityNotFoundException;

import com.team.intranet.repository.DeptRepository;

import com.team.intranet.entity.Board;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Position;
import com.team.intranet.dto.BoardDto;
import com.team.intranet.session.MemberSession;

@Service
@RequiredArgsConstructor
public class BoardService {
    
    private final BoardRepository boardRepository;
    private final CompanyRepository companyRepository;
    private final DeptRepository deptRepository;
    private final PositionRepository positionRepository;

    private Company findCompany(Long companyId) {
        return companyRepository.findById(companyId)
            .orElseThrow(() -> new EntityNotFoundException("회사를 찾을 수 없습니다."));
    }

    private Dept findDept(Long deptId) {
        return deptRepository.findById(deptId)
            .orElseThrow(() -> new EntityNotFoundException("부서를 찾을 수 없습니다."));
    }

    private Position findPosition(Long positionId) {
        return positionRepository.findById(positionId)
            .orElseThrow(() -> new EntityNotFoundException("직급을 찾을 수 없습니다."));
    }

    private Board findAll(Long boardId) {
        return boardRepository.findById(boardId)
            .orElseThrow(() -> new EntityNotFoundException("게시판을 찾을 수 없습니다."));
    }

    // 게시판 생성
    @Transactional
public Board createBoard(MemberSession ms, BoardDto dto) {
    
    Company company = findCompany(ms.getCompanyId());
    Dept dept = findDept(dto.getDeptId());
    Position position = findPosition(dto.getPositionId());
    
    Board board = Board.createBoard( 
        dto.getBoardName(), dto.getBoardType(),
        company, dept, position,
        dto.getViewType(), dto.getReadScope(),
        dto.getWriteScope(), dto.getCommentScope(),
        dto.getIsActive(), dto.getIsAiUse(),
        dto.getAnonymousType()
    );
    
    return boardRepository.save(board);
}
}