package com.team.intranet.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;  

import com.team.intranet.dto.DeptDto;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.Dept;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.DeptRepository;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // ⭐ 클래스 레벨 readOnly
public class DeptService {

    private static final int DEPT_CODE_INTERVAL = 10;
    private static final int DEPT_CODE_INITIAL = 10;
    private static final String DEPT_CODE_FORMAT = "%04d";

    private final DeptRepository deptRepository;
    private final CompanyRepository companyRepository;

    /**
     * 우리 회사 부서만 조회
     */
    public List<Dept> findAll(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.COMPANY_NOT_FOUND);
        }
        return deptRepository.findAllByCompanyCompanyId(companyId);
    }

    /**
     * 부서 생성
     */
    @Transactional
    public void createDept(MemberSession ms, DeptDto dto) {
        Company company = findCompany(ms.getCompanyId());
        String newCode = generateNextDeptCode(ms.getCompanyId());
        
        Dept dept = Dept.createDept(dto.getDeptName(), newCode, company);
        deptRepository.save(dept);
    }

    /**
     * 부서 수정
     */
    @Transactional
    public void editDept(MemberSession ms, Long deptId, DeptDto dto) {
        Dept dept = findDeptAndValidateOwner(ms, deptId);
        
        // ⭐ Entity의 update 메서드 호출 (setter 대신)
        dept.updateFromDto(dto);
        // 부서 코드는 자동 생성된 값 유지 (수정 불가)
    }

    /**
     * 부서 삭제 — 시스템 기본 부서는 삭제 금지.
     */
    @Transactional
    public void deleteDept(MemberSession ms, Long deptId) {
        Dept dept = findDeptAndValidateOwner(ms, deptId);
        if (dept.isSystemDefault()) {
            throw new BusinessException(ErrorCode.SYSTEM_PROTECTED_DEPT);
        }
        deptRepository.delete(dept);
    }

    /**
     * 다음 부서 코드 생성
     * 형식: 0010, 0020, 0030, ... 
     * 회사별로 독립적으로 증가
     */
    private String generateNextDeptCode(Long companyId) {
        String lastCode = deptRepository.findMaxDeptCodeByCompanyId(companyId);
        
        int nextCode = DEPT_CODE_INITIAL;
        
        if (lastCode != null && !lastCode.isBlank()) {
            try {
                String numericCode = lastCode.replaceAll("[^0-9]", "");
                if (!numericCode.isEmpty()) {
                    nextCode = Integer.parseInt(numericCode) + DEPT_CODE_INTERVAL;
                }
            } catch (NumberFormatException e) {
                // 파싱 실패 시 초기값 사용
                nextCode = DEPT_CODE_INITIAL;
            }
        }
        
        return String.format(DEPT_CODE_FORMAT, nextCode);
    }

    // ===== 헬퍼 메서드들 =====

    /**
     * 기업 조회 (없으면 예외)
     */
    private Company findCompany(Long companyId) {
        return companyRepository.findById(companyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));
    }

    /**
     * 부서 조회 + 회사 일치 검증 (멀티테넌시)
     */
    private Dept findDeptAndValidateOwner(MemberSession ms, Long deptId) {
        Dept dept = deptRepository.findById(deptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));
        
        if (!dept.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        
        return dept;
    }
}