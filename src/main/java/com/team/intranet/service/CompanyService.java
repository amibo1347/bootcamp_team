package com.team.intranet.service;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.entity.Company;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.Position;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.Status;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.DeptRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.repository.PositionRepository;

import lombok.RequiredArgsConstructor;

/**
 * MASTER 의 회사 관리 — 목록/검색, 생성(초기 ADMIN 동시), 정보 수정, 코드 재발급, 활성 토글.
 *  - 회사 생성은 Company + ADMIN 직급 + 기본 부서 + ADMIN 회원을 한 트랜잭션으로 만든다.
 *  - 회사 영구 삭제는 제공하지 않는다 (비활성으로만).
 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    /** 회사 코드 문자셋 — 혼동되는 0/O/1/I 등 제외. */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 임시 비밀번호 문자셋 — 혼동되는 0/O/1/l/I 등 제외. 대/소문자 + 숫자. */
    private static final String TEMP_PW_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int TEMP_PW_LENGTH = 10;

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final PositionRepository positionRepository;
    private final DeptRepository deptRepository;
    private final PasswordEncoder passwordEncoder;

    /** 회사 목록. keyword 가 있으면 회사명 부분 일치 검색. */
    @Transactional(readOnly = true)
    public List<Company> list(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return companyRepository.findAll(Sort.by(Sort.Direction.ASC, "companyId"));
        }
        return companyRepository.findByCompanyNameContainingIgnoreCaseOrderByCompanyIdAsc(keyword.trim());
    }

    @Transactional(readOnly = true)
    public Company get(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("회사를 찾을 수 없습니다."));
    }

    /**
     * 회사 생성 + 초기 ADMIN(대표) 계정을 한 트랜잭션으로 만든다.
     *  - Company + ADMIN 직급(Position) + 기본 부서(Dept) + ADMIN 회원(Member, 즉시 JOIN).
     *  - 대표 초기 비밀번호는 서버가 자동 생성한다 (MASTER 직접 입력 X).
     * @return 생성된 대표 초기 비밀번호(평문) — MASTER 가 대표에게 전달하는 용도.
     * @throws IllegalArgumentException ADMIN 아이디가 이미 사용 중인 경우
     */
    @Transactional
    public String create(String companyName, String companyDomain, boolean usesEmployeeNo,
                          String adminLoginId, String adminName, String adminEmail) {
        String loginId = adminLoginId.trim();
        // 도메인은 /{companyDomain}/login 로그인 URL 의 키이므로 필수 + 전역 유니크.
        String domain = normalizeDomain(companyDomain);
        if (companyRepository.existsByCompanyDomainIgnoreCase(domain)) {
            throw new IllegalArgumentException("이미 사용 중인 도메인입니다: " + domain);
        }
        // 신규 회사에는 회원이 없으므로 대표 아이디는 회사 안에서 항상 유니크 — 별도 중복 검사 불필요.

        Company company = Company.create(companyName.trim(), generateUniqueCode(), domain, usesEmployeeNo);
        companyRepository.save(company);

        Position adminPosition = Position.createPosition("대표", company, 1, Role.ADMIN);
        positionRepository.save(adminPosition);

        Dept defaultDept = Dept.createDept("경영지원팀", null, company);
        deptRepository.save(defaultDept);

        // 대표 초기 비밀번호는 서버가 자동 생성 — 비밀번호 초기화 기능과 동일한 방식.
        String tempPassword = generateTempPassword();
        Member admin = Member.createCompanyAdmin(loginId, passwordEncoder.encode(tempPassword),
                adminName.trim(), blankToNull(adminEmail), company, defaultDept, adminPosition);
        memberRepository.save(admin);
        return tempPassword;
    }

    /** 회사 정보(이름/도메인/사번제 여부) 수정. */
    @Transactional
    public void updateInfo(Long companyId, String companyName, String companyDomain, boolean usesEmployeeNo) {
        Company company = get(companyId);
        String domain = normalizeDomain(companyDomain);
        if (companyRepository.existsByCompanyDomainIgnoreCaseAndCompanyIdNot(domain, companyId)) {
            throw new IllegalArgumentException("이미 사용 중인 도메인입니다: " + domain);
        }
        company.updateInfo(companyName.trim(), domain, usesEmployeeNo);
        companyRepository.save(company);
    }

    /** 도메인으로 회사 조회 — 회사별 로그인 페이지 진입용. 없으면 null. */
    @Transactional(readOnly = true)
    public Company findByDomain(String companyDomain) {
        if (companyDomain == null || companyDomain.isBlank()) return null;
        return companyRepository.findByCompanyDomainIgnoreCase(companyDomain.trim()).orElse(null);
    }

    /** /{companyDomain}/login 경로와 충돌하면 안 되는 예약어. */
    private static final java.util.Set<String> RESERVED_DOMAINS = java.util.Set.of(
            "member", "master", "api", "admin", "subadmin", "css", "js", "images",
            "uploads", "error", "index", "calendar", "profile", "settings");

    /**
     * 도메인 정규화 + 검증.
     *  - 필수, 영문/숫자/.-_ 만, 2~40자, 예약어 금지.
     * @throws IllegalArgumentException 형식이 맞지 않으면
     */
    private static String normalizeDomain(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("회사 도메인을 입력하세요. (회사별 로그인 주소로 사용됩니다)");
        }
        String domain = raw.trim();
        if (!domain.matches("^[A-Za-z0-9._-]{2,40}$")) {
            throw new IllegalArgumentException("도메인은 영문/숫자/.-_ 조합 2~40자로 입력하세요.");
        }
        if (RESERVED_DOMAINS.contains(domain.toLowerCase())) {
            throw new IllegalArgumentException("사용할 수 없는 도메인입니다: " + domain);
        }
        return domain;
    }

    /** 로고 이미지 교체. */
    @Transactional
    public void updateLogo(Long companyId, byte[] logo) {
        Company company = get(companyId);
        company.updateLogo(logo);
        companyRepository.save(company);
    }

    /** 로고 이미지 삭제. */
    @Transactional
    public void clearLogo(Long companyId) {
        Company company = get(companyId);
        company.clearLogo();
        companyRepository.save(company);
    }

    /** 로고 BLOB 조회 (이미지 응답용). 없으면 null. */
    @Transactional(readOnly = true)
    public byte[] getLogo(Long companyId) {
        return get(companyId).getLogo();
    }

    /** 로고 보유 여부 (BLOB 미로딩). */
    @Transactional(readOnly = true)
    public boolean hasLogo(Long companyId) {
        return companyRepository.existsByCompanyIdAndLogoIsNotNull(companyId);
    }

    /** 회사 코드 재발급. 새 코드를 반환. */
    @Transactional
    public String reissueCode(Long companyId) {
        Company company = get(companyId);
        String code = generateUniqueCode();
        company.reissueCode(code);
        companyRepository.save(company);
        return code;
    }

    /** 회사 대표(ADMIN) 계정. 없으면 null. 여러 명이면 회사 생성 시 만든 원 대표(memberId 최소). */
    @Transactional(readOnly = true)
    public Member findCompanyAdmin(Long companyId) {
        return memberRepository
                .findByCompany_CompanyIdAndRoleOrderByMemberIdAsc(companyId, Role.ADMIN)
                .stream().findFirst().orElse(null);
    }

    /**
     * 회사 대표(ADMIN) 비밀번호 초기화.
     *  - 임시 비밀번호를 새로 발급해 BCrypt 로 저장하고, 평문 임시 비밀번호를 반환한다.
     *  - MASTER 가 발급된 임시 비밀번호를 대표에게 전달하는 용도.
     * @throws IllegalArgumentException 회사가 없거나 대표(ADMIN) 계정이 없는 경우
     */
    @Transactional
    public AdminPasswordReset resetAdminPassword(Long companyId) {
        Company company = get(companyId); // 회사 존재 검증
        Member admin = findCompanyAdmin(companyId);
        if (admin == null) {
            throw new IllegalArgumentException("이 회사의 대표(ADMIN) 계정을 찾을 수 없습니다.");
        }
        String tempPassword = generateTempPassword();
        admin.setPassword(passwordEncoder.encode(tempPassword));
        memberRepository.save(admin);
        return new AdminPasswordReset(admin.getLoginId(), tempPassword, company.getCompanyName());
    }

    /** 대표 비밀번호 초기화 결과 — 화면 안내용 (로그인 아이디 + 평문 임시 비밀번호 + 회사명). */
    public record AdminPasswordReset(String loginId, String tempPassword, String companyName) {}

    /** 대표 위임 후보 — 회사의 재직(JOIN) 회원 중 현재 대표를 제외, 이름순. */
    @Transactional(readOnly = true)
    public List<Member> listDelegationCandidates(Long companyId) {
        Member current = findCompanyAdmin(companyId);
        Long currentId = current != null ? current.getMemberId() : null;
        return memberRepository.findByStatusAndCompanyCompanyId(Status.JOIN, companyId).stream()
                .filter(m -> currentId == null || !m.getMemberId().equals(currentId))
                .sorted(Comparator.comparing(Member::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    /**
     * 회사 대표(ADMIN) 위임 — 같은 회사의 다른 재직 회원에게 대표직을 넘긴다.
     *  - 신임 대표: Role.ADMIN + 기존 대표가 보유하던 "대표" 직급을 넘겨받음.
     *  - 기존 대표: 일반 직원(Role.USER) 으로 강등 — 직급 해제 + 예외 권한 정리.
     *  - 권한 스냅샷(MemberSession)은 다음 로그인 시 반영된다.
     * @throws IllegalArgumentException 회사·대상 회원이 없거나, 대상이 타 회사/비재직/이미 대표인 경우
     */
    @Transactional
    public AdminDelegation delegateAdmin(Long companyId, Long newAdminMemberId) {
        get(companyId); // 회사 존재 검증
        Member current = findCompanyAdmin(companyId);
        if (current == null) {
            throw new IllegalArgumentException("이 회사의 대표(ADMIN) 계정을 찾을 수 없습니다.");
        }
        Member next = memberRepository.findById(newAdminMemberId)
                .orElseThrow(() -> new IllegalArgumentException("위임 대상 회원을 찾을 수 없습니다."));
        if (next.getCompany() == null || !next.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("위임 대상이 이 회사 소속이 아닙니다.");
        }
        if (next.getMemberId().equals(current.getMemberId())) {
            throw new IllegalArgumentException("이미 대표인 회원입니다.");
        }
        if (next.getStatus() != Status.JOIN) {
            throw new IllegalArgumentException("재직 중인 회원에게만 대표직을 위임할 수 있습니다.");
        }

        Position adminPosition = current.getPosition(); // 기존 대표의 "대표" 직급 (null 일 수 있음)

        // 기존 대표 → 일반 직원(USER): 직급 해제 + 예외 권한 정리.
        current.setRole(Role.USER);
        current.setPosition(null);
        current.replaceExtraPermissions(Set.of());

        // 신임 대표 → ADMIN: "대표" 직급을 넘겨받음.
        next.setPosition(adminPosition);
        next.setRole(Role.ADMIN);

        memberRepository.save(current);
        memberRepository.save(next);

        return new AdminDelegation(current.getLoginId(), next.getLoginId(), next.getName());
    }

    /** 대표 위임 결과 — 화면 안내용. */
    public record AdminDelegation(String oldAdminLoginId, String newAdminLoginId, String newAdminName) {}

    /** 활성/비활성 토글. 토글 후 활성 여부를 반환. */
    @Transactional
    public boolean toggleActive(Long companyId) {
        Company company = get(companyId);
        if (company.isActiveCompany()) {
            company.deactivate();
        } else {
            company.activate();
        }
        companyRepository.save(company);
        return company.isActiveCompany();
    }

    /** 중복되지 않는 회사 코드 생성. */
    private String generateUniqueCode() {
        for (int i = 0; i < 50; i++) {
            String code = randomCode();
            if (!companyRepository.existsByCompanyCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("회사 코드 생성에 실패했습니다. 다시 시도하세요.");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    /** 임시 비밀번호 생성 — 대/소문자 + 숫자 10자리. */
    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PW_LENGTH);
        for (int i = 0; i < TEMP_PW_LENGTH; i++) {
            sb.append(TEMP_PW_CHARS.charAt(RANDOM.nextInt(TEMP_PW_CHARS.length())));
        }
        return sb.toString();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
