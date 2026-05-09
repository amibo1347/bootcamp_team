    package com.team.intranet.session;

    import com.team.intranet.entity.Member;
    import com.team.intranet.entity.Company;
    import com.team.intranet.enums.member.Role;
    import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;

    @Getter
    @AllArgsConstructor
    public class MemberSession implements Serializable {
        private static final long serialVersionUID = 1L; // 직렬화 버전 체크용

        private final Long memberId;        // DB 식별자
        private final String loginId;       // 로그인 아이디
        private final String name;          // 사용자 이름
        private final String email;         // 사용자 이메일
        private final Role role;            // 권한
        private final Long companyId;       // 기업 id
        private final String companyName;   // 기업 이름
        private final String positionName;   // 직급 이름
        private final Long positionId;       // 직급 id
        private final Integer positionLevel;    // 직급 레벨 (추가)
        private final Long deptId;          // 부서 id (추가)

        // 엔티티를 세션 객체로 변환하는 생성자
        public MemberSession(Member member) {
            this.memberId = member.getMemberId();
            this.loginId = member.getLoginId();
            this.name = member.getName();
            this.email = member.getEmail();
            this.role = member.getRole();
            this.companyId = member.getCompany().getCompanyId();
            this.companyName = member.getCompany().getCompanyName();
            this.positionId = member.getPosition().getPositionId();
            this.positionName = member.getPosition().getPositionName();
            this.positionLevel = member.getPosition().getPositionLevel();
            this.deptId = member.getDept() != null ? member.getDept().getDeptId() : null;
        }

    public boolean isAdmin() {
         return role == Role.ADMIN || role == Role.MASTER || role == Role.SUB_ADMIN;
    }

    
}