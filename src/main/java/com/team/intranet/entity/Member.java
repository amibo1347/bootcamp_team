package com.team.intranet.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.team.intranet.dto.MemberDto;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.Status;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tbl_member")
@Getter
@Setter
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

    @Column(name = "accepted_at", updatable = true)
    private LocalDateTime acceptedAt; // 승인 날짜

    @Column(name = "name")
    private String name;

    @Column(name = "birth_day")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMdd", timezone = "Asia/Seoul")
    private LocalDateTime birthDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne
    @JoinColumn(name = "dept_id")
    private Dept dept;

    @ManyToOne
    @JoinColumn(name = "position_id")
    private Position position;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "profile_img")
    private byte[] profileImg;

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
        member.profileImg = dto.getProfileImg();
        member.company = company;
        member.status = Status.WAIT;
        member.role = Role.USER;
        member.createdAt = LocalDateTime.now();
        member.dept = dto.getDept();
        member.position = dto.getPosition();
        member.birthDay = dto.getFullBirthDate();
        return member;
    }

    // 가입 승인
    public void accept(Dept dept, Position position) {
        this.status = Status.JOIN;
        this.dept = dept;
        this.position = position;
        this.acceptedAt = LocalDateTime.now();
        this.role = position.getRole();
    }

    // 기존 회원 정보 변경
    public void updateInfo(Dept dept, Position position, byte[] profileImg, String phone, String email, String name, LocalDateTime birthDay) {
        this.phone = phone;
        this.email = email;
        this.name = name;
        this.birthDay = birthDay;
        if (profileImg != null) {
            this.profileImg = profileImg;
        }
        this.dept = dept;
        this.position = position;
        this.role = position.getRole();
    }

    public void reject() {
        this.status = Status.REJECT;
    }

    public void fire() {
        this.status = Status.LEAVE;
    }

}
