package com.team.intranet.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.team.intranet.repository.DeptRepository;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.session.MemberSession;

import jakarta.transaction.Transactional;

import com.team.intranet.exception.BusinessException;
import com.team.intranet.dto.DeptDto;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Company;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.member.Role;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeptService {

    private final DeptRepository deptRepository; 
    private final CompanyRepository companyRepository;

    // 우리 회사 부서만 조회
    public List<Dept> findAll(Long companyId){
        if(companyId == null){
            throw new BusinessException(ErrorCode.COMPANY_NOT_FOUND);
        }
        
        return deptRepository.findAllByCompanyCompanyId(companyId);
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

    // 부서에 대한 권한을 가진지 확인
    private Dept findDeptAndValidateOwner(MemberSession ms, Long deptId) {
        Dept dept = deptRepository.findById(deptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));
        if (!dept.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return dept;
    }

    // 부서 생성
    @Transactional
    public void createDept(MemberSession ms, DeptDto dto){
        validateAdmin(ms);
        Company company = findCompany(ms.getCompanyId());

        Dept dept = Dept.createDept(dto.getDeptName(), dto.getDeptCode(), company);
        deptRepository.save(dept);
    }

    // 부서 수정
    @Transactional
    public void updateDept(MemberSession ms, DeptDto dto, Long deptId){
        validateAdmin(ms);
        Dept dept = findDeptAndValidateOwner(ms, deptId);
        
        dept.setDeptName(dto.getDeptName());
        dept.setDeptCode(dto.getDeptCode());
    }

    // 부서 삭제
    @Transactional
    public void deleteDept(MemberSession ms, Long deptId){
        validateAdmin(ms);
        Dept dept = findDeptAndValidateOwner(ms, deptId);
        deptRepository.delete(dept);
    }
}