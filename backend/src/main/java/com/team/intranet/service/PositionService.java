package com.team.intranet.service;

import java.util.List;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import com.team.intranet.exception.BusinessException;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.member.Role;
import com.team.intranet.dto.PositionDto;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.Position;
import com.team.intranet.repository.PositionRepository;

import jakarta.transaction.Transactional;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;

    public List<Position> findAll(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.COMPANY_NOT_FOUND);
        }
        return positionRepository.findAllByCompanyCompanyId(companyId);
    }

    private void validateAdmin(Member admin) {
        if (admin.getRole() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_ADMIN_ROLE);
        }
    }

    private Position findPositionAndValidateOwner(Member admin, Long positionId) {
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));
        if (!position.getCompany().getCompanyId().equals(admin.getCompany().getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return position;
    }

    @Transactional
    public void createPosition(Member admin, PositionDto dto) {
        validateAdmin(admin);
        Position position = Position.createPosition(dto.getPositionName(), admin.getCompany());
        positionRepository.save(position);
    }

    @Transactional
    public void updatePosition(Member admin, PositionDto dto, Long positionId) {
        validateAdmin(admin);
        Position position = findPositionAndValidateOwner(admin, positionId);
        position.setPositionName(dto.getPositionName());
    }

    @Transactional
    public void deletePosition(Member admin, Long positionId) {
        validateAdmin(admin);
        Position position = findPositionAndValidateOwner(admin, positionId);
        positionRepository.delete(position);
    }
}
