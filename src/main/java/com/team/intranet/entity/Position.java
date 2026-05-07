package com.team.intranet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

import com.team.intranet.enums.member.Role;
@Entity
@Table(name="tbl_position")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="position_id")
    private Long positionId;

    @Column(name="position_name")
    private String positionName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="company_id")
    private Company company;

    @Column(name="position_level")
    private Integer positionLevel;

    @Column(name="role")
    @Enumerated(EnumType.STRING)
    private Role role;

    public boolean isAdmin() {
        return this.role == Role.SUB_ADMIN;
    }

    public static Position createPosition(String positionName, Company company, Integer positionLevel, Role role) {
        Position position = new Position();
        position.setPositionName(positionName); 
        position.setCompany(company);
        position.setRole(role);
        position.setPositionLevel(positionLevel);
        return position;
    }

    public void update(String positionName, Integer positionLevel, Role role) {
        this.positionName = positionName;
        this.positionLevel = positionLevel;
        this.role = role;
    }
    
}