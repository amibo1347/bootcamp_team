package com.team.intranet.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;  // ⭐ Spring 트랜잭션

import com.team.intranet.dto.PositionDto;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.Position;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.SubAdminPermission;
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
     * 직급 생성
     */
    @Transactional
    public void createPosition(MemberSession ms, PositionDto dto) {
        Company company = findCompany(ms.getCompanyId());
        
        // 레벨 중복 검증
        validateUniqueLevel(ms.getCompanyId(), dto.getPositionLevel(), null);
        
        Role role = dto.isAdmin() ? Role.SUB_ADMIN : Role.USER;
        
        Position position = Position.createPosition(
            dto.getPositionName(),
            company,
            dto.getPositionLevel(),
            role
        );
        
        positionRepository.save(position);
    }

    /**
     * 직급 수정
     */
    @Transactional
    public void updatePosition(MemberSession ms, PositionDto dto, Long positionId) {
        Position position = findPositionAndValidateOwner(ms, positionId);
        
        // 레벨이 바뀌었으면 중복 검증
        if (!position.getPositionLevel().equals(dto.getPositionLevel())) {
            validateUniqueLevel(ms.getCompanyId(), dto.getPositionLevel(), positionId);
        }
        
        Role role = dto.isAdmin() ? Role.SUB_ADMIN : Role.USER;
        
        // ⭐ Entity의 update 메서드 사용 (setter 대신)
        position.update(dto.getPositionName(), dto.getPositionLevel(), role);
        // JPA 변경 감지로 자동 저장 (save 불필요)
    }

    /**
     * 직급 삭제
     */
    @Transactional
    public void deletePosition(MemberSession ms, Long positionId) {
        Position position = findPositionAndValidateOwner(ms, positionId);
        positionRepository.delete(position);
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

    /**
     * 직급 레벨 중복 검증
     * 같은 회사 내에 동일 레벨이 이미 있으면 예외
     * @param excludePositionId 수정 시 자기 자신 제외용 (생성 시 null)
     */
    private void validateUniqueLevel(Long companyId, Integer level, Long excludePositionId) {
        if (level == null) return;
        
        boolean exists = positionRepository.existsByCompanyCompanyIdAndPositionLevelAndPositionIdNot(
            companyId, level, excludePositionId != null ? excludePositionId : -1L
        );
        
        if (exists) {
            throw new BusinessException(ErrorCode.DUPLICATE_POSITION_LEVEL);
        }
    }

}