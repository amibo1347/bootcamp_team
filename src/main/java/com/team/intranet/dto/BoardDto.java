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
}
