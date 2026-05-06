package com.team.intranet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;

import org.springframework.data.annotation.CreatedDate;
import com.team.intranet.enums.board.AnonymousType;
import com.team.intranet.enums.board.CommentScope;
import com.team.intranet.enums.board.ReadScope;
import com.team.intranet.enums.board.ViewType;
import com.team.intranet.enums.board.WriteScope;
import com.team.intranet.enums.board.BoardType;

@Entity
@Table(name = "tbl_board_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Board {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "board_id")
    private Long boardId;

    @Column(name = "board_name")
    private String boardName;

    @Enumerated(EnumType.STRING)
    @Column(name = "board_type")
    private BoardType boardType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dept_id")
    private Dept dept;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;

    @Enumerated(EnumType.STRING)
    @Column(name = "view_type")
    private ViewType viewType;

    @Enumerated(EnumType.STRING)
    @Column(name = "read_scope")
    private ReadScope readScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "write_scope")
    private WriteScope writeScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "comment_scope")
    private CommentScope commentScope;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "is_ai_use", nullable = false)
    private Boolean isAiUse;

    @Enumerated(EnumType.STRING)
    @Column(name = "anonymous_type")
    private AnonymousType anonymousType;

    @CreatedDate
    @Column(name = "created_at")
    private String createdAt;

    public static Board createBoard
    (String boardName, BoardType boardType, Company company, Dept dept, Position position, 
        ViewType viewType, ReadScope readScope, WriteScope writeScope, CommentScope commentScope, Boolean isActive, 
        Boolean isAiUse, AnonymousType anonymousType) {
        Board board = new Board();
        board.boardName = boardName;
        board.boardType = boardType;
        board.company = company;
        board.dept = dept;
        board.position = position;
        board.viewType = viewType;
        board.readScope = readScope;
        board.writeScope = writeScope;
        board.commentScope = commentScope;
        board.isActive = isActive;
        board.isAiUse = isAiUse;
        board.anonymousType = anonymousType;
        board.createdAt = java.time.LocalDateTime.now().toString();
        return board;
    }

}
