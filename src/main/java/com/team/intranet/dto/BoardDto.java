package com.team.intranet.dto;

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
    private Long deptId;
    private Long positionId;
    
    private BoardType boardType;
    private ViewType viewType;
    private ReadScope readScope;
    private WriteScope writeScope;
    private CommentScope commentScope;
    private AnonymousType anonymousType;
    
    private Boolean isActive;
    private Boolean isAiUse;

    public static BoardDto from(Board board) {
    return new BoardDto(
        board.getBoardId(),
        board.getBoardName(),
        board.getCompany() != null ? board.getCompany().getCompanyId() : null,
        board.getDept() != null ? board.getDept().getDeptId() : null,
        board.getPosition() != null ? board.getPosition().getPositionId() : null,
        board.getBoardType(),
        board.getViewType(),
        board.getReadScope(),
        board.getWriteScope(),
        board.getCommentScope(),
        board.getAnonymousType(),
        board.getIsActive(),
        board.getIsAiUse()
    );
}
}
