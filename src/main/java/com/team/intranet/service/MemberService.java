package com.team.intranet.service;

import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.team.intranet.dto.MemberDto;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.Position;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.member.MemberType;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.Status;
import com.team.intranet.exception.BusinessException;
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
            return MemberType.ALREADY_MEMBER;
        }

        if (!dto.is_Password_Match()) {
            return MemberType.NOT_MATCH_PASSWORD;
        }

        // 기업 코드 보안 검증
        if (dto.getCompanyCode() == null || dto.getCompanyCode().isEmpty()) {
            return MemberType.NOT_COMPANY;
        }
        Company company = companyRepository.findByCompanyCodeIgnoreCase(dto.getCompanyCode()).orElse(null);
        if (company == null) {
            return MemberType.NOT_COMPANY;
        }

        String encodedPassword = passwordEncoder.encode(dto.getPassword());
        Member member = Member.createPendingMember(dto, encodedPassword, company);
        memberRepository.save(member);

        return MemberType.JOIN_SUCCESS;
    }

    @Transactional
public void acceptMember(Long memberId, String adminLoginId, Long deptId, Long positionId) {
    // 1. 관리자 권한 체크 (기존 로직)
    Member admin = memberRepository.findByLoginId(adminLoginId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_ADMIN_ROLE));

    if (admin.getRole() != Role.ADMIN) {
        throw new BusinessException(ErrorCode.NOT_ADMIN_ROLE);
    }

    // 2. 대상 회원 조회
    Member targetMember = memberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    if (targetMember.getStatus() != Status.WAIT) {
        throw new BusinessException(ErrorCode.ALREADY_JOIN_MEMBER);
    }

    // 3. 부서 및 직급 조회
    Dept dept = deptRepository.findById(deptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));

    Position position = positionRepository.findById(positionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));

    // 4. 승인 처리 (이제 안심하고 실행)
    targetMember.accept(dept, position);
}

    // 로그인
    // @Transactional
    // public Member login(String loginId, String password) {
    //     Member member = memberRepository.findByLoginId(loginId)
    //             .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

    //     if (!passwordEncoder.matches(password, member.getPassword())) {
    //         throw new BusinessException(ErrorCode.LOGIN_FAILED);
    //     }

    //     if (member.getStatus() == Status.WAIT) {
    //         throw new BusinessException(ErrorCode.WAITING_ACCEPT);
    //     }

    //     return member;
    // }

    // 아이디 중복 확인
    public boolean isDuplicateId(String loginId) {
        return memberRepository.existsByLoginId(loginId);
    }

    // 기업 로고
    public String getLogoPath(Long companyId) {
        Company company = companyRepository.findById(companyId).orElse(null);

        return (company != null) ? company.getLogoPath() : null;
    }

    // 기업 코드 인증
    public Long getVerifyCompanyId(String companyCode) {
        return companyRepository.findByCompanyCodeIgnoreCase(companyCode)
                .map(Company::getCompanyId)
                .orElse(null);
    }

    public List<Member> findWaitMembers(Long companyId){
        return memberRepository.findByStatusAndCompanyCompanyId(Status.WAIT, companyId);
    }
}
