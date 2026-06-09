package com.team.intranet.dto;

import java.util.EnumSet;
import java.util.Set;

import com.team.intranet.entity.Company;
import com.team.intranet.entity.Position;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.SubAdminPermission;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PositionDto {
    private Long positionId;
    private String positionName;
    private Company company;
    private Integer positionLevel;

    @JsonProperty("isAdmin")
    private boolean isAdmin;

    /**
     * 대표(Role.ADMIN) 직급 여부 — 화면 표시용. SUB_ADMIN("관리자") 과 구분해서 "회사 관리자" 뱃지로 렌더링.
     * ※ toEntity 에는 영향 없음. 대표 직급의 role 은 회사 가입 시점에 고정되며 일반 직급 CRUD 에서 토글되지 않는다.
     */
    @JsonProperty("isCompanyAdmin")
    private boolean isCompanyAdmin;

    /** SUB_ADMIN 인 경우의 세부 권한 집합. USER 직급이면 비어있다. */
    private Set<SubAdminPermission> permissions;

    /** 시스템 보호 직급 여부 — UI에서 삭제/레벨/역할 변경을 잠그는 데 사용. */
    @JsonProperty("isSystem")
    private boolean isSystem;

    public Position toEntity(){
        Role role = isAdmin ? Role.SUB_ADMIN : Role.USER;
        // EnumSet.copyOf(Collection) 은 빈 컬렉션을 받으면 IllegalArgumentException 을 던지므로 isEmpty 가드 필수.
        Set<SubAdminPermission> perms = (permissions == null || permissions.isEmpty())
            ? EnumSet.noneOf(SubAdminPermission.class)
            : EnumSet.copyOf(permissions);
        // 일반 직급 생성 경로 — isSystem 은 항상 false. 시스템 직급은 CompanyService 에서만 만든다.
        // 인자 순서는 Position 의 필드 선언 순서(@AllArgsConstructor) 와 일치해야 한다:
        //   positionId, positionName, company, positionLevel, role, isSystem, permissions
        return new Position(null, positionName, company, positionLevel, role, Boolean.FALSE, perms);
    }

    public static PositionDto fromEntity(Position position) {
        boolean isAdmin = position.getRole() == Role.SUB_ADMIN;
        boolean isCompanyAdmin = position.getRole() == Role.ADMIN;
        Set<SubAdminPermission> perms = (position.getPermissions() == null || position.getPermissions().isEmpty())
            ? EnumSet.noneOf(SubAdminPermission.class)
            : EnumSet.copyOf(position.getPermissions());
        return new PositionDto(
            position.getPositionId(),
            position.getPositionName(),
            position.getCompany(),
            position.getPositionLevel(),
            isAdmin,
            isCompanyAdmin,
            perms,
            position.isSystemDefault()
        );
    }
}
