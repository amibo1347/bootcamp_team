package com.team.intranet.controller.view;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.Position;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.Status;
import com.team.intranet.service.DeptService;
import com.team.intranet.service.MemberService;
import com.team.intranet.service.PositionService;
import com.team.intranet.session.MemberSession;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class SubAdminController {

    /////////////////////////////////////
    /// 의존성 주입
    private final MemberService memberService;
    private final DeptService deptService;
    private final PositionService positionService;

    /////////////////////////////////////
    /// 컨트롤러

    @GetMapping("/waitingList")
    @PreAuthorize("hasRole('SUB_ADMIN') or hasRole('ADMIN')")
    public String joinList(Model model,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {

        Long companyId = ms.getCompanyId();

        List<Member> waitingMembers = memberService.findWaitingMembers(companyId);

        List<Dept> depts = deptService.findAll(companyId);
        // 승인 시 직급 드롭다운: 대표(ADMIN / 시스템 직급) 제외 + level 오름차순 정렬.
        //   - 대표 임명은 MASTER 의 별도 도구 — 가입 승인 단계에서 부여 X.
        //   - level null 은 맨 뒤로.
        List<Position> positions = positionService.findAll(companyId).stream()
                .filter(p -> p.getRole() != Role.ADMIN && !p.isSystemDefault())
                .sorted(Comparator.comparing(Position::getPositionLevel,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        model.addAttribute("members", waitingMembers); // 승인 대기중인 회원
        model.addAttribute("depts", depts); // 부서 목록
        model.addAttribute("positions", positions); // 직급 목록

        // 새로 추가했음 확인!!!!!!
        model.addAttribute("count", waitingMembers.size()); // 승인 대기중인 회원 수
        return "subAdmin/waitingList";
    }

    @GetMapping("/memberList")
    @PreAuthorize("hasRole('SUB_ADMIN') or hasRole('ADMIN')")
    public String memberList(Model model,
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "positionId", required = false) Long positionId,
            @RequestParam(value = "status", required = false) List<Status> statuses,
            @RequestParam(value = "sort", defaultValue = "desc") String sort,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            HttpServletRequest request) {

        Long companyId = ms.getCompanyId();
        


        // 첫 진입(파라미터 없음) 시 기본값으로 JOIN(활성) 탭만 보여주도록 한다.
        List<Member> filteredMembers = memberService.findFilteredMembers(
        companyId, keyword, deptId,
        (statuses == null || statuses.isEmpty()) ? List.of(Status.JOIN) : statuses,
        positionId, sort
    );

        List<Dept> depts = deptService.findAll(companyId);
        List<Position> positions = positionService.findAll(companyId);

        model.addAttribute("members", filteredMembers); // 회원 목록
        model.addAttribute("depts", depts); // 부서 목록
        model.addAttribute("positions", positions); // 직급 목록
        model.addAttribute("keyword", keyword); // 검색 키워드
        model.addAttribute("selectedDeptId", deptId); // 선택된 부서 ID
        model.addAttribute("selectedPositionId", positionId); // 선택된 직급 ID
        // 템플릿 호환: 단일 선택 시에만 selectedStatus 채움 (다중 선택은 selectedStatuses 로 별도 노출)
        Status selectedStatus = (statuses != null && statuses.size() == 1) ? statuses.get(0) : null;
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("selectedStatuses", statuses);
        model.addAttribute("currentSort", sort); // 현재 정렬 방식
        String requestedWith = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equals(requestedWith)) {
            // 해당 html 파일 내의 fragment 경로만 리턴
            return "subAdmin/memberList :: memberListFragment";
        }

        return "subAdmin/memberList";
    }
}
