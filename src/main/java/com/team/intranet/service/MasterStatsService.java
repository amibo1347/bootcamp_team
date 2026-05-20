package com.team.intranet.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.dto.CompanyUsageDto;
import com.team.intranet.entity.Company;
import com.team.intranet.repository.ApprovalRepository;
import com.team.intranet.repository.ArticleRepository;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * MASTER 사용량 대시보드 통계.
 *  - 회사별 회원 수 / 게시글 수 / 결재 수 집계.
 *  - 스토리지(첨부 BLOB 누적)는 별도 작업으로 보류.
 */
@Service
@RequiredArgsConstructor
public class MasterStatsService {

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final ArticleRepository articleRepository;
    private final ApprovalRepository approvalRepository;

    /** 전체 회사의 사용량 행 목록. */
    @Transactional(readOnly = true)
    public List<CompanyUsageDto> usageList() {
        List<Company> companies = companyRepository.findAll(Sort.by(Sort.Direction.ASC, "companyId"));
        List<CompanyUsageDto> rows = new ArrayList<>(companies.size());
        for (Company company : companies) {
            Long id = company.getCompanyId();
            rows.add(new CompanyUsageDto(
                    id,
                    company.getCompanyName(),
                    company.isActiveCompany(),
                    memberRepository.countByCompany_CompanyId(id),
                    articleRepository.countByCompanyId(id),
                    approvalRepository.countByCompanyId(id)));
        }
        return rows;
    }
}
