package com.team.intranet.service;

import java.util.Comparator;
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
    public void acceptMember(Long memberId, Long adminId, Long deptId, Long positionId) {
        // 1. 관리자 정보 및 권한 체크
        Member admin = memberRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (admin.getRole() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_ADMIN_ROLE);
        }

        // 2. 대상 회원 조회
        Member targetMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 3. 같은 회사인지 철저히 검증 (보안의 핵심)
        if (!targetMember.getCompany().getCompanyId().equals(admin.getCompany().getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        if (targetMember.getStatus() != Status.WAIT) {
            throw new BusinessException(ErrorCode.ALREADY_JOIN_MEMBER);
        }

        // 4. 부서 및 직급 조회 (Fetch Join 등을 고려하면 더 좋음)
        Dept dept = deptRepository.findById(deptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));

        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));

        // 5. 도메인 모델에 승인 로직 위임
        targetMember.accept(dept, position);
    }

    @Transactional
    public void updateMemberInfo(Long memberId, Long adminId, Long deptId, Long positionId, byte[] profileImg) {
        // 1. 관리자 정보 및 권한 체크
        Member admin = memberRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (admin.getRole() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_ADMIN_ROLE);
        }

        // 2. 수정 대상 회원 조회
        Member targetMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 3. 보안 체크: 같은 회사 소속인지 확인
        if (!targetMember.getCompany().getCompanyId().equals(admin.getCompany().getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 4. 부서 및 직급 조회 및 회사 일치 확인
        Dept dept = deptRepository.findById(deptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));

        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));

        if (!dept.getCompany().getCompanyId().equals(admin.getCompany().getCompanyId()) ||
                !position.getCompany().getCompanyId().equals(admin.getCompany().getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 5. 실제 수정 로직 수행 (Dirty Checking 활용)
        targetMember.updateInfo(dept, position, profileImg);
    }

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

    public List<Member> findWaitMembers(Long companyId) {
        return memberRepository.findByStatusAndCompanyCompanyId(Status.WAIT, companyId);
    }

    public List<Member> findJoinMembers(Long companyId) {
        return memberRepository.findByStatusAndCompanyCompanyId(Status.JOIN, companyId);
    }

    public List<Member> findFireMembers(Long companyId) {
        return memberRepository.findByStatusAndCompanyCompanyId(Status.LEAVE, companyId);
    }

    public List<Member> findRejectMembers(Long companyId) {
        return memberRepository.findByStatusAndCompanyCompanyId(Status.REJECT, companyId);
    }

    public List<Member> findAllMembers(Long companyId) {
        return memberRepository.findByCompanyCompanyId(companyId);
    }

    public List<Member> findFilteredMembers(Long companyId, Long deptId, Status status, Long positionId, String sort) {
        List<Member> members = memberRepository.searchMembers(companyId, deptId, status, positionId);
        
        // 💡 정렬 기준 생성 (Position의 Level 기준)
         Comparator<Member> comparator = Comparator.comparingInt(m -> 
         m.getPosition() != null ? m.getPosition().getPositionLevel() : 999);

        // 💡 sort 값이 "desc"면 정렬 순서를 반전시킴
        if ("desc".equalsIgnoreCase(sort)) {
            comparator = comparator.reversed();
        }

        members.sort(comparator.thenComparing(Member::getName)); // 직급 같으면 이름순
        return members;
    }

    // 1. 가입 반려 처리 (WAIT -> REJECT)
    @Transactional
    public void rejectMember(Long memberId, Long adminId) {
        Member admin = memberRepository.findById(adminId).orElseThrow();
        Member target = memberRepository.findById(memberId).orElseThrow();

        // 보안 체크: 같은 회사인지 확인
        if (!admin.getCompany().getCompanyId().equals(target.getCompany().getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        target.setStatus(Status.REJECT); // Dirty Checking으로 자동 업데이트
    }

    // 2. 퇴사 처리 (JOIN -> LEAVE)
    @Transactional
    public void fireMember(Long memberId, Long adminId) {
        Member admin = memberRepository.findById(adminId).orElseThrow();
        Member target = memberRepository.findById(memberId).orElseThrow();

        // 보안 체크
        if (!admin.getCompany().getCompanyId().equals(target.getCompany().getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        target.setStatus(Status.LEAVE);
    }

    public byte[] getProfileImg(Long memberId) {
        return memberRepository.findById(memberId)
                .map(Member::getProfileImg)
                .orElse(null);
    }
}
