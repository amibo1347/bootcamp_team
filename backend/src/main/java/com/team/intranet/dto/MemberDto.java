package com.team.intranet.dto;

import java.time.LocalDateTime;

import com.team.intranet.entity.Company;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.Position;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.Status;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberDto {
//////////////////////////////
/// 변수
   private Long memberId;
   private String loginId;
   private String password;
   private String passwordCheck;    
   private String email;    
   private LocalDateTime createdAt; 
   private LocalDateTime acceptedAt;
   private String name;
   private LocalDateTime birthDay;
   private Role role;
   private String phone;
   private Status status;
   private String companyCode; 
    private Long companyId;
   private Dept dept;
   private Position position;

//////////////////////////////////
/// 함수

   // 저장
   public Member toEntity(){
      return new Member(null, this.loginId, this.password, this.email, LocalDateTime.now(), this.acceptedAt, this.name, this.birthDay, Role.USER, this.phone, Status.WAIT, null, null, null);
   }

   // 비밀번호 비교
   public boolean is_Password_Match() {
    if (password == null || passwordCheck == null) {
        return false;
    }
    return password.equals(passwordCheck);
}

   
}
