package com.team.intranet.session;

import java.io.Serializable;

import com.team.intranet.entity.MasterAdmin;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 로그인한 MASTER 의 세션 스냅샷.
 *  - MemberSession 과 별개. MASTER 는 어떤 회사에도 소속되지 않으므로 회사/부서/직급 필드가 없다.
 *  - HttpSession 에 "masterSession" 키로 저장된다 (MasterLoginSuccessHandler).
 */
@Getter
@AllArgsConstructor
public class MasterSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Long masterAdminId;
    private final String loginId;
    private final String name;
    private final String email;

    /** 엔티티 → 세션 객체 변환. */
    public MasterSession(MasterAdmin master) {
        this.masterAdminId = master.getMasterAdminId();
        this.loginId = master.getLoginId();
        this.name = master.getName();
        this.email = master.getEmail();
    }
}
