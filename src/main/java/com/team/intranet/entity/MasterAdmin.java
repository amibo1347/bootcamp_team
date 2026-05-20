package com.team.intranet.entity;

import java.time.LocalDateTime;

import com.team.intranet.enums.IsActive;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MASTER 계정 — SaaS 운영자.
 *  - 어떤 회사에도 소속되지 않는다. Member 와 분리한 이유: Member.company 는 NOT NULL 이라
 *    MASTER 를 Member 로 만들면 가짜 회사가 필요해진다.
 *  - 회사 생성/비활성, 회사 ADMIN 관리 등 전사(全社) 운영 기능의 주체.
 *  - 인증은 일반 회원(form-login)과 분리된 경로(/master/login)로 처리한다 — 후속 PR.
 */
@Entity
@Table(name = "tbl_master_admin")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MasterAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "master_admin_id")
    private Long masterAdminId;

    /** 로그인 아이디 — 전체 MASTER 계정 중 유일. */
    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    /** BCrypt 해시. 평문 저장 금지. */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "email", length = 100)
    private String email;

    /** 계정 활성 여부. N 이면 로그인 차단 — 인증 단계 반영은 후속 PR. */
    @Enumerated(EnumType.STRING)
    @Column(name = "is_active", nullable = false, length = 1)
    private IsActive isActive;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 마지막 로그인 성공 시각. TOTP 2차 인증까지 통과한 시점에 갱신. */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** TOTP(인증기 앱) 시크릿. null 이면 2차 인증 미등록 → 최초 로그인 시 등록을 강제한다. */
    @Column(name = "totp_secret", length = 64)
    private String totpSecret;

    /**
     * 신규 MASTER 계정 생성.
     * @param encodedPassword 반드시 BCrypt 등으로 인코딩된 해시여야 한다 (평문 금지).
     */
    public static MasterAdmin create(String loginId, String encodedPassword, String name, String email) {
        return MasterAdmin.builder()
            .loginId(loginId)
            .password(encodedPassword)
            .name(name)
            .email(email)
            .isActive(IsActive.Y)
            .createdAt(LocalDateTime.now())
            .build();
    }

    /** 비밀번호 변경. 인자는 인코딩된 해시여야 한다. */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /** 로그인 성공 시각 기록. */
    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    /** TOTP 2차 인증 등록 (인증기 앱 시크릿 저장). */
    public void enrollTotp(String secret) {
        this.totpSecret = secret;
    }

    /** TOTP 2차 인증이 등록되어 있는지. */
    public boolean hasTotp() {
        return totpSecret != null && !totpSecret.isBlank();
    }

    /** 프로필(이름·이메일) 수정. */
    public void updateProfile(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public void activate() {
        this.isActive = IsActive.Y;
    }

    public void deactivate() {
        this.isActive = IsActive.N;
    }
}
