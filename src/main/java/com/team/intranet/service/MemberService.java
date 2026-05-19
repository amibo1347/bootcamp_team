package com.team.intranet.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;  // ⭐ Spring 트랜잭션

import com.team.intranet.dto.MemberDto;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.Position;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.member.MemberType;
import com.team.intranet.enums.member.Status;
import com.team.intranet.enums.member.SubAdminPermission;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.ArticleRepository;
import com.team.intranet.repository.CommentRepository;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.DeptRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.repository.PositionRepository;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // ⭐ 클래스 레벨 readOnly
public class MemberService {

    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;
    private final DeptRepository deptRepository;
    private final PositionRepository positionRepository;
    private final ArticleRepository articleRepository;
    private final CommentRepository commentRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String RETIRED_DISPLAY_NAME = "탈퇴 회원";

    // ===== 회원가입 =====

    /**
     * 회원가입 (PENDING 상태로)
     */
    @Transactional
    public MemberType join(MemberDto dto) {
        if (memberRepository.findByLoginId(dto.getLoginId()).isPresent()) {
            return MemberType.ALREADY_MEMBER;
        }
        if (!dto.is_Password_Match()) {
            return MemberType.NOT_MATCH_PASSWORD;
        }
        if (dto.getCompanyCode() == null || dto.getCompanyCode().isBlank()) {
            return MemberType.NOT_COMPANY;
        }

        Company company = companyRepository.findByCompanyCodeIgnoreCase(dto.getCompanyCode())
            .orElse(null);
        if (company == null) {
            return MemberType.NOT_COMPANY;
        }

        String encodedPassword = passwordEncoder.encode(dto.getPassword());
        Member member = Member.createPendingMember(dto, encodedPassword, company);
        memberRepository.save(member);

        return MemberType.JOIN_SUCCESS;
    }

    // ===== 회원 관리 =====

    /**
     * 가입 승인 (WAIT → JOIN)
     */
    @Transactional
    public void acceptMember(MemberSession ms, Long memberId, Long deptId, Long positionId) {
        Member targetMember = findMemberAndValidateOwner(ms, memberId);

        if (targetMember.getStatus() != Status.WAIT) {
            throw new BusinessException(ErrorCode.ALREADY_JOIN_MEMBER);
        }

        Dept dept = findDeptAndValidateOwner(ms, deptId);
        Position position = findPositionAndValidateOwner(ms, positionId);

        targetMember.accept(dept, position);
    }

    @Transactional
    public void changeStatus(MemberSession ms, Long memberId, Status next){
        Member target = findMemberAndValidateOwner(ms, memberId);

        if (!target.getStatus().canTransitionTo(next)){
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        switch (next) {
            case REJECT -> {
                // 가입 거절: 작성한 게시글/댓글이 없으므로 별도 처리 없이 row 즉시 DELETE
                memberRepository.delete(target);
            }
            case BANNED, LEAVE -> {
                // 게시글/댓글 표시명을 먼저 고정해야 함 (author 가 아직 살아있어 검색 가능)
                articleRepository.markAuthorDisplayName(target.getMemberId(), RETIRED_DISPLAY_NAME);
                commentRepository.markAuthorDisplayName(target.getMemberId(), RETIRED_DISPLAY_NAME);
                if (next == Status.BANNED) target.fire(); else target.leave();
                target.anonymizePii(passwordEncoder.encode(UUID.randomUUID().toString()));
            }
            case ON_LEAVE -> target.onLeave();
            case JOIN -> target.reinstate(); // ON_LEAVE -> JOIN 복직
            default -> throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
    }

    /**
     * 회원별 예외 권한(extra) 일괄 교체.
     *  - 선택된 회원 모두에게 동일한 권한 집합을 설정한다 (교체 의미).
     *  - permissions 가 null/empty 면 해당 회원들의 모든 예외 권한 해제.
     *  - 권한 관리 페이지는 ADMIN 만 접근하므로 호출 측에서 차단되어야 한다.
     */
    @Transactional
    public void updateExtraPermissions(MemberSession ms, List<Long> memberIds, Set<SubAdminPermission> permissions) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        Set<SubAdminPermission> normalized = (permissions == null || permissions.isEmpty())
            ? EnumSet.noneOf(SubAdminPermission.class)
            : EnumSet.copyOf(permissions);

        for (Long memberId : memberIds) {
            Member target = findMemberAndValidateOwner(ms, memberId);
            target.replaceExtraPermissions(normalized);
        }
    }

    /**
     * 인사이동: 선택된 회원들의 부서/직급을 일괄 변경.
     *  - deptId 가 null 이면 부서는 변경하지 않고 직급만 바꾼다(반대도 동일).
     *  - 둘 다 null 이거나 memberIds 가 비어있으면 BusinessException.
     */
    @Transactional
    public void reassignMembers(MemberSession ms, List<Long> memberIds, Long deptId, Long positionId) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        if (deptId == null && positionId == null) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        Dept dept = (deptId != null) ? findDeptAndValidateOwner(ms, deptId) : null;
        Position position = (positionId != null) ? findPositionAndValidateOwner(ms, positionId) : null;

        for (Long memberId : memberIds) {
            Member target = findMemberAndValidateOwner(ms, memberId);
            target.reassign(dept, position);
        }
    }

    /**
     * 회원 정보 수정
     */
    @Transactional
    public void updateMemberInfo(
            MemberSession ms,
            Long memberId,
            Long deptId,
            Long positionId,
            byte[] profileImg,
            String phone,
            String email,
            String name,
            LocalDateTime birthDay) {
        
        Member targetMember = findMemberAndValidateOwner(ms, memberId);
        Dept dept = findDeptAndValidateOwner(ms, deptId);
        Position position = findPositionAndValidateOwner(ms, positionId);

        targetMember.updateInfo(dept, position, profileImg, phone, email, name, birthDay);
    }

    /**
     * 로그인 회원 본인의 프로필 사진만 수정 (내 프로필 페이지용).
     *
     * @param ms           로그인 세션
     * @param profileImg   업로드한 이미지 바이트 (null·빈 배열이면 변경 없음)
     */
    @Transactional
    public void updateMyProfileImg(MemberSession ms, byte[] profileImg) {
        if (ms == null || ms.getMemberId() == null) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        if (profileImg == null || profileImg.length == 0) {
            return;
        }
        Member member = memberRepository.findById(ms.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.setProfileImg(profileImg);
    }

    // ===== 조회 =====

    /**
     * 아이디 중복 확인
     */
    public boolean isDuplicateId(String loginId) {
        return memberRepository.existsByLoginId(loginId);
    }

    /**
     * 기업 로고 경로
     */
    public String getLogoPath(Long companyId) {
        return companyRepository.findById(companyId)
            .map(Company::getLogoPath)
            .orElse(null);
    }

    /**
     * 기업 코드 인증
     */
    public Long getVerifyCompanyId(String companyCode) {
        return companyRepository.findByCompanyCodeIgnoreCase(companyCode)
            .map(Company::getCompanyId)
            .orElse(null);
    }

    /**
     * 상태별 회원 조회 (통합)
     */
    public List<Member> findMembersByStatus(Long companyId, Status status) {
        return memberRepository.findByStatusAndCompanyCompanyId(status, companyId);
    }

    /**
     * 회사의 모든 회원
     */
    public List<Member> findAllMembers(Long companyId) {
        return memberRepository.findByCompanyCompanyId(companyId);
    }

    public List<Member> findWaitingMembers(Long companyId) {
        return memberRepository.findByStatusAndCompanyCompanyId(Status.WAIT, companyId);
    }

    /**
     * 프로필 이미지
     */
    public byte[] getProfileImg(Long memberId) {
        return memberRepository.findProfileImgById(memberId);
    }

    /**
     * 회원 검색 + 필터 + 정렬
     */
    public List<Member> findFilteredMembers(
            Long companyId,
            String keyword,
            Long deptId,
            List<Status> statuses,
            Long positionId,
            String sort) {

        // 빈 리스트는 null 로 normalize (IN () SQL 오류 회피)
        List<Status> normalized = (statuses == null || statuses.isEmpty()) ? null : statuses;
        List<Member> members = memberRepository.searchMembers(
            companyId, keyword, deptId, normalized, positionId
        );

        // 직급 레벨 기준 정렬
        Comparator<Member> comparator = Comparator.comparingInt(
            m -> m.getPosition() != null ? m.getPosition().getPositionLevel() : Integer.MAX_VALUE
        );

        if ("desc".equalsIgnoreCase(sort)) {
            comparator = comparator.reversed();
        }

        members.sort(comparator.thenComparing(Member::getName));
        return members;
    }

    // ===== 헬퍼 메서드 (멀티테넌시 검증) =====

    /**
     * 회원 조회 + 회사 일치 검증
     */
    private Member findMemberAndValidateOwner(MemberSession ms, Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        if (!member.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return member;
    }

    /**
     * 부서 조회 + 회사 일치 검증
     */
    private Dept findDeptAndValidateOwner(MemberSession ms, Long deptId) {
        Dept dept = deptRepository.findById(deptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));
        
        if (!dept.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return dept;
    }

    /**
     * 직급 조회 + 회사 일치 검증
     */
    private Position findPositionAndValidateOwner(MemberSession ms, Long positionId) {
        Position position = positionRepository.findById(positionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));
        
        if (!position.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return position;
    }
}