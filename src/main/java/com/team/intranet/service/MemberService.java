package com.team.intranet.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.team.intranet.dto.MemberDto;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.Position;
import com.team.intranet.enums.member.MemberType;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.Status;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.DeptRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.repository.PositionRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;
    private final DeptRepository deptRepository;
    private final PositionRepository positionRepository;
    private final PasswordEncoder passwordEncoder;

    // 회원가입 
    @Transactional
    public MemberType join(MemberDto dto) {
        Optional<Member> foundMember = memberRepository.findByLoginId(dto.getLoginId());

        if (foundMember.isPresent()) {
            return MemberType.ALEADY_MEMBER;
        }

        if (!dto.is_Password_Match()) {
            return MemberType.NOT_MATCH_PASSWORD;
        }

        // 기업 코드 보안 검증
        if (dto.getCompanyCode() == null || dto.getCompanyCode().isEmpty()) {
            return MemberType.NOT_COMPANY;
        }
        System.out.println("전달받은 기업 코드: [" + dto.getCompanyCode() + "]");
        Company company = companyRepository.findByCompanyCodeIgnoreCase(dto.getCompanyCode()).orElse(null);
        System.out.println("DB 조회 결과: " + (company == null ? "찾지 못함" : "찾음 - " + company.getCompanyName()));
        if (company == null) {
            return MemberType.NOT_COMPANY;
        }

        String encodedPassword = passwordEncoder.encode(dto.getPassword());
        Member member = Member.createPendingMember(dto, encodedPassword, company);
        memberRepository.save(member);

        return MemberType.JOIN_SUCCESS;
    }

    // 가입 승인
    @Transactional
    public void acceptMember(Long memberId, String adminLoginId, Long deptId, Long positionId) {
        Member admin = memberRepository.findByLoginId(adminLoginId)
                .orElseThrow(() -> new IllegalArgumentException("관리자 정보가 없습니다."));

        if (admin.getRole() != Role.ADMIN) {
            throw new RuntimeException("승인 권한이 없습니다");
        }

        Member targetMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원이 없습니다"));

        Dept dept = deptRepository.findById(deptId)
                .orElseThrow(() -> new IllegalArgumentException("해당 부서가 없습니다"));

        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 직급이 없습니다"));

        if (targetMember.getStatus() == Status.WAIT) {
            targetMember.accept(dept, position);
        }
    }

    // 로그인
    @Transactional
    public Member login(String loginId, String password) {
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이디입니다."));

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        if (member.getStatus() == Status.WAIT) {
            throw new RuntimeException("관리자의 승인을 기다리는 중입니다.");
        }

        return member;
    }

    // 아이디 중복 확인
    public boolean isDuplicateId(String loginId) {
        return memberRepository.existsByLoginId(loginId);
    }

    // 기업 코드 인증
    public Long getVerifyCompanyId(String companyCode) {
        return companyRepository.findByCompanyCodeIgnoreCase(companyCode)
                .map(Company::getCompanyId)
                .orElse(null);
    }
}
