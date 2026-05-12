package com.team.intranet.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.dto.BoardDto;
import com.team.intranet.entity.Board;
import com.team.intranet.entity.BoardAlertPref;
import com.team.intranet.entity.BoardScopeRule;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.Position;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.board.BoardType;
import com.team.intranet.enums.board.CommentScope;
import com.team.intranet.enums.board.ReadScope;
import com.team.intranet.enums.board.ScopeType;
import com.team.intranet.enums.board.WriteScope;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.BoardAlertPrefRepository;
import com.team.intranet.repository.BoardRepository;
import com.team.intranet.repository.BoardscopeRuleRepository;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.DeptRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.repository.PositionRepository;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;
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
    private final BoardscopeRuleRepository scopeRuleRepository;
    private final BoardAlertPrefRepository boardAlertPrefRepository;
    private final MemberRepository memberRepository;

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

    // 게시판 조회
    public BoardDto findVisibleBoardById(MemberSession ms, Long boardId) {
      return BoardDto.from(getReadableBoard(ms, boardId));
  }
  
    // 권한 검증
    public Board getReadableBoard(MemberSession ms, Long boardId) {
      Board board = boardRepository.findById(boardId)
              .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
      validateSameCompany(board.getCompany().getCompanyId(), ms.getCompanyId());
      if (!board.getIsActive()) {
          throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
      }
      if (!canRead(ms, board)) {
          throw new BusinessException(ErrorCode.ACCESS_DENIED);
      }
      return board;
  }

  /** 글쓰기까지 가능한 Board */
  public Board getWritableBoard(MemberSession ms, Long boardId) {
      Board board = getReadableBoard(ms, boardId);
      if (!canWrite(ms, board)) {
          throw new BusinessException(ErrorCode.NO_AUTHORITY);
      }
      return board;
  }

  public Board getCommentableBoard(MemberSession ms, Long boardId){
    Board board = getWritableBoard(ms, boardId);
    if(!canComment(ms, board)){
        throw new BusinessException(ErrorCode.NO_AUTHORITY);
    }
    return board;
  }

    /**
     * 게시판 생성 — 권한별 다중 부서/직급 규칙 저장
     * 각 scope: 선택 항목이 0개면 ALL, 있으면 RESTRICTED + 카르테시안 곱으로 규칙 생성
     */
    @Transactional
    public Board createBoard(MemberSession ms, BoardDto dto) {
        Company company = findCompany(ms.getCompanyId());
        if (ms.getPositionId() == null) {
            throw new BusinessException(ErrorCode.POSITION_NOT_FOUND);
        }

        Board board = Board.createBoard(
                dto.getBoardName(), dto.getBoardType(), company,
                dto.getViewType(),
                resolveReadScope(dto),
                resolveWriteScope(dto),
                resolveCommentScope(dto),
                dto.getIsActive(), dto.getIsAiUse(),
                dto.getAnonymousType()
        );

        // Legacy NOT NULL 컬럼(dept_id, position_id) 호환값 세팅
        if (ms.getDeptId() != null) {
            Dept dept = findDept(ms.getDeptId());
            validateSameCompany(dept.getCompany().getCompanyId(), ms.getCompanyId());
            board.setDept(dept);
        }
        Position position = findPosition(ms.getPositionId());
        validateSameCompany(position.getCompany().getCompanyId(), ms.getCompanyId());
        board.setPosition(position);

        Board saved = boardRepository.save(board);

        saveScopeRules(saved, ScopeType.READ, dto.getReadDeptIds(), dto.getReadPositionIds());
        saveScopeRules(saved, ScopeType.WRITE, dto.getWriteDeptIds(), dto.getWritePositionIds());
        saveScopeRules(saved, ScopeType.COMMENT, dto.getCommentDeptIds(), dto.getCommentPositionIds());

        return saved;
    }

    /**
     * 게시판 수정 — 기존 규칙 모두 지우고 다시 생성
     */
    @Transactional
    public void updateBoard(MemberSession ms, Long boardId, BoardDto dto) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        validateSameCompany(board.getCompany().getCompanyId(), ms.getCompanyId());

        board.updateFromDto(
                dto.getBoardName(), dto.getBoardType(),
                dto.getViewType(),
                resolveReadScope(dto),
                resolveWriteScope(dto),
                resolveCommentScope(dto),
                dto.getIsActive(), dto.getIsAiUse(),
                dto.getAnonymousType()
        );

        scopeRuleRepository.deleteByBoardBoardId(boardId);
        saveScopeRules(board, ScopeType.READ, dto.getReadDeptIds(), dto.getReadPositionIds());
        saveScopeRules(board, ScopeType.WRITE, dto.getWriteDeptIds(), dto.getWritePositionIds());
        saveScopeRules(board, ScopeType.COMMENT, dto.getCommentDeptIds(), dto.getCommentPositionIds());
    }

    /**
     * 게시판 삭제 — 규칙도 함께 정리
     */
    @Transactional
    public void deleteBoard(MemberSession ms, Long boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        validateSameCompany(board.getCompany().getCompanyId(), ms.getCompanyId());

        scopeRuleRepository.deleteByBoardBoardId(boardId);
        boardRepository.delete(board);
    }

    // ===== 권한 체크 =====

    public boolean canRead(MemberSession ms, Board board) {
        return switch (board.getReadScope()) {
            case ALL -> true;
            case RESTRICTED -> matchesAnyRule(ms, board.getBoardId(), ScopeType.READ);
        };
    }

    public boolean canWrite(MemberSession ms, Board board) {
        return switch (board.getWriteScope()) {
            case ALL -> true;
            case RESTRICTED -> matchesAnyRule(ms, board.getBoardId(), ScopeType.WRITE);
        };
    }

    public boolean canComment(MemberSession ms, Board board) {
        return switch (board.getCommentScope()) {
            case ALL -> true;
            case RESTRICTED -> matchesAnyRule(ms, board.getBoardId(), ScopeType.COMMENT);
            case NONE -> false;
        };
    }

    /**
     * 해당 scope의 규칙 중 사용자에게 매칭되는 게 하나라도 있는지.
     * 규칙이 0건이면 = "전체"로 간주하여 통과.
     */
    private boolean matchesAnyRule(MemberSession ms, Long boardId, ScopeType scopeType) {
        List<BoardScopeRule> rules = scopeRuleRepository.findByBoardBoardIdAndScopeType(boardId, scopeType);
        if (rules.isEmpty()) {
            return true;
        }
        return rules.stream().anyMatch(rule -> matchesRestriction(ms, rule));
    }

    /**
     * 단일 규칙이 사용자 부서/직급과 매칭되는지.
     * rule.dept == null → 모든 부서 허용
     * rule.position == null → 모든 직급 허용
     * 둘 다 만족(AND)해야 통과
     */
    private boolean matchesRestriction(MemberSession ms, BoardScopeRule rule) {
        boolean deptOk = rule.getDept() == null
                || rule.getDept().getDeptId().equals(ms.getDeptId());

        boolean positionOk = rule.getPosition() == null
                || rule.getPosition().getPositionId().equals(ms.getPositionId());

        return deptOk && positionOk;
    }

    // ===== scope 결정 헬퍼 (선택 0개 → ALL, 1개 이상 → RESTRICTED) =====

    private ReadScope resolveReadScope(BoardDto dto) {
        return hasAnySelection(dto.getReadDeptIds(), dto.getReadPositionIds())
                ? ReadScope.RESTRICTED : ReadScope.ALL;
    }

    private WriteScope resolveWriteScope(BoardDto dto) {
        return hasAnySelection(dto.getWriteDeptIds(), dto.getWritePositionIds())
                ? WriteScope.RESTRICTED : WriteScope.ALL;
    }

    private CommentScope resolveCommentScope(BoardDto dto) {
        if (dto.getCommentScope() == CommentScope.NONE) {
            return CommentScope.NONE;
        }
        return hasAnySelection(dto.getCommentDeptIds(), dto.getCommentPositionIds())
                ? CommentScope.RESTRICTED : CommentScope.ALL;
    }

    private boolean hasAnySelection(List<Long> deptIds, List<Long> positionIds) {
        boolean d = deptIds != null && !deptIds.isEmpty();
        boolean p = positionIds != null && !positionIds.isEmpty();
        return d || p;
    }

    // ===== 규칙 저장 =====

    /**
     * 부서 N개 × 직급 M개 = N*M 개 규칙을 저장.
     * 한쪽 축이 비어있으면 그 축은 null(전체)로 표현.
     * 둘 다 비어있으면 아무것도 저장하지 않음 (= 전체 허용).
     */
    private void saveScopeRules(Board board, ScopeType scopeType, List<Long> deptIds, List<Long> positionIds) {
        boolean hasDept = deptIds != null && !deptIds.isEmpty();
        boolean hasPosition = positionIds != null && !positionIds.isEmpty();

        if (!hasDept && !hasPosition) {
            return; // 규칙 없음 = 전체
        }

        Long companyId = board.getCompany().getCompanyId();
        List<Long> dIds = hasDept ? deptIds : Collections.singletonList(null);
        List<Long> pIds = hasPosition ? positionIds : Collections.singletonList(null);

        for (Long dId : dIds) {
            Dept dept = null;
            if (dId != null) {
                dept = findDept(dId);
                validateSameCompany(dept.getCompany().getCompanyId(), companyId);
            }
            for (Long pId : pIds) {
                Position position = null;
                if (pId != null) {
                    position = findPosition(pId);
                    validateSameCompany(position.getCompany().getCompanyId(), companyId);
                }
                BoardScopeRule rule = new BoardScopeRule();
                rule.setBoard(board);
                rule.setScopeType(scopeType);
                rule.setDept(dept);
                rule.setPosition(position);
                scopeRuleRepository.save(rule);
            }
        }
    }

    // ===== 공통 헬퍼 =====

    private void validateSameCompany(Long entityCompanyId, Long userCompanyId) {
        if (!entityCompanyId.equals(userCompanyId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

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

    @Transactional(readOnly = true)
    public boolean isAlertOn(MemberSession ms, Long boardId) {
        Board board = getReadableBoard(ms, boardId);
        Member me = memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        return boardAlertPrefRepository.findByBoardAndMember(board, me)
            .map(BoardAlertPref::isEnabled)
            .orElseGet(() -> isDefaultOn(board.getBoardType()));   // 미설정이면 기본값
    }

    @Transactional
    public boolean toggleAlert(MemberSession ms, Long boardId) {
        Board board = getReadableBoard(ms, boardId);
        Member me = memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        boolean defaultOn = isDefaultOn(board.getBoardType());
        BoardAlertPref pref = boardAlertPrefRepository
            .findByBoardAndMember(board, me)
            .orElse(null);

        boolean currentlyOn = (pref != null) ? pref.isEnabled() : defaultOn;
        boolean next = !currentlyOn;

        if (pref == null) {
            pref = BoardAlertPref.builder()
                .board(board)
                .member(me)
                .isEnabled(next)
                .build();   // updatedAt 은 @PrePersist 가 자동 세팅
        } else {
            pref.setEnabled(next);   // updatedAt 은 @PreUpdate 가 자동 갱신
        }
        boardAlertPrefRepository.save(pref);
        return next;   // 프론트에 새 상태 응답
    }

    private boolean isDefaultOn(BoardType type) {
        return type == BoardType.NOTICE || type == BoardType.POLICY;
    }
}
