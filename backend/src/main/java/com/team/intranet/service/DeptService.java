package com.team.intranet.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.team.intranet.repository.DeptRepository;

import jakarta.transaction.Transactional;

import com.team.intranet.exception.BusinessException;
import com.team.intranet.dto.DeptDto;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.member.Role;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeptService {

    private final DeptRepository deptRepository;

    public List<Dept> findAll(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.COMPANY_NOT_FOUND);
        }
        return deptRepository.findAllByCompanyCompanyId(companyId);
    }

    private void validateAdmin(Member admin) {
        if (admin.getRole() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_ADMIN_ROLE);
        }
    }

    private Dept findDeptAndValidateOwner(Member admin, Long deptId) {
        Dept dept = deptRepository.findById(deptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));
        if (!dept.getCompany().getCompanyId().equals(admin.getCompany().getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return dept;
    }

    @Transactional
    public void createDept(Member admin, DeptDto dto) {
        validateAdmin(admin);
        Dept dept = Dept.createDept(dto.getDeptName(), dto.getDeptCode(), admin.getCompany());
        deptRepository.save(dept);
    }

    @Transactional
    public void updateDept(Member admin, DeptDto dto, Long deptId) {
        validateAdmin(admin);
        Dept dept = findDeptAndValidateOwner(admin, deptId);
        dept.setDeptName(dto.getDeptName());
        dept.setDeptCode(dto.getDeptCode());
    }

    @Transactional
    public void deleteDept(Member admin, Long deptId) {
        validateAdmin(admin);
        Dept dept = findDeptAndValidateOwner(admin, deptId);
        deptRepository.delete(dept);
    }
}
