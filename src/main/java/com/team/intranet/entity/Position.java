package com.team.intranet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="tbl_position")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Position {

    @Column(name="position_id")
    private Long positionId;

    @Column(name="position_name")
    private String positionName;

    @Column(name="level")
    private int level;

    @ManyToOne
    @JoinColumn(name="company_id")
    private Company company;
}
