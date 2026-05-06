package com.team.intranet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.team.intranet.repository.BoardRepository;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.PositionRepository;

import jakarta.persistence.EntityNotFoundException;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.enums.ErrorCode;

import com.team.intranet.repository.DeptRepository;
import java.util.List;

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

    // 게시판 전체 조회 (관리자용)
    public List<Board> findAll(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.COMPANY_NOT_FOUND);
        }
        return boardRepository.findAllByCompany_CompanyId(companyId);
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

    // 게시판 조회 - 로그인한 사용자가 볼 수 있는 게시판만
    public List<BoardDto> findVisibleBoards(MemberSession ms) {
        return boardRepository.findAllByCompany_CompanyId(ms.getCompanyId())
            .stream()
            .filter(Board::getIsActive)            // 활성만
            .filter(board -> canRead(ms, board))   // 권한 있는 것만
            .map(BoardDto::from)
            .toList();
    }

    private boolean canRead(MemberSession ms, Board board) {
    return switch (board.getReadScope()) {
        case ALL -> true;
        case RESTRICTED -> 
            (board.getDept() == null || board.getDept().getDeptId().equals(ms.getDeptId())) &&
            (board.getPosition() == null || board.getPosition().getPositionId().equals(ms.getPositionId()));
    };
}

    // 게시판 수정
    @Transactional
    public void updateBoard(MemberSession ms, Long boardId, BoardDto dto) {
        Board board = boardRepository.findById(boardId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        // 2. 관리자 권한 체크
        if (ms.getRole() != com.team.intranet.enums.member.Role.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_ADMIN_ROLE);
        }
    }

    // 게시판 삭제
    @Transactional
    public void deleteBoard(MemberSession ms, Long boardId) {
        // 1. 게시판 존재 여부 체크
         Board board = boardRepository.findById(boardId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        // 2. 관리자 권한 체크
        if (ms.getRole() != com.team.intranet.enums.member.Role.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_ADMIN_ROLE);
        }
        boardRepository.delete(board);
    }   
}