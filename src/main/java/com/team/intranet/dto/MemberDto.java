package com.team.intranet.dto;

import java.time.LocalDate;
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
   private Integer birthYear;
   private Integer birthMonth;
   private Integer birthDay;
   private Role role;
   private String phone;
   private Status status;
   private String companyCode;
   private Long companyId;
   private Dept dept;
   private Position position;
   private byte[] profileImg;

   //////////////////////////////////
   /// 함수

   // 생년월일을 LocalDateTime으로 반환하는 메서드
   public LocalDateTime getFullBirthDate() {
        return LocalDateTime.of(birthYear, birthMonth, birthDay, 0, 0);
    }
   // 저장
   //  ※ Member 의 마지막 인자 4개는 휴직 정보(reason/start/expectedReturn/extended) — 신규 가입은 null/false.
   public Member toEntity() {
      return new Member(null, this.loginId, this.password, this.email, LocalDateTime.now(), this.acceptedAt, this.name,
            getFullBirthDate(), Role.USER, this.phone, Status.WAIT, LocalDateTime.now(), null, null, null, null,
            java.util.EnumSet.noneOf(com.team.intranet.enums.member.SubAdminPermission.class),
            null, null, null, false);
   }

   // 비밀번호 비교
   public boolean is_Password_Match() {
      if (password == null || passwordCheck == null) {
         return false;
      }
      return password.equals(passwordCheck);
   }
}
