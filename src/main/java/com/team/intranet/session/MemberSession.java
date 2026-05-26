    package com.team.intranet.session;

    import com.team.intranet.entity.Member;
    import com.team.intranet.entity.Company;
    import com.team.intranet.enums.member.Role;
    import com.team.intranet.enums.member.SubAdminPermission;
    import java.io.Serializable;
    import java.time.LocalDateTime;
    import java.util.EnumSet;
    import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;

    @Getter
    @AllArgsConstructor
    public class MemberSession implements Serializable {
        private static final long serialVersionUID = 4L; // 직렬화 버전 체크용 (usesEmployeeNo 필드 추가로 bump)

        private final Long memberId;        // DB 식별자
        private final String loginId;       // 로그인 아이디
        private final String name;          // 사용자 이름
        private final String email;         // 사용자 이메일
        private final String phone;         // 전화번호
        private final LocalDateTime birthDay; // 생년월일
        private final Role role;            // 권한
        private final Long companyId;       // 기업 id
        private final String companyName;   // 기업 이름
        private final String positionName;   // 직급 이름
        private final Long positionId;       // 직급 id
        private final Integer positionLevel;    // 직급 레벨 (추가)
        private final Long deptId;          // 부서 id (추가)
        private final String deptName;    // 부서명 (대시보드 등 표시용)
        private final boolean usesEmployeeNo; // 소속 회사가 사번제면 true — index/화면에 loginId(=사번) 노출 여부 결정
        /**
         * 실효 권한 스냅샷 = 직급 권한 ∪ 회원 예외 권한.
         *  - ADMIN/MASTER 는 hasPermission() 에서 자동 통과하므로 비어 있어도 됨.
         *  - 권한 변경은 다음 로그인 시 반영(운영 단순화).
         */
        private final Set<SubAdminPermission> permissions;

        // 엔티티를 세션 객체로 변환하는 생성자
        public MemberSession(Member member) {
            this.memberId = member.getMemberId();
            this.loginId = member.getLoginId();
            this.name = member.getName();
            this.email = member.getEmail();
            this.phone = member.getPhone();
            this.birthDay = member.getBirthDay();
            this.role = member.getRole();
            this.companyId = member.getCompany().getCompanyId();
            this.companyName = member.getCompany().getCompanyName();
            this.usesEmployeeNo = member.getCompany().usesEmployeeNo();
            // position/dept 는 미지정(null) 일 수 있음 — 신규 ADMIN 계정 등. NPE 없이 null 로 둔다.
            var position = member.getPosition();
            this.positionId = position != null ? position.getPositionId() : null;
            this.positionName = position != null ? position.getPositionName() : null;
            this.positionLevel = position != null ? position.getPositionLevel() : null;
            this.deptId = member.getDept() != null ? member.getDept().getDeptId() : null;
            this.deptName = member.getDept() != null ? member.getDept().getDeptName() : null;

            // 실효 권한: 직급 권한 ∪ 회원 예외 권한.
            EnumSet<SubAdminPermission> effective = EnumSet.noneOf(SubAdminPermission.class);
            Set<SubAdminPermission> positionPerms = position != null ? position.getPermissions() : null;
            if (positionPerms != null && !positionPerms.isEmpty()) {
                effective.addAll(positionPerms);
            }
            Set<SubAdminPermission> extraPerms = member.getExtraPermissions();
            if (extraPerms != null && !extraPerms.isEmpty()) {
                effective.addAll(extraPerms);
            }
            this.permissions = effective;
        }

    public boolean isAdmin() {
         return role == Role.ADMIN || role == Role.MASTER || role == Role.SUB_ADMIN;
    }

    /** 진짜 ADMIN 이상(ADMIN/MASTER) — SUB_ADMIN 은 배제. 양식 관리 같이 인트라넷 관리자 전용 화면용. */
    public boolean isAdminOrMaster() {
         return role == Role.ADMIN || role == Role.MASTER;
    }

    /** 기업 대표(ADMIN) 만 — MASTER/SUB_ADMIN/USER 모두 배제. 권한 관리 페이지처럼 ADMIN 단독 전용. */
    public boolean isCompanyAdmin() {
         return role == Role.ADMIN;
    }

    /**
     * SUB_ADMIN 세부 권한 체크. ADMIN/MASTER 는 무조건 통과.
     *  - USER 는 SUB_ADMIN 메뉴 자체가 사이드바에서 안 나오지만, 안전장치로 false 반환.
     */
    public boolean hasPermission(SubAdminPermission permission) {
        if (role == Role.ADMIN || role == Role.MASTER) return true;
        if (role != Role.SUB_ADMIN) return false;
        return permissions != null && permissions.contains(permission);
    }

    /**
     * 다른 회원을 편집/관리할 수 있는지 — 백엔드 MemberService.validateLevelHierarchy 와 동일 정책.
     * UI 가드(버튼 disabled) 와 서버 가드의 정책 일관성을 위해 한 곳에 모아둔다.
     *  통과:
     *   - 본인 자신
     *   - ADMIN / MASTER
     *   - MEMBER_MANAGEMENT 권한 보유자
     *   - target.level 이 내 level 보다 낮은 경우
     *   - 한쪽이라도 level 이 null 이면 (위계 미정)
     *  차단: target.level >= 내 level
     */
    public boolean canManageMember(Long targetMemberId, Integer targetLevel) {
        if (memberId != null && memberId.equals(targetMemberId)) return true;
        if (role == Role.ADMIN || role == Role.MASTER) return true;
        if (hasPermission(SubAdminPermission.MEMBER_MANAGEMENT)) return true;
        if (positionLevel == null || targetLevel == null) return true;
        return targetLevel < positionLevel;
    }

}