package com.team.intranet.dto.approval;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * GET /api/approval/admin/pending, /admin/completed 응답.
 * 프론트는 items 와 total 만 사용.
 */
@Getter
@AllArgsConstructor
public class ApprovalListResponse {
    private List<ApprovalRow> items;
    private long total;
}
