package com.team.intranet.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;  // ⭐ Spring 트랜잭션

import com.team.intranet.dto.PositionDto;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.Position;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.SystemLogAction;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.SubAdminPermission;
import com.team.intranet.event.SystemLogEvent;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.PositionRepository;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // ⭐ 클래스 레벨 readOnly
public class PositionService {

    private final PositionRepository positionRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 우리 회사 직급 조회 (정렬 없음)
     */
    public List<Position> findAll(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.COMPANY_NOT_FOUND);
        }
        return positionRepository.findAllByCompanyCompanyId(companyId);
    }

    /**
     * 우리 회사 직급 조회 (레벨 내림차순)
     */
    public List<Position> findAllLevelDesc(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.COMPANY_NOT_FOUND);
        }
        return positionRepository.findByCompany_CompanyIdOrderByPositionLevelDesc(companyId);
    }

    /**
     * 직급 생성.
     *  - 동일 level 허용 (예: 인사팀 부장, 개발팀 부장).
     *  - 화면 정렬은 PositionRepository 가 level DESC, 이름 ASC 보조키로 안정성 보장.
     */
    @Transactional
    public void createPosition(MemberSession ms, PositionDto dto) {
        Company company = findCompany(ms.getCompanyId());

        Role role = dto.isAdmin() ? Role.SUB_ADMIN : Role.USER;

        Position position = Position.createPosition(
            dto.getPositionName(),
            company,
            dto.getPositionLevel(),
            role
        );

        Position saved = positionRepository.save(position);
        publishLog(ms, SystemLogAction.CREATE, "POSITION", saved.getPositionId(),
            saved.getPositionName() + "(level=" + saved.getPositionLevel() + ")",
            "직급 생성 / role=" + role.name());
    }

    /**
     * 직급 수정.
     *  - 시스템 기본 직급(isSystem)은 이름만 변경 허용. level/role 변경 시도는 거부.
     *  - 일반 직급은 level 중복 검증 없음 (동일 level 허용).
     */
    @Transactional
    public void updatePosition(MemberSession ms, PositionDto dto, Long positionId) {
        Position position = findPositionAndValidateOwner(ms, positionId);

        Role newRole = dto.isAdmin() ? Role.SUB_ADMIN : Role.USER;

        if (position.isSystemDefault()) {
            boolean levelChanged = !position.getPositionLevel().equals(dto.getPositionLevel());
            boolean roleChanged = position.getRole() != newRole;
            if (levelChanged || roleChanged) {
                throw new BusinessException(ErrorCode.SYSTEM_PROTECTED_POSITION_FIELD);
            }
        }

        String prev = position.getPositionName() + "(level=" + position.getPositionLevel() + ")";

        // ⭐ Entity의 update 메서드 사용 (setter 대신) — isSystem 이면 엔티티가 이름만 갱신
        position.update(dto.getPositionName(), dto.getPositionLevel(), newRole);
        // JPA 변경 감지로 자동 저장 (save 불필요)

        String now = position.getPositionName() + "(level=" + position.getPositionLevel() + ")";
        publishLog(ms, SystemLogAction.UPDATE, "POSITION", positionId, position.getPositionName(),
            "직급 수정: " + prev + " → " + now);
    }

    /**
     * 직급 삭제 — 시스템 기본 직급은 삭제 금지.
     */
    @Transactional
    public void deletePosition(MemberSession ms, Long positionId) {
        Position position = findPositionAndValidateOwner(ms, positionId);
        if (position.isSystemDefault()) {
            throw new BusinessException(ErrorCode.SYSTEM_PROTECTED_POSITION);
        }
        String label = position.getPositionName() + "(level=" + position.getPositionLevel() + ")";
        positionRepository.delete(position);
        publishLog(ms, SystemLogAction.DELETE, "POSITION", positionId, label, "직급 삭제");
    }

    /**
     * 권한 관리 페이지: 특정 직급의 SUB_ADMIN 세부 권한 일괄 교체.
     *  - permissions 가 null/empty 면 USER 직급으로 자동 강등, 1개 이상이면 SUB_ADMIN 으로 자동 승격.
     *  - ADMIN/MASTER 직급은 권한 관리 페이지에서 편집할 수 없도록 차단(시스템/기업 대표 권한이 권한 컬렉션으로 좁혀지면 안 됨).
     *  - 권한 관리 페이지는 ADMIN 만 접근하므로 호출 측에서 PreAuthorize 로 차단되어야 한다.
     */
    @Transactional
    public void updatePermissions(MemberSession ms, Long positionId, Set<SubAdminPermission> permissions) {
        Position position = findPositionAndValidateOwner(ms, positionId);

        // ADMIN/MASTER 직급은 권한 시스템과 무관(모든 권한 자동 통과)이므로 편집 금지.
        if (position.getRole() == Role.ADMIN || position.getRole() == Role.MASTER) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        Set<SubAdminPermission> normalized = (permissions == null || permissions.isEmpty())
            ? EnumSet.noneOf(SubAdminPermission.class)
            : EnumSet.copyOf(permissions);
        position.replacePermissions(normalized);

        // 권한 부여 결과에 따라 Role 자동 전환.
        //  - 권한 1개 이상 → SUB_ADMIN
        //  - 권한 0개      → USER
        Role newRole = normalized.isEmpty() ? Role.USER : Role.SUB_ADMIN;
        if (position.getRole() != newRole) {
            position.setRole(newRole);
        }

        String permsLabel = normalized.isEmpty() ? "(권한 없음 → USER)"
            : normalized.stream().map(Enum::name).reduce((a, b) -> a + ", " + b).orElse("");
        publishLog(ms, SystemLogAction.UPDATE, "POSITION", positionId, position.getPositionName(),
            "직급 권한 변경: " + permsLabel);
    }

    /** 시스템 로그 이벤트 발행 — AFTER_COMMIT 리스너가 적재. */
    private void publishLog(MemberSession ms, SystemLogAction action, String targetType, Long targetId,
                            String targetLabel, String detail) {
        if (ms == null || ms.getCompanyId() == null) return;
        eventPublisher.publishEvent(new SystemLogEvent(
            ms.getCompanyId(), ms.getMemberId(), ms.getName(),
            action, targetType, targetId, targetLabel, detail));
    }

    // ===== 헬퍼 메서드 =====

    /**
     * 기업 조회
     */
    private Company findCompany(Long companyId) {
        return companyRepository.findById(companyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));
    }

    /**
     * 직급 조회 + 회사 일치 검증 (멀티테넌시)
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