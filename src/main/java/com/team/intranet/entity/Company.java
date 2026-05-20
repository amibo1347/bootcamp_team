package com.team.intranet.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.team.intranet.enums.IsActive;

@Entity
@Table(name = "tbl_company")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "company_code")
    private String companyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_active")
    private IsActive isActive;

    @Column(name = "company_domain")
    private String companyDomain;

    /** 회사 로고 이미지 (BLOB). 회원 프로필 사진(Member.profileImg)과 동일한 방식. null 이면 로고 없음. */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "logo")
    private byte[] logo;

    /** 신규 회사 생성. 활성(Y) 상태로 시작. */
    public static Company create(String companyName, String companyCode, String companyDomain) {
        Company company = new Company();
        company.companyName = companyName;
        company.companyCode = companyCode;
        company.companyDomain = companyDomain;
        company.isActive = IsActive.Y;
        return company;
    }

    /** 회사 정보 수정 (이름/도메인). 로고는 updateLogo 로 별도 처리. */
    public void updateInfo(String companyName, String companyDomain) {
        this.companyName = companyName;
        this.companyDomain = companyDomain;
    }

    /** 로고 이미지 교체. */
    public void updateLogo(byte[] logo) {
        this.logo = logo;
    }

    /** 로고 이미지 삭제. */
    public void clearLogo() {
        this.logo = null;
    }

    /** 로고 보유 여부. */
    public boolean hasLogo() {
        return logo != null && logo.length > 0;
    }

    /** 회사 코드 재발급. */
    public void reissueCode(String newCode) {
        this.companyCode = newCode;
    }

    public void activate() { this.isActive = IsActive.Y; }

    public void deactivate() { this.isActive = IsActive.N; }

    /** 활성 상태 여부. */
    public boolean isActiveCompany() {
        return this.isActive == IsActive.Y;
    }
}
