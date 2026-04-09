package com.team.intranet.session;

import com.team.intranet.entity.Member;
import com.team.intranet.enums.member.Role;
import java.io.Serializable;
import lombok.Getter;

@Getter
public class MemberSession implements Serializable {
    private static final long serialVersionUID = 1L; // 직렬화 버전 체크용

    private final Long memberId;    // DB 식별자
    private final String loginId;   // 로그인 아이디
    private final String name;      // 사용자 이름
    private final Role role;        // 권한

    // 엔티티를 세션 객체로 변환하는 생성자
    public MemberSession(Member member) {
        this.memberId = member.getMemberId();
        this.loginId = member.getLoginId();
        this.name = member.getName();
        this.role = member.getRole();
    }
}