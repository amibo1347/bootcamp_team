package com.team.intranet.service;

import java.util.List;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import com.team.intranet.session.MemberSession;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.member.Role;
import com.team.intranet.dto.PositionDto;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.Position;
import com.team.intranet.repository.PositionRepository;
import com.team.intranet.repository.CompanyRepository;
import jakarta.transaction.Transactional;

@Service
@RequiredArgsConstructor
public class PositionService {
    
    private final PositionRepository positionRepository;
    private final CompanyRepository companyRepository;

    // 우리 회사 직급만 조회
    public List<Position> findAll(Long companyId){
        if(companyId == null){
            throw new BusinessException(ErrorCode.COMPANY_NOT_FOUND);
        }
        return positionRepository.findAllByCompanyCompanyId(companyId);
    }

    public List<Position> findAllByCompanyCompanyIdOrderByPositionLevelDESC(Long companyId) {
        if(companyId == null){
            throw new BusinessException(ErrorCode.COMPANY_NOT_FOUND);
        }
        return positionRepository.findByCompany_CompanyIdOrderByPositionLevelDesc(companyId);
    }

    // 기업 확인
    private Company findCompany(Long companyId) {
        return companyRepository.findById(companyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));
    }

    // 관리자 권한 확인
    private void validateAdmin(MemberSession ms) {
        if(ms.getRole() != Role.ADMIN) throw new BusinessException(ErrorCode.NOT_ADMIN_ROLE);
    } 

    // 직급에 대한 권한을 가진지 확인
    private Position findPositionAndValidateOwner(MemberSession ms, Long positionId) {
        Position position = positionRepository.findById(positionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));
        if (!position.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return position;
    }

    // 직급 생성
    @Transactional
    public void createPosition(MemberSession ms, PositionDto dto){
        validateAdmin(ms);
        Company company = findCompany(ms.getCompanyId());

        Position position = Position.createPosition(dto.getPositionName(), company);
        position.setPositionLevel(dto.getPositionLevel());
        position.setRole(dto.isAdmin() ? Role.SUB_ADMIN : Role.USER);
        positionRepository.save(position);
    }

    // 직급 수정
    @Transactional
    public void updatePosition(MemberSession ms, PositionDto dto, Long positionId){
        validateAdmin(ms);
        Position position = findPositionAndValidateOwner(ms, positionId);

        position.setPositionName(dto.getPositionName());
        position.setPositionLevel(dto.getPositionLevel());
        position.setRole(dto.isAdmin() ? Role.SUB_ADMIN : Role.USER);
        positionRepository.save(position);
    }

    // 직급 삭제
    @Transactional
    public void deletePosition(MemberSession ms, Long positionId){
        validateAdmin(ms);
        Position position = findPositionAndValidateOwner(ms, positionId);
        positionRepository.delete(position);
    }
}
