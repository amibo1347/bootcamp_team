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
import com.team.intranet.enums.member.Role;
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

        // loginId(=사번/아이디)는 회사 단위로만 유니크 — 같은 회사 안에서만 중복 검사.
        if (memberRepository.existsByCompany_CompanyIdAndLoginId(company.getCompanyId(), dto.getLoginId())) {
            return MemberType.ALREADY_MEMBER;
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
     * 관리자(SUB_ADMIN/ADMIN) 가 직원 비밀번호를 임시 비번으로 강제 초기화한다.
     *  - 같은 회사 직원에 한해서만 가능 (findMemberAndValidateOwner 로 검증).
     *  - 영문 대소문자 + 숫자로 구성된 안전한 임시 비번 8자를 생성해 인코딩 저장.
     *  - 평문 임시 비번을 반환 → 컨트롤러가 응답으로 내려 관리자가 직원에게 직접 전달.
     *  - (후속) 직원 다음 로그인 시 비번 변경 강제는 별도 mustChangePassword 플래그로 추가 예정.
     *
     * @return 발급된 평문 임시 비밀번호 (관리자 화면에 1회 노출 후 폐기)
     */
    @Transactional
    public String resetMemberPassword(MemberSession ms, Long memberId) {
        Member target = findMemberAndValidateOwner(ms, memberId);
        String tempPassword = generateTempPassword(TEMP_PASSWORD_LENGTH);
        target.changePassword(passwordEncoder.encode(tempPassword));
        return tempPassword;
    }

    private static final int TEMP_PASSWORD_LENGTH = 8;
    // 사용자가 1·l·I·0·O 등 혼동하기 쉬운 문자는 제외 → 구두 전달 오류 방지
    private static final String TEMP_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ" +  // I, O 제외
            "abcdefghjkmnpqrstuvwxyz" +   // i, l, o 제외
            "23456789";                   // 0, 1 제외

    private static String generateTempPassword(int length) {
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(TEMP_PASSWORD_ALPHABET.charAt(random.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * 로그인 회원 본인의 정보 수정 (내 프로필 페이지용).
     *  - 이름/이메일/전화/생년월일만 변경. 부서/직급/역할/프로필 사진은 다른 경로로만.
     *  - 트랜잭션 안에서 새 MemberSession 을 만들어 반환 → 컨트롤러가 세션에 박아 즉시 반영.
     *
     * @return 갱신된 MemberSession (호출자는 HttpSession 에 setAttribute 로 교체).
     */
    @Transactional
    public MemberSession updateMyInfo(MemberSession ms, String name, String email, String phone, LocalDateTime birthDay) {
        if (ms == null || ms.getMemberId() == null) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        Member me = memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        me.updateSelfInfo(name, email, phone, birthDay);
        // Position.permissions / Dept 등 LAZY 필드들이 이 시점에 로딩됨 → 트랜잭션 밖에서 안전.
        return new MemberSession(me);
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

    /**
     * 본인 비밀번호 변경 (내 프로필 페이지용).
     *  - MasterAccountService.changePassword 와 동일 패턴 — 현재 비밀번호 검증 후 새 비번 인코딩 저장.
     *  - 입력값 검증(빈 값/길이/일치/현재 비번과 동일) 은 호출자(컨트롤러)가 수행.
     *
     * @throws IllegalArgumentException 계정이 없거나 현재 비밀번호가 틀린 경우
     *                                  (호출자가 사용자 메시지로 그대로 사용)
     */
    @Transactional
    public void changePassword(MemberSession ms, String currentRawPassword, String newRawPassword) {
        if (ms == null || ms.getMemberId() == null) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        Member me = memberRepository.findById(ms.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));

        if (!passwordEncoder.matches(currentRawPassword, me.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        me.changePassword(passwordEncoder.encode(newRawPassword));
    }

    // ===== 조회 =====

    /**
     * 아이디/사번 중복 확인 — 같은 회사 안에서만 검사한다.
     */
    public boolean isDuplicateId(Long companyId, String loginId) {
        return memberRepository.existsByCompany_CompanyIdAndLoginId(companyId, loginId);
    }

    /**
     * 회사가 사번제인지 여부 — 회원가입 화면의 입력 칸 라벨(사번/아이디) 결정용.
     */
    public boolean usesEmployeeNo(Long companyId) {
        if (companyId == null) return false;
        return companyRepository.findById(companyId)
            .map(Company::usesEmployeeNo)
            .orElse(false);
    }

    /**
     * 회사 도메인 — 회원가입 성공 후 그 회사 로그인 페이지로 보내기 위함. 없으면 null.
     */
    public String getCompanyDomain(Long companyId) {
        if (companyId == null) return null;
        return companyRepository.findById(companyId)
            .map(Company::getCompanyDomain)
            .orElse(null);
    }

    /**
     * 기업 로고 경로
     */
    public String getLogoPath(Long companyId) {
        if (companyId == null) return null;
        // 로고 BLOB 은 /api/company/{id}/logo 엔드포인트로 서빙. 로고가 있을 때만 URL 반환.
        return companyRepository.existsByCompanyIdAndLogoIsNotNull(companyId)
            ? "/api/company/" + companyId + "/logo"
            : null;
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

    /** profileImg 회사 스코프 검증용 — 회원의 회사 ID. 회원 미존재 시 null. */
    public Long getCompanyIdByMemberId(Long memberId) {
        return memberRepository.findCompanyIdByMemberId(memberId);
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
     * 회원 조회 + 회사 일치 검증 + 상위 직급 보호 가드.
     *  - 회사 일치 검증(멀티테넌시) 통과 후 level 위계 검증.
     */
    private Member findMemberAndValidateOwner(MemberSession ms, Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!member.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        validateLevelHierarchy(ms, member);
        return member;
    }

    /**
     * 상위 직급 보호 가드 — 본인보다 같거나 높은 level 회원의 정보는 변경 불가.
     *  통과 조건:
     *   - 본인 자신 (안전망 — 본인 정보는 별도 me/ 경로를 사용해야 한다).
     *   - 요청자가 ADMIN / MASTER (회사 대표·시스템 관리자라 level 무관).
     *   - 요청자가 MEMBER_MANAGEMENT 권한 보유자 AND target.level < me.level.
     *   - level 한쪽이 null 인 위계 미정(PENDING 등): MEMBER_MANAGEMENT 권한자만 통과.
     *  차단:
     *   - target.level >= me.level (권한 보유 여부 무관, 동등도 차단 → 자기 보호).
     *   - MEMBER_MANAGEMENT 권한 미보유자.
     *
     * ※ 변경 이력: 과거에는 MEMBER_MANAGEMENT 권한 보유자가 level 위계와 무관하게 통과했으나,
     *   부장(SUB_ADMIN) 이 대표(ADMIN, level=99) 의 정보를 변경할 수 있는 문제가 있어
     *   "권한이 있어도 상위 직급은 못 건드린다" 로 정책을 보수적으로 변경했다.
     *   UI 가드(MemberSession.canManageMember) 와 동일 정책.
     */
    private void validateLevelHierarchy(MemberSession ms, Member target) {
        if (ms.getMemberId().equals(target.getMemberId())) return;
        if (ms.getRole() == Role.ADMIN || ms.getRole() == Role.MASTER) return;

        if (!ms.hasPermission(SubAdminPermission.MEMBER_MANAGEMENT)) {
            throw new BusinessException(ErrorCode.SUPERIOR_MEMBER_PROTECTED);
        }

        Integer myLevel = ms.getPositionLevel();
        Integer targetLevel = target.getPosition() != null ? target.getPosition().getPositionLevel() : null;
        if (myLevel == null || targetLevel == null) return; // 위계 미정 → 권한자 통과

        if (targetLevel >= myLevel) {
            throw new BusinessException(ErrorCode.SUPERIOR_MEMBER_PROTECTED);
        }
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