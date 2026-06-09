package com.team.intranet.dto.approval;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * GET /api/approval/my 응답 (프론트 my-inbox.js 의 response.items / response.total 사용).
 */
@Getter
@AllArgsConstructor
public class ApprovalPageResponse {
    private List<ApprovalRow> items;
    private int page;
    private int pageSize;
    private long total;
    private int totalPages;
}
