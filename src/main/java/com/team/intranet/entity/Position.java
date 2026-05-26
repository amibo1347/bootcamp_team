package com.team.intranet.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

import java.util.EnumSet;
import java.util.Set;

import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.SubAdminPermission;
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

    /**
     * 시스템 보호 직급 — 회사 생성 시 자동 생성된 "대표" 가 true.
     *  - 삭제 금지(서비스에서 차단).
     *  - level / role 변경 금지(이름은 회사가 자유롭게 바꿀 수 있다).
     *  - 회원 승인 시 직급 필수라 최소 1개 보장이 필요해 시스템이 책임진다.
     *  - DDL: 기존 row 는 null/false 로 들어오며 SystemDefaultMigration 이 마이그레이션.
     */
    @Column(name = "is_system")
    private Boolean isSystem;

    public boolean isSystemDefault() {
        return Boolean.TRUE.equals(isSystem);
    }

    /**
     * SUB_ADMIN 세부 권한 집합.
     *  - ADMIN/MASTER 는 이 집합과 무관하게 모든 권한 통과(애플리케이션 로직에서 분기).
     *  - USER 직급은 비어 있어야 함(권한 관리 페이지에서 SUB_ADMIN 직급만 편집 가능하도록 가드).
     *  - tbl_position_permission(position_id, permission) 별도 테이블로 매핑. ddl-auto=update 라 첫 기동 시 자동 생성.
     *  - FetchType.EAGER 이유: 로그인 성공 핸들러(LoginSuccessHandler)는 트랜잭션 밖이라 lazy 컬렉션에 접근 시
     *    LazyInitializationException 이 난다. 권한은 회사당 직급 수가 적고 enum 6개라 데이터 양이 미미하므로
     *    EAGER 로 두는 편이 안전하다.
     */
    @ElementCollection(targetClass = SubAdminPermission.class, fetch = FetchType.EAGER)
    @CollectionTable(
        name = "tbl_position_permission",
        joinColumns = @JoinColumn(name = "position_id")
    )
    @Column(name = "permission", length = 50)
    @Enumerated(EnumType.STRING)
    private Set<SubAdminPermission> permissions = EnumSet.noneOf(SubAdminPermission.class);

    public boolean isAdmin() {
        return this.role == Role.SUB_ADMIN;
    }

    public boolean hasPermission(SubAdminPermission permission) {
        return permissions != null && permissions.contains(permission);
    }

    public static Position createPosition(String positionName, Company company, Integer positionLevel, Role role) {
        Position position = new Position();
        position.setPositionName(positionName);
        position.setCompany(company);
        position.setRole(role);
        position.setPositionLevel(positionLevel);
        position.setPermissions(EnumSet.noneOf(SubAdminPermission.class));
        position.setIsSystem(Boolean.FALSE);
        return position;
    }

    /** 시스템 디폴트 직급 생성 — 회사 생성 시 "대표" 용. 삭제·의미 변경 차단 대상. */
    public static Position createSystemPosition(String positionName, Company company, Integer positionLevel, Role role) {
        Position position = createPosition(positionName, company, positionLevel, role);
        position.setIsSystem(Boolean.TRUE);
        return position;
    }

    /**
     * 직급 수정.
     *  - isSystem 직급은 level / role 을 그대로 유지하고 이름만 갱신한다.
     *    (서비스 단에서도 차단하지만 엔티티가 마지막 안전망.)
     */
    public void update(String positionName, Integer positionLevel, Role role) {
        this.positionName = positionName;
        if (!isSystemDefault()) {
            this.positionLevel = positionLevel;
            this.role = role;
            // SUB_ADMIN 이 아닌 직급으로 전환되면 기존 권한은 의미가 없으므로 비운다.
            if (role != Role.SUB_ADMIN && this.permissions != null) {
                this.permissions.clear();
            }
        }
    }

    /**
     * 권한 일괄 교체. 권한 관리 페이지의 [저장] 에서 사용.
     *  - SUB_ADMIN 이 아닌 직급에 권한을 부여하려는 시도는 호출 측(Service)에서 차단한다.
     *  - null 입력은 빈 집합으로 정규화.
     */
    public void replacePermissions(Set<SubAdminPermission> newPermissions) {
        if (this.permissions == null) {
            this.permissions = EnumSet.noneOf(SubAdminPermission.class);
        }
        this.permissions.clear();
        if (newPermissions != null && !newPermissions.isEmpty()) {
            this.permissions.addAll(newPermissions);
        }
    }

}
