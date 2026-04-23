package com.team.intranet.dto.auth;

import com.team.intranet.dto.MemberDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignupRequest(
        @NotBlank String loginId,
        @NotBlank String password,
        @NotBlank String passwordCheck,
        @NotBlank String name,
        @Email String email,
        String phone,
        @NotBlank String companyCode
) {
    public MemberDto toMemberDto() {
        MemberDto dto = new MemberDto();
        dto.setLoginId(loginId);
        dto.setPassword(password);
        dto.setPasswordCheck(passwordCheck);
        dto.setName(name);
        dto.setEmail(email);
        dto.setPhone(phone);
        dto.setCompanyCode(companyCode);
        return dto;
    }
}
