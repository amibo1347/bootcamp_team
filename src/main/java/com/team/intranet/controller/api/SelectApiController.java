package com.team.intranet.controller.api;

import java.util.Collections;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.member.Status;
import com.team.intranet.service.DeptService;
import com.team.intranet.service.MemberService;
import com.team.intranet.config.AuthenticatedMember;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * [캘린더 일정 모달 - 공유 대상 셀렉터 전용]
 * - GET /api/depts        : 로그인 회사의 부서 전체
 * - GET /api/members?q={} : 로그인 회사의 재직중 회원 이름 LIKE 검색
 *
 * 프론트(static/js/calendar/calendar-init.js)의 loadDeptAndMemberOptions / 검색 입력이 호출한다.
 * 응답 필드명은 프론트 기대 키(deptId/deptName, memberId/name/deptName/positionName) 와 일치시킨다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SelectApiController {

    private final MemberService memberService;
    private final DeptService deptService;

    /** 로그인 회사 부서 전체 (다중 선택용) */
    @GetMapping("/depts")
    public List<DeptOption> listDepts(
            @AuthenticatedMember MemberSession ms) {
        if (ms == null || ms.getCompanyId() == null) {
            return Collections.emptyList();
        }
        return deptService.findAll(ms.getCompanyId()).stream()
                .map(d -> new DeptOption(d.getDeptId(), d.getDeptName()))
                .toList();
    }

    /**
     * 로그인 회사 회원 이름 LIKE 검색 (재직중 JOIN 상태만).
     * 동명이인 구분을 위해 부서명·직급명을 함께 내려 프론트에서 옅은 회색으로 표시한다.
     * q 가 비었거나 null 이면 전체(상위 일부) 가 반환된다 — 검색어 입력 전 첫 로드도 같은 엔드포인트 사용.
     */
    @GetMapping("/members")
    public List<MemberOption> searchMembers(
            @RequestParam(value = "q", required = false) String q,
            @AuthenticatedMember MemberSession ms) {
        if (ms == null || ms.getCompanyId() == null) {
            return Collections.emptyList();
        }
        String keyword = (q == null) ? "" : q.trim();

        // MemberService.findFilteredMembers : keyword IS NULL/'' → 필터 미적용 분기 보유.
        List<Member> members = memberService.findFilteredMembers(
                ms.getCompanyId(),
                keyword,
                null,                       // deptId 필터 없음 (회사 전체에서 검색)
                List.of(Status.JOIN),       // 재직중만 — 대기/휴직/탈퇴는 공유 대상에서 제외
                null,                       // positionId 필터 없음
                "asc"
        );

        return members.stream()
                .map(m -> new MemberOption(
                        m.getMemberId(),
                        m.getName(),
                        m.getDept() != null ? m.getDept().getDeptName() : null,
                        m.getPosition() != null ? m.getPosition().getPositionName() : null
                ))
                .toList();
    }

    // ===== 응답 DTO (프론트 라벨에 필요한 최소 필드만 노출 — 비밀번호/이메일 등 누설 방지) =====

    public record DeptOption(Long deptId, String deptName) {}

    public record MemberOption(Long memberId, String name, String deptName, String positionName) {}
}
