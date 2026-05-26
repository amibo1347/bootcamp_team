package com.team.intranet.controller.view;

import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.dto.ArticleDto;
import com.team.intranet.dto.AttendanceDto;
import com.team.intranet.dto.AttendancePolicyDto;
import com.team.intranet.entity.Board;
import com.team.intranet.repository.ArticleRepository;
import com.team.intranet.service.AttendanceService;
import com.team.intranet.service.BoardService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final AttendanceService attendanceService;
    private final BoardService boardService;
    private final ArticleRepository articleRepository;

    @GetMapping({ "/", "/index", "" })
    public String index(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            Model model) {
        // 비로그인 진입 — 출퇴근 카드/공지 카드는 빈 상태로.
        if (ms == null) {
            model.addAttribute("hasTodayCheckIn", Boolean.FALSE);
            model.addAttribute("hasTodayCheckOut", Boolean.FALSE);
            model.addAttribute("todayAttendance", null);
            model.addAttribute("attendancePolicy", null);
            model.addAttribute("noticeBoardId", null);
            model.addAttribute("latestNotices", Collections.emptyList());
            return "index";
        }

        AttendanceDto today = attendanceService.findToday(ms.getMemberId()).orElse(null);
        AttendancePolicyDto policy = attendanceService.getPolicyDto(ms.getCompanyId());

        // 출퇴근 3상태:
        //  - 둘 다 없음 → [출근] 버튼만
        //  - 출근만   → [퇴근] 버튼만
        //  - 둘 다 있음 → [퇴근 취소] 버튼만 (실수 복구용)
        boolean hasClockIn = today != null && today.getClockInTime() != null;
        boolean hasClockOut = today != null && today.getClockOutTime() != null;
        model.addAttribute("hasTodayCheckIn", hasClockIn && !hasClockOut);
        model.addAttribute("hasTodayCheckOut", hasClockOut);
        model.addAttribute("todayAttendance", today);
        model.addAttribute("attendancePolicy", policy);

        // 시스템 디폴트 공지사항 게시판 보장 후 최신 3개 글을 SSR.
        //  - 회사별로 한 개의 안정적인 NOTICE 보드를 유지 (없으면 lazy 생성).
        //  - 일정처럼 즉시 변화하는 데이터가 아니므로 페이지 로드 시점 스냅샷으로 충분.
        Board notice = boardService.getOrCreateSystemNoticeBoard(ms.getCompanyId());
        List<ArticleDto> latestNotices = articleRepository
            .findByBoard_BoardIdAndIsDeletedFalse(notice.getBoardId(),
                PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "createdAt")))
            .getContent().stream().map(ArticleDto::from).toList();
        model.addAttribute("noticeBoardId", notice.getBoardId());
        model.addAttribute("latestNotices", latestNotices);
        return "index";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @GetMapping("/calendar")
    public String calendar(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            Model model) {
        // 일정 셀에서 본인 제외 처리를 위해 currentMemberId 를 meta 태그로 노출 (calendar.html)
        model.addAttribute("currentMemberId", ms != null ? ms.getMemberId() : null);
        return "calendar/calendar";
    }

    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }
}
