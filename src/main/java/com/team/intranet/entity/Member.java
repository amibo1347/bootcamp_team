package com.team.intranet.entity;

import java.time.LocalDateTime;

import com.team.intranet.dto.MemberDto;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.Status;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tbl_member")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Member {

//////////////////////////////////
/// 컬럼
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "login_id")
    private String loginId;

    @Column(name = "password")
    private String password;

    @Column(name = "email")
    private String email;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt; // 승인 날짜
    @Column(name = "name")
    private String name;

    @Column(name = "birth_day")
    private LocalDateTime birthDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne
    @JoinColumn(name = "dept_id")
    private Dept dept;

    @ManyToOne
    @JoinColumn(name = "position_id")
    private Position position;

///////////////////////////////
/// 함수

     // 승인 대기 상태의 회원 생성
    public static Member createPendingMember(MemberDto dto, String encodedPassword, Company company) {
        Member member = new Member();
        member.loginId = dto.getLoginId();
        member.password = encodedPassword;
        member.name = dto.getName();
        member.email = dto.getEmail();
        member.phone = dto.getPhone();
        member.company = company;
        member.status = Status.WAIT;
        member.role = Role.USER;
        member.createdAt = LocalDateTime.now();
        return member;
    }

    // 가입 승인
    public void accept(Dept dept, Position position) {
        this.status = Status.JOIN;
        this.dept = dept;
        this.position = position;
        this.acceptedAt = LocalDateTime.now(); 
    }

}
