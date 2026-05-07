package com.team.intranet.service;

import java.util.List;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import com.team.intranet.dto.BoardDto;
import com.team.intranet.entity.Board;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Position;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.BoardRepository;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.DeptRepository;
import com.team.intranet.repository.PositionRepository;
import com.team.intranet.session.MemberSession;
import org.springframework.transaction.annotation.Transactional; 
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {
    
    private final BoardRepository boardRepository;
    private final CompanyRepository companyRepository;
    private final DeptRepository deptRepository;
    private final PositionRepository positionRepository;
    
    /**
     * 게시판 전체 조회 (관리자용 - 비활성 포함)
     */
    public List<Board> findAll(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.COMPANY_NOT_FOUND);
        }
        return boardRepository.findAllByCompany_CompanyId(companyId);
    }
    
    /**
     * 게시판 조회 - 로그인한 사용자가 볼 수 있는 게시판만
     */
    public List<BoardDto> findVisibleBoards(MemberSession ms) {
        List<BoardDto> result = boardRepository.findAllByCompany_CompanyId(ms.getCompanyId())
            .stream()
            .filter(Board::getIsActive)
            .filter(board -> canRead(ms, board))
            .map(BoardDto::from)
            .toList();
        log.info("findVisibleBoards size={}, items={}", result.size(), result);
        return result;
    }

    @Transactional(readOnly = true)
    public BoardDto findVisibleBoardById(MemberSession ms, Long boardId) {
    // 1. 게시판 조회
    Board board = boardRepository.findById(boardId)
        .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
    
    // 2. 멀티테넌시 검증 (다른 회사 게시판 차단)
    if (!board.getCompany().getCompanyId().equals(ms.getCompanyId())) {
        throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    
    // 3. 활성 상태 체크
    if (!board.getIsActive()) {
        throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
    }
    
    // 4. 읽기 권한 체크
    if (!canRead(ms, board)) {
        throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    
    // 5. DTO로 변환해서 반환
    return BoardDto.from(board);
}
    
    /**
     * 게시판 생성
     */
    @Transactional
    public Board createBoard(MemberSession ms, BoardDto dto) {
        Company company = findCompany(ms.getCompanyId());
        
        // 멀티테넌시 체크: 부서/직급도 같은 회사 소속인지 확인
        Dept dept = null;
        if (dto.getDeptId() != null) {
            dept = findDept(dto.getDeptId());
            validateSameCompany(dept.getCompany().getCompanyId(), ms.getCompanyId());
        }
        
        Position position = null;
        if (dto.getPositionId() != null) {
            position = findPosition(dto.getPositionId());
            validateSameCompany(position.getCompany().getCompanyId(), ms.getCompanyId());
        }
        
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
    
    /**
     * 게시판 수정
     */
    @Transactional
    public void updateBoard(MemberSession ms, Long boardId, BoardDto dto) {
        Board board = boardRepository.findById(boardId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        
        // 멀티테넌시 체크
        validateSameCompany(board.getCompany().getCompanyId(), ms.getCompanyId());
        
        // 부서/직급도 같은 회사 소속인지 확인
        Dept dept = null;
        if (dto.getDeptId() != null) {
            dept = findDept(dto.getDeptId());
            validateSameCompany(dept.getCompany().getCompanyId(), ms.getCompanyId());
        }
        
        Position position = null;
        if (dto.getPositionId() != null) {
            position = findPosition(dto.getPositionId());
            validateSameCompany(position.getCompany().getCompanyId(), ms.getCompanyId());
        }
        
        // ⭐ 실제 수정 로직 (Entity의 update 메서드 호출)
        board.updateFromDto(
            dto.getBoardName(), dto.getBoardType(),
            dept, position,
            dto.getViewType(), dto.getReadScope(),
            dto.getWriteScope(), dto.getCommentScope(),
            dto.getIsActive(), dto.getIsAiUse(),
            dto.getAnonymousType()
        );
        // JPA 변경 감지(Dirty Checking)로 자동 저장됨
    }
    
    /**
     * 게시판 삭제
     */
    @Transactional
    public void deleteBoard(MemberSession ms, Long boardId) {
        Board board = boardRepository.findById(boardId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        
        // 멀티테넌시 체크
        validateSameCompany(board.getCompany().getCompanyId(), ms.getCompanyId());
        
        boardRepository.delete(board);
    }
    
    /**
     * 권한 체크 - 사용자가 게시판을 읽을 수 있는지
     */
    private boolean canRead(MemberSession ms, Board board) {
        return switch (board.getReadScope()) {
            case ALL -> true;
            case RESTRICTED -> 
                (board.getDept() == null || board.getDept().getDeptId().equals(ms.getDeptId())) &&
                (board.getPosition() == null || board.getPosition().getPositionId().equals(ms.getPositionId()));
        };
    }
    
    /**
     * 멀티테넌시 검증 - 같은 회사 데이터인지 확인
     */
    private void validateSameCompany(Long entityCompanyId, Long userCompanyId) {
        if (!entityCompanyId.equals(userCompanyId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
    
    // 헬퍼 메서드들
    private Company findCompany(Long companyId) {
        return companyRepository.findById(companyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));
    }
    
    private Dept findDept(Long deptId) {
        return deptRepository.findById(deptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));
    }
    
    private Position findPosition(Long positionId) {
        return positionRepository.findById(positionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));
    }
}