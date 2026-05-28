package com.team.intranet.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import java.util.EnumSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.team.intranet.dto.MemberDto;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.Status;
import com.team.intranet.enums.member.SubAdminPermission;

import jakarta.persistence.Basic;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
// loginId 는 전역이 아니라 '회사 단위'로만 유니크하다 (회사가 다르면 같은 사번/아이디 허용).
@Table(name = "tbl_member",
       uniqueConstraints = @UniqueConstraint(name = "uk_member_company_login",
                                             columnNames = {"company_id", "login_id"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    // FetchType.EAGER 명시 — LoginSuccessHandler 가 트랜잭션 밖에서 회사/부서/직급에 접근하므로 EAGER 가 필요.
    // 기본값(EAGER) 의존 대신 명시해서 의도를 코드로 드러낸다.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dept_id")
    private Dept dept;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "position_id")
    private Position position;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "profile_img")
    private byte[] profileImg;

    /**
     * 회원별 예외 권한 (additive).
     *  - 실효 권한 = position.permissions ∪ member.extraPermissions.
     *  - 직급 권한에 더해 이 회원에게만 특별히 부여하는 권한을 보관한다.
     *  - 차감(빼기)은 지원하지 않는다. 차감이 필요하면 직급을 옮기는 것이 의도가 명확.
     *  - LoginSuccessHandler 가 트랜잭션 밖이므로 EAGER. 회원당 enum 6개 이하라 비용 미미.
     */
    @ElementCollection(targetClass = SubAdminPermission.class, fetch = FetchType.EAGER)
    @CollectionTable(
        name = "tbl_member_extra_permission",
        joinColumns = @JoinColumn(name = "member_id")
    )
    @Column(name = "permission", length = 50)
    @Enumerated(EnumType.STRING)
    private Set<SubAdminPermission> extraPermissions = EnumSet.noneOf(SubAdminPermission.class);

    /* ── 휴직 정보 (status = ON_LEAVE 일 때만 의미) ───────────────────────
     *  - leaveReason: 사유 (자유 텍스트 200자)
     *  - leaveStartDate: 휴직 시작일 (포함)
     *  - leaveExpectedReturnDate: 복귀 예정일 (이 날부터 복귀, 즉 이 날 -1 까지 휴직으로 표시)
     *  - leaveExtended: 복귀일을 한 번이라도 수정(연장)한 적이 있는지 — UI '연장' 배지용
     *  복귀(reinstate) 시 4개 모두 null/false 로 초기화.
     */
    @Column(name = "leave_reason", length = 200)
    private String leaveReason;

    @Column(name = "leave_start_date")
    private LocalDate leaveStartDate;

    @Column(name = "leave_expected_return_date")
    private LocalDate leaveExpectedReturnDate;

    // Oracle: NUMBER(1) DEFAULT 0 NOT NULL — ddl-auto=update 가 기존 row 가 있는 테이블에
    // NOT NULL 컬럼을 추가할 때 default 가 없으면 ALTER 실패. default 0 으로 안전 추가.
    @Column(name = "leave_extended", nullable = false, columnDefinition = "NUMBER(1) DEFAULT 0 NOT NULL")
    private boolean leaveExtended;

    /** 회원별 예외 권한 일괄 교체. 빈 집합으로 호출하면 모든 예외 권한 해제. */
    public void replaceExtraPermissions(Set<SubAdminPermission> newPermissions) {
        if (this.extraPermissions == null) {
            this.extraPermissions = EnumSet.noneOf(SubAdminPermission.class);
        }
        this.extraPermissions.clear();
        if (newPermissions != null && !newPermissions.isEmpty()) {
            this.extraPermissions.addAll(newPermissions);
        }
    }

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
        member.statusChangedAt = LocalDateTime.now();
        member.role = Role.USER;
        member.createdAt = LocalDateTime.now();
        member.dept = dto.getDept();
        member.position = dto.getPosition();
        member.birthDay = dto.getFullBirthDate();
        return member;
    }

    // MASTER 가 회사 생성 시 함께 만드는 초기 ADMIN(대표) 회원 — 승인 절차 없이 즉시 JOIN.
    public static Member createCompanyAdmin(String loginId, String encodedPassword, String name,
                                            String email, Company company, Dept dept, Position position) {
        Member member = new Member();
        member.loginId = loginId;
        member.password = encodedPassword;
        member.name = name;
        member.email = email;
        member.company = company;
        member.dept = dept;
        member.position = position;
        member.role = Role.ADMIN;
        member.status = Status.JOIN;
        member.createdAt = LocalDateTime.now();
        member.acceptedAt = LocalDateTime.now();
        member.statusChangedAt = LocalDateTime.now();
        return member;
    }

    // 가입 승인
    public void accept(Dept dept, Position position) {
        this.status = Status.JOIN;
        this.statusChangedAt = LocalDateTime.now();
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

    // 본인 자기수정 (내 프로필 페이지): 이름/이메일/전화/생년월일만 변경.
    // ※ 부서/직급/역할/프로필 사진은 별도 경로로만 변경 (관리자 권한 또는 /api/member/me/profileImg).
    public void updateSelfInfo(String name, String email, String phone, LocalDateTime birthDay) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.birthDay = birthDay;
    }

    // 비밀번호 변경 — 호출자가 이미 PasswordEncoder 로 인코딩한 값을 전달해야 한다.
    // ※ MASTER 의 MasterAdmin#changePassword 와 동일 패턴 (도메인 객체 안에서 단일 책임).
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    // 인사이동: 부서/직급만 일괄 변경. dept 또는 position 이 null 이면 해당 항목은 유지한다.
    public void reassign(Dept dept, Position position) {
        if (dept != null) {
            this.dept = dept;
        }
        if (position != null) {
            this.position = position;
            this.role = position.getRole();
        }
    }

    public void reject() {
        this.status = Status.REJECT;
        this.statusChangedAt = LocalDateTime.now();
    }

    public void fire() {
        this.status = Status.BANNED;
        this.statusChangedAt = LocalDateTime.now();
    }

    public void leave() {
        this.status = Status.LEAVE;
        this.statusChangedAt = LocalDateTime.now();
    }

    public void onLeave(){
        this.status = Status.ON_LEAVE;
        this.statusChangedAt = LocalDateTime.now();
    }

    /**
     * 사유 + 기간이 있는 휴직 등록.
     *  - leaveExtended 는 false 로 초기화 (이전 휴직 기록과 무관).
     *  - 호출 측에서 startDate/expectedReturnDate null 가드 + 순서 검증을 한다.
     */
    public void putOnLeave(String reason, LocalDate startDate, LocalDate expectedReturnDate) {
        this.status = Status.ON_LEAVE;
        this.statusChangedAt = LocalDateTime.now();
        this.leaveReason = reason;
        this.leaveStartDate = startDate;
        this.leaveExpectedReturnDate = expectedReturnDate;
        this.leaveExtended = false;
    }

    /**
     * 휴직 복귀일 수정(연장 또는 단축). status 는 ON_LEAVE 유지.
     *  - 한 번이라도 호출되면 leaveExtended=true.
     */
    public void updateLeaveReturnDate(LocalDate newReturnDate) {
        this.leaveExpectedReturnDate = newReturnDate;
        this.leaveExtended = true;
    }

    // 휴직 -> 재직 복귀 (dept/position 은 휴직 시점 그대로 유지)
    public void reinstate(){
        this.status = Status.JOIN;
        this.statusChangedAt = LocalDateTime.now();
        // 휴직 정보 초기화 — 다음 휴직 시점에 다시 채워짐.
        this.leaveReason = null;
        this.leaveStartDate = null;
        this.leaveExpectedReturnDate = null;
        this.leaveExtended = false;
    }

    // LEAVE/BANNED 전이 직후 호출. 이름/전화/이메일은 보존(퇴사 후 문의 대응),
    // 그 외 PII는 비우고 비밀번호는 garbage 해시로 덮어 로그인 차단.
    public void anonymizePii(String garbagePassword) {
        this.password = garbagePassword;
        this.birthDay = null;
        this.role = null;
        this.dept = null;
        this.position = null;
        this.profileImg = null;
    }

}
