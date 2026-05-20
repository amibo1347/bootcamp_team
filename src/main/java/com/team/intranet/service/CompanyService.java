package com.team.intranet.service;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.entity.Company;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.Position;
import com.team.intranet.enums.member.Role;
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
     * @throws IllegalArgumentException ADMIN 아이디가 이미 사용 중인 경우
     */
    @Transactional
    public void create(String companyName, String companyDomain,
                       String adminLoginId, String adminName, String adminEmail, String adminRawPassword) {
        String loginId = adminLoginId.trim();
        if (memberRepository.existsByLoginId(loginId)) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다: " + loginId);
        }

        Company company = Company.create(companyName.trim(), generateUniqueCode(), blankToNull(companyDomain));
        companyRepository.save(company);

        Position adminPosition = Position.createPosition("대표", company, 1, Role.ADMIN);
        positionRepository.save(adminPosition);

        Dept defaultDept = Dept.createDept("경영지원팀", null, company);
        deptRepository.save(defaultDept);

        Member admin = Member.createCompanyAdmin(loginId, passwordEncoder.encode(adminRawPassword),
                adminName.trim(), blankToNull(adminEmail), company, defaultDept, adminPosition);
        memberRepository.save(admin);
    }

    /** 회사 정보(이름/도메인) 수정. */
    @Transactional
    public void updateInfo(Long companyId, String companyName, String companyDomain) {
        Company company = get(companyId);
        company.updateInfo(companyName.trim(), blankToNull(companyDomain));
        companyRepository.save(company);
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

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
