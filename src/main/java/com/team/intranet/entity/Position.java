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
    private int positionLevel;

    @Column(name="role")
    @Enumerated(EnumType.STRING)
    private Role role;

    public boolean isAdmin() {
        return this.role == Role.SUB_ADMIN;
    }

    public static Position createPosition(String positionName, Company company) {
        Position position = new Position();
        position.setPositionName(positionName); 
        position.setCompany(company);
        position.setRole(Role.USER); // 기본적으로 일반 사용자 권한 부여
        position.setPositionLevel(1); // 기본 직급 레벨 설정
        return position;
    }

    
}