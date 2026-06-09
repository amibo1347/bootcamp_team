package com.team.intranet.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.team.intranet.enums.board.AnonymousType;
import com.team.intranet.enums.board.CommentScope;
import com.team.intranet.enums.board.ReadScope;
import com.team.intranet.enums.board.ViewType;
import com.team.intranet.enums.board.WriteScope;
import com.team.intranet.enums.board.BoardType;
import com.team.intranet.entity.Board;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BoardDto {
    private Long boardId;
    private String boardName;
    private Long companyId;

    private BoardType boardType;
    private ViewType viewType;
    private ReadScope readScope;
    private WriteScope writeScope;
    private CommentScope commentScope;
    private AnonymousType anonymousType;

    private Boolean isActive;
    private Boolean isAiUse;

    // 권한별 다중 선택: null/empty면 "전체" 의미
    private List<Long> readDeptIds;
    private List<Long> readPositionIds;
    private List<Long> writeDeptIds;
    private List<Long> writePositionIds;
    private List<Long> commentDeptIds;
    private List<Long> commentPositionIds;

    /**
     * 엔티티 → DTO (목록/사이드바용 기본 정보만 담음)
     * 권한 규칙(read/write/commentDeptIds 등)은 비어있는 상태로 둠.
     * 편집 폼처럼 규칙까지 필요한 곳에서는 서비스가 별도로 채움.
     */
    public static BoardDto from(Board board) {
        BoardDto dto = new BoardDto();
        dto.setBoardId(board.getBoardId());
        dto.setBoardName(board.getBoardName());
        dto.setCompanyId(board.getCompany() != null ? board.getCompany().getCompanyId() : null);
        dto.setBoardType(board.getBoardType());
        dto.setViewType(board.getViewType());
        dto.setReadScope(board.getReadScope());
        dto.setWriteScope(board.getWriteScope());
        dto.setCommentScope(board.getCommentScope());
        dto.setAnonymousType(board.getAnonymousType());
        dto.setIsActive(board.getIsActive());
        dto.setIsAiUse(board.getIsAiUse());
        return dto;
    }
}
