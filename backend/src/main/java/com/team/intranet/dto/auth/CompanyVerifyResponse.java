package com.team.intranet.dto.auth;

public record CompanyVerifyResponse(
        boolean verified,
        Long companyId,
        String logoPath
) {
    public static CompanyVerifyResponse notFound() {
        return new CompanyVerifyResponse(false, null, null);
    }

    public static CompanyVerifyResponse found(Long companyId, String logoPath) {
        return new CompanyVerifyResponse(true, companyId, logoPath);
    }
}
