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
public void createDept(MemberSession ms, DeptDto dto) {
    validateAdmin(ms);
    Company company = findCompany(ms.getCompanyId());
    
    // DB에서 최대 코드를 가져옴
    String lastCode = deptRepository.findMaxDeptCodeByCompanyId(ms.getCompanyId());
    
    int nextCode = 10; // 기본 시작 값

    // lastCode가 null이 아니고, 비어있지 않으며, 공백이 아닐 때만 파싱 시도
    if (lastCode != null && !lastCode.trim().isEmpty()) {
        try {
            // 숫자 외의 문자가 섞여있을 경우를 대비해 숫자만 추출
            String numericCode = lastCode.replaceAll("[^0-9]", "");
            
            if (!numericCode.isEmpty()) {
                nextCode = Integer.parseInt(numericCode) + 10;
            }
        } catch (NumberFormatException e) {
            // 파싱 실패 시 초기값 10으로 진행 (로그 확인용)
            System.err.println("부서 코드 파싱 실패, 기본값으로 설정: " + lastCode);
            nextCode = 10;
        }
    }

    // 4자리 숫자로 변환 (예: 10 -> "0010")
    String newCode = String.format("%04d", nextCode);

    // 생성 및 저장
    Dept dept = Dept.createDept(dto.getDeptName(), newCode, company);
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