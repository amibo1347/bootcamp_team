package com.team.intranet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.team.intranet.enums.board.ScopeType;

@Entity
@Table(name = "tbl_board_scope_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BoardScopeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type")
    private ScopeType scopeType; // READ, WRITE, COMMENT

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dept_id")
    private Dept dept; // 허용된 부서 (null이면 전체 허용)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position; // 허용된 직급 (null이면 전체 허용)
}
