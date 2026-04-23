package com.team.intranet.dto.auth;

import com.team.intranet.entity.Member;
import com.team.intranet.enums.member.Role;

public record LoginResponse(
        String token,
        long expiresIn,
        UserInfo user
) {
    public record UserInfo(
            Long memberId,
            String loginId,
            String name,
            Role role,
            Long companyId,
            String companyName
    ) {
        public static UserInfo of(Member member) {
            return new UserInfo(
                    member.getMemberId(),
                    member.getLoginId(),
                    member.getName(),
                    member.getRole(),
                    member.getCompany().getCompanyId(),
                    member.getCompany().getCompanyName()
            );
        }
    }
}
