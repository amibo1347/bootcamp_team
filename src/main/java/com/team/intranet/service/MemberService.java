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

    //////////////////////////////////
/// 의존성 주입
    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;
    private final DeptRepository deptRepository;
    private final PositionRepository positionRepository;
    private final PasswordEncoder passwordEncoder;

    //////////////////////////////////
/// 메소드

    // 회원가입 
    @Transactional
    public MemberType join(MemberDto dto) {
        Optional<Member> foundMember = memberRepository.findByLoginId(dto.getLoginId());
        Company company = companyRepository.findByCompanyCode(dto.getCompany().getCompanyCode()).orElse(null);

        if (!dto.is_Password_Match()) {
            return MemberType.NOT_MATCH_PASSWORD;
        }
        String encodedPassword = passwordEncoder.encode(dto.getPassword());

        if (foundMember.isPresent()) { // 회원 유무
            return MemberType.ALEADY_MEMBER; // 회원이 있을 경우 ALEADY_MEMBER 반환
        }

        Member member = Member.createPendingMember(dto, encodedPassword, company);
        memberRepository.save(member); // 회원 정보 저장

        return MemberType.JOIN_SUCCESS; // 가입 성공 반환
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

        if (targetMember.getStatus() == Status.PENDING) {
            targetMember.accept(dept, position);
        }
    }

    @Transactional
    public Member login(String loginId, String password) {

        // 1. 아이디로 회원 조회
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이디입니다."));

        // 2. 비밀번호 일치 확인
        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 3. 가입 승인 상태 확인
        if (member.getStatus() == Status.PENDING) {
            throw new RuntimeException("관리자의 승인을 기다리는 중입니다.");
        }

        return member;
    }

    public boolean isDuplicateId(String loginId) {
        // 여기서 에러가 나는지 확인!
        return memberRepository.existsByLoginId(loginId);
    }

    public Long getVerifyCompanyId(String companyCode) {
    try {
        if (companyRepository == null) {
            System.err.println("!!! 리포지토리가 null입니다. 주입이 안 됐어요 !!!");
            return null;
        }
        
        return companyRepository.findByCompanyCode(companyCode)
                .map(Company::getCompanyId)
                .orElse(null);
                
    } catch (Exception e) {
        // 인텔리제이 콘솔에 아주 상세한 에러 이유를 찍어줍니다.
        System.err.println("!!! 여기서 에러가 났습니다 !!!");
        e.printStackTrace(); 
        throw e; // 다시 던져서 500 에러를 유지하되, 로그는 남깁니다.
    }
}
}
